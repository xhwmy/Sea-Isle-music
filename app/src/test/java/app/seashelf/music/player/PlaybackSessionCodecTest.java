package app.seashelf.music.player;

import app.seashelf.music.api.Song;
import org.junit.Test;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import static org.junit.Assert.*;

public class PlaybackSessionCodecTest {
    private final String account = PlaybackSessionCodec.accountKey("https://music.example", "alice");
    private PlaybackSession session() {
        return new PlaybackSession(account, List.of(
                new Song("1", "潮汐", "歌手", "专辑", "cover-1", 180),
                new Song("2", "远海", "歌手", "专辑", "cover-2", 240)),
                "2", 54321, PlaybackMode.SHUFFLE, "搜索结果");
    }
    @Test public void roundTripKeepsQueueModePositionAndUnicode() throws Exception {
        PlaybackSession restored = PlaybackSessionCodec.decode(PlaybackSessionCodec.encode(session()), account);
        assertEquals("2", restored.currentId()); assertEquals(54321, restored.positionMs());
        assertEquals(PlaybackMode.SHUFFLE, restored.mode()); assertEquals("搜索结果", restored.source());
        assertEquals("潮汐", restored.songs().get(0).title()); assertEquals(2, restored.songs().size());
        try { restored.songs().clear(); fail("mutable session"); } catch (UnsupportedOperationException expected) { }
    }
    @Test public void accountKeySeparatesServersAndUsersWithoutStoringCredentials() throws Exception {
        assertNotEquals(account, PlaybackSessionCodec.accountKey("https://other.example", "alice"));
        assertNotEquals(account, PlaybackSessionCodec.accountKey("https://music.example", "bob"));
        assertFalse(account.contains("alice")); assertFalse(account.contains("example"));
        rejects(PlaybackSessionCodec.encode(session()), PlaybackSessionCodec.accountKey("https://music.example", "bob"));
    }
    @Test public void truncatedCorruptAndTrailingDataAreRejected() throws Exception {
        byte[] valid = PlaybackSessionCodec.encode(session());
        rejects(Arrays.copyOf(valid, valid.length - 1), account);
        byte[] corrupt = valid.clone(); corrupt[corrupt.length / 2] ^= 32; rejects(corrupt, account);
        rejects(Arrays.copyOf(valid, valid.length + 5), account);
        rejects(new byte[0], account);
    }
    @Test public void invalidSelectionNegativeProgressAndUnboundedMetadataAreRejected() throws Exception {
        invalid(new PlaybackSession(account, session().songs(), "missing", 0, PlaybackMode.LIST_LOOP, "音乐库"));
        invalid(new PlaybackSession(account, session().songs(), "1", -1, PlaybackMode.LIST_LOOP, "音乐库"));
        invalid(new PlaybackSession(account, List.of(new Song("1", "x".repeat(17000), "", "", "", 100)),
                "1", 0, PlaybackMode.LIST_LOOP, "音乐库"));
    }
    private static void invalid(PlaybackSession value) throws Exception {
        try { PlaybackSessionCodec.encode(value); fail("invalid session accepted"); } catch (IOException expected) { }
    }
    private static void rejects(byte[] data, String identity) throws Exception {
        try { PlaybackSessionCodec.decode(data, identity); fail("corrupt or wrong-account session accepted"); }
        catch (IOException expected) { }
    }
}
