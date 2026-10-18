package app.seashelf.music.player;

import app.seashelf.music.api.Song;
import org.junit.Test;
import java.nio.file.Files;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.ArrayDeque;
import static org.junit.Assert.*;

public class PlaybackSessionStoreTest {
    private static PlaybackSession value(String key, int position) {
        return new PlaybackSession(key, List.of(new Song("1", "歌", "歌手", "专辑", "", 120)),
                "1", position, PlaybackMode.LIST_LOOP, "音乐库");
    }
    @Test public void queuedWriteCannotRepublishSessionAfterSignOut() throws Exception {
        DeferredExecutor executor = new DeferredExecutor();
        PlaybackSessionStore store = new PlaybackSessionStore(Files.createTempDirectory("playback-session").toFile(), executor);
        String key = PlaybackSessionCodec.accountKey("https://server", "alice");
        PlaybackSessionStore.Lease lease = store.activate(key);
        store.save(lease, value(key, 30000));
        store.clear(key);
        store.save(lease, value(key, 60000));
        executor.drain();
        assertNull(store.load(store.activate(key)));
        assertFalse(store.isValid(lease));
    }
    @Test public void separateAccountsRestoreTheirOwnPositionAndTruncationIsIgnored() throws Exception {
        DeferredExecutor executor = new DeferredExecutor();
        java.io.File folder = Files.createTempDirectory("playback-session").toFile();
        PlaybackSessionStore store = new PlaybackSessionStore(folder, executor);
        String alice = PlaybackSessionCodec.accountKey("https://server", "alice");
        String bob = PlaybackSessionCodec.accountKey("https://server", "bob");
        PlaybackSessionStore.Lease a = store.activate(alice), b = store.activate(bob);
        store.save(a, value(alice, 12000)); store.save(b, value(bob, 45000)); executor.drain();
        assertEquals(12000, store.load(a).positionMs()); assertEquals(45000, store.load(b).positionMs());
        store.clear(alice); executor.drain(); assertNull(store.load(store.activate(alice)));
        assertEquals(45000, store.load(b).positionMs());
        Files.write(new java.io.File(folder, bob + ".session").toPath(), new byte[] {1, 2, 3});
        assertNull(store.load(b));
    }
    @Test public void immediateReloginCannotReadOldFileWhileClearIsQueued() throws Exception {
        DeferredExecutor executor = new DeferredExecutor();
        PlaybackSessionStore store = new PlaybackSessionStore(Files.createTempDirectory("playback-session").toFile(), executor);
        String key = PlaybackSessionCodec.accountKey("https://server", "alice");
        store.save(store.activate(key), value(key, 20000)); executor.drain();
        store.clear(key);
        PlaybackSessionStore.Lease relogin = store.activate(key);
        assertNull(store.load(relogin));
        store.save(relogin, value(key, 5000)); executor.drain();
        assertEquals(5000, store.load(relogin).positionMs());
    }
    private static final class DeferredExecutor extends AbstractExecutorService {
        private final ArrayDeque<Runnable> pending = new ArrayDeque<>();
        public void execute(Runnable task) { pending.add(task); }
        void drain() { while (!pending.isEmpty()) pending.remove().run(); }
        public void shutdown() { }
        public List<Runnable> shutdownNow() { return List.of(); }
        public boolean isShutdown() { return false; }
        public boolean isTerminated() { return false; }
        public boolean awaitTermination(long value, TimeUnit unit) { return true; }
    }
}
