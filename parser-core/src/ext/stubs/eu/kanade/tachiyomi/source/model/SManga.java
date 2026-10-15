package eu.kanade.tachiyomi.source.model;

public interface SManga {
    int UNKNOWN = 0;
    int ONGOING = 1;
    int COMPLETED = 2;

    void setUrl(String url);

    String getUrl();

    void setTitle(String title);

    void setThumbnail_url(String url);

    void setArtist(String artist);

    void setAuthor(String author);

    void setStatus(int status);

    void setDescription(String description);

    void setGenre(String genre);

    void setInitialized(boolean initialized);

    final class Companion {
        public static final Companion INSTANCE = new Companion();

        private Companion() {}

        public SManga create() {
            return null;
        }
    }
}
