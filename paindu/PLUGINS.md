# The paindu plugin protocol

**Status: draft 0, for discussion.** Only built-in plugins exist today. Nothing in this document is final.

## Why plugins

paindu itself should stay small enough to understand at a glance: tabs, bookmarks, downloads and Shield. Everything else is a plugin that is **off until the user switches it on**, costs nothing while it is off, and can be switched off again with its data removed.

## Principles

1. **The user decides.** A plugin never installs, enables or updates itself. No plugin may show a notification, a badge or a prompt asking to be turned on.
2. **Declared, narrow powers.** A plugin states the capabilities it needs. paindu shows them in plain words before the plugin is switched on, and gives it nothing else.
3. **No phoning home by default.** A plugin gets no network access of its own unless it declares the hosts it will contact, and those are shown to the user.
4. **Removable without trace.** Switching a plugin off deletes what it stored, unless the user chooses to keep it.
5. **Open.** The protocol, the reference plugins and the marketplace index are public and MIT-licensed.

## Three kinds of plugin

| Kind | Runs as | Can use | Distribution |
|---|---|---|---|
| **Built-in** | Kotlin compiled into paindu | Any capability, including Android features | Ships with paindu. Exists today |
| **Script** | JavaScript and declarative rules in paindu's sandbox | Web-facing capabilities only (see below) | The paindu marketplace. Planned |
| **Companion app** | A separate Android app, in its own sandbox | Anything Android allows that app, plus the capabilities paindu grants over a bound service | Any app store. Planned |

Why three: Google Play does not allow an app to download compiled code, but that restriction does not apply to code running in an interpreter such as JavaScript in a WebView. So plugins that only touch web pages can be downloaded as scripts, and plugins that need Android features must either ship inside paindu or be their own app.

## Built-in plugins (today)

Defined in [`android/app/src/main/kotlin/org/gitav/paindu/plugins/Plugins.kt`](android/app/src/main/kotlin/org/gitav/paindu/plugins/Plugins.kt).

| id | Name | Status |
|---|---|---|
| `whush` | whush: private tabs in a separate compartment that is wiped when they close | Available |
| `history` | History, address-bar suggestions, "continue where you left off" | Available |
| `care` | Device care | Available |
| `passwords` | Encrypted password vault | Planned |
| `vpn` | Bring your own WireGuard | Planned |

Each has a name, a one-line tagline, an "about" text that states what it keeps and its limits, and an on/off state stored as `plugin.<id>`.

## The manifest (script and companion plugins)

A plugin is described by one `plugin.json`:

```json
{
  "protocol": 0,
  "id": "org.example.linkcleaner",
  "name": "Link cleaner",
  "version": "1.0.0",
  "publisher": { "name": "Example", "url": "https://example.org" },
  "tagline": "Strips tracking tags from links you open.",
  "about": "What it does, what it stores, and what it can't do, in plain words.",
  "license": "MIT",
  "source": "https://github.com/example/linkcleaner",
  "kind": "script",
  "capabilities": ["navigation.rewrite"],
  "hosts": [],
  "entry": "main.js",
  "sha256": { "main.js": "…" }
}
```

- `id` is reverse-domain and permanent.
- `capabilities` is the complete list; anything not listed is refused at run time.
- `hosts` is the complete list of hosts the plugin itself may contact. An empty list means the plugin has no network access.
- `sha256` pins every file. paindu refuses a plugin whose files don't match.

## Capabilities

Each capability maps to one sentence shown to the user.

| Capability | Lets the plugin | Shown to the user as | Script | Companion |
|---|---|---|---|---|
| `page.script` | Run a content script on pages matching declared patterns | "Read and change the pages you visit on: …" | yes | yes |
| `page.style` | Add a stylesheet to matching pages | "Change how pages look on: …" | yes | yes |
| `request.block` | Add whole-domain block rules to Shield | "Block more sites and trackers" | yes | yes |
| `navigation.rewrite` | Rewrite an address before it loads (same host only) | "Tidy the addresses of pages you open" | yes | yes |
| `menu.item` | Add one item to the browser menu | "Add a menu item" | yes | yes |
| `home.card` | Add one card to the home screen | "Add a card to the home screen" | yes | yes |
| `settings.section` | Add a section under Plugins | (none) | yes | yes |
| `storage.local` | Keep up to 5 MB of its own data on the phone | "Store its own data on this phone" | yes | yes |
| `tabs.read` | See the address and title of open tabs | "See which pages you have open" | no | yes |
| `history.read` | Read history (only if the History plugin is on) | "Read your browsing history" | no | yes |
| `proxy` | Route paindu's traffic through a local proxy it provides | "Carry all of paindu's traffic" | no | yes |
| `credentials` | Offer to fill and save sign-in forms | "See and fill sign-in forms" | no | yes |

Deliberately absent: reading cookies, reading other plugins' data, changing Shield's warning page, making paindu the default anything, showing notifications.

## Script plugins: the sandbox

- Content scripts run in an isolated world on matching pages, at document start, like browser-extension content scripts.
- Background logic runs in a JavaScript sandbox with no DOM and no Android access. It talks to paindu only through a small message API: `paindu.storage`, `paindu.menu`, `paindu.home`, `paindu.rules`.
- No `eval` of fetched code, no remote scripts: everything a plugin runs is in its pinned package.
- Network access from the background sandbox is limited to the declared `hosts`.

## Companion-app plugins: the wire protocol

- The companion app declares a service with the action `org.gitav.paindu.PLUGIN` and a `<meta-data>` pointing at its `plugin.json`.
- paindu discovers companions only when the user opens Plugins, never in the background.
- When the user switches one on, paindu binds to the service. Messages are versioned JSON over a `Messenger`: `hello`, `capability.request`, `event.*`, `call.*`.
- paindu verifies the companion's signing certificate against the fingerprint recorded when the user first switched it on, and refuses a companion whose signature changes.
- The bring-your-own-WireGuard plugin is the intended first use of `proxy`: the companion runs a userspace WireGuard client and exposes a local SOCKS proxy; paindu points its web engine at that proxy and at nothing else.

## Marketplace

- A static, signed JSON index in a public repository, listing each plugin's manifest address, version and hashes. No server, no accounts, no ratings, no telemetry.
- paindu downloads the index only when the user opens the marketplace tab.
- Listing requires: public source, a manifest that matches the code, and a human review of the capabilities against what the plugin says it does.
- Updates are never automatic. Plugins shows "update available" inside the Plugins screen only; no notification.

## Open questions

1. Should script plugins be able to declare `hosts` at all, or should all networked plugins be companions?
2. Who reviews marketplace submissions, and how is the index signing key held?
3. Should a plugin be allowed to depend on another plugin (for example a History search plugin)?
4. Play policy review: the script sandbox must be shown not to let downloaded scripts do anything Play forbids. This needs checking against Google's current wording before the marketplace ships.
