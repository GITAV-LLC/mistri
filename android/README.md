# mistri for Android

A web browser with mistri Shield built in, plus Device care. Status: **prototype**. It compiles and its logic is unit-tested in CI; it has not yet been tested on a phone or released.

## What it does

**Browser** (pages are rendered by Android System WebView, the Chromium engine Google keeps updated):

| Feature | How |
|---|---|
| Ad and tracker blocking | The same lists and whole-domain rules as mistri Shield, applied to every request a page makes. Ads and trackers are blocked only as third parties |
| Malware and scam sites | abuse.ch URLhaus and uBlock Badware. A warning page with "Go back to safety" and a this-session override. Google Safe Browsing stays on alongside |
| Pop-up guard | A new tab needs a real tap, one tap opens one tab, and a pop-up that lands on a blocked site is closed |
| Cookie auto-reject | The extension's own `cookie_reject.js`, injected at the start of every page and frame. Never clicks Accept |
| Empty ad boxes hidden | The extension's own `cosmetic.css` |
| Pause on this site | One switch in the Shield sheet |
| Third-party cookies | Refused |
| Downloads | Fingerprinted and checked against known malware before they reach the Downloads folder |
| Reading | Reader view and Read aloud (Mozilla Readability + the phone's text-to-speech), page text size |

The page scripts are not copies: the build reads them straight from [`../extension/content`](../extension/content), so the extension and the app can't drift apart.

**Device care** reports and never acts on its own. Android does not let one app stop, clean or change another, so each finding opens the matching Android settings screen.

| Card | What it shows |
|---|---|
| Check apps for malware | SHA-256 of every installed app package against abuse.ch MalwareBazaar |
| Apps with powerful access | Accessibility services, device admins, notification readers, SMS, background location, overlay, install-other-apps, and apps from outside an app store |
| Storage | Free space; with optional usage access, the largest apps, their cache, and apps unused for three months |
| Check a folder | Known malware, very large files, leftover installers and exact duplicates in a folder you pick. Deletes only on request, one file at a time |
| Phone health | Memory, battery condition and temperature, age of the last security update |

## Honest limits

- **No memory booster or task killer.** Since Android 14 an app can only end its own background processes, and killing apps makes a phone slower. mistri shows what's using space and links to the app's settings instead.
- **No clearing other apps' caches.** Android doesn't allow it. mistri shows the sizes and opens the app's settings page.
- **The malware check finds known files only.** It is a fingerprint match, not behaviour analysis. It complements Google Play Protect; it doesn't replace it.
- **The signature database needs a one-time setup** before it exists (below). Until then the scanner says so and downloads are saved unchecked.
- **Blocking is inside this browser only.** Device-wide ad blocking needs a VPN service, which Google Play does not allow to interfere with ads.
- **Whole-domain rules only**, as in the extension: ads served from a site's own domain aren't blocked.
- **Not a full Chrome.** No extensions, sync, private tabs, find-in-page or desktop-site mode yet. Camera, microphone and location requests from pages are declined.
- **Third-party test is approximate.** Without the full Public Suffix List, unusual country-code domains may be treated as the same site, which means not blocked.

## Build

Needs JDK 17 and the Android SDK (platform 35).

```
cd android
./gradlew testDebugUnitTest assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/`. CI (`.github/workflows/android.yml`) does the same on every push and keeps the APK as a build artifact.

## Malware signatures: one-time setup

The app downloads one file, `malware-sha256.bin`, from this repository's `signatures` release. `.github/workflows/signatures.yml` rebuilds it daily from the MalwareBazaar hash export, which needs a free abuse.ch account:

1. Get an Auth-Key at https://auth.abuse.ch/.
2. Add it to this repository as the secret `ABUSE_CH_AUTH_KEY`.
3. Sign in at https://bazaar.abuse.ch/export/, copy the address of the full SHA-256 export, replace your key in it with `{key}`, and save it as the repository variable `MALWAREBAZAAR_EXPORT_URL`.
4. Run the **Malware signatures** workflow once from the Actions tab.

## Before a Play Store release

- Raise `targetSdk` to whatever Google Play currently requires.
- Declare `QUERY_ALL_PACKAGES` in the Play Console (antivirus and browser are both permitted uses) and complete the Data safety form: no data collected.
- Set up release signing (Play App Signing) and a release workflow.
- Test on real phones, with TalkBack, at the largest font size, and with Google's Accessibility Scanner.

## Layout

```
app/src/main/kotlin/org/gitav/mistri/
  shield/    FilterLists (port of extension/lists.js), ShieldEngine
  browser/   Browser (tabs, WebView clients), Downloader
  care/      Care (device checks), HashDb, Signatures
  ui/        Compose screens, theme, ⓘ help texts
tools/build_signatures.py   builds the signature file
```
