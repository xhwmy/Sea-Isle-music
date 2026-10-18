package app.seashelf.music.api;

import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import static org.junit.Assert.*;

public final class AndroidXmlCompatibilityTest {
    @Test public void testNormalizesRealStructuredLyricShapeOnAndroidParser() throws Exception {
        String xml = "<subsonic-response xmlns='http://subsonic.org/restapi' status='ok'><lyricsList>"
                + "<structuredLyrics synced='true' lang='und'><line start='2000'>Later fixture</line>"
                + "<line start='1000'>First fixture</line><line start='1000'>Translation fixture</line>"
                + "<line>Untimed invalid fixture</line></structuredLyrics></lyricsList></subsonic-response>";
        Lyrics lyrics = SubsonicXml.lyrics(xml);
        assertEquals(2, lyrics.lines().size());
        assertEquals("First fixture\nTranslation fixture", lyrics.lines().get(0).text());
        assertEquals(0, lyrics.activeLine(1999));
    }
    @Test public void testParsesNavidromeErrorResponseOnAndroid() throws Exception {
        String xml = "<subsonic-response xmlns='http://subsonic.org/restapi' status='failed' "
                + "version='1.16.1'><error code='40' message='Wrong username or password'/>"
                + "</subsonic-response>";
        try {
            SubsonicXml.ping(xml);
            fail("Expected an authentication error");
        } catch (ApiError error) {
            assertEquals("Parser failed: " + error.getCause(), 40, error.code());
        }
    }

    @Test public void testRejectsDocumentTypeOnAndroid() throws Exception {
        String xml = "<!DOCTYPE response [<!ENTITY secret SYSTEM 'file:///etc/passwd'>]>"
                + "<subsonic-response status='ok'>&secret;</subsonic-response>";
        try {
            SubsonicXml.ping(xml);
            fail("Expected DTD rejection");
        } catch (ApiError error) {
            assertEquals(-1, error.code());
        }
    }

    @Test public void testPhoneReachesNavidromeThroughFunnel() throws Exception {
        // Optional real-network probe: never hardcode a private server in a shared test.
        // Pass -Pandroid.testInstrumentationRunnerArguments.testServer=https://YOUR_SERVER
        String server = InstrumentationRegistry.getArguments().getString("testServer");
        if (server == null || server.trim().isEmpty()) return;
        SubsonicClient client = new SubsonicClient(
                ServerAddress.parse(server),
                "probe", "wrong");
        try {
            client.ping();
            fail("Expected authentication rejection");
        } catch (ApiError error) {
            assertEquals("Phone received: " + error, 40, error.code());
        }
    }
}
