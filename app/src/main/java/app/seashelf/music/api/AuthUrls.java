package app.seashelf.music.api;

import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Map;

/** Subsonic token authentication URLs. */
public final class AuthUrls {
    private static final SecureRandom RANDOM = new SecureRandom();

    private AuthUrls() { }

    public static URL forMethod(ServerAddress address, String username, String password,
                                String method, Map<String, String> parameters) {
        byte[] random = new byte[12];
        RANDOM.nextBytes(random);
        return forMethod(address, username, password, method, parameters, hex(random));
    }

    /** Salt overload kept deterministic for request tests. */
    public static URL forMethod(ServerAddress address, String username, String password,
                                String method, Map<String, String> parameters, String salt) {
        if (username == null || username.trim().isEmpty() || password == null || password.isEmpty()) {
            throw new IllegalArgumentException("请输入用户名和密码");
        }
        StringBuilder query = new StringBuilder();
        append(query, "u", username);
        append(query, "t", md5(password + salt));
        append(query, "s", salt);
        append(query, "v", "1.16.1");
        append(query, "c", "SeaShelf");
        append(query, "f", "xml");
        for (Map.Entry<String, String> entry : parameters.entrySet()) {
            if ("u".equals(entry.getKey()) || "t".equals(entry.getKey())
                    || "s".equals(entry.getKey()) || "p".equals(entry.getKey())) {
                throw new IllegalArgumentException("请求参数不能覆盖认证字段");
            }
            append(query, entry.getKey(), entry.getValue());
        }
        try {
            return new URL(address.endpoint(method) + "?" + query);
        } catch (MalformedURLException error) {
            throw new IllegalArgumentException("无法构造服务器请求", error);
        }
    }

    private static void append(StringBuilder query, String key, String value) {
        if (query.length() > 0) query.append('&');
        query.append(encoded(key));
        query.append('=');
        query.append(encoded(value == null ? "" : value));
    }

    private static String encoded(String value) {
        try {
            return URLEncoder.encode(value, "UTF-8");
        } catch (UnsupportedEncodingException impossible) {
            throw new IllegalStateException("UTF-8 unavailable", impossible);
        }
    }

    private static String md5(String text) {
        try {
            return hex(MessageDigest.getInstance("MD5").digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("MD5 unavailable", error);
        }
    }

    private static String hex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            result.append(Character.forDigit((value >>> 4) & 0xf, 16));
            result.append(Character.forDigit(value & 0xf, 16));
        }
        return result.toString();
    }
}
