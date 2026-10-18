package app.seashelf.music.player;

import org.junit.Test;
import static org.junit.Assert.*;

public class PlaybackIntentTest {
    @Test public void pauseWhilePreparingPreventsDelayedAutoplay() {
        PlaybackIntent intent = new PlaybackIntent();
        long request = intent.begin();
        intent.pause();
        assertTrue(intent.isCurrent(request));
        assertFalse(intent.wantsPlay());
        intent.resume();
        assertTrue(intent.wantsPlay());
    }

    @Test public void skipAndStopInvalidateCallbacksFromOldSong() {
        PlaybackIntent intent = new PlaybackIntent();
        long first = intent.begin();
        long second = intent.begin();
        assertFalse(intent.isCurrent(first));
        assertTrue(intent.isCurrent(second));
        intent.stop();
        assertFalse(intent.isCurrent(second));
        assertFalse(intent.wantsPlay());
    }

    @Test public void seekDuringPreparationAndFallbackRetainsLatestTarget() {
        PlaybackIntent intent = new PlaybackIntent();
        long generation = intent.begin(false, 23000);
        intent.seek(45000);
        assertEquals(45000, intent.positionMs());
        assertFalse(intent.wantsPlay());
        assertTrue(intent.isCurrent(generation));
        intent.resume();
        assertTrue(intent.wantsPlay());
        intent.begin(true, 0);
        assertEquals(0, intent.positionMs());
        assertFalse(intent.isCurrent(generation));
    }

    @Test public void userPauseAfterTransientLossPreventsFocusResume() {
        PlaybackIntent intent = new PlaybackIntent();
        intent.begin(); intent.focusLost(true);
        assertFalse(intent.wantsPlay());
        intent.pause(); intent.focusGained();
        assertFalse(intent.wantsPlay());
    }

    @Test public void transientLossResumesButPermanentLossAndStopDoNot() {
        PlaybackIntent intent = new PlaybackIntent();
        intent.begin(); intent.focusLost(true); intent.focusGained(); assertTrue(intent.wantsPlay());
        intent.focusLost(false); intent.focusGained(); assertFalse(intent.wantsPlay());
        intent.begin(); intent.focusLost(true); intent.stop(); intent.focusGained(); assertFalse(intent.wantsPlay());
    }

}
