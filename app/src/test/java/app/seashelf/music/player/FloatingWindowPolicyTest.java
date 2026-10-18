package app.seashelf.music.player;

import org.junit.Test;
import static org.junit.Assert.*;

public class FloatingWindowPolicyTest {
    @Test public void permissionReturnOnlyOpensAfterAnExplicitStillPendingRequest() {
        FloatingWindowPolicy state = new FloatingWindowPolicy();
        assertFalse(state.permissionReturned(true, true));
        assertEquals(FloatingWindowPolicy.Request.PERMISSION, state.request(false, true));
        assertFalse(state.permissionReturned(false, true));
        assertFalse(state.enabled());
        assertFalse(state.permissionReturned(true, true));
        state.request(false, true);
        assertTrue(state.permissionReturned(true, true));
        assertTrue(state.enabled());
    }

    @Test public void closingAndRevocationCannotReopenOnUnlockAndEmptyQueueCannotEnable() {
        FloatingWindowPolicy state = new FloatingWindowPolicy();
        assertEquals(FloatingWindowPolicy.Request.NO_SONG, state.request(true, false));
        assertFalse(state.visible(true, true, true));
        state.request(true, true);
        assertFalse(state.visible(true, false, true));
        assertTrue(state.visible(true, true, true));
        state.close();
        assertFalse(state.visible(true, true, true));
        state.request(true, true);
        assertFalse(state.visible(false, true, true));
        assertFalse(state.visible(true, true, true));
    }

    @Test public void dragBoundsRemainReachableAfterRotationAndOversizedWindows() {
        FloatingBounds.Position position = FloatingBounds.clamp(900, -99, 320, 240, 1080, 1920, 24);
        assertEquals(760, position.x); assertEquals(24, position.y);
        position = FloatingBounds.clamp(position.x, 1800, 320, 240, 1920, 1080, 24);
        assertEquals(760, position.x); assertEquals(816, position.y);
        position = FloatingBounds.clamp(-50, 999, 300, 250, 100, 100, 24);
        assertEquals(0, position.x); assertEquals(0, position.y);
    }
}
