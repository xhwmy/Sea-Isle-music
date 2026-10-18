package app.seashelf.music.player;

/** User intent survives buffering; generations reject callbacks from replaced songs. */
public final class PlaybackIntent {
    private long generation;
    private boolean wantsPlay;
    private boolean resumeAfterFocus;
    private int positionMs;
    public long begin() { return begin(true, 0); }
    public long begin(boolean play, int position) {
        wantsPlay = play;
        resumeAfterFocus = false;
        positionMs = Math.max(0, position);
        return ++generation;
    }
    public boolean isCurrent(long request) { return request == generation; }
    public boolean wantsPlay() { return wantsPlay; }
    public int positionMs() { return positionMs; }
    public void seek(int value) { positionMs = Math.max(0, value); }
    public void pause() { wantsPlay = false; resumeAfterFocus = false; }
    public void resume() { wantsPlay = true; resumeAfterFocus = false; }
    public void focusLost(boolean transientLoss) {
        resumeAfterFocus = transientLoss && wantsPlay;
        wantsPlay = false;
    }
    public void focusGained() {
        if (resumeAfterFocus) wantsPlay = true;
        resumeAfterFocus = false;
    }
    public void stop() { pause(); generation++; }
}
