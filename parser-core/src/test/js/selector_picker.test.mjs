import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { JSDOM } from "jsdom";

const scriptPath = process.env.PICKER_SCRIPT
  || fileURLToPath(new URL("../../main/resources/selector_picker.js", import.meta.url));
const source = readFileSync(scriptPath, "utf8");

function boot(html) {
  const dom = new JSDOM(html, {
    url: "https://example.com/manga",
    runScripts: "outside-only",
  });
  dom.window.eval(source);
  return dom.window;
}

function assertEqual(actual, expected, label) {
  if (actual !== expected) {
    console.error(`${label}\n  actual:   ${actual}\n  expected: ${expected}`);
    process.exit(1);
  }
}

function assert(condition, label) {
  if (!condition) {
    console.error(label);
    process.exit(1);
  }
}

const window = boot(`<!doctype html><html><body>
  <div class="container">
    <ul>
      <li class="chapter active">
        <a href="/c/1">Chapter 1</a>
        <img src="/covers/1.jpg" alt="cover">
      </li>
      <li class="chapter"><a href="/c/2">Chapter 2</a></li>
    </ul>
  </div>
  <div id="main"><span class="title">Hi</span></div>
  <div class="css-1abcde2 item"><a href="/item">Item</a></div>
  <div><span>A</span><span>B</span></div>
</body></html>`);

const chapterLink = window.document.querySelector("li.chapter a");
chapterLink.classList.add("__pf_hover");
assertEqual(
  window.__pf.buildSelector(chapterLink),
  "div.container > ul > li.chapter > a",
  "repeating chapter selector",
);
assert(!window.__pf.buildSelector(chapterLink).includes("__pf"), "hover class leaked");
assert(!window.__pf.buildSelector(chapterLink).includes("active"), "state class leaked");

assertEqual(
  window.__pf.buildSelector(window.document.querySelector("#main .title")),
  "div#main > span.title",
  "id ancestor",
);

assertEqual(
  window.__pf.buildSelector(window.document.querySelector("div.item a")),
  "div.item > a",
  "generated class skipped",
);

assertEqual(
  window.__pf.buildSelector(window.document.querySelectorAll("div > span")[1]),
  "div > span",
  "loose repeating tag path",
);

window.eval(source);
let picks = 0;
window.ParserForgeBridge = {
  onElementPicked(selector, tag, text, href, src) {
    picks += 1;
    this.last = { selector, tag, text, href, src };
  },
  onHtmlSnapshot(selector, html, pageUrl) {
    this.snapshot = { selector, html, pageUrl };
  },
};

window.__pf.setPick(false);
chapterLink.dispatchEvent(new window.MouseEvent("click", { bubbles: true, cancelable: true }));
assertEqual(picks, 0, "pick mode off ignores clicks");

window.__pf.setPick(true);
assert(window.document.getElementById("__pf_banner"), "banner visible");
chapterLink.dispatchEvent(new window.MouseEvent("click", { bubbles: true, cancelable: true }));
assertEqual(picks, 1, "single bridge callback");
assertEqual(window.ParserForgeBridge.last.selector, "div.container > ul > li.chapter > a", "bridge selector");
assertEqual(window.ParserForgeBridge.last.tag, "a", "bridge tag");
assertEqual(window.ParserForgeBridge.last.text, "Chapter 1", "bridge text");
assert(window.ParserForgeBridge.last.href.endsWith("/c/1"), "bridge href");

const image = window.document.querySelector("img");
image.dispatchEvent(new window.MouseEvent("click", { bubbles: true, cancelable: true }));
assert(window.ParserForgeBridge.last.src.endsWith("/covers/1.jpg"), "bridge src");

window.__pf.snapshot("li.chapter a");
assert(window.ParserForgeBridge.snapshot.html.includes("Chapter 1"), "snapshot html");
assertEqual(window.ParserForgeBridge.snapshot.pageUrl, "https://example.com/manga", "snapshot url");

window.__pf.setPick(false);
assert(!window.document.getElementById("__pf_banner"), "banner removed");

console.log("selector picker ok");
