package app.seashelf.music.player;

import app.seashelf.music.api.Lyrics;
import java.util.Collections;
import org.junit.Test;
import static org.junit.Assert.*;

public class LyricRequestsTest {
    private final Lyrics lyrics = new Lyrics(true, Collections.singletonList(new Lyrics.Line(200, "Fixture")));

    @Test public void oneRequestIsSharedUntilExplicitReloadAndLateReloadCannotReplaceNewerResult() {
        LyricRequests requests = new LyricRequests(); Object owner = new Object();
        LyricRequests.Ticket first = requests.select(owner, "a", false);
        assertNull(requests.select(owner, "a", false));
        LyricRequests.Ticket reload = requests.select(owner, "a", true);
        assertFalse(requests.complete(first, lyrics, false));
        assertTrue(requests.complete(reload, lyrics, false));
        assertEquals(LyricState.Status.READY, requests.state().status);
    }

    @Test public void songOwnerAndLogoutChangesRejectStaleLyricResultsImmediately() {
        LyricRequests requests = new LyricRequests(); Object owner = new Object();
        LyricRequests.Ticket previous = requests.select(owner, "a", false);
        LyricRequests.Ticket nextSong = requests.select(owner, "b", false);
        assertEquals("b", requests.state().songId);
        assertFalse(requests.complete(previous, lyrics, false));
        LyricRequests.Ticket otherOwner = requests.select(new Object(), "b", false);
        assertFalse(requests.complete(nextSong, lyrics, false));
        requests.clear();
        assertFalse(requests.complete(otherOwner, lyrics, false));
        assertEquals(LyricState.Status.EMPTY, requests.state().status);
    }

    @Test public void missingAndFailedLyricsAreDifferentStatesThatRemainReloadable() {
        LyricRequests requests = new LyricRequests(); Object owner = new Object();
        assertTrue(requests.complete(requests.select(owner, "a", false), new Lyrics(false, Collections.emptyList()), false));
        assertEquals(LyricState.Status.MISSING, requests.state().status);
        assertTrue(requests.complete(requests.select(owner, "a", true), null, true));
        assertEquals(LyricState.Status.ERROR, requests.state().status);
    }
}
