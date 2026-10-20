package app.seashelf.music.player;

import org.junit.Test;
import static org.junit.Assert.*;

public final class PlaybackRecoveryTest {
    @Test public void shortDisconnectsRetryWithBackoffAndEventuallyStop() {
        PlaybackRecovery recovery = new PlaybackRecovery();
        assertEquals(1000, recovery.nextDelayMs());
        assertEquals(2000, recovery.nextDelayMs());
        assertEquals(5000, recovery.nextDelayMs());
        assertEquals(10000, recovery.nextDelayMs());
        assertEquals(-1, recovery.nextDelayMs());
    }

    @Test public void sustainedPlaybackRestoresRetryBudgetButBriefPlaybackDoesNot() {
        PlaybackRecovery recovery = new PlaybackRecovery();
        assertEquals(1000, recovery.nextDelayMs());
        assertEquals(2000, recovery.nextDelayMs());
        recovery.playingStarted(1000);
        recovery.playingStopped(20000);
        assertEquals(5000, recovery.nextDelayMs());
        recovery.playingStarted(30000);
        recovery.playingStopped(65000);
        assertEquals(1000, recovery.nextDelayMs());
    }

    @Test public void aNewSongGetsItsOwnRetryBudget() {
        PlaybackRecovery recovery = new PlaybackRecovery();
        recovery.nextDelayMs();
        recovery.nextDelayMs();
        recovery.reset();
        assertEquals(1000, recovery.nextDelayMs());
    }
}
