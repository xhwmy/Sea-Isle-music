package app.seashelf.music.player;

/** A seek can finish at READY or at ENDED. Completion invalidates its old timeout in both cases. */
public final class PlaybackSeek {
    public enum Completion { NONE, SETTLED, SEEK_AGAIN }
    private boolean active;
    private int applied;
    private long operation;
    public boolean active() { return active; }
    public long begin(int target) { applied = target; active = true; return ++operation; }
    public boolean isCurrent(long candidate) { return active && candidate == operation; }
    public Completion complete(int latestTarget) {
        if (!active) return Completion.NONE;
        active = false; operation++;
        return latestTarget == applied ? Completion.SETTLED : Completion.SEEK_AGAIN;
    }
    public void cancel() { active = false; operation++; }
}
