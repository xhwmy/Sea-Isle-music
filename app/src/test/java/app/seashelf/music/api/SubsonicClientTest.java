package app.seashelf.music.api;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import static org.junit.Assert.*;

public class SubsonicClientTest {
    private ServerSocket server;
    private String base;
    private final AtomicReference<String> path = new AtomicReference<>();
    private volatile int status = 200;
    private volatile Function<String, String> response = ignored -> "<subsonic-response status='ok'><albumList2><album id='x' name='Test'/></albumList2></subsonic-response>";

    @Before public void start() throws Exception {
        server = new ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"));
        base = "http://127.0.0.1:" + server.getLocalPort();
        Thread responder = new Thread(() -> {
            while (!server.isClosed()) {
                try (Socket socket = server.accept()) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                    String first = reader.readLine();
                    if (first == null) continue;
                    path.set(first.split(" ")[1]);
                    String header;
                    while ((header = reader.readLine()) != null && !header.isEmpty()) { /* headers */ }
                    byte[] bytes = status == 200
                            ? response.apply(path.get()).getBytes(StandardCharsets.UTF_8)
                            : new byte[0];
                    OutputStream out = socket.getOutputStream();
                    out.write(("HTTP/1.1 " + status + " " + (status == 200 ? "OK" : "Unavailable") + "\r\nContent-Length: " + bytes.length + "\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
                    out.write(bytes);
                    out.flush();
                } catch (Exception ignored) {
                    if (server.isClosed()) return;
                }
            }
        }, "fake-subsonic");
        responder.setDaemon(true);
        responder.start();
    }

    @After public void stop() throws Exception { server.close(); }

    @Test public void fetchesAlbumsWithAuthenticatedApiRequest() throws Exception {
        SubsonicClient client = new SubsonicClient(ServerAddress.parse(base), "user", "password");
        assertEquals(1, client.albums().size());
        assertTrue(path.get().startsWith("/rest/getAlbumList2.view?"));
        assertTrue(path.get().contains("type=newest"));
        assertTrue(path.get().contains("t="));
        assertFalse(path.get().contains("password"));
    }

    @Test public void convertsHttpFailureToActionableError() throws Exception {
        status = 503;
        try {
            new SubsonicClient(ServerAddress.parse(base), "user", "password").ping();
            fail("Expected service error");
        } catch (ApiError error) {
            assertTrue(error.getMessage().contains("503"));
        }
    }

    @Test public void requestsBandwidthLimitedStreamWithOriginalFallback() {
        SubsonicClient client = new SubsonicClient(ServerAddress.parse(base), "user", "password");
        String url = client.streamUrl("song-1").toString();
        assertTrue(url.contains("/rest/stream.view?"));
        assertTrue(url.contains("id=song-1"));
        assertTrue(url.contains("format=mp3"));
        assertTrue(url.contains("maxBitRate=192"));
        String original = client.originalStreamUrl("song-1").toString();
        assertTrue(original.contains("format=raw"));
        assertFalse(original.contains("maxBitRate="));
    }

    @Test public void loadsAlbumsBeyondFirstSixty() throws Exception {
        response = request -> "<subsonic-response status='ok'><albumList2>"
                + items("album", number(request, "offset", 0), number(request, "size", 10), 125)
                + "</albumList2></subsonic-response>";
        java.util.List<Album> albums = new SubsonicClient(ServerAddress.parse(base), "user", "password").albums();
        assertEquals(125, albums.size());
        assertEquals("album124", albums.get(124).id());
    }

    @Test public void pagesSearchCategoriesIndependentlyAndHandlesExactFullSongPage() throws Exception {
        response = request -> "<subsonic-response status='ok'><searchResult3>"
                + items("artist", number(request, "artistOffset", 0), number(request, "artistCount", 20), 21)
                + items("album", number(request, "albumOffset", 0), number(request, "albumCount", 20), 31)
                + items("song", number(request, "songOffset", 0), number(request, "songCount", 20), 120)
                + "</searchResult3></subsonic-response>";
        SearchResults found = new SubsonicClient(ServerAddress.parse(base), "user", "password").search("music");
        assertEquals(21, found.artists().size());
        assertEquals(31, found.albums().size());
        assertEquals(120, found.songs().size());
        assertEquals("song119", found.songs().get(119).id());
    }

    private static String items(String tag, int offset, int size, int total) {
        StringBuilder xml = new StringBuilder();
        for (int i = offset; i < Math.min(offset + size, total); i++)
            xml.append('<').append(tag).append(" id='").append(tag).append(i)
                    .append("' name='Item' title='Item'/>");
        return xml.toString();
    }

    @Test public void loadsLibraryQueueAcrossPagesWithoutArtistOrAlbumRequests() throws Exception {
        response = request -> {
            assertTrue(request.contains("query=&"));
            assertEquals(0, number(request, "artistCount", -1));
            assertEquals(0, number(request, "albumCount", -1));
            return "<subsonic-response status='ok'><searchResult3>"
                    + items("song", number(request, "songOffset", 0), number(request, "songCount", 20), 205)
                    + "</searchResult3></subsonic-response>";
        };
        java.util.List<Song> songs = new SubsonicClient(ServerAddress.parse(base), "user", "password").songs();
        assertEquals(205, songs.size());
        assertEquals("song204", songs.get(204).id());
    }

    private static int number(String request, String key, int fallback) {
        String query = request.substring(request.indexOf('?') + 1);
        for (String parameter : query.split("&")) {
            if (parameter.startsWith(key + "=")) return Integer.parseInt(parameter.substring(key.length() + 1));
        }
        return fallback;
    }
}
