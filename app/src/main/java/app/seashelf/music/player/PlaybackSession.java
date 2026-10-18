package app.seashelf.music.player;

import app.seashelf.music.api.Song;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Metadata and progress only: credentials and stream URLs never enter this model. */
public final class PlaybackSession {
    private final String accountKey;
    private final List<Song> songs;
    private final String currentId;
    private final int positionMs;
    private final PlaybackMode mode;
    private final String source;

    public PlaybackSession(String accountKey, List<Song> songs, String currentId, int positionMs,
                           PlaybackMode mode, String source) {
        this.accountKey = accountKey;
        this.songs = Collections.unmodifiableList(new ArrayList<>(songs));
        this.currentId = currentId;
        this.positionMs = positionMs;
        this.mode = mode;
        this.source = source;
    }
    public String accountKey() { return accountKey; }
    public List<Song> songs() { return songs; }
    public String currentId() { return currentId; }
    public int positionMs() { return positionMs; }
    public PlaybackMode mode() { return mode; }
    public String source() { return source; }
}
