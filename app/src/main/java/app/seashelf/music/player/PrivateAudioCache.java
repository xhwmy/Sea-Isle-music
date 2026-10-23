package app.seashelf.music.player;

import android.annotation.SuppressLint;
import android.content.Context;
import android.net.Uri;
import androidx.media3.database.StandaloneDatabaseProvider;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DataSpec;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.datasource.TransferListener;
import androidx.media3.datasource.cache.CacheDataSource;
import androidx.media3.datasource.cache.CacheWriter;
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor;
import androidx.media3.datasource.cache.SimpleCache;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

/** One process-wide cache owner; release of a song never destroys reusable audio spans. */
@SuppressLint("UnsafeOptInUsageError")
public final class PrivateAudioCache {
    public static final long MAX_BYTES = 256L * 1024 * 1024;
    private static SimpleCache instance;
    private PrivateAudioCache() { }

    private static synchronized SimpleCache get(Context context) {
        if (instance == null) {
            Context app = context.getApplicationContext();
            instance = new SimpleCache(new File(app.getCacheDir(), "audio-v1"),
                    new LeastRecentlyUsedCacheEvictor(MAX_BYTES), new StandaloneDatabaseProvider(app));
        }
        return instance;
    }

    public static DataSource.Factory factory(Context context) {
        return cachedFactory(get(context), http(10000, 15000));
    }

    public static PrefetchScheduler.Task prefetchTask(Context context, String url, String key) {
        return prefetchTask(get(context), http(5000, 5000), url, key);
    }

    /** Visible to instrumentation: prefetch and playback use identical cache identity and URI privacy rules. */
    public static PrefetchScheduler.Task prefetchTask(SimpleCache cache, DataSource.Factory upstream,
                                                      String url, String key) {
        CacheDataSource source = cachedFactory(cache, upstream).createDataSourceForDownloading();
        CacheWriter writer = new CacheWriter(source,
                new DataSpec.Builder().setUri(Uri.parse(url)).setKey(key).build(), null, null);
        return new PrefetchScheduler.Task() {
            @Override public void run() throws IOException { writer.cache(); }
            @Override public void cancel() { writer.cancel(); }
        };
    }

    private static DefaultHttpDataSource.Factory http(int connectTimeoutMs, int readTimeoutMs) {
        return new DefaultHttpDataSource.Factory().setUserAgent("SeaShelf/0.4.3")
                .setConnectTimeoutMs(connectTimeoutMs).setReadTimeoutMs(readTimeoutMs)
                .setContentTypePredicate(StreamPolicy::acceptsContentType);
    }

    /** Visible to instrumentation: exercise the same cache/URI privacy path as playback. */
    public static CacheDataSource.Factory cachedFactory(SimpleCache cache, DataSource.Factory upstream) {
        return new CacheDataSource.Factory().setCache(cache)
                .setCacheKeyFactory(spec -> {
                    if (spec.key == null || !spec.key.matches("audio-[a-f0-9]{64}"))
                        throw new IllegalArgumentException("A hashed audio cache identity is required");
                    return spec.key;
                })
                .setUpstreamDataSourceFactory(() -> new RequestUriDataSource(upstream.createDataSource()))
                .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR);
    }

    /**
     * CacheDataSource persists getUri() only when it differs from DataSpec.uri (redirects).
     * Always report the original in-memory request URI so signed redirect URLs cannot enter
     * durable ContentMetadata. The real response headers and bytes still pass through unchanged.
     */
    private static final class RequestUriDataSource implements DataSource {
        private final DataSource upstream;
        private Uri requestUri;
        RequestUriDataSource(DataSource upstream) { this.upstream = upstream; }
        @Override public void addTransferListener(TransferListener listener) { upstream.addTransferListener(listener); }
        @Override public long open(DataSpec spec) throws IOException { requestUri = spec.uri; return upstream.open(spec); }
        @Override public int read(byte[] buffer, int offset, int length) throws IOException { return upstream.read(buffer, offset, length); }
        @Override public Uri getUri() { return requestUri; }
        @Override public Map<String, List<String>> getResponseHeaders() { return upstream.getResponseHeaders(); }
        @Override public void close() throws IOException {
            try { upstream.close(); } finally { requestUri = null; }
        }
    }
}
