# ParserForge

Android tool for building HTML scrapers by clicking elements in a WebView. A tap becomes a CSS selector, the selector is assigned to a field, and the parser is stored as one JSON document an external reader can load.

## Screens

- **Parser list.** Create a scraper, then open Edit JSON, Test Run, or Launch Visual Builder.
- **Visual builder.** Split WebView and selector sheet. Pick mode injects `selector_picker.js`, which blocks the click and calls `ParserForgeBridge.onElementPicked`.
- **Selector sheet.** Manga List Title, Cover Thumbnail, Chapter URL, Reader Page Images. Assign writes the last picked path into that field. Jsoup previews matches against a DOM snapshot from the page.
- **JSON schema.** Edit the stored document directly.
- **Test run.** OkHttp GETs `entryUrl` with the captured user agent and cookies, then Jsoup runs the same selectors.

The chrome is black, white, and `#00FF66`, monospace, with 1dp borders and no rounded cards.

## Session cookies

When a main frame finishes loading, or when you press Capture Session, the app reads `CookieManager` for that page and stores the cookie header plus the WebView user agent on the parser. Later OkHttp requests send those cookies only to the host they were captured on. `cf_clearance` is included when the WebView already has it. The app does not generate clearance tokens.

## JSON schema

`schemaVersion` is `1`.

```json
{
  "schemaVersion": 1,
  "id": "uuid",
  "name": "Example",
  "entryUrl": "https://example.com/manga",
  "session": {
    "userAgent": "Mozilla/5.0 ...",
    "capturedAtUrl": "https://example.com/manga",
    "cookies": [
      { "name": "cf_clearance", "value": "...", "domain": "example.com" }
    ]
  },
  "selectors": {
    "mangaListTitle": { "selector": "h1.title", "extract": "text" },
    "coverThumbnail": { "selector": "img.cover", "extract": "src" },
    "chapterUrl": { "selector": "li.chapter > a", "extract": "href" },
    "readerPageImages": { "selector": "img.page", "extract": "src" }
  }
}
```

`extract` is `text`, `href`, or `src`. Image fields also read `data-src`, `data-lazy-src`, `data-original`, and the first `srcset` candidate. Href and src values are resolved against the page URL.

## Layout

- `parser-core` — models, schema, URL and cookie handling, OkHttp session client, Jsoup preview, parser store, picker script.
- `app` — Compose UI and `SelectorWebView`.
- `parser-core/src/main/resources/selector_picker.js` — click capture and CSS path builder.

## Build

JDK 17 and Android SDK 35.

```bash
./gradlew :parser-core:test
./gradlew :app:assembleDebug
```

The picker script can also be checked with Node and jsdom:

```bash
PICKER_SCRIPT=parser-core/src/main/resources/selector_picker.js \
  node parser-core/src/test/js/selector_picker.test.mjs
```
