# Privacy Policy

**mistri and mistri Shield, by GITAV LLC**
Effective 10 November 2026

The short version: **neither mistri nor mistri Shield collects, stores or shares any information about you.** There are no accounts, no analytics, no advertising and no tracking. Everything they do happens on your own computer.

This policy covers two products:

- **mistri Shield**, the Chrome extension
- **mistri**, the Windows app

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

## Children

Neither product collects information from anyone, including children.

## Changes

If this policy changes, the new version is published at this address with a new effective date. The history of every change is public in this repository.

## Contact

Questions or concerns: open an issue at https://github.com/GITAV-LLC/mistri/issues
