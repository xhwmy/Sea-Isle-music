package app.seashelf.music.api;

import java.util.Arrays;
import org.junit.Test;
import static org.junit.Assert.*;

public class LyricsTest {
    @Test public void sortsTimingsAndGroupsSimultaneousTranslationsWithoutInventingTiming() {
        Lyrics value = new Lyrics(true, Arrays.asList(new Lyrics.Line(3000, "Later"),
                new Lyrics.Line(1000, "First"), new Lyrics.Line(1000, "Translation"),
                new Lyrics.Line(-1, "Invalid"), new Lyrics.Line(1000, "First")));
        assertEquals(2, value.lines().size());
        assertEquals("First\nTranslation", value.lines().get(0).text());
        assertEquals(-1, value.activeLine(999));
        assertEquals(0, value.activeLine(2999));
        assertEquals(1, value.activeLine(3000));
    }

    @Test public void malformedXmlTimingsAreNotShownAsZeroTimeLyrics() throws Exception {
        Lyrics value = SubsonicXml.lyrics("<subsonic-response status='ok'><lyricsList><structuredLyrics synced='true'>"
                + "<line start='oops'>Bad</line><line>No timing</line><line start='-9'>Negative</line>"
                + "<line start='1000'>Valid</line></structuredLyrics></lyricsList></subsonic-response>");
        assertEquals(1, value.lines().size());
        assertEquals(-1, value.activeLine(0));
        assertEquals("Valid", value.lines().get(0).text());
    }

    @Test public void plainLyricsRemainStaticAndKeepTheirOrder() {
        Lyrics value = new Lyrics(false, Arrays.asList(new Lyrics.Line(5, "One"), new Lyrics.Line(2, "Two")));
        assertFalse(value.synced());
        assertEquals("One", value.lines().get(0).text());
        assertEquals(-1, value.activeLine(10000));
    }
}
