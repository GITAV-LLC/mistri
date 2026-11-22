# paindu by GITAV

<img src="branding/paindu-icon-1024.png" width="96" alt="paindu icon">

**paindu** (پینڈو) is a private, careful web browser. A *paindu* is someone from the village; GITAV stands for *Gahandaran – It Takes A Village*. PB is Punjab, and it is also paindu browser.

paindu is a separate product from [mistri](../README.md), GITAV's Windows repair tool and Chrome extension. It reuses mistri Shield's open-source blocking engine and nothing else.

| Platform | Status |
|---|---|
| Android | Prototype in [`android/`](android/). Builds in CI and passes its tests on an emulator. Not yet tested on a phone or released |
| Windows | Planned: a Chromium-based browser |

## Brand

- **Name:** always lowercase, `paindu`. Full form: `paindu by GITAV`.
- **Icon:** the Urdu letter pe (پ), the first letter of paindu and the P of PB, drawn as a furrow with its three dots as seeds. A small G in the lower right, drawn as one of the rings of the GITAV logo, marks it as GITAV's. Everything is generated from one definition by [`branding/make_icons.py`](branding/make_icons.py).
- **Colours:** the Punjabi countryside.

| Role | Hex |
|---|---|
| Deep green (icon ground) | `#0F4A2A` |
| Field green (primary) | `#1F5A32` |
| Sarson mustard (accent) | `#E0A82E` |
| Clay (warnings) | `#A1401C` |
| Cream (ground) | `#F7F2E7` |
| Soil (ink) | `#2A2118` |

- **Principles** (shared with every GITAV product): lightweight and transparent, built on open-source parts with credit to each; the user stays in control and nothing happens without a button press; no telemetry, accounts, ads or upsells; a calm interface with explanations behind ⓘ buttons; honest about limits.
- **Interface rules:** every screen is split into clearly bordered sections with a tinted title band; anything that can be pressed is a button, a switch or a bordered row, never plain text with an arrow; Settings and Device care are divided into tabs.

## paindu for Android

paindu itself is deliberately small: tabs, bookmarks, downloads and Shield. Everything else is a **plugin** that is off until you switch it on. The first time paindu opens, a four-step walkthrough offers the choices below; each step can be skipped, and paindu never asks again or sends notifications.

**Browser** (pages are rendered by Android System WebView, the Chromium engine Google keeps updated):

| Feature | How |
|---|---|
| Ad and tracker blocking | EasyList, EasyPrivacy and Peter Lowe's list as whole-domain rules, applied to every request a page makes. Blocked only as third parties |
| Malware and scam sites | abuse.ch URLhaus and uBlock Badware. A warning page with "Go back to safety" and a this-session override. Google Safe Browsing stays on alongside |
| Pop-up guard | A new tab needs a real tap, one tap opens one tab, and a pop-up that lands on a blocked site is closed |
| Cookie auto-reject | Presses "Reject" on about 25 consent systems, in many languages. Never presses Accept |
| Empty ad boxes hidden | A small, conservative stylesheet |
| Pause on this site | One switch behind the shield button |
| Third-party cookies | Refused |
| Downloads | Fingerprinted and checked against known malware before they reach the Downloads folder |
| Reading | Reader view and Read aloud (Mozilla Readability + the phone's text-to-speech), page text size |
| Sleeping tabs | Tabs you aren't looking at are put to sleep, keeping only their address and position. The decision goes by free memory, Android's memory warnings, the phone's RAM and how long since each tab was used, not by a fixed tab count. Restored tabs start asleep |
| Default browser | One button in the walkthrough and in Settings › Browsing. paindu never asks on its own |
| Screen lock | Optional: Android's own fingerprint / face / PIN / pattern prompt before paindu opens. Locks again after a minute out of sight |
| Backup | Settings, bookmarks and open tabs are included in Android's backup to the user's Google Drive, only when that backup is end-to-end encrypted with the screen lock. Cookies and site data are never included |

**Plugins** (all off by default; see [PLUGINS.md](PLUGINS.md) for the draft protocol):

| Plugin | What it adds | Status |
|---|---|---|
| whush | Private tabs with their own cookies, storage and cache, deleted when the last whush tab closes. Not in history, not restored, not in screenshots | Available |
| History | paindu keeps no history without it. Adds history, address-bar suggestions and "continue where you left off". Switching it off deletes the history | Available |
| Device care | The Security, Storage and Health checks below | Available |
| Passwords | Encrypted vault unlocked by the screen lock; breach check on import; no plain-text export | Planned |
| Bring your own VPN | Route only paindu's traffic through a WireGuard server the user chooses. GITAV runs no servers | Planned |

**Device care** (a plugin) reports and never acts on its own. Android does not let one app stop, clean or change another, so each finding has a button that opens the matching Android settings screen.

| Tab | Sections |
|---|---|
| Security | Check apps for malware (SHA-256 of every installed package against abuse.ch MalwareBazaar); apps with powerful access (accessibility, device admin, notification readers, SMS, background location, overlay, install-other-apps, sideloaded) |
| Storage | Free space; with optional usage access, the largest apps, their cache, and apps unused for three months; "Check a folder" for known malware, very large files, leftover installers and exact duplicates |
| Health | Memory, battery condition and temperature, age of the last security update |

**Accessibility:** TalkBack labels and headings throughout; switches and choices announced with their role and state; 48dp touch targets; every colour pair meets WCAG AA contrast; follows the phone's font size and dark theme; page text size; Reader view; Read aloud.

### Honest limits

- **No memory booster or task killer.** Since Android 14 an app can only end its own background processes, and killing apps makes a phone slower. paindu shows what's using space and opens the app's settings instead.
- **No clearing other apps' caches.** Android doesn't allow it.
- **The malware check finds known files only.** It is a fingerprint match, not behaviour analysis. It complements Google Play Protect; it doesn't replace it.
- **The signature database needs a one-time setup** before it exists (below). Until then the scanner says so and downloads are saved unchecked.
- **Blocking is inside this browser only.** Device-wide ad blocking needs a VPN service, which Google Play does not allow to interfere with ads.
- **Whole-domain rules only:** ads served from a site's own domain, such as YouTube's, aren't blocked.
- **Not a full Chrome.** No extensions, sync, find-in-page or desktop-site mode yet. Camera, microphone and location requests from pages are declined.
- **whush is separation, not invisibility.** It doesn't hide browsing from the network, an employer or the sites visited. It needs a recent Android System WebView; on older ones the plugin says so and stays off.
- **Sites are already kept apart, with one caveat.** One site can never read another site's cookies, and third-party cookies are refused, which removes the usual cross-site tracking route. The browser engine also keys its cache by site. A site you visit directly still keeps its own cookies between visits unless you use whush or clear browsing data.
- **Sleeping tabs can't tell if a background tab is playing audio.** Such a tab may be put to sleep under memory pressure.
- **The screen lock has not been exercised on a phone with a lock set.** The emulator has none; only the refusal path is tested.
- **Android's backup is controlled by Android**, not by a paindu switch: it runs if the user has backup on for their Google account.
- **Third-party test is approximate.** Without the full Public Suffix List, unusual country-code domains may be treated as the same site, which means not blocked.

### Build

Needs JDK 17 and the Android SDK (platform 35).

```
cd paindu/android
./gradlew testDebugUnitTest assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/`. CI (`.github/workflows/paindu-android.yml`) runs the unit tests, lint and build, then starts the app on an Android emulator and checks that every screen and tab opens and that Shield works inside a real WebView.

### Malware signatures: one-time setup

The app downloads one file, `malware-sha256.bin`, from the repository's `signatures` release. `.github/workflows/signatures.yml` rebuilds it daily from the MalwareBazaar hash export, which needs a free abuse.ch account:

1. Get an Auth-Key at https://auth.abuse.ch/.
2. Add it to the repository as the secret `ABUSE_CH_AUTH_KEY`.
3. Sign in at https://bazaar.abuse.ch/export/, copy the address of the full SHA-256 export, replace your key in it with `{key}`, and save it as the repository variable `MALWAREBAZAAR_EXPORT_URL`.
4. Run the **Malware signatures** workflow once from the Actions tab.

### Before a Play Store release

- Raise `targetSdk` to whatever Google Play currently requires.
- Declare `QUERY_ALL_PACKAGES` in the Play Console (antivirus apps and browsers are both permitted uses) and complete the Data safety form: no data collected.
- Set up release signing (Play App Signing) and a release workflow.
- Test on real phones, with TalkBack, at the largest font size, and with Google's Accessibility Scanner.

### Layout

```
branding/                     make_icons.py, icon PNGs
android/app/src/main/
  kotlin/org/gitav/paindu/
    shield/                   FilterLists (list parser), ShieldEngine
    browser/                  Browser (tabs, WebView clients), Downloader
    care/                     Care (device checks), HashDb, Signatures
    plugins/                  the catalogue of built-in plugins
    ui/                       Compose screens, theme, ⓘ help texts
  assets/                     page scripts (cookie reject, pop-up guard, ad-box CSS), Readability.js
android/tools/                build_signatures.py, ci_gradle.sh
PLUGINS.md                    draft plugin protocol
PRIVACY.md
```

## Open-source components

Chromium / Android System WebView (BSD), Jetpack Compose, Material 3 and AndroidX (Apache 2.0), Kotlin (Apache 2.0), Mozilla Readability (Apache 2.0), EasyList and EasyPrivacy (GPLv3 / CC BY-SA 3.0), Peter Lowe's Blocklist, abuse.ch URLhaus and MalwareBazaar (CC0), uBlock Origin uAssets (GPLv3), Material Symbols (Apache 2.0). The hash-based scanner follows the approach proven by Hypatia (Divested Computing); no Hypatia code is used. The same list is shown in the app under Settings › About.

## License

MIT © GITAV LLC
