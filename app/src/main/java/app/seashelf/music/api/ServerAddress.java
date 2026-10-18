package app.seashelf.music.api;

import java.net.URI;
import java.util.Locale;

/** Validated base URL for a Navidrome server. */
public final class ServerAddress {
    private final String base;

    private ServerAddress(String base) {
        this.base = base;
    }

    public static ServerAddress parse(String input) {
        if (input == null || input.trim().isEmpty()) {
            throw new IllegalArgumentException("请输入服务器地址");
        }
        URI uri;
        try {
            uri = URI.create(input.trim());
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException("服务器地址格式不正确", error);
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if ((!"http".equals(scheme) && !"https".equals(scheme))
                || uri.getHost() == null || uri.getUserInfo() != null
                || uri.getRawQuery() != null || uri.getRawFragment() != null) {
            throw new IllegalArgumentException("请输入以 http:// 或 https:// 开头的服务器地址");
        }
        if ("http".equals(scheme) && !isLocalHost(uri.getHost())) {
            throw new IllegalArgumentException("外网服务器请使用 HTTPS 地址");
        }
        String path = uri.getRawPath() == null ? "" : uri.getRawPath();
        while (path.endsWith("/") && !path.isEmpty()) path = path.substring(0, path.length() - 1);
        if (path.endsWith("/app")) path = path.substring(0, path.length() - 4);
        return new ServerAddress(scheme + "://" + uri.getRawAuthority() + path);
    }

    public String endpoint(String method) {
        if (method == null || !method.matches("[A-Za-z][A-Za-z0-9]*")) {
            throw new IllegalArgumentException("无效的 API 方法");
        }
        return base + "/rest/" + method + ".view";
    }

    public String baseUrl() {
        return base;
    }

    private static boolean isLocalHost(String host) {
        String lower = host.toLowerCase(Locale.ROOT);
        if (lower.equals("localhost") || lower.endsWith(".localhost") || lower.endsWith(".local")) return true;
        String ipv6 = lower.startsWith("[") && lower.endsWith("]")
                ? lower.substring(1, lower.length() - 1) : lower;
        if (ipv6.contains(":") && (ipv6.equals("::1") || ipv6.startsWith("fc")
                || ipv6.startsWith("fd") || ipv6.startsWith("fe80:"))) return true;
        String[] parts = lower.split("\\.");
        if (parts.length != 4) return false;
        int[] octets = new int[4];
        try {
            for (int i = 0; i < 4; i++) {
                if (parts[i].isEmpty() || parts[i].length() > 3) return false;
                octets[i] = Integer.parseInt(parts[i]);
                if (octets[i] < 0 || octets[i] > 255) return false;
            }
        } catch (NumberFormatException error) {
            return false;
        }
        return octets[0] == 10 || octets[0] == 127 || octets[0] == 169 && octets[1] == 254
                || octets[0] == 172 && octets[1] >= 16 && octets[1] <= 31
                || octets[0] == 192 && octets[1] == 168;
    }
}
