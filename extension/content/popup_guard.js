// Runs in the page's own context (MAIN world) at document_start.
// Chrome already blocks popups that open without a click; this adds:
//   * at most one window.open per click (stops "click once, get 3 tabs")
//   * notification-permission prompts only after a real click
//   * no window.open from inside invisible/tiny ad iframes
(() => {
  if (window.__pcmShieldGuard) return;
  Object.defineProperty(window, "__pcmShieldGuard", { value: true });

  const hasGesture = () => !navigator.userActivation || navigator.userActivation.isActive;
  const tinyFrame = () => {
    if (window === window.top) return false;
    const w = window.innerWidth, h = window.innerHeight;
    return w < 5 || h < 5;
  };

  const realOpen = window.open;
  let lastOpen = 0;
  const guardedOpen = function open(...args) {
    const now = Date.now();
    if (!hasGesture() || tinyFrame() || now - lastOpen < 1000) {
      console.debug("[mistri Shield] blocked popup:", args[0]);
      return null;
    }
    lastOpen = now;
    return realOpen.apply(this, args);
  };
  guardedOpen.toString = () => realOpen.toString();
  window.open = guardedOpen;

  if (window.Notification && typeof Notification.requestPermission === "function") {
    const realReq = Notification.requestPermission.bind(Notification);
    Notification.requestPermission = function requestPermission(...args) {
      if (!hasGesture()) {
        const cb = args[0];
        if (typeof cb === "function") cb("default");
        return Promise.resolve("default");
      }
      return realReq(...args);
    };
  }
})();
