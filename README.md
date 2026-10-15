# ExtForge

ExtForge builds Mihon catalogue extensions from CSS selectors and exports them as files other apps can open: a JSON profile, and a signed APK Mihon can install.

The screen is black with phosphor green type, square borders, and monospace text.

## What an extension can do

- Popular, search, and latest list pages, with `{base}`, `{page}`, and `{query}` in the URL templates
- A next-page selector that only answers whether another page exists
- Detail fields: title, description, author, artist, genre, and status words
- Chapters with a name, URL, date pattern, and document or reverse order
- Page images from `src`, `data-src`, `data-lazy-src`, `srcset`, or the first one that is set
- NSFW flag, language, and a source version id
- A pause between requests, from 0 to 10000 ms
- Image referer: none, the site root, or the chapter page

## Cookies

`NO COOKIES` is the default. The extension clears the cookie jar and does not send a Cookie header.

`MANUAL` sends only a cookie header you paste yourself, and only to the source host. ExtForge does not open a browser, does not collect `cf_clearance`, and does not solve check pages. If a test run looks like a check page, copy the cookie header from your own browser and paste it.

## Using the files

Share JSON writes a schema v2 file and opens the share sheet. Import JSON reads one profile or a list. Older ParserForge files (schema v1) open in the editor; the four old selectors are copied onto the list item, cover, chapter item, and page image so you can finish them.

Share APK and Install APK write a signed Mihon extension:

- feature `tachiyomi.extension`
- class `ex.extforge.runtime.ForgeSource`
- `versionName` `1.4.<code>` so Mihon treats it as extension lib 1.4
- a stable package `ex.extforge.s` plus 16 hex characters from the profile id

Install it, open Mihon, and trust the signature when asked. The signing key ships with the app so later exports can update the same extension; it is not a private key. Each export bumps the APK version code. On Android 10 and newer the APK is also copied to Downloads.

## Build

Android SDK 35 and build-tools 35.0.0 are required. `local.properties` should set `sdk.dir`.

```bash
./gradlew :parser-core:test :app:assembleDebug
```

`tools/build-forge-dex.sh` compiles the extension class against Mihon stubs and writes `parser-core/src/main/resources/mihon/forge-source.dex`. The app rewrites that dex with the profile JSON, packs a binary manifest, and signs it. The dex does not include OkHttp, Jsoup, or Mihon; those come from the Mihon app at runtime.
