// Auto-reject optional cookies. It only ever clicks "reject / necessary only"
// controls – never "accept" – and gives up quietly after ~20 seconds.
(() => {
  if (window.__pcmCookieReject) return;
  window.__pcmCookieReject = true;

  // Reject buttons of common consent platforms (CMPs).
  const SELECTORS = [
    "#onetrust-reject-all-handler", ".ot-pc-refuse-all-handler",                    // OneTrust
    "#CybotCookiebotDialogBodyButtonDecline",                                        // Cookiebot
    "#CybotCookiebotDialogBodyLevelButtonLevelOptinDeclineAll",
    "#didomi-notice-disagree-button", ".didomi-continue-without-agreeing",          // Didomi
    "button[data-testid='uc-deny-all-button']",                                     // Usercentrics
    "button.sp_choice_type_REJECT_ALL",                                             // Sourcepoint
    "#truste-consent-required",                                                     // TrustArc
    ".osano-cm-denyAll", ".cc-deny",                                                // Osano / cookieconsent
    ".cky-btn-reject",                                                              // CookieYes
    ".cmplz-btn.cmplz-deny",                                                        // Complianz
    "[data-tid='banner-decline']",                                                  // Termly
    ".cm-btn-decline", ".cn-decline",                                               // Klaro
    ".iubenda-cs-reject-btn",                                                       // iubenda
    "#axeptio_btn_dismiss",                                                         // Axeptio
    ".moove-gdpr-infobar-reject-btn",                                               // GDPR Cookie Compliance
    "#cn-refuse-cookie",                                                            // Cookie Notice
    "#wt-cli-reject-btn",                                                           // CookieLawInfo
    "#ccc-reject-settings",                                                         // Civic
    "#tarteaucitronAllDenied2",                                                     // tarteaucitron
    ".fc-cta-do-not-consent",                                                       // Google Funding Choices
    "button[data-cookiefirst-action='reject']",                                     // CookieFirst
    "#hs-eu-decline-button",                                                        // HubSpot
  ];

  const REJECT = new RegExp([
    "^(reject|decline|deny|refuse|disagree)( all| optional| non-essential| additional)?( cookies)?$",
    "^(only|use|accept|allow) (strictly )?(necessary|essential|required)( cookies)?( only)?$",
    "^(strictly )?(necessary|essential|required)( cookies)? only$",
    "^(accept|allow) only (strictly )?(necessary|essential|required)( cookies)?$",
    "^continue without (accepting|agreeing)$", "^do not consent$", "^i do not accept$",
    "^(alle )?ablehnen$", "^nur (notwendige|erforderliche|essenzielle)( cookies)?( akzeptieren| zulassen| erlauben)?$",
    "^(tout )?refuser( tout)?$", "^continuer sans accepter$",
    "^rechazar( todo| todas)?$", "^rifiuta( tutto| tutti)?$", "^(alles )?weigeren$",
    "^rejeitar( tudo| todos)?$", "^odrzuć( wszystkie)?$", "^avvisa( alla)?$", "^afvis( alle)?$",
    "^hylkää( kaikki)?$", "^odmítnout( vše)?$",
  ].join("|"), "i");

  const CONTAINER = /cookie|consent|gdpr|cmp|privacy|onetrust|didomi|usercentrics|cookiebot|truste|sp_message|osano|cky-|cmplz|iubenda|axeptio|klaro|termly|tarteaucitron/i;
  const WHOLE_DOC_IS_CMP =
    /^consent\./.test(location.hostname) ||
    (window !== window.top && /consent|privacy-?mgmt|sp_message|cmp|sourcepoint|cookie/i.test(location.href));

  const GATE = "[id*='cookie' i],[class*='cookie' i],[id*='consent' i],[class*='consent' i]," +
    "[id*='gdpr' i],[class*='gdpr' i],[id*='cmp' i],[class*='cmp' i],[aria-modal='true'],[role='dialog']";

  const visible = (el) => {
    const r = el.getBoundingClientRect();
    if (r.width < 2 || r.height < 2) return false;
    const s = getComputedStyle(el);
    return s.visibility !== "hidden" && s.display !== "none" && parseFloat(s.opacity || "1") > 0.05;
  };

  const inConsentUI = (el) => {
    if (WHOLE_DOC_IS_CMP) return true;
    let n = el;
    for (let depth = 0; n && depth < 14; depth++) {
      if (n.nodeType === 1) {
        const tag = `${n.id || ""} ${typeof n.className === "string" ? n.className : ""} ` +
          `${n.getAttribute("aria-label") || ""} ${n.getAttribute("aria-labelledby") || ""}`;
        if (CONTAINER.test(tag)) return true;
        if (n.getAttribute("role") === "dialog" || n.getAttribute("aria-modal") === "true") {
          if (/cookie|consent|privacy|datenschutz|confidentialité/i.test((n.textContent || "").slice(0, 3000))) return true;
        }
      }
      n = n.parentElement || (n.getRootNode && n.getRootNode().host) || null;
    }
    return false;
  };

  const label = (el) => (el.innerText || el.value || el.getAttribute("aria-label") || "")
    .replace(/\s+/g, " ").trim();

  const roots = () => {
    const out = [document];
    const walker = document.createTreeWalker(document.body || document.documentElement, NodeFilter.SHOW_ELEMENT);
    let n, seen = 0;
    while ((n = walker.nextNode()) && seen++ < 8000) if (n.shadowRoot) out.push(n.shadowRoot);
    return out;
  };

  let clicks = 0;
  const tryReject = () => {
    for (const root of roots()) {
      for (const sel of SELECTORS) {
        const el = root.querySelector(sel);
        if (el && visible(el)) return click(el, sel);
      }
      // Cheap gate: only look at every button when something consent-like is on the page.
      if (!WHOLE_DOC_IS_CMP && !root.querySelector(GATE)) continue;
      for (const el of root.querySelectorAll("button, [role='button'], a, input[type='button'], input[type='submit']")) {
        const t = label(el);
        if (t && t.length <= 48 && REJECT.test(t) && visible(el) && inConsentUI(el)) return click(el, t);
      }
    }
    return false;
  };

  const click = (el, why) => {
    clicks++;
    el.click();
    console.debug("[mistri Shield] rejected optional cookies via:", why);
    return true;
  };

  let timer = null;
  const schedule = () => {
    if (timer) return;
    timer = setTimeout(() => {
      timer = null;
      if (clicks >= 2) return stop();
      tryReject();
    }, 400);
  };
  const obs = new MutationObserver(schedule);
  const stop = () => { obs.disconnect(); clearTimeout(timer); };

  tryReject();
  obs.observe(document.documentElement, { childList: true, subtree: true });
  setTimeout(stop, 20000);
})();
