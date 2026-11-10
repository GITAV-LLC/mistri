(async () => {
  const back = document.getElementById("back");
  back.addEventListener("click", () => {
    if (history.length > 1) history.back(); else location.href = "about:blank";
  });
  let info = {};
  try { info = await chrome.runtime.sendMessage({ type: "blockedInfo" }); } catch (_) {}
  if (!info || !info.url) return;
  let host = "";
  try { host = new URL(info.url).hostname; } catch (_) { return; }
  document.getElementById("host").textContent = host;
  const go = document.getElementById("proceed");
  go.hidden = false;
  go.addEventListener("click", () => chrome.runtime.sendMessage({ type: "allowOnce", url: info.url }));
})();
