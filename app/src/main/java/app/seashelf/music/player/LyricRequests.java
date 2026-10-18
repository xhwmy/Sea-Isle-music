package app.seashelf.music.player;

import app.seashelf.music.api.Lyrics;

/** Playback-thread owned request identity. A result cannot cross song, account or reload boundaries. */
final class LyricRequests {
    private Object owner;
    private long generation;
    private LyricState state = LyricState.empty();
    LyricState state() { return state; }
    Ticket select(Object nextOwner, String songId, boolean reload) {
        if (nextOwner == null || songId == null || songId.isEmpty()) { clear(); return null; }
        if (!reload && owner == nextOwner && songId.equals(state.songId)) return null;
        owner = nextOwner;
        state = new LyricState(songId, LyricState.Status.LOADING, null);
        return new Ticket(owner, songId, ++generation);
    }
    boolean complete(Ticket ticket, Lyrics value, boolean failed) {
        if (ticket == null || ticket.owner != owner || ticket.generation != generation || !ticket.songId.equals(state.songId)) return false;
        state = new LyricState(ticket.songId, failed ? LyricState.Status.ERROR
                : value == null || value.lines().isEmpty() ? LyricState.Status.MISSING : LyricState.Status.READY, value);
        return true;
    }
    void clear() { ++generation; owner = null; state = LyricState.empty(); }
    static final class Ticket {
        final Object owner;
        final String songId;
        final long generation;
        Ticket(Object owner, String songId, long generation) { this.owner = owner; this.songId = songId; this.generation = generation; }
    }
}
