// Open-source filter lists and a small parser that turns them into domain sets.
//
// Only whole-domain rules are used (`||example.com^` and hosts-file lines). That
// covers the bulk of ad, tracker and malware hosts, keeps the rule set tiny and
// fast, and avoids the site breakage that complex cosmetic/path rules can cause.

export const LISTS = [
  {
    id: "easylist", category: "ads", name: "EasyList",
    url: "https://easylist.to/easylist/easylist.txt", format: "abp",
    mirrors: ["https://raw.githubusercontent.com/easylist/easylist/gh-pages/easylist.txt"],
    license: "GPLv3 / CC BY-SA 3.0",
  },
  {
    id: "easyprivacy", category: "trackers", name: "EasyPrivacy",
    url: "https://easylist.to/easylist/easyprivacy.txt", format: "abp",
    mirrors: ["https://raw.githubusercontent.com/easylist/easylist/gh-pages/easyprivacy.txt"],
    license: "GPLv3 / CC BY-SA 3.0",
  },
  {
    id: "pgl", category: "ads", name: "Peter Lowe's Blocklist",
    url: "https://pgl.yoyo.org/adservers/serverlist.php?hostformat=hosts&showintro=0&mimetype=plaintext",
    format: "hosts", license: "Free to use (see pgl.yoyo.org)",
  },
  {
    id: "urlhaus", category: "malware", name: "URLhaus malicious hosts (abuse.ch)",
    url: "https://urlhaus.abuse.ch/downloads/hostfile/", format: "hosts",
    license: "CC0",
  },
  {
    id: "ubo-badware", category: "malware", name: "uBlock filters – Badware risks",
    url: "https://raw.githubusercontent.com/uBlockOrigin/uAssets/master/filters/badware.txt", format: "abp",
    license: "GPLv3",
  },
];

const DOMAIN_RE = /^(?=.{1,253}$)(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\.)+[a-z][a-z0-9-]{0,62}$/;
// ABP options that don't narrow *which pages* a rule applies to; anything else
// (domain=, ~third-party, redirect=, removeparam=, csp=, popup, ...) is skipped.
const SAFE_OPTS = new Set(["third-party", "3p", "all", "doc", "document", "script", "image", "xhr",
  "xmlhttprequest", "subdocument", "frame", "media", "object", "ping", "websocket", "other",
  "stylesheet", "css", "font", "important"]);
const SKIP_HOSTS = new Set(["localhost", "localhost.localdomain", "local", "broadcasthost",
  "ip6-localhost", "ip6-loopback", "0.0.0.0"]);

export function parseList(text, format) {
  const out = new Set();
  for (let raw of text.split(/\r?\n/)) {
    const line = raw.trim();
    if (!line || line[0] === "!" || line[0] === "#" || line[0] === "[") continue;
    if (format === "hosts") {
      const parts = line.split(/\s+/);
      if (parts.length < 2) continue;
      if (!/^(0\.0\.0\.0|127\.0\.0\.1|::1?|::)$/.test(parts[0])) continue;
      const host = parts[1].toLowerCase().replace(/\.$/, "");
      if (!SKIP_HOSTS.has(host) && DOMAIN_RE.test(host)) out.add(host);
      continue;
    }
    // ABP / uBO syntax
    if (line.startsWith("@@") || line.includes("##") || line.includes("#@#") || line.includes("#?#")
        || line.includes("#$#") || line.includes("#%#")) continue;
    const m = /^\|\|([a-z0-9.-]+)\^(?:\$(.*))?$/i.exec(line);
    if (!m) continue;
    if (m[2]) {
      const opts = m[2].toLowerCase().split(",");
      if (!opts.every((o) => SAFE_OPTS.has(o))) continue;
    }
    const host = m[1].toLowerCase();
    if (DOMAIN_RE.test(host)) out.add(host);
  }
  return out;
}

// Drop subdomains already covered by a parent in the set (requestDomains matches subdomains).
export function collapse(domains) {
  const set = domains instanceof Set ? domains : new Set(domains);
  const out = [];
  for (const d of set) {
    let covered = false;
    let i = d.indexOf(".");
    while (i !== -1) {
      const parent = d.slice(i + 1);
      if (parent.includes(".") && set.has(parent)) { covered = true; break; }
      i = d.indexOf(".", i + 1);
    }
    if (!covered) out.push(d);
  }
  return out.sort();
}

// True if host or any parent domain is in the set.
export function hostInSet(host, set) {
  host = (host || "").toLowerCase();
  while (host) {
    if (set.has(host)) return true;
    const i = host.indexOf(".");
    if (i === -1) return false;
    host = host.slice(i + 1);
  }
  return false;
}

export function chunk(arr, size) {
  const out = [];
  for (let i = 0; i < arr.length; i += size) out.push(arr.slice(i, i + size));
  return out;
}
