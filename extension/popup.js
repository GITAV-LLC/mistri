const $ = (s) => document.querySelector(s);
const send = (msg) => chrome.runtime.sendMessage(msg);

let tab = null, host = "";

function ago(ts) {
  if (!ts) return "never";
  const m = Math.round((Date.now() - ts) / 60000);
  if (m < 1) return "just now";
  if (m < 60) return `${m} min ago`;
  const h = Math.round(m / 60);
  return h < 48 ? `${h} h ago` : `${Math.round(h / 24)} days ago`;
}

function render(state) {
  document.querySelectorAll("input[data-key]").forEach((el) => { el.checked = !!state[el.dataset.key]; });
  $("#blocked").textContent = state.blocked == null ? "–" : state.blocked.toLocaleString();
  const failed = state.lists.filter((l) => l.ok === false).length;
  $("#lists").textContent = state.totalDomains
    ? `${state.totalDomains.toLocaleString()} domains · updated ${ago(state.lastUpdate)}` + (failed ? ` · ${failed} failed` : "")
    : "Using starter rules · lists not downloaded yet";
  const base = host.replace(/^www\./, "");
  const paused = !!base && state.allowlist.some((h) => base === h || base.endsWith("." + h));
  const btn = $("#pause");
  btn.disabled = !base;
  btn.textContent = paused ? "Paused · resume" : "Pause on this site";
  btn.classList.toggle("paused", paused);
}

async function refresh() {
  render(await send({ type: "getState", tabId: tab ? tab.id : null }));
}

(async () => {
  [tab] = await chrome.tabs.query({ active: true, currentWindow: true });
  try {
    const u = new URL(tab.url);
    if (/^https?:$/.test(u.protocol)) host = u.hostname;
  } catch (_) {}
  $("#site").textContent = host || "Not a web page";
  await refresh();

  document.querySelectorAll("input[data-key]").forEach((el) =>
    el.addEventListener("change", async () => {
      await send({ type: "set", key: el.dataset.key, value: el.checked });
      refresh();
    }));

  $("#pause").addEventListener("click", async () => {
    await send({ type: "toggleSite", host });
    await chrome.tabs.reload(tab.id);
    refresh();
  });

  $("#update").addEventListener("click", async () => {
    const b = $("#update");
    b.disabled = true; b.textContent = "Updating…";
    await send({ type: "updateNow" });
    b.disabled = false; b.textContent = "Update lists";
    refresh();
  });
})();
