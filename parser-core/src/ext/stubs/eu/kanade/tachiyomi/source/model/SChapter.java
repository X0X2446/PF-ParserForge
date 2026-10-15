package eu.kanade.tachiyomi.source.model;

public interface SChapter {
    void setUrl(String url);

    String getUrl();

    void setName(String name);

    void setChapter_number(float number);

    void setDate_upload(long date);

    final class Companion {
        public static final Companion INSTANCE = new Companion();

        private Companion() {}

        public SChapter create() {
            return null;
        }
    }
}
