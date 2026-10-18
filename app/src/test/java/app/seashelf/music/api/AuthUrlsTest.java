package app.seashelf.music.api;

import org.junit.Test;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;
import static org.junit.Assert.*;

public class AuthUrlsTest {
    @Test public void signsWithoutPuttingPasswordInUrl() throws Exception {
        URL url = AuthUrls.forMethod(ServerAddress.parse("https://music.example.com"),
                "alice", "my-secret", "ping", Map.of(), "a1b2c3");
        String query = url.getQuery();
        String expected = bytesToHex(MessageDigest.getInstance("MD5")
                .digest("my-secreta1b2c3".getBytes(StandardCharsets.UTF_8)));
        assertTrue(query.contains("u=alice"));
        assertTrue(query.contains("s=a1b2c3"));
        assertTrue(query.contains("t=" + expected));
        assertTrue(query.contains("v=1.16.1"));
        assertFalse(query.contains("my-secret"));
    }

    @Test public void encodesChineseSearchTerms() throws Exception {
        URL url = AuthUrls.forMethod(ServerAddress.parse("https://music.example.com"),
                "用户", "secret", "search3", Map.of("query", "云与海 & 夏天"), "salt");
        Map<String, String> args = new HashMap<>();
        for (String part : url.getQuery().split("&")) {
            String[] pair = part.split("=", 2);
            args.put(pair[0], URLDecoder.decode(pair[1], StandardCharsets.UTF_8));
        }
        assertEquals("用户", args.get("u"));
        assertEquals("云与海 & 夏天", args.get("query"));
        assertEquals("https", url.getProtocol());
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder text = new StringBuilder();
        for (byte value : bytes) text.append(String.format("%02x", value & 0xff));
        return text.toString();
    }
}
