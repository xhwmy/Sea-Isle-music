package app.seashelf.music.player;

import android.annotation.SuppressLint;
import android.content.Context;
import android.net.Uri;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.media3.database.StandaloneDatabaseProvider;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DataSpec;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.datasource.HttpDataSource;
import androidx.media3.datasource.cache.ContentMetadata;
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor;
import androidx.media3.datasource.cache.SimpleCache;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import junit.framework.TestCase;

/** Real HTTP + Media3 cache regression. No production server or real credentials are used. */
@SuppressLint("UnsafeOptInUsageError")
public final class AudioCacheIntegrationTest extends TestCase {
    private Context getContext() { return InstrumentationRegistry.getInstrumentation().getTargetContext(); }
    public void testRotatedAuthenticationReusesAudioAndRedirectUriIsNeverPersisted() throws Exception {
        File directory = new File(getContext().getCacheDir(), "audio-test-" + System.nanoTime());
        StandaloneDatabaseProvider database = new StandaloneDatabaseProvider(getContext());
        SimpleCache cache = new SimpleCache(directory, new LeastRecentlyUsedCacheEvictor(1024 * 1024), database);
        try (Fixture fixture = new Fixture()) {
            DataSource.Factory factory = PrivateAudioCache.cachedFactory(cache, http());
            String key = StreamPolicy.cacheKey("account-a", "song-1", StreamPolicy.MP3_PROFILE);
            assertTrue(Arrays.equals(fixture.audio, read(factory, fixture.url("/signed?s=first-token"), key)));
            int firstRequests = fixture.requests.get();
            assertEquals(2, firstRequests); // Real HTTP redirect followed by real audio response.
            assertTrue(Arrays.equals(fixture.audio, read(factory, fixture.url("/signed?s=rotated-token"), key)));
            assertEquals("Rotating authentication must not redownload cached bytes", firstRequests, fixture.requests.get());
            assertNull(ContentMetadata.getRedirectedUri(cache.getContentMetadata(key)));
            assertEquals(fixture.audio.length, cache.getCachedBytes(key, 0, fixture.audio.length));

            String otherAccount = StreamPolicy.cacheKey("account-b", "song-1", StreamPolicy.MP3_PROFILE);
            read(factory, fixture.url("/signed?s=other-account"), otherAccount);
            String otherProfile = StreamPolicy.cacheKey("account-a", "song-1", StreamPolicy.ORIGINAL_PROFILE);
            read(factory, fixture.url("/signed?s=other-profile"), otherProfile);
            assertEquals(6, fixture.requests.get());
            assertEquals(3, cache.getKeys().size());
            for (String stored : cache.getKeys()) {
                assertTrue(stored.matches("audio-[a-f0-9]{64}"));
                assertNull(ContentMetadata.getRedirectedUri(cache.getContentMetadata(stored)));
            }
            cache.release();
            cache = new SimpleCache(directory, new LeastRecentlyUsedCacheEvictor(1024 * 1024), database);
            read(PrivateAudioCache.cachedFactory(cache, http()), fixture.url("/signed?s=after-restart"), key);
            assertEquals("The cache survives a new SimpleCache owner", 6, fixture.requests.get());
            assertNull(ContentMetadata.getRedirectedUri(cache.getContentMetadata(key)));
        } finally {
            cache.release(); database.close(); delete(directory);
        }
    }

    public void testXmlAndUnauthorizedResponsesAreRejectedWithoutCachingErrorBody() throws Exception {
        File directory = new File(getContext().getCacheDir(), "audio-errors-" + System.nanoTime());
        StandaloneDatabaseProvider database = new StandaloneDatabaseProvider(getContext());
        SimpleCache cache = new SimpleCache(directory, new LeastRecentlyUsedCacheEvictor(1024 * 1024), database);
        try (Fixture fixture = new Fixture()) {
            DataSource.Factory factory = PrivateAudioCache.cachedFactory(cache, http());
            String xmlKey = StreamPolicy.cacheKey("account-a", "xml", StreamPolicy.MP3_PROFILE);
            try { read(factory, fixture.url("/xml"), xmlKey); fail("XML is an API error, not audio"); }
            catch (HttpDataSource.InvalidContentTypeException expected) { }
            assertEquals(0, cache.getCachedBytes(xmlKey, 0, 4096));
            String httpKey = StreamPolicy.cacheKey("account-a", "http", StreamPolicy.MP3_PROFILE);
            try { read(factory, fixture.url("/unauthorized"), httpKey); fail("HTTP authorization must fail"); }
            catch (HttpDataSource.InvalidResponseCodeException expected) { assertEquals(401, expected.responseCode); }
            assertEquals(0, cache.getCachedBytes(httpKey, 0, 4096));
        } finally { cache.release(); database.close(); delete(directory); }
    }

    private static DefaultHttpDataSource.Factory http() {
        return new DefaultHttpDataSource.Factory().setContentTypePredicate(StreamPolicy::acceptsContentType)
                .setConnectTimeoutMs(3000).setReadTimeoutMs(3000);
    }
    private static byte[] read(DataSource.Factory factory, String url, String key) throws Exception {
        DataSource source = factory.createDataSource();
        try {
            source.open(new DataSpec.Builder().setUri(Uri.parse(url)).setKey(key).build());
            ByteArrayOutputStream bytes = new ByteArrayOutputStream(); byte[] buffer = new byte[1024]; int count;
            while ((count = source.read(buffer, 0, buffer.length)) != -1) bytes.write(buffer, 0, count);
            return bytes.toByteArray();
        } finally { source.close(); }
    }
    private static void delete(File file) {
        File[] children = file.listFiles();
        if (children != null) for (File child : children) delete(child);
        file.delete();
    }
    private static final class Fixture implements AutoCloseable {
        final ServerSocket server;
        final AtomicInteger requests = new AtomicInteger();
        final byte[] audio = new byte[8192];
        Fixture() throws Exception {
            for (int i = 0; i < audio.length; i++) audio[i] = (byte) (i * 31);
            server = new ServerSocket(0, 20, InetAddress.getByName("127.0.0.1"));
            Thread thread = new Thread(() -> {
                while (!server.isClosed()) {
                    try (Socket socket = server.accept()) {
                        socket.setSoTimeout(3000);
                        BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII));
                        String first = reader.readLine(); if (first == null) continue;
                        String path = first.split(" ")[1]; String header;
                        while ((header = reader.readLine()) != null && !header.isEmpty()) { }
                        requests.incrementAndGet(); OutputStream output = socket.getOutputStream();
                        if (path.startsWith("/signed")) {
                            output.write(("HTTP/1.1 302 Found\r\nLocation: " + url("/audio?token=redirect-secret")
                                    + "\r\nContent-Length: 0\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
                        } else {
                            boolean xml = path.startsWith("/xml"), unauthorized = path.startsWith("/unauthorized");
                            byte[] body = xml ? "<subsonic-response status='failed'><error code='40'/></subsonic-response>"
                                    .getBytes(StandardCharsets.UTF_8) : unauthorized ? new byte[0] : audio;
                            output.write(("HTTP/1.1 " + (unauthorized ? "401 Unauthorized" : "200 OK")
                                    + "\r\nContent-Type: " + (xml ? "application/xml" : "audio/mpeg")
                                    + "\r\nContent-Length: " + body.length + "\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
                            output.write(body);
                        }
                        output.flush();
                    } catch (Exception ignored) { if (server.isClosed()) return; }
                }
            }, "audio-http-fixture");
            thread.setDaemon(true); thread.start();
        }
        String url(String path) { return "http://127.0.0.1:" + server.getLocalPort() + path; }
        @Override public void close() throws Exception { server.close(); }
    }
}
