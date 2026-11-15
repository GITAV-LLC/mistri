# Privacy Policy

**paindu, by GITAV LLC**
Effective 15 November 2026

The short version: **paindu does not collect, store or share any information about you.** There are no accounts, no analytics, no advertising and no tracking. GITAV has no servers for paindu and receives nothing from it.

This policy covers **paindu for Android**, the web browser with built-in Shield and Device care.

## What it does not do

- It does not collect, record or transmit your browsing history, the pages you visit, what you type, your list of installed apps, your files, or any personal information.
- It does not use analytics, crash reporting, advertising or any third-party tracking code.
- It does nothing in the background. Block lists and malware signatures are refreshed only while the app is open.

## What stays on your phone

These are kept in the app's private storage, on your device only. Clearing the app's storage or uninstalling it deletes all of them.

- **Your settings**, bookmarks, history, open tabs and the sites you've paused protection on.
- **Website data**: cookies, cache and site storage, as in any browser. **Settings › Privacy › Clear browsing data** removes them. Third-party cookies are refused.
- **The downloaded block lists and malware signatures.**

## How it uses its permissions

- **Internet**: to browse, and to download the public block lists and the signature file.
- **See all installed apps** (`QUERY_ALL_PACKAGES`): Device care checks each installed app's package against known malware and lists apps that hold powerful access. The check happens on the phone. The list of apps is never sent anywhere.
- **Usage access** (optional, off until you switch it on in Android's settings): shows how much space each app takes and when you last used it. This also stays on the phone.
- **Folders you pick**: "Check a folder" can read, and delete files from, only the folder you choose in Android's folder picker, and deletes a file only when you press Delete and confirm.

paindu cannot stop, change or clean other apps, and does not try to. It shows what it finds and opens the matching Android settings screen for you to decide.

## Network requests it makes

Apart from loading the websites you visit:

- **Block lists**, at most once a day while the app is open, and when you press **Update now**: public lists from easylist.to, raw.githubusercontent.com, pgl.yoyo.org and urlhaus.abuse.ch.
- **Malware signatures**, only when you press the button in Device care: one file from `github.com` (GITAV's repository releases), built from the abuse.ch MalwareBazaar database.

These are ordinary file downloads. Those servers can see your IP address and the time of the request, as any web server can. paindu sends them nothing else. Files and apps are checked by comparing fingerprints on your phone; no file, fingerprint or app name is uploaded.

## Google Safe Browsing

Web pages are displayed by Android System WebView, the Chromium engine that is part of Android. It includes Google Safe Browsing, which warns about dangerous sites and is on by default. To do that, WebView may send Google partial, hashed information about the addresses you visit, under [Google's privacy policy](https://policies.google.com/privacy). You can turn it off under **Settings › Shield › Google Safe Browsing**; paindu's own lists keep working without it.

## Read aloud

Read aloud uses the text-to-speech voice installed on your phone. paindu passes it the article text; how that voice processes text is governed by its own provider's terms.

## Children

paindu does not collect information from anyone, including children.

## Changes

If this policy changes, the new version is published at this address with a new effective date. The history of every change is public in this repository.

## Contact

Questions or concerns: open an issue at https://github.com/GITAV-LLC/mistri/issues
