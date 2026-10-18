package app.seashelf.music.player;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;

/** Private atomic files. A revoked lease prevents even already queued writes after sign-out. */
public final class PlaybackSessionStore {
    private final File folder;
    private final ExecutorService writer;
    private final Map<String, Long> epochs = new HashMap<>();
    private final Set<String> clearing = new HashSet<>();

    public PlaybackSessionStore(File folder, ExecutorService writer) { this.folder = folder; this.writer = writer; }
    public synchronized Lease activate(String account) {
        if (account == null || !account.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("Invalid account key");
        return new Lease(account, epochs.getOrDefault(account, 0L));
    }
    public synchronized boolean isValid(Lease lease) {
        return lease != null && epochs.getOrDefault(lease.account, 0L) == lease.epoch;
    }
    /** Caller is the playback thread; disk work never happens in an Activity. */
    public PlaybackSession load(Lease lease) {
        if (!canLoad(lease)) return null;
        try {
            File file = file(lease.account);
            if (!file.isFile() || file.length() > PlaybackSessionCodec.MAX_BYTES) return null;
            PlaybackSession value = PlaybackSessionCodec.decode(Files.readAllBytes(file.toPath()), lease.account);
            return canLoad(lease) ? value : null;
        } catch (IOException invalid) { return null; }
    }
    public synchronized void save(Lease lease, PlaybackSession value) {
        if (!isValid(lease) || !lease.account.equals(value.accountKey())) return;
        writer.execute(() -> write(lease, value));
    }
    public synchronized void clear(String account) {
        if (account == null || !account.matches("[0-9a-f]{64}")) return;
        long clearedEpoch = epochs.getOrDefault(account, 0L) + 1;
        epochs.put(account, clearedEpoch);
        clearing.add(account);
        writer.execute(() -> {
            try {
                Files.deleteIfExists(file(account).toPath());
                synchronized (this) {
                    if (epochs.getOrDefault(account, 0L) == clearedEpoch) clearing.remove(account);
                }
            } catch (IOException ignored) { /* Keep loads blocked if deletion did not succeed. */ }
        });
    }
    private synchronized boolean canLoad(Lease lease) { return isValid(lease) && !clearing.contains(lease.account); }
    private void write(Lease lease, PlaybackSession value) {
        if (!isValid(lease)) return;
        File temporary = new File(folder, lease.account + ".pending");
        try {
            byte[] bytes = PlaybackSessionCodec.encode(value);
            if (!folder.isDirectory() && !folder.mkdirs()) return;
            try (FileOutputStream out = new FileOutputStream(temporary)) { out.write(bytes); out.getFD().sync(); }
            synchronized (this) {
                if (!isValid(lease)) return;
                try { Files.move(temporary.toPath(), file(lease.account).toPath(),
                        StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
                catch (AtomicMoveNotSupportedException unsupported) {
                    Files.move(temporary.toPath(), file(lease.account).toPath(), StandardCopyOption.REPLACE_EXISTING);
                }
            }
        } catch (IOException ignored) { /* Playback continues if private storage is full or unavailable. */ }
        finally { try { Files.deleteIfExists(temporary.toPath()); } catch (IOException ignored) { } }
    }
    private File file(String account) { return new File(folder, account + ".session"); }
    public static final class Lease {
        private final String account;
        private final long epoch;
        private Lease(String account, long epoch) { this.account = account; this.epoch = epoch; }
    }
}
