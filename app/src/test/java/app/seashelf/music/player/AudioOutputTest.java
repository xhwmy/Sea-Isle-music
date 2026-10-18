package app.seashelf.music.player;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public final class AudioOutputTest {
    @Test public void noGlobalEffectAndZeroBoostAllocatesNothing() {
        Backend backend = new Backend(); AudioOutput output = new AudioOutput(backend);
        output.setBoostDb(4); output.sessionChanged(0);
        assertEquals(AudioOutput.Status.WAITING, output.state().status);
        assertTrue(backend.created.isEmpty());
        output.setBoostDb(0); output.sessionChanged(91);
        assertEquals(AudioOutput.Status.OFF, output.state().status);
        assertTrue(backend.created.isEmpty());
    }
    @Test public void replacementReleasesOldEffectAndReappliesSelectedGain() {
        Backend backend = new Backend(); AudioOutput output = new AudioOutput(backend);
        output.setBoostDb(6); output.sessionChanged(91);
        Effect first = backend.created.get(0);
        assertEquals(600, first.gain); assertTrue(first.enabled);
        assertEquals(AudioOutput.Status.ACTIVE, output.state().status);
        output.sessionChanged(92);
        assertTrue(first.released); assertEquals(2, backend.created.size());
        assertEquals(600, backend.created.get(1).gain);
        output.release();
        assertTrue(backend.created.get(1).released);
        assertEquals(AudioOutput.Status.WAITING, output.state().status);
        assertEquals(6, output.state().boostDb);
    }
    @Test public void duckingSurvivesReplacementWithoutChangingEnhancement() {
        Backend backend = new Backend(); AudioOutput output = new AudioOutput(backend);
        output.setBoostDb(3); output.sessionChanged(91); output.duck();
        assertEquals(0.2f, output.playerVolume(), 0.001f);
        output.release(); output.sessionChanged(92);
        assertEquals(0.2f, output.playerVolume(), 0.001f);
        assertEquals(300, backend.created.get(1).gain);
        output.focusGranted();
        assertEquals(1f, output.playerVolume(), 0.001f);
        assertEquals(3, output.state().boostDb);
    }
    @Test public void disablingEnhancementReleasesOnlyEffectAndKeepsDucking() {
        Backend backend = new Backend(); AudioOutput output = new AudioOutput(backend);
        output.sessionChanged(91); output.setBoostDb(4); output.duck();
        output.setBoostDb(0);
        assertTrue(backend.created.get(0).released);
        assertEquals(AudioOutput.Status.OFF, output.state().status);
        assertEquals(0.2f, output.playerVolume(), 0.001f);
    }
    @Test public void unsupportedCreationOrRejectedEnableNeverClaimsApplied() {
        Backend backend = new Backend(); backend.failCreate = true;
        AudioOutput output = new AudioOutput(backend); output.setBoostDb(5); output.sessionChanged(91);
        assertEquals(AudioOutput.Status.UNAVAILABLE, output.state().status);
        assertEquals(1f, output.playerVolume(), 0.001f);
        backend.failCreate = false; backend.rejectEnable = true; output.sessionChanged(92);
        assertEquals(AudioOutput.Status.UNAVAILABLE, output.state().status);
        assertTrue(backend.created.get(0).released);
    }
    @Test public void effectFailureReleasesResourcesAndNextSessionCanRecover() {
        Backend backend = new Backend(); AudioOutput output = new AudioOutput(backend);
        output.sessionChanged(91); output.setBoostDb(2);
        Effect first = backend.created.get(0); first.failGain = true;
        output.setBoostDb(4);
        assertTrue(first.released); assertEquals(AudioOutput.Status.UNAVAILABLE, output.state().status);
        output.sessionChanged(92);
        assertEquals(AudioOutput.Status.ACTIVE, output.state().status);
        assertEquals(400, backend.created.get(1).gain);
    }
    @Test public void effectControlRevokedByRouteChangeStopsClaimingEnhancement() {
        Backend backend = new Backend(); AudioOutput output = new AudioOutput(backend);
        output.sessionChanged(91); output.setBoostDb(2);
        Effect effect = backend.created.get(0); effect.hasControl = false;
        output.verify();
        assertTrue(effect.released);
        assertEquals(AudioOutput.Status.UNAVAILABLE, output.state().status);
        assertEquals(1f, output.playerVolume(), 0.001f);
    }
    @Test public void zeroOrUnsetSessionReleasesThePreviousPrivateEffect() {
        Backend backend = new Backend(); AudioOutput output = new AudioOutput(backend);
        output.sessionChanged(91); output.setBoostDb(2);
        output.sessionChanged(-1); output.sessionChanged(0);
        assertTrue(backend.created.get(0).released);
        assertEquals(1, backend.created.size());
        assertEquals(AudioOutput.Status.WAITING, output.state().status);
    }
    private static final class Backend implements AudioOutput.EffectFactory {
        final List<Effect> created = new ArrayList<>(); boolean failCreate, rejectEnable;
        @Override public AudioOutput.Effect create(int sessionId) {
            assertTrue(sessionId > 0);
            if (failCreate) throw new UnsupportedOperationException();
            Effect effect = new Effect(); effect.acceptEnable = !rejectEnable; created.add(effect); return effect;
        }
    }
    private static final class Effect implements AudioOutput.Effect {
        int gain; boolean enabled, released, failGain, acceptEnable = true, hasControl = true;
        @Override public void gain(int millibels) { if (failGain) throw new IllegalStateException(); gain = millibels; }
        @Override public boolean enable() { enabled = acceptEnable; return acceptEnable; }
        @Override public boolean active() { return enabled && hasControl; }
        @Override public void release() { released = true; }
    }
}
