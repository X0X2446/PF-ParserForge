package eu.kanade.tachiyomi.source;

public interface Source {
    long getId();

    String getName();

    String getLang();

    boolean getSupportsLatest();
}
