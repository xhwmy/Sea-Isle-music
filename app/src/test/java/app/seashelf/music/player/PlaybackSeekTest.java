package app.seashelf.music.player;

import org.junit.Test;
import static org.junit.Assert.*;

public final class PlaybackSeekTest {
    @Test public void endOfMediaMustSettleSeekAndInvalidateTimeout() {
        PlaybackIntent intent = new PlaybackIntent(); intent.begin(false, 0);
        PlaybackSeek state = new PlaybackSeek();
        intent.seek(180000); long timeout = state.begin(intent.positionMs());
        assertEquals(PlaybackSeek.Completion.SETTLED, state.complete(intent.positionMs()));
        assertFalse(state.active());
        assertFalse("ENDED must invalidate the 15 second timeout", state.isCurrent(timeout));
        assertFalse("Finishing a seek must not resume a paused song", intent.wantsPlay());
        assertEquals(180000, intent.positionMs());
    }
    @Test public void newerSeekSupersedesEndOfMediaAndRetainsPause() {
        PlaybackIntent intent = new PlaybackIntent(); intent.begin(false, 180000);
        PlaybackSeek state = new PlaybackSeek(); long old = state.begin(180000);
        intent.seek(30000);
        assertEquals(PlaybackSeek.Completion.SEEK_AGAIN, state.complete(intent.positionMs()));
        long latest = state.begin(intent.positionMs());
        assertFalse(state.isCurrent(old)); assertTrue(state.isCurrent(latest));
        assertEquals(PlaybackSeek.Completion.SETTLED, state.complete(30000));
        assertFalse(intent.wantsPlay()); assertEquals(30000, intent.positionMs());
    }
    @Test public void replacedSongCancelsSeekTimeoutAndRepeatedCompletion() {
        PlaybackSeek state = new PlaybackSeek(); long old = state.begin(15000);
        state.cancel(); assertFalse(state.isCurrent(old));
        assertEquals(PlaybackSeek.Completion.NONE, state.complete(15000));
    }
}
