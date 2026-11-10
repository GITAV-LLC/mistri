// mistri Shield – service worker.
// Blocking is done by Chrome itself (declarativeNetRequest), so the extension
// doesn't read or slow down your traffic; this worker only maintains the rules.

import { LISTS, parseList, collapse, hostInSet, chunk } from "./lists.js";

const DEFAULTS = {
  ads: true, trackers: true, malware: true, popups: true, cookies: true, cosmetic: true,
  allowlist: [], lastUpdate: 0, listStats: {},
};
const UPDATE_EVERY_MIN = 24 * 60;
const DOMAINS_PER_RULE = 2000;
const ALLOW_RULE_ID = 1;
const BASE_RULE = { ads: 1, trackers: 2 };          // ids inside rules/base.json
const ID_BASE = { malware: 100, ads: 10000, trackers: 20000 };

// ---------------------------------------------------------------- settings

async function settings() {
  const s = await chrome.storage.local.get(Object.keys(DEFAULTS));
  return { ...DEFAULTS, ...s };
}

function cleanHost(h) {
  return (h || "").toLowerCase().trim().replace(/^\*\./, "").replace(/:\d+$/, "").replace(/\.$/, "");
}

// ---------------------------------------------------------------- filter lists

async function fetchList(list) {
  let lastErr;
  for (const url of [list.url, ...(list.mirrors || [])]) {
    try {
      const res = await fetch(url, { cache: "no-store", signal: AbortSignal.timeout(30000) });
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      const set = parseList(await res.text(), list.format);
      if (set.size) return set;
      lastErr = new Error("empty list");
    } catch (e) { lastErr = e; }
  }
  throw new Error(`${list.name}: ${lastErr}`);
}

async function updateLists() {
  const stats = (await settings()).listStats;
  const results = await Promise.allSettled(LISTS.map(fetchList));
  const toStore = {};
  results.forEach((r, i) => {
    const list = LISTS[i];
    if (r.status === "fulfilled" && r.value.size > 0) {
      toStore[`list:${list.id}`] = [...r.value];
      stats[list.id] = { count: r.value.size, ok: true, at: Date.now() };
    } else {
      stats[list.id] = { ...(stats[list.id] || {}), ok: false, error: String(r.reason || "empty list") };
    }
  });
  await chrome.storage.local.set({ ...toStore, listStats: stats, lastUpdate: Date.now() });
  await rebuildRules();
  return stats;
}

async function domainsFor(category) {
  const ids = LISTS.filter((l) => l.category === category).map((l) => `list:${l.id}`);
  const data = await chrome.storage.local.get(ids);
  const set = new Set();
  for (const id of ids) for (const d of data[id] || []) set.add(d);
  return set;
}

// ---------------------------------------------------------------- rules

let blockSet = null;   // used to close popup tabs that land on blocked domains

async function rebuildRules() {
  const s = await settings();
  const rules = [];

  const allow = s.allowlist.map(cleanHost).filter(Boolean);
  if (allow.length) {
    rules.push({
      id: ALLOW_RULE_ID, priority: 1000, action: { type: "allowAllRequests" },
      condition: { requestDomains: allow, resourceTypes: ["main_frame", "sub_frame"] },
    });
  }

  const nextBlockSet = new Set();
  for (const cat of ["malware", "ads", "trackers"]) {
    if (!s[cat]) continue;
    const domains = collapse(await domainsFor(cat));
    if (cat !== "trackers") domains.forEach((d) => nextBlockSet.add(d));
    let id = ID_BASE[cat];
    for (const part of chunk(domains, DOMAINS_PER_RULE)) {
      if (cat === "malware") {
        // Sub-resources: block. Whole pages: show a warning page with a way through.
        rules.push({ id: id++, priority: 10, action: { type: "block" },
          condition: { requestDomains: part, excludedResourceTypes: ["main_frame"] } });
        rules.push({ id: id++, priority: 10, action: { type: "redirect", redirect: { extensionPath: "/blocked.html" } },
          condition: { requestDomains: part, resourceTypes: ["main_frame"] } });
      } else {
        // Only block as third party: visiting a site directly is never broken by this.
        rules.push({ id: id++, priority: 1, action: { type: "block" },
          condition: { requestDomains: part, domainType: "thirdParty", excludedResourceTypes: ["main_frame"] } });
      }
    }
  }

  const old = await chrome.declarativeNetRequest.getDynamicRules();
  await chrome.declarativeNetRequest.updateDynamicRules({
    removeRuleIds: old.map((r) => r.id), addRules: rules,
  });

  const disable = [];
  if (!s.ads) disable.push(BASE_RULE.ads);
  if (!s.trackers) disable.push(BASE_RULE.trackers);
  const enable = [BASE_RULE.ads, BASE_RULE.trackers].filter((x) => !disable.includes(x));
  await chrome.declarativeNetRequest.updateStaticRules({
    rulesetId: "base", disableRuleIds: disable, enableRuleIds: enable,
  });
  blockSet = nextBlockSet;
}

// After the worker sleeps and wakes, rebuild the in-memory set without touching rules.
async function loadBlockSet() {
  const s = await settings();
  const set = new Set();
  for (const cat of ["malware", "ads"]) if (s[cat]) for (const d of await domainsFor(cat)) set.add(d);
  blockSet = set;
}

// ---------------------------------------------------------------- content scripts

async function registerScripts() {
  const s = await settings();
  const exclude = s.allowlist.map(cleanHost).filter((h) => /^[a-z0-9.-]+$/.test(h))
    .flatMap((h) => [`*://${h}/*`, `*://*.${h}/*`]);
  const common = { matches: ["<all_urls>"], allFrames: true, persistAcrossSessions: true,
    ...(exclude.length ? { excludeMatches: exclude } : {}) };
  const scripts = [];
  if (s.popups) scripts.push({ id: "popup-guard", js: ["content/popup_guard.js"], runAt: "document_start",
    world: "MAIN", ...common });
  if (s.cookies) scripts.push({ id: "cookie-reject", js: ["content/cookie_reject.js"], runAt: "document_idle",
    ...common });
  if (s.cosmetic) scripts.push({ id: "cosmetic", css: ["content/cosmetic.css"], runAt: "document_start",
    ...common });
  try { await chrome.scripting.unregisterContentScripts(); } catch (_) { /* none registered */ }
  if (scripts.length) await chrome.scripting.registerContentScripts(scripts);
}

// ---------------------------------------------------------------- popup tabs

// Tabs opened by a page (window.open / target=_blank) in the last few seconds.
const freshPopups = new Map();

chrome.webNavigation.onCreatedNavigationTarget.addListener((d) => {
  freshPopups.set(d.tabId, Date.now());
  checkPopup(d.tabId, d.url);
});

chrome.webNavigation.onCommitted.addListener((d) => {
  if (d.frameId === 0 && freshPopups.has(d.tabId)) checkPopup(d.tabId, d.url);
});

async function checkPopup(tabId, url) {
  const t = freshPopups.get(tabId);
  if (!t) return;
  if (Date.now() - t > 8000) { freshPopups.delete(tabId); return; }
  const s = await settings();
  if (!s.popups) return;
  let host;
  try { host = new URL(url).hostname; } catch (_) { return; }
  if (!host) return;
  if (!blockSet) await loadBlockSet();
  if (hostInSet(host, blockSet) && !hostInSet(host, new Set(s.allowlist))) {
    freshPopups.delete(tabId);
    chrome.tabs.remove(tabId).catch(() => {});
  }
}

// Remember where each tab was going, so the malware warning page can name the site.
chrome.webNavigation.onBeforeNavigate.addListener((d) => {
  if (d.frameId === 0 && /^https?:/.test(d.url)) {
    chrome.storage.session.set({ [`nav:${d.tabId}`]: d.url });
  }
});
chrome.tabs.onRemoved.addListener((tabId) => {
  freshPopups.delete(tabId);
  chrome.storage.session.remove(`nav:${tabId}`);
});

// ---------------------------------------------------------------- messages from popup / warning page

chrome.runtime.onMessage.addListener((msg, sender, reply) => {
  (async () => {
    switch (msg.type) {
      case "getState": {
        const s = await settings();
        let blocked = null;
        try {
          if (msg.tabId != null) {
            const m = await chrome.declarativeNetRequest.getMatchedRules({ tabId: msg.tabId });
            blocked = m.rulesMatchedInfo.length;
          }
        } catch (_) { /* quota or no access */ }
        const total = Object.values(s.listStats).reduce((a, x) => a + (x.count || 0), 0);
        return { ...s, blocked, totalDomains: total, lists: LISTS.map(({ id, name, category, license }) =>
          ({ id, name, category, license, ...(s.listStats[id] || {}) })) };
      }
      case "set": {
        await chrome.storage.local.set({ [msg.key]: msg.value });
        if (["ads", "trackers", "malware"].includes(msg.key)) await rebuildRules();
        if (["popups", "cookies", "cosmetic"].includes(msg.key)) await registerScripts();
        return { ok: true };
      }
      case "toggleSite": {
        const host = cleanHost(msg.host).replace(/^www\./, "");
        const s = await settings();
        const list = new Set(s.allowlist);
        list.has(host) ? list.delete(host) : list.add(host);
        await chrome.storage.local.set({ allowlist: [...list].sort() });
        await rebuildRules();
        await registerScripts();
        return { paused: list.has(host) };
      }
      case "updateNow":
        return { stats: await updateLists() };
      case "blockedInfo": {
        const tabId = sender.tab && sender.tab.id;
        const key = `nav:${tabId}`;
        const v = await chrome.storage.session.get(key);
        return { url: v[key] || null };
      }
      case "allowOnce": {
        const host = cleanHost(new URL(msg.url).hostname);
        const existing = await chrome.declarativeNetRequest.getSessionRules();
        const id = existing.reduce((m, r) => Math.max(m, r.id), 0) + 1;
        await chrome.declarativeNetRequest.updateSessionRules({ addRules: [{
          id, priority: 2000, action: { type: "allowAllRequests" },
          condition: { requestDomains: [host], resourceTypes: ["main_frame", "sub_frame"] },
        }] });
        if (sender.tab) await chrome.tabs.update(sender.tab.id, { url: msg.url });
        return { ok: true };
      }
      default:
        return { error: "unknown message" };
    }
  })().then(reply, (e) => reply({ error: String(e) }));
  return true; // async reply
});

// ---------------------------------------------------------------- lifecycle

async function init() {
  await chrome.declarativeNetRequest.setExtensionActionOptions({ displayActionCountAsBadgeText: true });
  await chrome.action.setBadgeBackgroundColor({ color: "#2e6b4f" });
  if (!(await chrome.alarms.get("update-lists"))) {
    chrome.alarms.create("update-lists", { periodInMinutes: UPDATE_EVERY_MIN, delayInMinutes: UPDATE_EVERY_MIN });
  }
}

chrome.runtime.onInstalled.addListener(async () => {
  const cur = await chrome.storage.local.get(null);
  const missing = Object.fromEntries(Object.entries(DEFAULTS).filter(([k]) => !(k in cur)));
  await chrome.storage.local.set(missing);
  await init();
  await registerScripts();
  await rebuildRules();          // starter rules work offline right away
  updateLists().catch((e) => console.warn("List update failed:", e));
});

chrome.runtime.onStartup.addListener(async () => {
  await init();
  const { lastUpdate } = await settings();
  if (Date.now() - lastUpdate > UPDATE_EVERY_MIN * 60000) updateLists().catch(() => {});
});

chrome.alarms.onAlarm.addListener((a) => {
  if (a.name === "update-lists") updateLists().catch((e) => console.warn("List update failed:", e));
});
