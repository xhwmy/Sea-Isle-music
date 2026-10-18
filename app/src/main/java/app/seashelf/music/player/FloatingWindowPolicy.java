package app.seashelf.music.player;

/** Explicit opening only: permission grants or unlocks alone never enable a window. */
public final class FloatingWindowPolicy {
    public enum Request { NO_SONG, PERMISSION, OPEN }
    private boolean pending, enabled;
    public Request request(boolean permission, boolean hasSong) {
        pending = false;
        if (!hasSong) return Request.NO_SONG;
        if (!permission) { pending = true; return Request.PERMISSION; }
        enabled = true; return Request.OPEN;
    }
    public boolean permissionReturned(boolean permission, boolean hasSong) {
        boolean open = pending && permission && hasSong;
        pending = false;
        if (open) enabled = true;
        return open;
    }
    public boolean enabled() { return enabled; }
    public boolean pending() { return pending; }
    public void close() { pending = false; enabled = false; }
    public boolean visible(boolean permission, boolean unlocked, boolean hasSong) {
        if (!permission || !hasSong) close();
        return enabled && permission && unlocked && hasSong;
    }
}
