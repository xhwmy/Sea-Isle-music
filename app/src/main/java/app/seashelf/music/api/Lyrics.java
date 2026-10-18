package app.seashelf.music.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Comparator;

/** Lyric lines supplied by the user's Navidrome server. */
public final class Lyrics {
    private final boolean synced;
    private final List<Line> lines;

    public Lyrics(boolean synced, List<Line> lines) {
        List<Line> clean = new ArrayList<>();
        if (lines != null) for (Line line : lines) {
            if (line == null || line.text == null || line.text.trim().isEmpty() || (synced && line.startMs < 0)) continue;
            clean.add(new Line(synced ? line.startMs : -1, line.text.trim()));
        }
        if (synced) {
            clean.sort(Comparator.comparingInt(Line::startMs));
            List<Line> grouped = new ArrayList<>();
            for (Line line : clean) {
                if (!grouped.isEmpty() && grouped.get(grouped.size() - 1).startMs == line.startMs) {
                    Line previous = grouped.remove(grouped.size() - 1);
                    boolean duplicate = false;
                    for (String text : previous.text.split("\n")) if (text.equals(line.text)) duplicate = true;
                    grouped.add(new Line(line.startMs, previous.text + (duplicate ? "" : "\n" + line.text)));
                } else grouped.add(line);
            }
            clean = grouped;
        }
        this.synced = synced && !clean.isEmpty();
        this.lines = Collections.unmodifiableList(clean);
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
