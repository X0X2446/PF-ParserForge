package ex.extforge.runtime;

import com.extforge.core.parse.ForgeHtml;
import eu.kanade.tachiyomi.source.model.FilterList;
import eu.kanade.tachiyomi.source.model.MangasPage;
import eu.kanade.tachiyomi.source.model.Page;
import eu.kanade.tachiyomi.source.model.SChapter;
import eu.kanade.tachiyomi.source.model.SManga;
import eu.kanade.tachiyomi.source.online.HttpSource;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import okhttp3.CookieJar;
import okhttp3.Headers;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Mihon catalogue source. Cookies are off unless the profile says manual,
 * in which case only the pasted header is sent to the source host.
 * Check pages are not solved.
 */
public final class ForgeSource extends HttpSource {
    private final ForgeHtml.Config config;
    private volatile OkHttpClient cached;

    public ForgeSource() {
        super();
        config = ForgeHtml.Config.parse(ForgeConfig.JSON.trim());
    }

    @Override
    public String getBaseUrl() {
        return config.base;
    }

    @Override
    public String getName() {
        return config.name;
    }

    @Override
    public String getLang() {
        return config.lang;
    }

    @Override
    public boolean getSupportsLatest() {
        return config.latest;
    }

    @Override
    public int getVersionId() {
        return config.versionId;
    }

    @Override
    public OkHttpClient getClient() {
        OkHttpClient current = cached;
        if (current != null) return current;
        synchronized (this) {
            if (cached != null) return cached;
            OkHttpClient.Builder builder = super.getClient().newBuilder();
            builder.cookieJar(CookieJar.NO_COOKIES);
            final int delay = config.delayMs;
            if (delay > 0) {
                builder.addInterceptor(new Interceptor() {
                    @Override
                    public Response intercept(Chain chain) throws IOException {
                        try {
                            Thread.sleep(delay);
                        } catch (InterruptedException interrupted) {
                            Thread.currentThread().interrupt();
                        }
                        return chain.proceed(chain.request());
                    }
                });
            }
            cached = builder.build();
            return cached;
        }
    }

    @Override
    protected Headers.Builder headersBuilder() {
        Headers.Builder builder = super.headersBuilder();
        if (config.ua != null && config.ua.length() > 0) {
            builder.set("User-Agent", config.ua);
        }
        return builder;
    }

    @Override
    protected Request popularMangaRequest(int page) {
        return html(config.popularUrl(page));
    }

    @Override
    protected MangasPage popularMangaParse(Response response) {
        return pageOf(ForgeHtml.parseListing(config, body(response), response.request().url().toString()));
    }

    @Override
    protected Request searchMangaRequest(int page, String query, FilterList filters) {
        return html(config.searchUrl(page, query));
    }

    @Override
    protected MangasPage searchMangaParse(Response response) {
        return pageOf(ForgeHtml.parseListing(config, body(response), response.request().url().toString()));
    }

    @Override
    protected Request latestUpdatesRequest(int page) {
        return html(config.latestPageUrl(page));
    }

    @Override
    protected MangasPage latestUpdatesParse(Response response) {
        return pageOf(ForgeHtml.parseListing(config, body(response), response.request().url().toString()));
    }

    @Override
    public Request mangaDetailsRequest(SManga manga) {
        return html(getBaseUrl() + manga.getUrl());
    }

    @Override
    protected SManga mangaDetailsParse(Response response) {
        String pageUrl = response.request().url().toString();
        ForgeHtml.Detail detail = ForgeHtml.parseDetail(config, body(response), pageUrl);
        SManga manga = SManga.Companion.INSTANCE.create();
        manga.setUrl(ForgeHtml.withoutDomain(pageUrl));
        manga.setTitle(detail.title);
        manga.setDescription(emptyToNull(detail.description));
        manga.setAuthor(emptyToNull(detail.author));
        manga.setArtist(emptyToNull(detail.artist));
        manga.setGenre(emptyToNull(detail.genre));
        manga.setStatus(detail.status);
        if (detail.cover != null && detail.cover.length() > 0) {
            manga.setThumbnail_url(detail.cover);
        }
        manga.setInitialized(true);
        return manga;
    }

    @Override
    protected Request chapterListRequest(SManga manga) {
        return html(getBaseUrl() + manga.getUrl());
    }

    @Override
    protected List<SChapter> chapterListParse(Response response) {
        List<ForgeHtml.Chapter> parsed = ForgeHtml.parseChapters(
            config,
            body(response),
            response.request().url().toString()
        );
        List<SChapter> chapters = new ArrayList<SChapter>();
        for (int i = 0; i < parsed.size(); i++) {
            ForgeHtml.Chapter row = parsed.get(i);
            SChapter chapter = SChapter.Companion.INSTANCE.create();
            chapter.setUrl(row.url);
            chapter.setName(row.name);
            chapter.setChapter_number(row.number);
            chapter.setDate_upload(row.dateUpload);
            chapters.add(chapter);
        }
        return chapters;
    }

    @Override
    protected Request pageListRequest(SChapter chapter) {
        return html(getBaseUrl() + chapter.getUrl());
    }

    @Override
    protected List<Page> pageListParse(Response response) {
        String pageUrl = response.request().url().toString();
        List<String> images = ForgeHtml.parsePages(config, body(response), pageUrl);
        String refererPath = ForgeHtml.withoutDomain(pageUrl);
        List<Page> pages = new ArrayList<Page>();
        for (int i = 0; i < images.size(); i++) {
            pages.add(new Page(i, refererPath, images.get(i), null));
        }
        return pages;
    }

    @Override
    protected Request imageRequest(Page page) {
        Request.Builder builder = new Request.Builder().url(page.getImageUrl()).headers(headersBuilder().build());
        if ("base".equals(config.referer)) {
            builder.header("Referer", getBaseUrl() + "/");
        } else if ("page".equals(config.referer)) {
            String path = page.getUrl();
            if (path != null && path.length() > 0) {
                String referer = path.startsWith("http") ? path : getBaseUrl() + path;
                builder.header("Referer", referer);
            }
        }
        return builder.build();
    }

    private Request html(String url) {
        Request.Builder builder = new Request.Builder()
            .url(url)
            .headers(headersBuilder().build())
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
        if ("manual".equals(config.cookies) && config.cookie.length() > 0) {
            String host = null;
            try {
                host = new URI(url).getHost();
            } catch (Exception ignored) {
                host = null;
            }
            if (ForgeHtml.hostMatches(host, config.baseHost)) {
                builder.header("Cookie", config.cookie);
            }
        }
        return builder.get().build();
    }

    private static MangasPage pageOf(ForgeHtml.Listed listed) {
        List<SManga> mangas = new ArrayList<SManga>();
        for (int i = 0; i < listed.items.size(); i++) {
            ForgeHtml.Item item = listed.items.get(i);
            SManga manga = SManga.Companion.INSTANCE.create();
            manga.setTitle(item.title);
            manga.setUrl(item.url.length() == 0 ? "/" : item.url);
            if (item.cover != null && item.cover.length() > 0) {
                manga.setThumbnail_url(item.cover);
            }
            mangas.add(manga);
        }
        return new MangasPage(mangas, listed.hasNext);
    }

    private static String body(Response response) {
        try {
            ResponseBody payload = response.body();
            if (payload == null) return "";
            String text = payload.string();
            if (text.length() > 2000000) return text.substring(0, 2000000);
            return text;
        } catch (IOException error) {
            throw new IllegalStateException(error);
        }
    }

    private static String emptyToNull(String value) {
        if (value == null || value.trim().length() == 0) return null;
        return value;
    }
}
