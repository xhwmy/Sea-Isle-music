package app.seashelf.music.player;

/** Bounded reconnect budget for temporary stream failures. Owned by the playback thread. */
public final class PlaybackRecovery {
    private static final long[] DELAYS_MS = {1000, 2000, 5000, 10000};
    private int attempts;
    private long playingSinceMs = -1;

    public void reset() { attempts = 0; playingSinceMs = -1; }

    public void playingStarted(long nowMs) {
        if (playingSinceMs < 0) playingSinceMs = nowMs;
    }

    public void playingStopped(long nowMs) {
        if (playingSinceMs >= 0 && nowMs - playingSinceMs >= 30000) attempts = 0;
        playingSinceMs = -1;
    }

    public long nextDelayMs() {
        return attempts < DELAYS_MS.length ? DELAYS_MS[attempts++] : -1;
    }
}
