package app.seashelf.music.api;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Collections;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

/** Secure, small XML parser for the Navidrome Subsonic responses used by this app. */
public final class SubsonicXml {
    private SubsonicXml() { }

    public static void ping(String xml) throws ApiError {
        response(xml);
    }

    public static List<Album> albums(String xml) throws ApiError {
        return albumsWithin(response(xml), "albumList2");
    }

    public static List<Album> artistAlbums(String xml) throws ApiError {
        return albumsWithin(response(xml), "artist");
    }

    public static List<Song> songsInAlbum(String xml) throws ApiError {
        Element root = response(xml);
        Element container = firstChild(root, "album");
        if (container == null) return Collections.emptyList();
        List<Song> songs = new ArrayList<>();
        for (Element item : children(container, "song")) songs.add(song(item));
        return songs;
    }

    public static SearchResults search(String xml) throws ApiError {
        Element container = firstChild(response(xml), "searchResult3");
        if (container == null) return new SearchResults(Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
        List<Artist> artists = new ArrayList<>();
        List<Album> albums = new ArrayList<>();
        List<Song> songs = new ArrayList<>();
        for (Element item : children(container, "artist"))
            artists.add(new Artist(item.getAttribute("id"), item.getAttribute("name")));
        for (Element item : children(container, "album")) albums.add(album(item));
        for (Element item : children(container, "song")) songs.add(song(item));
        return new SearchResults(artists, albums, songs);
    }

    public static Lyrics lyrics(String xml) throws ApiError {
        Element list = firstChild(response(xml), "lyricsList");
        if (list == null) return new Lyrics(false, Collections.emptyList());
        Lyrics plain = new Lyrics(false, Collections.emptyList());
        for (Element section : children(list, "structuredLyrics")) {
            boolean synced = "true".equalsIgnoreCase(section.getAttribute("synced"));
            List<Lyrics.Line> lines = new ArrayList<>();
            for (Element item : children(section, "line")) {
                String value = item.getAttribute("value").trim();
                if (value.isEmpty()) value = item.getTextContent().trim();
                if (value.isEmpty()) continue;
                int start = -1;
                if (synced) try { start = Integer.parseInt(item.getAttribute("start")); }
                catch (NumberFormatException ignored) { }
                lines.add(new Lyrics.Line(start, value));
            }
            if (!lines.isEmpty()) {
                Lyrics result = new Lyrics(synced, lines);
                if (result.synced()) return result;
                if (plain.lines().isEmpty()) plain = result;
            }
        }
        return plain;
    }

    private static List<Album> albumsWithin(Element root, String containerName) {
        Element container = firstChild(root, containerName);
        if (container == null) return Collections.emptyList();
        List<Album> albums = new ArrayList<>();
        for (Element item : children(container, "album")) albums.add(album(item));
        return albums;
    }

    private static Album album(Element item) {
        return new Album(item.getAttribute("id"), item.getAttribute("name"),
                item.getAttribute("artist"), item.getAttribute("coverArt"),
                integer(item.getAttribute("songCount")));
    }

    private static Song song(Element item) {
        return new Song(item.getAttribute("id"), item.getAttribute("title"),
                item.getAttribute("artist"), item.getAttribute("album"),
                item.getAttribute("coverArt"), integer(item.getAttribute("duration")));
    }

    private static int integer(String value) {
        try { return Integer.parseInt(value); } catch (NumberFormatException ignored) { return 0; }
    }

    private static Element response(String xml) throws ApiError {
        try {
            // Android's built-in parser rejects the disallow-doctype-decl feature.
            // Reject DTDs before parsing so external entities cannot be declared.
            if (xml.contains("<!DOCTYPE")) throw new ApiError(-1, "服务器返回了不安全的 XML");
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            setFeatureIfSupported(factory, "http://xml.org/sax/features/external-general-entities", false);
            setFeatureIfSupported(factory, "http://xml.org/sax/features/external-parameter-entities", false);
            setFeatureIfSupported(factory, "http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            setFeatureIfSupported(factory, XMLConstants.FEATURE_SECURE_PROCESSING, true);
            try { factory.setXIncludeAware(false); } catch (UnsupportedOperationException ignored) { }
            factory.setExpandEntityReferences(false);
            Document document = factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
            Element root = document.getDocumentElement();
            if (root == null || !"subsonic-response".equals(root.getTagName()))
                throw new ApiError(-1, "服务器返回了无法识别的数据");
            if (!"ok".equals(root.getAttribute("status"))) {
                Element error = firstChild(root, "error");
                int code = error == null ? -1 : integer(error.getAttribute("code"));
                String message = error == null ? "服务器拒绝了请求" : error.getAttribute("message");
                throw new ApiError(code, message.trim().isEmpty() ? "服务器拒绝了请求" : message);
            }
            return root;
        } catch (ApiError error) {
            throw error;
        } catch (Exception error) {
            throw new ApiError(-1, "服务器返回的数据无法解析", error);
        }
    }

    private static void setFeatureIfSupported(DocumentBuilderFactory factory, String name, boolean value) {
        try { factory.setFeature(name, value); } catch (ParserConfigurationException ignored) { }
    }

    private static Element firstChild(Element parent, String name) {
        for (Node node = parent.getFirstChild(); node != null; node = node.getNextSibling())
            if (node instanceof Element && name.equals(node.getNodeName())) return (Element) node;
        return null;
    }

    private static List<Element> children(Element parent, String name) {
        List<Element> result = new ArrayList<>();
        NodeList nodes = parent.getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) {
            Node node = nodes.item(i);
            if (node instanceof Element && name.equals(node.getNodeName())) result.add((Element) node);
        }
        return result;
    }
}
