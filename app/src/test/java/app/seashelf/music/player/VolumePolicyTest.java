package app.seashelf.music.player;

import org.junit.Test;
import static org.junit.Assert.*;

public final class VolumePolicyTest {
    @Test public void boostIsOffByDefaultAndLimitedToSixDecibels() {
        assertEquals(0, VolumePolicy.boostDb(-10));
        assertEquals(3, VolumePolicy.boostDb(3));
        assertEquals(6, VolumePolicy.boostDb(20));
        assertEquals(600, VolumePolicy.boostMillibels(6));
    }
    @Test public void systemVolumeUsesRealStepsAndHandlesUnavailableRange() {
        assertEquals(2, VolumePolicy.streamIndex(-3, 2, 15));
        assertEquals(15, VolumePolicy.streamIndex(22, 2, 15));
        assertEquals(47, VolumePolicy.streamPercent(7, 15));
        assertEquals(0, VolumePolicy.streamPercent(7, 0));
        assertEquals(100, VolumePolicy.streamPercent(40, 15));
    }
}
