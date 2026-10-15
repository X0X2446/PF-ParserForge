package com.extforge.core.parse;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

/**
 * HTML rules shared by the in-app test run and the Mihon extension.
 * Cookie policy is only "none" or "manual". Nothing here solves a check page.
 */
public final class ForgeHtml {
    private static final Pattern NUMBER = Pattern.compile("(\\d+(?:\\.\\d+)?)");
    private static final String[] GUESSED_DATES = new String[] {
        "yyyy-MM-dd",
        "yyyy/MM/dd",
        "MM/dd/yyyy",
        "dd/MM/yyyy",
        "MMM d, yyyy",
        "d MMM yyyy",
        "MMMM d, yyyy",
    };
    private static final int TEXT_LIMIT = 500;
    private static final int DESC_LIMIT = 4000;
    private static final int PAGE_LIMIT = 500;

    private ForgeHtml() {}

    public static final class Config {
        public final String name;
        public final String lang;
        public final String base;
        public final String baseHost;
        public final boolean nsfw;
        public final boolean latest;
        public final int versionId;
        public final String cookies;
        public final String ua;
        public final String cookie;
        public final int delayMs;
        public final String referer;
        public final String popular;
        public final String search;
        public final String latestUrl;
        public final String next;
        public final String item;
        public final String title;
        public final String url;
        public final String cover;
        public final String detailTitle;
        public final String description;
        public final String author;
        public final String artist;
        public final String genre;
        public final String status;
        public final String ongoing;
        public final String completed;
        public final String chapterItem;
        public final String chapterName;
        public final String chapterUrl;
        public final String chapterDate;
        public final String datePattern;
        public final String chapterOrder;
        public final String pageImage;
        public final String pageAttr;

        private Config(Map<String, String> map) {
            name = str(map, "name");
            lang = str(map, "lang");
            base = trimSlash(str(map, "base"));
            baseHost = hostOf(base);
            nsfw = bool(map, "nsfw");
            latest = bool(map, "latest");
            versionId = Math.max(1, integer(map, "versionId", 1));
            cookies = str(map, "cookies");
            ua = str(map, "ua");
            cookie = cleanHeader(str(map, "cookie"));
            delayMs = Math.max(0, Math.min(10000, integer(map, "delayMs", 0)));
            referer = str(map, "referer");
            popular = str(map, "popular");
            search = str(map, "search");
            latestUrl = str(map, "latestUrl");
            next = str(map, "next");
            item = str(map, "item");
            title = str(map, "title");
            url = str(map, "url");
            cover = str(map, "cover");
            detailTitle = str(map, "detailTitle");
            description = str(map, "description");
            author = str(map, "author");
            artist = str(map, "artist");
            genre = str(map, "genre");
            status = str(map, "status");
            ongoing = str(map, "ongoing");
            completed = str(map, "completed");
            chapterItem = str(map, "chapterItem");
            chapterName = str(map, "chapterName");
            chapterUrl = str(map, "chapterUrl");
            chapterDate = str(map, "chapterDate");
            datePattern = str(map, "datePattern");
            chapterOrder = str(map, "chapterOrder");
            pageImage = str(map, "pageImage");
            pageAttr = str(map, "pageAttr");
        }

        public static Config parse(String raw) {
            return new Config(readObject(raw));
        }

        public String popularUrl(int page) {
            return expand(blank(popular) ? "{base}/" : popular, page, "");
        }

        public String searchUrl(int page, String query) {
            if (blank(search)) {
                throw new IllegalArgumentException("Search template is empty");
            }
            return expand(search, page, query == null ? "" : query);
        }

        public String latestPageUrl(int page) {
            if (blank(latestUrl)) {
                throw new IllegalArgumentException("Latest template is empty");
            }
            return expand(latestUrl, page, "");
        }

        public String expand(String template, int page, String query) {
            String encoded = encode(query);
            String out = template
                .replace("{base}", base)
                .replace("{page}", Integer.toString(page))
                .replace("{query}", encoded);
            if (!out.startsWith("http://") && !out.startsWith("https://")) {
                if (!out.startsWith("/")) out = "/" + out;
                out = base + out;
            }
            return out;
        }
    }

    public static final class Item {
        public final String title;
        public final String url;
        public final String cover;

        public Item(String title, String url, String cover) {
            this.title = title;
            this.url = url;
            this.cover = cover;
        }
    }

    public static final class Listed {
        public final List<Item> items;
        public final boolean hasNext;

        public Listed(List<Item> items, boolean hasNext) {
            this.items = items;
            this.hasNext = hasNext;
        }
    }

    public static final class Detail {
        public final String title;
        public final String description;
        public final String author;
        public final String artist;
        public final String genre;
        public final String cover;
        public final int status;

        public Detail(
            String title,
            String description,
            String author,
            String artist,
            String genre,
            String cover,
            int status
        ) {
            this.title = title;
            this.description = description;
            this.author = author;
            this.artist = artist;
            this.genre = genre;
            this.cover = cover;
            this.status = status;
        }
    }

    public static final class Chapter {
        public final String name;
        public final String url;
        public final float number;
        public final long dateUpload;

        public Chapter(String name, String url, float number, long dateUpload) {
            this.name = name;
            this.url = url;
            this.number = number;
            this.dateUpload = dateUpload;
        }
    }

    public static Listed parseListing(Config config, String html, String pageUrl) {
        Document doc = document(html, pageUrl);
        if (blank(config.item)) {
            throw new IllegalArgumentException("List item selector is empty");
        }
        Elements nodes = select(doc, config.item);
        List<Item> items = new ArrayList<Item>();
        for (Element node : nodes) {
            String title = textOf(node, config.title);
            if (blank(title)) continue;
            String href = hrefOf(node, config.url);
            String cover = srcOf(node, config.cover, config.pageAttr);
            items.add(new Item(title, withoutDomain(href), cover));
        }
        boolean hasNext = !blank(config.next) && !select(doc, config.next).isEmpty();
        return new Listed(items, hasNext);
    }

    public static Detail parseDetail(Config config, String html, String pageUrl) {
        Document doc = document(html, pageUrl);
        String title = textOf(doc, config.detailTitle);
        if (blank(title)) title = doc.title();
        if (blank(title)) title = "untitled";
        String description = textOf(doc, config.description);
        if (description.length() > DESC_LIMIT) description = description.substring(0, DESC_LIMIT);
        String author = textOf(doc, config.author);
        String artist = textOf(doc, config.artist);
        String genre = join(doc, config.genre);
        String cover = srcOf(doc, config.cover, "auto");
        int status = statusOf(textOf(doc, config.status), config.ongoing, config.completed);
        return new Detail(title, description, author, artist, genre, cover, status);
    }

    public static List<Chapter> parseChapters(Config config, String html, String pageUrl) {
        if (blank(config.chapterItem)) return new ArrayList<Chapter>();
        Document doc = document(html, pageUrl);
        Elements nodes = select(doc, config.chapterItem);
        List<Chapter> chapters = new ArrayList<Chapter>();
        int index = 0;
        for (Element node : nodes) {
            String name = textOf(node, config.chapterName);
            if (blank(name)) name = "Chapter " + (index + 1);
            String href = hrefOf(node, config.chapterUrl);
            float number = lastNumber(name, index + 1);
            long date = parseDate(textOf(node, config.chapterDate), config.datePattern);
            chapters.add(new Chapter(name, withoutDomain(href), number, date));
            index++;
        }
        if ("reverse".equals(config.chapterOrder)) Collections.reverse(chapters);
        return chapters;
    }

    public static List<String> parsePages(Config config, String html, String pageUrl) {
        List<String> pages = new ArrayList<String>();
        if (blank(config.pageImage)) return pages;
        Document doc = document(html, pageUrl);
        Elements nodes = select(doc, config.pageImage);
        for (Element node : nodes) {
            String image = srcOf(node, "", config.pageAttr);
            if (blank(image) || image.startsWith("data:")) continue;
            pages.add(image);
            if (pages.size() >= PAGE_LIMIT) break;
        }
        return pages;
    }

    public static boolean looksLikeCheck(int code, String body) {
        String lowered = body == null ? "" : body.toLowerCase(Locale.US);
        boolean marker = lowered.contains("just a moment")
            || lowered.contains("cf-ray")
            || lowered.contains("cf-mitigated")
            || lowered.contains("attention required")
            || lowered.contains("checking your browser");
        return marker && (code == 200 || code == 403 || code == 429 || code == 503);
    }

    public static String withoutDomain(String absoluteOrPath) {
        if (absoluteOrPath == null || absoluteOrPath.length() == 0) return "";
        if (absoluteOrPath.startsWith("/")) return absoluteOrPath;
        try {
            URI uri = new URI(absoluteOrPath.replace(" ", "%20"));
            String out = uri.getPath();
            if (out == null || out.length() == 0) out = "/";
            if (uri.getQuery() != null) out += "?" + uri.getRawQuery();
            if (uri.getFragment() != null) out += "#" + uri.getRawFragment();
            return out;
        } catch (URISyntaxException error) {
            return absoluteOrPath;
        }
    }

    public static boolean hostMatches(String host, String baseHost) {
        if (blank(host) || blank(baseHost)) return false;
        String left = host.toLowerCase(Locale.US);
        String right = baseHost.toLowerCase(Locale.US);
        return left.equals(right) || left.endsWith("." + right);
    }

    private static Document document(String html, String pageUrl) {
        String base = blank(pageUrl) ? "https://localhost/" : pageUrl;
        return Jsoup.parse(html == null ? "" : html, base);
    }

    private static Elements select(Element scope, String selector) {
        try {
            return scope.select(selector);
        } catch (RuntimeException error) {
            throw new IllegalArgumentException("Selector failed: " + selector);
        }
    }

    private static Element pick(Element scope, String selector) {
        if (blank(selector)) return scope;
        try {
            return scope.selectFirst(selector);
        } catch (RuntimeException error) {
            throw new IllegalArgumentException("Selector failed: " + selector);
        }
    }

    private static String textOf(Element scope, String selector) {
        Element node = pick(scope, selector);
        if (node == null) return "";
        String text = node.text().replaceAll("\\s+", " ").trim();
        if (text.length() == 0) text = node.attr("alt").trim();
        if (text.length() > TEXT_LIMIT) text = text.substring(0, TEXT_LIMIT);
        return text;
    }

    private static String join(Element scope, String selector) {
        if (blank(selector)) return "";
        Elements nodes = select(scope, selector);
        StringBuilder out = new StringBuilder();
        for (Element node : nodes) {
            String text = node.text().replaceAll("\\s+", " ").trim();
            if (text.length() == 0) continue;
            if (out.length() > 0) out.append(", ");
            out.append(text);
        }
        return out.toString();
    }

    private static String hrefOf(Element scope, String selector) {
        Element node = pick(scope, selector);
        if (node == null) return "";
        String direct = firstAttr(node, new String[] {"href", "data-href"});
        if (!blank(direct)) return resolve(node, direct);
        if (!"a".equals(node.tagName())) {
            Element link = node.selectFirst("a[href]");
            if (link != null) return hrefOf(link, "");
        }
        return "";
    }

    private static String srcOf(Element scope, String selector, String mode) {
        if (blank(selector) && scope instanceof Document) return "";
        Element node = pick(scope, selector);
        if (node == null) return "";
        String direct = imageAttr(node, mode);
        if (!blank(direct)) return resolve(node, direct);
        if (!"img".equals(node.tagName())) {
            Element image = node.selectFirst("img");
            if (image != null) {
                String nested = imageAttr(image, mode);
                if (!blank(nested)) return resolve(image, nested);
            }
        }
        return "";
    }

    private static String imageAttr(Element node, String mode) {
        if ("src".equals(mode)) return node.attr("src");
        if ("data-src".equals(mode)) return node.attr("data-src");
        if ("data-lazy-src".equals(mode)) return node.attr("data-lazy-src");
        if ("srcset".equals(mode)) return firstSrcset(node.attr("srcset"));
        String found = firstAttr(node, new String[] {"src", "data-src", "data-lazy-src", "data-original"});
        if (!blank(found)) return found;
        return firstSrcset(node.attr("srcset"));
    }

    private static String firstAttr(Element node, String[] names) {
        for (int i = 0; i < names.length; i++) {
            String value = node.attr(names[i]);
            if (!blank(value)) return value;
        }
        return "";
    }

    private static String firstSrcset(String raw) {
        if (blank(raw)) return "";
        String first = raw.split(",")[0].trim();
        if (first.length() == 0) return "";
        return first.split("\\s+")[0];
    }

    private static String resolve(Element element, String raw) {
        if (blank(raw)) return "";
        if (raw.startsWith("http://") || raw.startsWith("https://")) return raw;
        Element anchor = new Element("a");
        anchor.setBaseUri(element.baseUri());
        anchor.attr("href", raw);
        String abs = anchor.absUrl("href");
        return blank(abs) ? raw : abs;
    }

    private static int statusOf(String text, String ongoing, String completed) {
        String lowered = text == null ? "" : text.toLowerCase(Locale.US);
        if (containsWord(lowered, completed)) return 2;
        if (containsWord(lowered, ongoing)) return 1;
        return 0;
    }

    private static boolean containsWord(String text, String words) {
        if (blank(text) || blank(words)) return false;
        String[] parts = words.split(",");
        for (int i = 0; i < parts.length; i++) {
            String word = parts[i].trim().toLowerCase(Locale.US);
            if (word.length() > 0 && text.contains(word)) return true;
        }
        return false;
    }

    private static float lastNumber(String name, int fallback) {
        Matcher matcher = NUMBER.matcher(name == null ? "" : name);
        String found = null;
        while (matcher.find()) found = matcher.group(1);
        if (found == null) return fallback;
        try {
            return Float.parseFloat(found);
        } catch (NumberFormatException error) {
            return fallback;
        }
    }

    private static long parseDate(String text, String pattern) {
        if (blank(text)) return 0L;
        if (!blank(pattern)) return parseWith(text, pattern);
        for (int i = 0; i < GUESSED_DATES.length; i++) {
            long parsed = parseWith(text, GUESSED_DATES[i]);
            if (parsed > 0L) return parsed;
        }
        return 0L;
    }

    private static long parseWith(String text, String pattern) {
        try {
            SimpleDateFormat format = new SimpleDateFormat(pattern, Locale.US);
            format.setLenient(false);
            ParsePosition pos = new ParsePosition(0);
            String trimmed = text.trim();
            java.util.Date date = format.parse(trimmed, pos);
            if (date == null || pos.getIndex() == 0) return 0L;
            return date.getTime();
        } catch (IllegalArgumentException error) {
            return 0L;
        }
    }

    private static String encode(String query) {
        try {
            return URLEncoder.encode(query == null ? "" : query, "UTF-8").replace("+", "%20");
        } catch (java.io.UnsupportedEncodingException error) {
            return "";
        }
    }

    private static String hostOf(String url) {
        try {
            String host = new URI(url).getHost();
            return host == null ? "" : host;
        } catch (URISyntaxException error) {
            return "";
        }
    }

    private static String trimSlash(String url) {
        if (url == null) return "";
        while (url.endsWith("/")) url = url.substring(0, url.length() - 1);
        return url;
    }

    private static String cleanHeader(String raw) {
        if (raw == null) return "";
        String cleaned = raw.replace("\r", "").replace("\n", "").trim();
        if (cleaned.length() > 8192) return cleaned.substring(0, 8192);
        return cleaned;
    }

    private static boolean blank(String value) {
        return value == null || value.trim().length() == 0;
    }

    private static String str(Map<String, String> map, String key) {
        String value = map.get(key);
        return value == null ? "" : value;
    }

    private static boolean bool(Map<String, String> map, String key) {
        return "true".equals(map.get(key));
    }

    private static int integer(Map<String, String> map, String key, int fallback) {
        String value = map.get(key);
        if (blank(value)) return fallback;
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException error) {
            return fallback;
        }
    }

    private static Map<String, String> readObject(String raw) {
        if (raw == null) throw new IllegalArgumentException("Config is empty");
        Scanner scanner = new Scanner(raw.trim());
        scanner.skip();
        scanner.expect('{');
        Map<String, String> map = new LinkedHashMap<String, String>();
        scanner.skip();
        if (scanner.eat('}')) return map;
        while (scanner.i < scanner.s.length()) {
            String key = scanner.string();
            scanner.expect(':');
            map.put(key, scanner.value());
            scanner.skip();
            if (scanner.eat('}')) return map;
            scanner.expect(',');
        }
        throw new IllegalArgumentException("Config JSON did not end");
    }

    private static final class Scanner {
        final String s;
        int i;

        Scanner(String s) {
            this.s = s;
        }

        void skip() {
            while (i < s.length()) {
                char c = s.charAt(i);
                if (c == ' ' || c == '\n' || c == '\r' || c == '\t') i++;
                else break;
            }
        }

        boolean eat(char c) {
            skip();
            if (i < s.length() && s.charAt(i) == c) {
                i++;
                return true;
            }
            return false;
        }

        void expect(char c) {
            if (!eat(c)) throw new IllegalArgumentException("Expected '" + c + "' in config");
        }

        String string() {
            skip();
            if (i >= s.length() || s.charAt(i) != '"') {
                throw new IllegalArgumentException("Expected a string in config");
            }
            i++;
            StringBuilder out = new StringBuilder();
            while (i < s.length()) {
                char c = s.charAt(i++);
                if (c == '"') return out.toString();
                if (c != '\\') {
                    out.append(c);
                    continue;
                }
                if (i >= s.length()) break;
                char escaped = s.charAt(i++);
                if (escaped == 'u') {
                    if (i + 4 > s.length()) throw new IllegalArgumentException("Bad unicode escape");
                    int code = Integer.parseInt(s.substring(i, i + 4), 16);
                    out.append((char) code);
                    i += 4;
                } else if (escaped == 'n') out.append('\n');
                else if (escaped == 'r') out.append('\r');
                else if (escaped == 't') out.append('\t');
                else out.append(escaped);
            }
            throw new IllegalArgumentException("Unterminated string in config");
        }

        String value() {
            skip();
            if (i < s.length() && s.charAt(i) == '"') return string();
            int start = i;
            while (i < s.length()) {
                char c = s.charAt(i);
                if (c == ',' || c == '}' || c == ' ' || c == '\n' || c == '\r' || c == '\t') break;
                i++;
            }
            if (start == i) throw new IllegalArgumentException("Missing config value");
            return s.substring(start, i);
        }
    }
}
