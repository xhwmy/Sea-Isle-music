package app.seashelf.music.player;

import app.seashelf.music.api.Song;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class PlaybackQueueTest {
    private static Song song(String id) { return new Song(id, "歌" + id, "歌手", "专辑", "", 120); }
    private PlaybackQueue queue() { return new PlaybackQueue(List.of(song("1"), song("2"), song("3")), 0, new Random(7)); }

    @Test public void sequentialCompletionStopsButManualSkipsWrap() {
        PlaybackQueue q = queue(); q.setMode(PlaybackMode.SEQUENTIAL);
        assertEquals("2", q.next().id()); assertEquals("3", q.next().id());
        assertNull(q.next()); assertEquals("3", q.current().id());
        assertEquals("1", q.skipNext().id()); assertEquals("3", q.previous().id());
    }
    @Test public void listLoopIsDefaultAndWrapsAtCompletion() {
        PlaybackQueue q = queue(); q.select("3");
        assertEquals("1", q.peekNext().id());
        assertEquals("1", q.next().id());
    }
    @Test public void lookaheadFollowsAutomaticCompletionRulesAndQueueEdits() {
        PlaybackQueue q = queue();
        assertEquals("2", q.peekNext().id());
        q.playNext("3"); assertEquals("3", q.peekNext().id());
        q.remove("3"); assertEquals("2", q.peekNext().id());
        q.setMode(PlaybackMode.SEQUENTIAL); q.select("2"); assertNull(q.peekNext());
        q.setMode(PlaybackMode.REPEAT_ONE); assertNull(q.peekNext());
        q.setMode(PlaybackMode.SINGLE_PLAY); assertNull(q.peekNext());
    }
    @Test public void shuffleLookaheadReservesTheSongActuallyPlayed() {
        PlaybackQueue q = queue(); q.setMode(PlaybackMode.SHUFFLE);
        for (int i = 0; i < 9; i++) {
            String planned = q.peekNext().id();
            assertEquals(planned, q.peekNext().id());
            assertEquals(planned, q.next().id());
        }
        PlaybackQueue edited = queue(); edited.setMode(PlaybackMode.SHUFFLE);
        edited.playNext("3"); assertEquals("3", edited.peekNext().id());
        assertEquals("3", edited.next().id());
    }
    @Test public void repeatOneAndSinglePlayOnlyAffectAutomaticCompletion() {
        PlaybackQueue q = queue(); q.setMode(PlaybackMode.REPEAT_ONE);
        assertEquals("1", q.next().id()); assertEquals("2", q.skipNext().id());
        q.setMode(PlaybackMode.SINGLE_PLAY); assertNull(q.next());
        assertEquals("1", q.previous().id());
    }
    @Test public void emptyAndSingleSongQueuesAreSafeInEveryMode() {
        PlaybackQueue empty = new PlaybackQueue(List.of(), 0);
        for (PlaybackMode mode : PlaybackMode.values()) {
            empty.setMode(mode); assertNull(empty.current()); assertNull(empty.next());
            assertNull(empty.skipNext()); assertNull(empty.previous());
        }
        PlaybackQueue one = new PlaybackQueue(List.of(song("1")), 0);
        for (PlaybackMode mode : PlaybackMode.values()) {
            one.setMode(mode); assertEquals("1", one.skipNext().id()); assertEquals("1", one.previous().id());
        }
    }
    @Test public void shuffleVisitsCycleWithoutRepeatAndPreviousRetracesActualHistory() {
        PlaybackQueue q = queue(); q.setMode(PlaybackMode.SHUFFLE);
        String a = q.current().id(), b = q.next().id(), c = q.next().id();
        assertEquals(3, new HashSet<>(List.of(a, b, c)).size());
        assertEquals(b, q.previous().id()); assertEquals(a, q.previous().id());
        assertEquals(b, q.skipNext().id()); assertEquals(c, q.skipNext().id());
        assertNotEquals(c, q.next().id());
    }
    @Test public void modeAndExpansionPreserveSelectionAndDeduplicate() {
        PlaybackQueue q = queue(); q.select("2"); q.setMode(PlaybackMode.SHUFFLE);
        q = q.expanded(List.of(song("3"), song("4"), song("4")));
        assertEquals("2", q.current().id()); assertEquals(PlaybackMode.SHUFFLE, q.mode());
        assertEquals(4, q.songs().size()); assertFalse(q.select("2")); assertFalse(q.select("missing"));
    }
    @Test public void shuffleEveryLaterCycleIncludesEverySongOnceWithoutBoundaryRepeat() {
        PlaybackQueue q = queue(); q.setMode(PlaybackMode.SHUFFLE);
        q.next(); q.next();
        for (int cycle = 0; cycle < 20; cycle++) {
            String previous = q.current().id();
            Set<String> visited = new HashSet<>();
            for (int i = 0; i < 3; i++) {
                String id = q.next().id();
                assertNotEquals(previous, id); assertTrue("Repeated within cycle", visited.add(id));
                previous = id;
            }
        }
    }
    @Test public void playNextMovesExistingItemAndSelectionDoesNotRestartCurrent() {
        PlaybackQueue q = queue(); assertTrue(q.playNext("3"));
        assertEquals(List.of("1", "3", "2"), ids(q)); assertEquals("1", q.current().id());
        assertFalse(q.playNext("1")); assertEquals("3", q.skipNext().id());
    }
    @Test public void playNextOverridesNextShuffleChoiceWithoutDuplicatingTrack() {
        PlaybackQueue q = queue(); q.setMode(PlaybackMode.SHUFFLE); q.playNext("3");
        assertEquals("3", q.next().id()); assertEquals(3, q.songs().size());
    }
    @Test public void removingOtherTrackPreservesCurrentAndRemovingCurrentUsesSuccessor() {
        PlaybackQueue q = queue(); q.select("2"); q.remove("1");
        assertEquals("2", q.current().id()); q.remove("2"); assertEquals("3", q.current().id());
        q.remove("3"); assertNull(q.current()); assertEquals(0, q.songs().size()); assertNull(q.next());
    }
    @Test public void snapshotsCannotBeMutatedOrChangedByLaterEdits() {
        PlaybackQueue q = queue(); List<Song> snapshot = q.songs(); q.remove("3");
        assertEquals(3, snapshot.size());
        try { snapshot.clear(); fail("mutable queue snapshot"); } catch (UnsupportedOperationException expected) { }
    }
    @Test public void duplicateInputKeepsChosenIdAndInvalidIndexIsRejected() {
        PlaybackQueue q = new PlaybackQueue(List.of(song("1"), song("1"), song("2")), 2);
        assertEquals("2", q.current().id()); assertEquals(2, q.songs().size());
        try { new PlaybackQueue(List.of(song("1")), 1); fail("invalid index"); } catch (IllegalArgumentException expected) { }
    }
    private static List<String> ids(PlaybackQueue q) {
        List<String> result = new ArrayList<>(); for (Song s : q.songs()) result.add(s.id()); return result;
    }
}
