package app.seashelf.music.api;

import org.junit.Test;
import java.util.List;
import static org.junit.Assert.*;

public class SubsonicXmlTest {
    @Test public void parsesAlbumsAndChineseSongs() throws Exception {
        String albums = "<subsonic-response status='ok'><albumList2>"
                + "<album id='a1' name='云与海' artist='阿YueYue' coverArt='c1' songCount='2'/>"
                + "</albumList2></subsonic-response>";
        List<Album> parsed = SubsonicXml.albums(albums);
        assertEquals(1, parsed.size());
        assertEquals("云与海", parsed.get(0).name());
        assertEquals("c1", parsed.get(0).coverArtId());

        String album = "<subsonic-response status='ok'><album id='a1' name='云与海'>"
                + "<song id='s1' title='云与海' artist='阿YueYue' album='云与海' duration='258' coverArt='c1'/>"
                + "</album></subsonic-response>";
        List<Song> songs = SubsonicXml.songsInAlbum(album);
        assertEquals(1, songs.size());
        assertEquals("阿YueYue", songs.get(0).artist());
        assertEquals(258, songs.get(0).durationSeconds());
    }

    @Test public void parsesMixedSearchResultsAndEmptyResults() throws Exception {
        String xml = "<subsonic-response status='ok'><searchResult3>"
                + "<artist id='ar1' name='阿YueYue'/>"
                + "<album id='al1' name='云与海' artist='阿YueYue'/>"
                + "<song id='s1' title='云与海' artist='阿YueYue'/>"
                + "</searchResult3></subsonic-response>";
        SearchResults results = SubsonicXml.search(xml);
        assertEquals(1, results.artists().size());
        assertEquals(1, results.albums().size());
        assertEquals(1, results.songs().size());
        assertTrue(SubsonicXml.search("<subsonic-response status='ok'><searchResult3/></subsonic-response>").songs().isEmpty());
    }

    @Test public void reportsApiErrorAndRejectsMalformedOrExternalXml() throws Exception {
        String denied = "<subsonic-response status='failed'><error code='40' message='Wrong password'/></subsonic-response>";
        try {
            SubsonicXml.ping(denied);
            fail("Expected API error");
        } catch (ApiError error) {
            assertEquals(40, error.code());
        }
        try {
            SubsonicXml.ping("<subsonic-response");
            fail("Expected malformed XML error");
        } catch (ApiError expected) {
            assertEquals(-1, expected.code());
        }
        try {
            SubsonicXml.ping("<!DOCTYPE foo [<!ENTITY xxe SYSTEM 'file:///etc/passwd'>]><subsonic-response status='ok'>&xxe;</subsonic-response>");
            fail("Expected external entity rejection");
        } catch (ApiError expected) {
            assertEquals(-1, expected.code());
        }
    }

    @Test public void parsesTimedLyricsAndFindsCurrentLine() throws Exception {
        String xml = "<subsonic-response status='ok'><lyricsList>"
                + "<structuredLyrics synced='true' lang='zh'><line start='0' value='第一句'/><line start='12500' value='第二句'/>"
                + "</structuredLyrics></lyricsList></subsonic-response>";
        Lyrics lyrics = SubsonicXml.lyrics(xml);
        assertTrue(lyrics.synced());
        assertEquals(2, lyrics.lines().size());
        assertEquals(0, lyrics.activeLine(12499));
        assertEquals(1, lyrics.activeLine(12500));
        assertEquals("第二句", lyrics.lines().get(1).text());
    }

    @Test public void handlesPlainAndMissingLyrics() throws Exception {
        String plain = "<subsonic-response status='ok'><lyricsList><structuredLyrics synced='false'>"
                + "<line value='第一句'/><line value='第二句'/></structuredLyrics></lyricsList></subsonic-response>";
        Lyrics lyrics = SubsonicXml.lyrics(plain);
        assertFalse(lyrics.synced());
        assertEquals(2, lyrics.lines().size());
        assertEquals(-1, lyrics.activeLine(5000));
        assertTrue(SubsonicXml.lyrics("<subsonic-response status='ok'><lyricsList/></subsonic-response>").lines().isEmpty());
    }

    @Test public void readsLyricsFromNavidromeLineBody() throws Exception {
        String xml = "<subsonic-response status='ok'><lyricsList>"
                + "<structuredLyrics synced='true'><line start='130'>第一句</line>"
                + "<line start='1460'>第二句</line></structuredLyrics>"
                + "</lyricsList></subsonic-response>";
        Lyrics lyrics = SubsonicXml.lyrics(xml);
        assertEquals(2, lyrics.lines().size());
        assertEquals("第一句", lyrics.lines().get(0).text());
        assertEquals(1, lyrics.activeLine(1460));
    }
}
