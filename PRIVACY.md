# Privacy Policy

**mistri, mistri Shield and mistri for Android, by GITAV LLC**
Effective 15 November 2026

The short version: **none of the mistri products collects, stores or shares any information about you.** There are no accounts, no analytics, no advertising and no tracking. Everything they do happens on your own computer or phone.

This policy covers three products:

- **mistri Shield**, the Chrome extension
- **mistri**, the Windows app
- **mistri for Android**, the browser and device-care app (in development)

## mistri Shield (Chrome extension)

### What it does not do

- It does not collect, record or transmit your browsing history, the pages you visit, what you type, or any personal information.
- It does not use analytics, crash reporting or any third-party tracking.
- It does not sell, share or transfer any data, because it has none to share.

### What stays on your computer

These are saved in Chrome's local extension storage, on your device only:

- **Your settings**: which protections are switched on.
- **Sites you've paused**: the site names you choose to pause protection on.
- **The downloaded filter lists**: public lists of ad, tracker and malware domains.
- **The address of a blocked dangerous page, briefly**, so the warning page can name the site. It is held in session memory and cleared when the tab closes.

Removing the extension deletes all of this.

### How it uses its permissions

- **Access to all websites**: blocking and cookie rejection have to work on every site you visit. The extension's scripts run inside the page, on your computer, to click a cookie banner's "Reject" button and to stop unwanted pop-ups. Nothing they see leaves your computer.
- **declarativeNetRequest**: Chrome itself blocks requests to listed domains. The extension does not read your traffic.
- **scripting**: runs the cookie-reject and pop-up guard scripts described above.
- **storage, unlimitedStorage**: keeps your settings and the filter lists, which are large, on your computer.
- **alarms**: refreshes the filter lists once a day.
- **webNavigation**: notices when a page opens a pop-up tab, so the extension can close it if it lands on a blocked site.
- **activeTab**: when you click the toolbar icon, lets the popup show how many requests were blocked on the current page.

### Network requests it makes

Once a day, and when you press **Update lists**, the extension downloads public filter lists from:

- easylist.to and raw.githubusercontent.com (EasyList, EasyPrivacy, uBlock Origin filters)
- pgl.yoyo.org (Peter Lowe's list)
- urlhaus.abuse.ch (URLhaus)

These are ordinary file downloads. Like any web server, those sites can see your IP address and the time of the request. The extension sends them nothing else.

## mistri (Windows app)

mistri runs entirely on your PC and has no telemetry. It keeps its settings, undo history, logs and quarantined files in `%LOCALAPPDATA%\mistri`, which you can delete at any time. The uninstaller offers to remove it.

It connects to the internet only for these things:

- **Update check**, once a day when the app opens: a request to `api.github.com` for the latest mistri release. You can turn this off on the Home page under **About mistri**. Updates download from `github.com` only after you click **Update now**.
- **ClamAV**, only if you choose to install it: Cisco's installer downloads from `github.com`, and its malware signatures from ClamAV's own servers.
- **Microsoft tools that mistri starts at your request**: Microsoft Defender signature updates, Windows repair (DISM) and app updates through winget. These connect to Microsoft and app publishers exactly as they would if you ran them yourself, under those services' own privacy terms.

## mistri for Android

mistri for Android is a web browser with mistri Shield built in, plus a Device care section. GITAV has no servers for it and receives nothing from it.

### What it does not do

- It does not collect, record or transmit your browsing history, the pages you visit, what you type, your list of installed apps, your files, or any personal information.
- It does not use analytics, crash reporting, advertising or any third-party tracking code.
- It does nothing in the background. Block lists and malware signatures are refreshed only while the app is open.

### What stays on your phone

These are kept in the app's private storage, on your device only. Clearing the app's storage or uninstalling it deletes all of them.

- **Your settings**, bookmarks, history, open tabs and the sites you've paused protection on.
- **Website data**: cookies, cache and site storage, as in any browser. **Settings › Clear browsing data** removes them. Third-party cookies are refused.
- **The downloaded block lists and malware signatures.**

### How it uses its permissions

- **Internet**: to browse, and to download the public block lists and the signature file.
- **See all installed apps** (`QUERY_ALL_PACKAGES`): Device care checks each installed app's package against known malware and lists apps that hold powerful access. The check happens on the phone. The list of apps is never sent anywhere.
- **Usage access** (optional, off until you switch it on in Android's settings): shows how much space each app takes and when you last used it. This also stays on the phone.
- **Folders you pick**: "Check a folder" can read, and delete files from, only the folder you choose in Android's folder picker, and deletes a file only when you press Delete and confirm.

mistri for Android cannot stop, change or clean other apps, and does not try to. It shows what it finds and opens the matching Android settings screen for you to decide.

### Network requests it makes

Apart from loading the websites you visit:

- **Block lists**, at most once a day while the app is open, and when you press **Update now**: the same public lists as mistri Shield, from easylist.to, raw.githubusercontent.com, pgl.yoyo.org and urlhaus.abuse.ch.
- **Malware signatures**, only when you press the button in Device care: one file from `github.com` (the mistri repository's releases), built from the abuse.ch MalwareBazaar database.

These are ordinary file downloads. Those servers can see your IP address and the time of the request, as any web server can. mistri sends them nothing else. Files and apps are checked by comparing fingerprints on your phone; no file, fingerprint or app name is uploaded.

### Google Safe Browsing

Web pages are displayed by Android System WebView, the Chromium engine that is part of Android. It includes Google Safe Browsing, which warns about dangerous sites and is on by default. To do that, WebView may send Google partial, hashed information about the addresses you visit, under [Google's privacy policy](https://policies.google.com/privacy). You can turn it off under **Settings › Shield › Google Safe Browsing**; mistri's own lists keep working without it.

### Read aloud

Read aloud uses the text-to-speech voice installed on your phone. mistri passes it the article text; how that voice processes text is governed by its own provider's terms.

## Children

None of the products collects information from anyone, including children.

## Changes

If this policy changes, the new version is published at this address with a new effective date. The history of every change is public in this repository.

## Contact

Questions or concerns: open an issue at https://github.com/GITAV-LLC/mistri/issues
