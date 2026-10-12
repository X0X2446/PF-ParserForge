/*
 * ParserForge selector picker.
 * SelectorWebView evaluates this in the main frame after each load.
 *
 * Contract:
 *   ParserForgeBridge.onElementPicked(selector, tag, text, href, src)
 *   ParserForgeBridge.onHtmlSnapshot(selector, html, pageUrl)
 *   window.__pf.setPick(boolean)
 *   window.__pf.snapshot(selector)
 *   window.__pf.buildSelector(element)
 */
(function () {
  if (!window.document || !window.document.documentElement) return false;
  if (window.__pf && window.__pf.installed) return false;

  var pf = window.__pf = {};
  pf.installed = true;
  pf.pick = false;

  var SKIP_EXACT = {
    hover: true,
    active: true,
    focus: true,
    selected: true,
    disabled: true
  };

  var STYLE = [
    "html.__pf_on, html.__pf_on *{cursor:crosshair !important;}",
    ".__pf_hover{outline:1px solid #00FF66 !important;outline-offset:-1px !important;}",
    "#__pf_banner{position:fixed;left:0;right:0;top:0;z-index:2147483647;",
    "background:#000000;color:#00FF66;font:12px/16px ui-monospace,monospace;",
    "padding:4px 8px;border-bottom:1px solid #FFFFFF;pointer-events:none;white-space:nowrap;",
    "overflow:hidden;text-overflow:ellipsis;}"
  ].join("");

  function esc(value) {
    var text = String(value);
    if (window.CSS && typeof window.CSS.escape === "function") return window.CSS.escape(text);
    return text.replace(/([^a-zA-Z0-9_-])/g, "\\$&");
  }

  function stableId(el) {
    if (!el || !el.getAttribute) return "";
    var id = el.getAttribute("id") || "";
    if (!id || id.indexOf("__pf") === 0) return "";
    if (/^(react|ember|vue|ng)-/i.test(id)) return "";
    if (/[0-9]{4,}/.test(id) && id.length > 16) return "";
    return id;
  }

  function usableClasses(el) {
    var list = el.classList;
    if (!list || !list.length) return [];
    var out = [];
    for (var i = 0; i < list.length; i++) {
      var name = list[i];
      if (!name || name.indexOf("__pf") === 0) continue;
      if (SKIP_EXACT[name]) continue;
      if (name.length > 48) continue;
      if (/^(ng-|jsx-|css-|sc-|svelte-)/.test(name)) continue;
      out.push(name);
      if (out.length === 3) break;
    }
    return out;
  }

  function segment(el, withNth) {
    var tag = el.tagName.toLowerCase();
    var id = stableId(el);
    if (id) return tag + "#" + esc(id);
    var sel = tag;
    var classes = usableClasses(el);
    for (var i = 0; i < classes.length; i++) sel += "." + esc(classes[i]);
    if (withNth && el.parentElement) {
      var sameTag = [];
      var children = el.parentElement.children;
      for (var j = 0; j < children.length; j++) {
        if (children[j].tagName === el.tagName) sameTag.push(children[j]);
      }
      if (sameTag.length > 1) {
        var index = -1;
        for (var k = 0; k < sameTag.length; k++) {
          if (sameTag[k] === el) index = k;
        }
        if (index >= 0) sel += ":nth-of-type(" + (index + 1) + ")";
      }
    }
    return sel;
  }

  function pathOf(el, withNth) {
    var parts = [];
    var node = el;
    var guard = 0;
    while (node && node.nodeType === 1 && node.tagName && node.tagName !== "HTML" && node.tagName !== "BODY" && guard < 8) {
      var nodeId = node.getAttribute && node.getAttribute("id");
      if (nodeId === "__pf_banner" || nodeId === "__pf_style") break;
      parts.unshift(segment(node, withNth));
      if (stableId(node)) break;
      node = node.parentElement;
      guard++;
    }
    return parts.join(" > ");
  }

  function matchesEl(selector, el) {
    try {
      return el.matches(selector);
    } catch (err) {
      return false;
    }
  }

  function buildSelector(el) {
    if (!el || el.nodeType !== 1) return "";
    var loose = pathOf(el, false);
    if (loose && matchesEl(loose, el)) return loose;
    var strict = pathOf(el, true);
    if (!strict) return el.tagName.toLowerCase();
    var parts = strict.split(" > ");
    for (var i = parts.length - 1; i >= 0; i--) {
      if (parts[i].indexOf(":nth-of-type(") === -1) continue;
      var saved = parts[i];
      parts[i] = saved.replace(/:nth-of-type\(\d+\)/g, "");
      var trial = parts.join(" > ");
      if (!matchesEl(trial, el)) parts[i] = saved;
    }
    var loosened = parts.join(" > ");
    if (loosened && matchesEl(loosened, el)) return loosened;
    return strict;
  }

  function cleanText(value) {
    return String(value || "").replace(/\s+/g, " ").trim().slice(0, 180);
  }

  function elementFrom(target) {
    if (!target) return null;
    if (target.nodeType === 1) return target;
    return target.parentElement || null;
  }

  function hrefOf(el) {
    if (!el) return "";
    if (typeof el.href === "string" && el.href) return String(el.href);
    var raw = el.getAttribute && (el.getAttribute("href") || el.getAttribute("data-href"));
    if (raw) {
      try {
        return new URL(raw, window.location.href).toString();
      } catch (err) {
        return String(raw);
      }
    }
    if (el.closest) {
      var anchor = el.closest("a");
      if (anchor && anchor !== el) return hrefOf(anchor);
    }
    return "";
  }

  function srcOf(el) {
    if (!el) return "";
    var direct = el.currentSrc || "";
    if (!direct && typeof el.src === "string" && el.getAttribute && el.getAttribute("src")) direct = el.src;
    if (!direct && el.getAttribute) {
      direct = el.getAttribute("src") || el.getAttribute("data-src") || el.getAttribute("data-lazy-src") || el.getAttribute("data-original") || "";
    }
    if (direct) {
      try {
        return new URL(direct, window.location.href).toString();
      } catch (err) {
        return String(direct);
      }
    }
    if (el.querySelector) {
      var img = el.querySelector("img");
      if (img && img !== el) return srcOf(img);
    }
    return "";
  }

  function ensureStyle() {
    if (document.getElementById("__pf_style")) return;
    var style = document.createElement("style");
    style.id = "__pf_style";
    style.textContent = STYLE;
    (document.head || document.documentElement).appendChild(style);
  }

  function banner(text) {
    var el = document.getElementById("__pf_banner");
    if (!pf.pick) {
      if (el && el.parentNode) el.parentNode.removeChild(el);
      return;
    }
    if (!el) {
      el = document.createElement("div");
      el.id = "__pf_banner";
      document.documentElement.appendChild(el);
    }
    el.textContent = text || "PICK  click an element";
  }

  var hovered = null;
  function clearHover() {
    if (hovered && hovered.classList) hovered.classList.remove("__pf_hover");
    hovered = null;
  }

  function arm(on) {
    pf.pick = !!on;
    ensureStyle();
    if (document.documentElement && document.documentElement.classList) {
      document.documentElement.classList.toggle("__pf_on", pf.pick);
    }
    if (!pf.pick) clearHover();
    banner(pf.pick ? "PICK  click an element" : "");
  }

  document.addEventListener("mouseover", function (event) {
    if (!pf.pick) return;
    var target = elementFrom(event.target);
    if (!target || target.id === "__pf_banner" || target === document.documentElement) return;
    if (hovered === target) return;
    clearHover();
    if (target.classList) {
      target.classList.add("__pf_hover");
      hovered = target;
    }
  }, true);

  document.addEventListener("mouseout", function (event) {
    if (!pf.pick) return;
    if (event.target === hovered) clearHover();
  }, true);

  document.addEventListener("touchstart", function (event) {
    if (!pf.pick) return;
    var target = elementFrom(event.target);
    if (!target || !target.classList || target.id === "__pf_banner") return;
    clearHover();
    target.classList.add("__pf_hover");
    hovered = target;
  }, true);

  document.addEventListener("click", function (event) {
    if (!pf.pick) return;
    var target = elementFrom(event.target);
    if (!target || target.id === "__pf_banner") return;
    event.preventDefault();
    event.stopPropagation();
    if (event.stopImmediatePropagation) event.stopImmediatePropagation();
    if (target.classList) {
      clearHover();
      target.classList.add("__pf_hover");
      hovered = target;
    }
    var selector = buildSelector(target);
    var text = cleanText(target.innerText || target.textContent || "");
    var href = hrefOf(target);
    var src = srcOf(target);
    banner(selector);
    try {
      if (window.ParserForgeBridge && typeof window.ParserForgeBridge.onElementPicked === "function") {
        window.ParserForgeBridge.onElementPicked(selector, target.tagName.toLowerCase(), text, href, src);
      }
    } catch (err) {}
  }, true);

  document.addEventListener("submit", function (event) {
    if (!pf.pick) return;
    event.preventDefault();
    event.stopPropagation();
  }, true);

  document.addEventListener("contextmenu", function (event) {
    if (!pf.pick) return;
    event.preventDefault();
  }, true);

  pf.setPick = arm;
  pf.buildSelector = buildSelector;
  pf.snapshot = function (selector) {
    var html = "";
    var page = "";
    try {
      page = String(window.location.href || "");
      var root = document.documentElement;
      html = root ? root.outerHTML : "";
      if (html.length > 350000) {
        var node = null;
        try {
          node = selector ? document.querySelector(selector) : null;
        } catch (err) {
          node = null;
        }
        var clip = node || document.body || root;
        for (var i = 0; i < 5 && clip && clip.parentElement && clip.parentElement !== document.documentElement; i++) {
          var parent = clip.parentElement;
          if (!parent.outerHTML || parent.outerHTML.length > 350000) break;
          clip = parent;
        }
        html = clip && clip.outerHTML ? clip.outerHTML : html.slice(0, 350000);
      }
    } catch (err) {
      html = "";
    }
    try {
      if (window.ParserForgeBridge && typeof window.ParserForgeBridge.onHtmlSnapshot === "function") {
        window.ParserForgeBridge.onHtmlSnapshot(selector || "", html, page);
      }
    } catch (bridgeErr) {}
  };

  ensureStyle();
  return true;
})();
