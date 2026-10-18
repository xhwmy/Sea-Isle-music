package app.seashelf.music.api;

import org.junit.Test;
import static org.junit.Assert.*;

public class ServerAddressTest {
    @Test public void normalizesRootAndAppUrls() {
        assertEquals("http://192.168.1.5:4533/rest/ping.view",
                ServerAddress.parse(" http://192.168.1.5:4533/ ").endpoint("ping"));
        assertEquals("https://music.example.ts.net/rest/search3.view",
                ServerAddress.parse("https://music.example.ts.net/app/").endpoint("search3"));
        assertEquals("https://example.com/navidrome/rest/ping.view",
                ServerAddress.parse("https://example.com/navidrome/").endpoint("ping"));
    }

    @Test public void rejectsUnsafeOrInvalidAddresses() {
        for (String input : new String[] {"", "ftp://example.com", "example.com:4533",
                "http://user:secret@example.com", "http://localhost:4533/#/app"}) {
            try {
                ServerAddress.parse(input);
                fail("Accepted " + input);
            } catch (IllegalArgumentException expected) {
                // Expected.
            }
        }
    }

    @Test public void requiresHttpsForPublicServer() {
        assertEquals("http://172.16.155.57:4533/rest/ping.view",
                ServerAddress.parse("http://172.16.155.57:4533").endpoint("ping"));
        try {
            ServerAddress.parse("http://music.example.com");
            fail("Public plaintext server accepted");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("HTTPS"));
        }
        for (String address : new String[] {"http://fc-music.example.com", "http://fdomain.example.com"}) {
            try {
                ServerAddress.parse(address);
                fail("Public hostname accepted: " + address);
            } catch (IllegalArgumentException expected) {
                assertTrue(expected.getMessage().contains("HTTPS"));
            }
        }
    }
}
