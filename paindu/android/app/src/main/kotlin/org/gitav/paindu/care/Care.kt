package org.gitav.paindu.care

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.ActivityManager
import android.app.AppOpsManager
import android.app.admin.DevicePolicyManager
import android.app.usage.StorageStatsManager
import android.app.usage.UsageStatsManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.Process
import android.os.StatFs
import android.os.storage.StorageManager
import android.provider.DocumentsContract
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.time.LocalDate
import java.time.temporal.ChronoUnit

data class AppItem(val pkg: String, val label: String, val detail: String = "")

data class AccessGroup(val title: String, val help: String, val apps: List<AppItem>)

data class AppSize(val pkg: String, val label: String, val total: Long, val cache: Long, val lastUsed: Long)

data class Health(
    val ramTotal: Long,
    val ramAvailable: Long,
    val lowMemory: Boolean,
    val storageTotal: Long,
    val storageFree: Long,
    val batteryPercent: Int,
    val batteryHealth: String,
    val batteryTempC: Float?,
    val androidVersion: String,
    val securityPatch: String,
    val patchAgeDays: Long?,
)

data class FoundFile(val uri: Uri, val name: String, val size: Long, val path: String)

data class FolderReport(
    val scanned: Int,
    val checkedForMalware: Boolean,
    val malware: List<FoundFile>,
    val large: List<FoundFile>,
    val installers: List<FoundFile>,
    val duplicates: List<List<FoundFile>>,
    val truncated: Boolean,
)

/**
 * Device care: everything here only looks. Android does not let one app change,
 * stop or clean another, so paindu reports what it finds and opens the matching
 * system screen, where the user decides.
 */
object Care {
    private const val MAX_FILES = 3000
    private const val MAX_HASH_BYTES = 200L * 1024 * 1024
    private const val LARGE_BYTES = 100L * 1024 * 1024

    private val APP_STORES = setOf(
        "com.android.vending", "com.amazon.venezia", "com.sec.android.app.samsungapps",
        "com.huawei.appmarket", "com.xiaomi.mipicks", "com.xiaomi.market", "com.oppo.market",
        "com.heytap.market", "org.fdroid.fdroid", "com.aurora.store",
    )

    @Suppress("DEPRECATION")
    private fun userApps(ctx: Context): List<ApplicationInfo> =
        ctx.packageManager.getInstalledApplications(0).filter { app ->
            val system = (app.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            val updated = (app.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
            (!system || updated) && app.packageName != ctx.packageName
        }

    fun userAppCount(ctx: Context): Int = userApps(ctx).size

    // ------------------------------------------------------------ malware check

    /** Fingerprint every installed app's package files and compare with the database. */
    fun scanApps(ctx: Context, db: HashDb, progress: (Int, Int) -> Unit): List<AppItem> {
        val pm = ctx.packageManager
        val apps = userApps(ctx)
        val hits = ArrayList<AppItem>()
        for ((i, app) in apps.withIndex()) {
            progress(i, apps.size)
            val files = ArrayList<String>()
            app.sourceDir?.let { files.add(it) }
            app.splitSourceDirs?.let { files.addAll(it) }
            for (path in files) {
                try {
                    val hash = File(path).inputStream().use { HashDb.sha256(it) }
                    if (db.contains(hash)) {
                        hits.add(
                            AppItem(
                                app.packageName,
                                pm.getApplicationLabel(app).toString(),
                                "Matches a known malware file (${HashDb.hex(hash).take(16)}…)",
                            )
                        )
                        break
                    }
                } catch (e: Exception) {
                    // Unreadable package file: nothing we can say about it.
                }
            }
        }
        progress(apps.size, apps.size)
        return hits
    }

    // ------------------------------------------------------------ powerful access

    @Suppress("DEPRECATION")
    private fun installerOf(pm: PackageManager, pkg: String): String? = try {
        if (Build.VERSION.SDK_INT >= 30) pm.getInstallSourceInfo(pkg).installingPackageName
        else pm.getInstallerPackageName(pkg)
    } catch (e: Exception) {
        null
    }

    private fun holds(p: PackageInfo, permission: String, grantedOnly: Boolean): Boolean {
        val requested = p.requestedPermissions ?: return false
        val index = requested.indexOf(permission)
        if (index < 0) return false
        if (!grantedOnly) return true
        val flags = p.requestedPermissionsFlags ?: return false
        return index < flags.size && (flags[index] and PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0
    }

    /** Apps that hold the kinds of access malware and stalkerware typically abuse. */
    @Suppress("DEPRECATION")
    fun powerfulAccess(ctx: Context): List<AccessGroup> {
        val pm = ctx.packageManager
        fun item(pkg: String): AppItem? {
            if (pkg == ctx.packageName) return null
            return try {
                AppItem(pkg, pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString())
            } catch (e: PackageManager.NameNotFoundException) {
                null
            }
        }

        fun named(packages: List<String>): List<AppItem> =
            packages.distinct().mapNotNull { item(it) }.sortedBy { it.label.lowercase() }

        val groups = ArrayList<AccessGroup>()

        val am = ctx.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val accessibility = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .mapNotNull { it.resolveInfo?.serviceInfo?.packageName }
        groups.add(
            AccessGroup(
                "Can see and control the screen",
                "These apps run an accessibility service. That lets an app read what's on screen and tap " +
                    "for you. Screen readers and password managers need it; so does most banking malware. " +
                    "Keep only the ones you recognise and use.",
                named(accessibility),
            )
        )

        val dpm = ctx.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        groups.add(
            AccessGroup(
                "Device admin apps",
                "Device admin apps can lock or erase the phone and can make themselves hard to uninstall. " +
                    "Work profiles and Find My Device use this. An app you don't recognise here is a red flag.",
                named((dpm.activeAdmins ?: emptyList<ComponentName>()).map { it.packageName }),
            )
        )

        val listeners = (Settings.Secure.getString(ctx.contentResolver, "enabled_notification_listeners") ?: "")
            .split(':').mapNotNull { ComponentName.unflattenFromString(it)?.packageName }
        groups.add(
            AccessGroup(
                "Can read your notifications",
                "These apps can read every notification, including one-time codes from your bank. " +
                    "Watches and launchers need this; most other apps don't.",
                named(listeners),
            )
        )

        val packages = pm.getInstalledPackages(PackageManager.GET_PERMISSIONS).filter { p ->
            val app = p.applicationInfo
            app != null && (app.flags and ApplicationInfo.FLAG_SYSTEM) == 0 && p.packageName != ctx.packageName
        }

        fun holders(permission: String, grantedOnly: Boolean): List<AppItem> =
            named(packages.filter { holds(it, permission, grantedOnly) }.map { it.packageName })

        groups.add(
            AccessGroup(
                "Can read your text messages",
                "These apps have permission to read SMS, which includes sign-in codes.",
                holders("android.permission.READ_SMS", true),
            )
        )
        groups.add(
            AccessGroup(
                "Can see your location all the time",
                "These apps can get your location even when you aren't using them.",
                holders("android.permission.ACCESS_BACKGROUND_LOCATION", true),
            )
        )
        groups.add(
            AccessGroup(
                "May draw over other apps",
                "These apps have asked to show things on top of other apps. Chat bubbles use this; so do " +
                    "fake login screens. Android doesn't tell other apps whether it's switched on, so open " +
                    "an app here to check.",
                holders("android.permission.SYSTEM_ALERT_WINDOW", false),
            )
        )
        groups.add(
            AccessGroup(
                "May install other apps",
                "These apps have asked to install apps from outside the Play Store. Android doesn't tell " +
                    "other apps whether it's switched on, so open an app here to check.",
                holders("android.permission.REQUEST_INSTALL_PACKAGES", false),
            )
        )

        val sideloaded = packages.filter { (installerOf(pm, it.packageName) ?: "") !in APP_STORES }.map { it.packageName }
        groups.add(
            AccessGroup(
                "Installed from outside an app store",
                "These apps didn't come from an app store, so nobody reviewed them before they reached " +
                    "your phone. That's fine for apps you chose to install yourself.",
                named(sideloaded),
            )
        )
        return groups
    }

    // ------------------------------------------------------------ storage

    @Suppress("DEPRECATION")
    fun hasUsageAccess(ctx: Context): Boolean {
        val ops = ctx.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName)
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /** App sizes, biggest first. Needs usage access; returns an empty list without it. */
    fun appSizes(ctx: Context): List<AppSize> {
        val pm = ctx.packageManager
        val storage = ctx.getSystemService(Context.STORAGE_STATS_SERVICE) as StorageStatsManager
        val usage = ctx.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()
        val used = try {
            usage.queryAndAggregateUsageStats(now - 365L * 24 * 60 * 60 * 1000, now)
        } catch (e: Exception) {
            emptyMap<String, android.app.usage.UsageStats>()
        }
        val user = Process.myUserHandle()
        val out = ArrayList<AppSize>()
        for (app in userApps(ctx)) {
            try {
                val s = storage.queryStatsForPackage(StorageManager.UUID_DEFAULT, app.packageName, user)
                out.add(
                    AppSize(
                        app.packageName,
                        pm.getApplicationLabel(app).toString(),
                        s.appBytes + s.dataBytes,
                        s.cacheBytes,
                        used[app.packageName]?.lastTimeUsed ?: 0L,
                    )
                )
            } catch (e: Exception) {
                // No access, or the app was removed while we were looking.
            }
        }
        out.sortByDescending { it.total }
        return out
    }

    // ------------------------------------------------------------ phone health

    fun health(ctx: Context): Health {
        val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mem = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mem)

        val stat = StatFs(Environment.getDataDirectory().path)

        val battery = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = battery?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val percent = if (level >= 0 && scale > 0) level * 100 / scale else -1
        val healthText = when (battery?.getIntExtra(BatteryManager.EXTRA_HEALTH, 0)) {
            BatteryManager.BATTERY_HEALTH_GOOD -> "Good"
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheating"
            BatteryManager.BATTERY_HEALTH_DEAD -> "Worn out"
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Over voltage"
            BatteryManager.BATTERY_HEALTH_COLD -> "Too cold"
            else -> "Not reported"
        }
        val tenths = battery?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE) ?: Int.MIN_VALUE
        val temp = if (tenths == Int.MIN_VALUE) null else tenths / 10f

        val patch = Build.VERSION.SECURITY_PATCH ?: ""
        val age = try {
            ChronoUnit.DAYS.between(LocalDate.parse(patch), LocalDate.now())
        } catch (e: Exception) {
            null
        }

        return Health(
            ramTotal = mem.totalMem,
            ramAvailable = mem.availMem,
            lowMemory = mem.lowMemory,
            storageTotal = stat.totalBytes,
            storageFree = stat.availableBytes,
            batteryPercent = percent,
            batteryHealth = healthText,
            batteryTempC = temp,
            androidVersion = Build.VERSION.RELEASE ?: "",
            securityPatch = patch,
            patchAgeDays = age,
        )
    }

    // ------------------------------------------------------------ folder check

    /**
     * Look through a folder the user picked: known malware, very large files,
     * leftover app installers and exact duplicates. Nothing is changed.
     */
    fun scanFolder(ctx: Context, tree: Uri, db: HashDb?, progress: (Int) -> Unit): FolderReport {
        val files = ArrayList<FoundFile>()
        var truncated = false

        fun walk(dir: DocumentFile, path: String, depth: Int) {
            for (f in dir.listFiles()) {
                if (files.size >= MAX_FILES) {
                    truncated = true
                    return
                }
                val name = f.name ?: continue
                if (f.isDirectory) {
                    if (depth < 6) walk(f, "$path$name/", depth + 1)
                } else {
                    files.add(FoundFile(f.uri, name, f.length(), path))
                    if (files.size % 25 == 0) progress(files.size)
                }
            }
        }

        val root = DocumentFile.fromTreeUri(ctx, tree)
        if (root != null) walk(root, "", 0)

        val hashes = HashMap<Uri, ByteArray>()
        fun hashOf(f: FoundFile): ByteArray? {
            hashes[f.uri]?.let { return it }
            if (f.size <= 0 || f.size > MAX_HASH_BYTES) return null
            return try {
                ctx.contentResolver.openInputStream(f.uri)?.use { HashDb.sha256(it) }?.also { hashes[f.uri] = it }
            } catch (e: Exception) {
                null
            }
        }

        val malware = ArrayList<FoundFile>()
        if (db != null) {
            for (f in files) {
                val h = hashOf(f) ?: continue
                if (db.contains(h)) malware.add(f)
            }
        }

        val duplicates = ArrayList<List<FoundFile>>()
        for ((_, sameSize) in files.filter { it.size > 0 }.groupBy { it.size }) {
            if (sameSize.size < 2) continue
            val byHash = sameSize.mapNotNull { f -> hashOf(f)?.let { HashDb.hex(it) to f } }.groupBy({ it.first }, { it.second })
            for ((_, same) in byHash) if (same.size > 1) duplicates.add(same)
        }
        duplicates.sortByDescending { it[0].size * (it.size - 1) }

        return FolderReport(
            scanned = files.size,
            checkedForMalware = db != null,
            malware = malware,
            large = files.filter { it.size >= LARGE_BYTES }.sortedByDescending { it.size }.take(20),
            installers = files.filter { f ->
                val n = f.name.lowercase()
                n.endsWith(".apk") || n.endsWith(".xapk") || n.endsWith(".apks")
            }.sortedByDescending { it.size },
            duplicates = duplicates.take(30),
            truncated = truncated,
        )
    }

    fun deleteFile(ctx: Context, uri: Uri): Boolean = try {
        DocumentsContract.deleteDocument(ctx.contentResolver, uri)
    } catch (e: Exception) {
        false
    }

    // ------------------------------------------------------------ system screens

    private fun start(ctx: Context, intent: Intent, fallback: Intent? = null): Boolean {
        for (candidate in listOfNotNull(intent, fallback)) {
            try {
                ctx.startActivity(candidate.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return true
            } catch (e: Exception) {
                // Try the fallback, if there is one.
            }
        }
        return false
    }

    private val settingsHome get() = Intent(Settings.ACTION_SETTINGS)

    fun openApp(ctx: Context, pkg: String) =
        start(ctx, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg")), settingsHome)

    fun openUsageAccess(ctx: Context) = start(ctx, Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS), settingsHome)

    fun openStorage(ctx: Context) =
        start(ctx, Intent(StorageManager.ACTION_MANAGE_STORAGE), Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS))

    fun openBattery(ctx: Context) = start(ctx, Intent(Intent.ACTION_POWER_USAGE_SUMMARY), settingsHome)

    fun openSystemUpdate(ctx: Context) =
        start(ctx, Intent("android.settings.SYSTEM_UPDATE_SETTINGS"), Intent(Settings.ACTION_DEVICE_INFO_SETTINGS))

    fun openPlayProtect(ctx: Context) = start(
        ctx,
        Intent("com.google.android.gms.settings.VERIFY_APPS_SETTINGS").setPackage("com.google.android.gms"),
        Intent(Settings.ACTION_SECURITY_SETTINGS),
    )
}
