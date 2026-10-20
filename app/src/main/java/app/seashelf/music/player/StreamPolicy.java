package app.seashelf.music.player;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;

/** Identity and retry rules contain no URL, authentication token or Android state. */
public final class StreamPolicy {
    public static final String MP3_PROFILE = "mp3-192-v1";
    public static final String METERED_MP3_PROFILE = "mp3-96-v1";
    public static final String ORIGINAL_PROFILE = "original-v1";
    public enum Failure { NETWORK, HTTP, INVALID_RESPONSE, UNSUPPORTED_CONTAINER, UNSUPPORTED_CODEC, OTHER }

    /** ExoPlayer keeps fetching while buffered audio is below minMs, then resumes as it is consumed. */
    public static final class BufferWindow {
        public final int minMs, maxMs, startMs, rebufferMs;
        private BufferWindow(int minMs, int maxMs, int startMs, int rebufferMs) {
            this.minMs = minMs; this.maxMs = maxMs; this.startMs = startMs; this.rebufferMs = rebufferMs;
        }
    }

    private StreamPolicy() { }

    public static BufferWindow bufferWindow() { return new BufferWindow(90000, 180000, 3000, 8000); }

    public static boolean mayReconnect(Failure failure, int httpStatus) {
        return failure == Failure.NETWORK || failure == Failure.HTTP
                && (httpStatus == 429 || httpStatus >= 500 && httpStatus <= 599);
    }

    public static int bitrateForMetered(boolean metered) { return metered ? 96 : 192; }

    public static String profileForBitrate(int bitrate) {
        if (bitrate == 96) return METERED_MP3_PROFILE;
        if (bitrate == 192) return MP3_PROFILE;
        throw new IllegalArgumentException("Unsupported MP3 bitrate");
    }

    public static String cacheKey(String account, String songId, String profile) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (String part : new String[] { account, songId, profile }) {
                byte[] bytes = part.getBytes(StandardCharsets.UTF_8);
                // Length framing prevents ambiguous tuples, including user controlled song IDs.
                digest.update((byte) (bytes.length >>> 24)); digest.update((byte) (bytes.length >>> 16));
                digest.update((byte) (bytes.length >>> 8)); digest.update((byte) bytes.length); digest.update(bytes);
            }
            StringBuilder key = new StringBuilder("audio-");
            for (byte value : digest.digest()) key.append(String.format(Locale.ROOT, "%02x", value & 255));
            return key.toString();
        } catch (NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }

    public static boolean mayUseOriginal(boolean alreadyOriginal, Failure failure) {
        // Unknown containers can be untyped API/HTML errors, not proof of playable audio.
        return !alreadyOriginal && failure == Failure.UNSUPPORTED_CODEC;
    }

    public static boolean acceptsContentType(String value) {
        if (value == null || value.trim().isEmpty()) return true; // Extractor validates untyped responses.
        String mime = value.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
        return mime.startsWith("audio/") || mime.equals("application/octet-stream")
                || mime.equals("binary/octet-stream") || mime.equals("application/ogg");
    }
}
