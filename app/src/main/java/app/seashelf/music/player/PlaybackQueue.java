package app.seashelf.music.player;

import app.seashelf.music.api.Song;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Random;

/** Playback-thread-owned queue. Published lists remain immutable after later edits. */
public final class PlaybackQueue {
    private final List<Song> items = new ArrayList<>();
    private final Random random;
    private List<Song> snapshot;
    private int index;
    private PlaybackMode mode = PlaybackMode.LIST_LOOP;
    private final List<String> unvisited = new ArrayList<>();
    private final List<String> history = new ArrayList<>();
    private int historyIndex;
    private String plannedNext;

    public PlaybackQueue(List<Song> songs, int startIndex) { this(songs, startIndex, new Random()); }

    public PlaybackQueue(List<Song> songs, int startIndex, Random random) {
        if (songs == null || startIndex < 0 || (songs.isEmpty() ? startIndex != 0 : startIndex >= songs.size()))
            throw new IllegalArgumentException("无效的播放列表");
        this.random = random;
        String selected = songs.isEmpty() ? null : songs.get(startIndex).id();
        LinkedHashMap<String, Song> unique = new LinkedHashMap<>();
        for (Song song : songs) {
            if (song == null || song.id() == null || song.id().isEmpty()) throw new IllegalArgumentException("无效的歌曲");
            unique.putIfAbsent(song.id(), song);
        }
        items.addAll(unique.values());
        index = selected == null ? -1 : find(selected);
        refreshSnapshot();
        resetShuffle();
    }

    public Song current() { return index < 0 ? null : items.get(index); }
    /** Reserve shuffle's next random choice so disk prefetch and playback agree. */
    public Song peekNext() {
        if (current() == null || mode == PlaybackMode.SINGLE_PLAY || mode == PlaybackMode.REPEAT_ONE) return null;
        if (mode == PlaybackMode.SEQUENTIAL && index == items.size() - 1) return null;
        if (items.size() == 1) return null;
        if (mode != PlaybackMode.SHUFFLE) return items.get((index + 1) % items.size());
        if (plannedNext != null && find(plannedNext) >= 0 && !plannedNext.equals(current().id()))
            return items.get(find(plannedNext));
        if (historyIndex + 1 < history.size()) return items.get(find(history.get(historyIndex + 1)));
        if (unvisited.isEmpty()) for (Song song : items) unvisited.add(song.id());
        List<String> candidates = new ArrayList<>(unvisited);
        if (candidates.size() > 1) candidates.remove(current().id());
        plannedNext = candidates.get(random.nextInt(candidates.size()));
        return items.get(find(plannedNext));
    }
    public int index() { return index; }
    public List<Song> songs() { return snapshot; }
    public PlaybackMode mode() { return mode; }
    public void setMode(PlaybackMode value) {
        if (value == null || mode == value) return;
        mode = value;
        resetShuffle();
    }

    /** Automatic completion can stop; it does not clear the selected song. */
    public Song next() {
        if (current() == null) return null;
        if (mode == PlaybackMode.SINGLE_PLAY) return null;
        if (mode == PlaybackMode.REPEAT_ONE) return current();
        if (mode == PlaybackMode.SEQUENTIAL && index == items.size() - 1) return null;
        return skipNext();
    }

    public Song skipNext() {
        if (current() == null) return null;
        if (mode == PlaybackMode.SHUFFLE) return shuffleNext();
        index = (index + 1) % items.size();
        return current();
    }

    public Song previous() {
        if (current() == null) return null;
        plannedNext = null;
        if (mode == PlaybackMode.SHUFFLE) {
            if (historyIndex > 0) index = find(history.get(--historyIndex));
            else if (items.size() > 1) {
                List<String> candidates = new ArrayList<>();
                for (Song song : items) if (!song.id().equals(current().id())) candidates.add(song.id());
                String id = candidates.get(random.nextInt(candidates.size()));
                history.add(0, id);
                unvisited.remove(id);
                index = find(id);
            }
        } else index = (index + items.size() - 1) % items.size();
        return current();
    }

    /** False for current/missing IDs: callers must not restart the current player. */
    public boolean select(String id) {
        int selected = find(id);
        if (selected < 0 || selected == index) return false;
        plannedNext = null;
        index = selected;
        if (mode == PlaybackMode.SHUFFLE) remember(id);
        return true;
    }

    public boolean remove(String id) {
        int removed = find(id);
        if (removed < 0) return false;
        String selected = current().id();
        items.remove(removed);
        if (items.isEmpty()) index = -1;
        else if (selected.equals(id)) index = Math.min(removed, items.size() - 1);
        else index = find(selected);
        refreshSnapshot();
        for (int i = history.size() - 1; i >= 0; i--) if (history.get(i).equals(id)) {
            history.remove(i);
            if (i <= historyIndex) historyIndex--;
        }
        unvisited.remove(id);
        if (id.equals(plannedNext)) plannedNext = null;
        if (current() != null && (historyIndex < 0 || historyIndex >= history.size()
                || !history.get(historyIndex).equals(current().id()))) remember(current().id());
        return true;
    }

    public boolean playNext(String id) {
        int from = find(id);
        if (from < 0 || from == index) return false;
        String selected = current().id();
        Song moved = items.remove(from);
        index = find(selected);
        items.add(index + 1, moved);
        plannedNext = id;
        refreshSnapshot();
        return true;
    }

    /** Append missing songs without reordering the album, selection, or shuffle history. */
    public PlaybackQueue expanded(List<Song> library) {
        for (Song song : library) if (find(song.id()) < 0) {
            items.add(song);
            unvisited.add(song.id());
        }
        if (index < 0 && !items.isEmpty()) { index = 0; resetShuffle(); }
        refreshSnapshot();
        return this;
    }

    private Song shuffleNext() {
        if (items.size() == 1) return current();
        if (plannedNext != null && find(plannedNext) >= 0 && !plannedNext.equals(current().id())) {
            String id = plannedNext; plannedNext = null; index = find(id); remember(id); return current();
        }
        if (historyIndex + 1 < history.size()) { index = find(history.get(++historyIndex)); return current(); }
        if (unvisited.isEmpty()) for (Song song : items) unvisited.add(song.id());
        List<String> candidates = new ArrayList<>(unvisited);
        if (candidates.size() > 1) candidates.remove(current().id());
        String id = candidates.get(random.nextInt(candidates.size()));
        index = find(id);
        remember(id);
        return current();
    }

    private void remember(String id) {
        while (history.size() > historyIndex + 1) history.remove(history.size() - 1);
        history.add(id);
        historyIndex = history.size() - 1;
        if (history.size() > 2000) { history.remove(0); historyIndex--; }
        unvisited.remove(id);
    }
    private void resetShuffle() {
        plannedNext = null;
        history.clear(); unvisited.clear(); historyIndex = -1;
        if (current() == null) return;
        for (Song song : items) if (!song.id().equals(current().id())) unvisited.add(song.id());
        remember(current().id());
    }
    private int find(String id) {
        for (int i = 0; i < items.size(); i++) if (items.get(i).id().equals(id)) return i;
        return -1;
    }
    private void refreshSnapshot() { snapshot = Collections.unmodifiableList(new ArrayList<>(items)); }
}
