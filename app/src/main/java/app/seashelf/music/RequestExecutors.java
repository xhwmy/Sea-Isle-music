package app.seashelf.music;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Artwork must never occupy the workers used for songs, search and lyrics. */
public final class RequestExecutors implements AutoCloseable {
    private final ExecutorService api = Executors.newFixedThreadPool(3);
    private final ExecutorService images = Executors.newFixedThreadPool(2);
    public ExecutorService api() { return api; }
    public ExecutorService images() { return images; }
    @Override public void close() { api.shutdownNow(); images.shutdownNow(); }
}
