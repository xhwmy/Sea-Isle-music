package app.seashelf.music.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Lyric lines supplied by the user's Navidrome server. */
public final class Lyrics {
    private final boolean synced;
    private final List<Line> lines;

    public Lyrics(boolean synced, List<Line> lines) {
        this.synced = synced;
        this.lines = Collections.unmodifiableList(new ArrayList<>(lines));
    }

    public boolean synced() { return synced; }
    public List<Line> lines() { return lines; }

    /** Returns -1 before the first timestamp or for lyrics without timing. */
    public int activeLine(int positionMs) {
        if (!synced || lines.isEmpty()) return -1;
        int low = 0, high = lines.size() - 1, answer = -1;
        while (low <= high) {
            int mid = (low + high) >>> 1;
            if (lines.get(mid).startMs <= positionMs) {
                answer = mid;
                low = mid + 1;
            } else high = mid - 1;
        }
        return answer;
    }

    public static final class Line {
        private final int startMs;
        private final String text;

        public Line(int startMs, String text) {
            this.startMs = startMs;
            this.text = text;
        }

        public int startMs() { return startMs; }
        public String text() { return text; }
    }
}
