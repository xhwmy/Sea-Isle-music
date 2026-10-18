package app.seashelf.music.api;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.Collections;

/** Blocking network API; callers must use a background executor. */
public final class SubsonicClient {
    private final ServerAddress address;
    private final String username;
    private final String password;

    public SubsonicClient(ServerAddress address, String username, String password) {
        this.address = address;
        this.username = username;
        this.password = password;
    }

    public void ping() throws IOException { SubsonicXml.ping(request("ping", Collections.emptyMap())); }

    public List<Album> albums() throws IOException {
        List<Album> albums = new ArrayList<>();
        int offset = 0;
        while (true) {
            List<Album> page = SubsonicXml.albums(request("getAlbumList2", params(
                    "type", "newest", "size", "60", "offset", Integer.toString(offset))));
            albums.addAll(page);
            if (page.size() < 60) return albums;
            offset += page.size();
        }
    }

    public List<Song> album(String id) throws IOException {
        return SubsonicXml.songsInAlbum(request("getAlbum", params("id", id)));
    }

    public List<Album> artist(String id) throws IOException {
        return SubsonicXml.artistAlbums(request("getArtist", params("id", id)));
    }

    public SearchResults search(String query) throws IOException {
        List<Artist> artists = new ArrayList<>();
        List<Album> albums = new ArrayList<>();
        List<Song> songs = new ArrayList<>();
        boolean moreArtists = true, moreAlbums = true, moreSongs = true;
        while (moreArtists || moreAlbums || moreSongs) {
            SearchResults page = SubsonicXml.search(request("search3", params("query", query,
                    "artistCount", moreArtists ? "20" : "0", "artistOffset", Integer.toString(artists.size()),
                    "albumCount", moreAlbums ? "30" : "0", "albumOffset", Integer.toString(albums.size()),
                    "songCount", moreSongs ? "60" : "0", "songOffset", Integer.toString(songs.size()))));
            if (moreArtists) {
                artists.addAll(page.artists());
                moreArtists = page.artists().size() == 20;
            }
            if (moreAlbums) {
                albums.addAll(page.albums());
                moreAlbums = page.albums().size() == 30;
            }
            if (moreSongs) {
                songs.addAll(page.songs());
                moreSongs = page.songs().size() == 60;
            }
        }
        return new SearchResults(artists, albums, songs);
    }

    public Lyrics lyrics(String songId) throws IOException {
        return SubsonicXml.lyrics(request("getLyricsBySongId", params("id", songId)));
    }

    public List<Song> songs() throws IOException {
        List<Song> songs = new ArrayList<>();
        while (true) {
            List<Song> page = SubsonicXml.search(request("search3", params("query", "",
                    "artistCount", "0", "albumCount", "0", "songCount", "200",
                    "songOffset", Integer.toString(songs.size())))).songs();
            songs.addAll(page);
            if (page.size() < 200) return songs;
        }
    }

    public URL coverArtUrl(String id) {
        return coverArtUrl(id, 512);
    }

    public URL coverArtUrl(String id, int size) {
        return url("getCoverArt", params("id", id, "size", Integer.toString(size)));
    }

    public URL streamUrl(String songId) {
        return url("stream", params("id", songId, "format", "mp3", "maxBitRate", "192"));
    }

    public URL originalStreamUrl(String songId) {
        return url("stream", params("id", songId, "format", "raw"));
    }

    public ServerAddress address() { return address; }
    public String playbackAccountKey() {
        return app.seashelf.music.player.PlaybackSessionCodec.accountKey(address.baseUrl(), username);
    }

    private static Map<String, String> params(String... items) {
        Map<String, String> result = new HashMap<>();
        for (int i = 0; i < items.length; i += 2) result.put(items[i], items[i + 1]);
        return result;
    }

    private URL url(String method, Map<String, String> args) {
        return AuthUrls.forMethod(address, username, password, method, args);
    }

    private String request(String method, Map<String, String> args) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) url(method, args).openConnection();
        connection.setConnectTimeout(8000);
        connection.setReadTimeout(15000);
        connection.setInstanceFollowRedirects(false);
        connection.setRequestProperty("Accept", "application/xml, text/xml");
        try {
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) throw new ApiError(status, "服务器连接失败（HTTP " + status + "）");
            try (InputStream in = connection.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[4096];
                int count;
                while ((count = in.read(buffer)) != -1) {
                    if (out.size() + count > 8 * 1024 * 1024) throw new ApiError(-1, "服务器返回的数据过大");
                    out.write(buffer, 0, count);
                }
                return out.toString("UTF-8");
            }
        } finally {
            connection.disconnect();
        }
    }
}
