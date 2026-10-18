package app.seashelf.music.player;

import app.seashelf.music.api.Song;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.CRC32;

/** Bounded versioned binary format, checked before a saved queue can reach the player. */
public final class PlaybackSessionCodec {
    public static final int MAX_BYTES = 8 * 1024 * 1024;
    private static final int MAX_SONGS = 10000;
    private static final int MAX_TEXT_BYTES = 16384;
    private static final int MAX_POSITION = 7 * 24 * 60 * 60 * 1000;
    private static final int MAGIC = 0x53455131;
    private PlaybackSessionCodec() { }

    public static String accountKey(String server, String username) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] value = digest.digest((server + "\n" + username).getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : value) hex.append(String.format(java.util.Locale.ROOT, "%02x", b & 255));
            return hex.toString();
        } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    public static byte[] encode(PlaybackSession session) throws IOException {
        validate(session);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeInt(MAGIC);
        writeText(out, session.accountKey());
        writeText(out, session.currentId());
        out.writeInt(session.positionMs());
        writeText(out, session.mode().name());
        writeText(out, session.source());
        out.writeInt(session.songs().size());
        for (Song song : session.songs()) {
            writeText(out, song.id()); writeText(out, song.title()); writeText(out, song.artist());
            writeText(out, song.album()); writeText(out, song.coverArtId()); out.writeInt(song.durationSeconds());
            if (bytes.size() > MAX_BYTES - 8) throw new IOException("Session too large");
        }
        out.flush();
        CRC32 crc = new CRC32(); crc.update(bytes.toByteArray());
        out.writeLong(crc.getValue()); out.flush();
        return bytes.toByteArray();
    }

    public static PlaybackSession decode(byte[] bytes, String expectedAccount) throws IOException {
        if (bytes == null || bytes.length < 16 || bytes.length > MAX_BYTES) throw new IOException("Invalid session size");
        CRC32 crc = new CRC32(); crc.update(bytes, 0, bytes.length - 8);
        DataInputStream checksum = new DataInputStream(new ByteArrayInputStream(bytes, bytes.length - 8, 8));
        if (checksum.readLong() != crc.getValue()) throw new IOException("Session checksum mismatch");
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes, 0, bytes.length - 8));
        if (in.readInt() != MAGIC) throw new IOException("Unsupported session");
        String account = readText(in);
        if (!account.equals(expectedAccount)) throw new IOException("Different account");
        String current = readText(in);
        int position = in.readInt();
        PlaybackMode mode;
        try { mode = PlaybackMode.valueOf(readText(in)); }
        catch (IllegalArgumentException invalid) { throw new IOException("Invalid playback mode"); }
        String source = readText(in);
        int count = in.readInt();
        if (count < 1 || count > MAX_SONGS) throw new IOException("Invalid queue size");
        List<Song> songs = new ArrayList<>();
        for (int i = 0; i < count; i++) songs.add(new Song(readText(in), readText(in), readText(in),
                readText(in), readText(in), in.readInt()));
        if (in.available() != 0) throw new IOException("Trailing session data");
        PlaybackSession session = new PlaybackSession(account, songs, current, position, mode, source);
        validate(session);
        return session;
    }

    private static void validate(PlaybackSession value) throws IOException {
        if (value == null || value.accountKey() == null || !value.accountKey().matches("[0-9a-f]{64}")
                || value.songs().isEmpty() || value.songs().size() > MAX_SONGS
                || value.positionMs() < 0 || value.positionMs() > MAX_POSITION || value.mode() == null
                || !("音乐库".equals(value.source()) || "搜索结果".equals(value.source()))) throw new IOException("Invalid session");
        Set<String> ids = new HashSet<>();
        for (Song song : value.songs()) {
            if (song == null || song.id() == null || song.id().isEmpty() || !ids.add(song.id())
                    || song.durationSeconds() < 0 || song.durationSeconds() > MAX_POSITION / 1000) throw new IOException("Invalid song");
        }
        if (!ids.contains(value.currentId())) throw new IOException("Missing selected song");
    }
    private static void writeText(DataOutputStream out, String text) throws IOException {
        if (text == null) throw new IOException("Null metadata");
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_TEXT_BYTES) throw new IOException("Metadata too large");
        out.writeInt(bytes.length); out.write(bytes);
    }
    private static String readText(DataInputStream in) throws IOException {
        int size = in.readInt();
        if (size < 0 || size > MAX_TEXT_BYTES || size > in.available()) throw new IOException("Invalid metadata size");
        byte[] bytes = new byte[size]; in.readFully(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
