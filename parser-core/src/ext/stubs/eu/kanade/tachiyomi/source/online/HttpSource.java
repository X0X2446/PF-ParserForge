package eu.kanade.tachiyomi.source.online;

import eu.kanade.tachiyomi.source.CatalogueSource;
import eu.kanade.tachiyomi.source.model.FilterList;
import eu.kanade.tachiyomi.source.model.MangasPage;
import eu.kanade.tachiyomi.source.model.Page;
import eu.kanade.tachiyomi.source.model.SChapter;
import eu.kanade.tachiyomi.source.model.SManga;
import java.util.List;
import okhttp3.Headers;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public abstract class HttpSource implements CatalogueSource {
    public abstract String getBaseUrl();

    public abstract String getName();

    public abstract String getLang();

    public abstract boolean getSupportsLatest();

    public int getVersionId() {
        return 1;
    }

    public long getId() {
        return 0L;
    }

    public OkHttpClient getClient() {
        return null;
    }

    protected Headers.Builder headersBuilder() {
        return new Headers.Builder();
    }

    protected Request popularMangaRequest(int page) {
        throw new UnsupportedOperationException();
    }

    protected MangasPage popularMangaParse(Response response) {
        throw new UnsupportedOperationException();
    }

    protected Request searchMangaRequest(int page, String query, FilterList filters) {
        throw new UnsupportedOperationException();
    }

    protected MangasPage searchMangaParse(Response response) {
        throw new UnsupportedOperationException();
    }

    protected Request latestUpdatesRequest(int page) {
        throw new UnsupportedOperationException();
    }

    protected MangasPage latestUpdatesParse(Response response) {
        throw new UnsupportedOperationException();
    }

    public Request mangaDetailsRequest(SManga manga) {
        throw new UnsupportedOperationException();
    }

    protected SManga mangaDetailsParse(Response response) {
        throw new UnsupportedOperationException();
    }

    protected Request chapterListRequest(SManga manga) {
        throw new UnsupportedOperationException();
    }

    protected List<SChapter> chapterListParse(Response response) {
        throw new UnsupportedOperationException();
    }

    protected Request pageListRequest(SChapter chapter) {
        throw new UnsupportedOperationException();
    }

    protected List<Page> pageListParse(Response response) {
        throw new UnsupportedOperationException();
    }

    protected Request imageRequest(Page page) {
        throw new UnsupportedOperationException();
    }
}
