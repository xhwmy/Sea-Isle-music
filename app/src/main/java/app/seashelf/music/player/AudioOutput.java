package app.seashelf.music.player;

import android.media.audiofx.AudioEffect;
import android.media.audiofx.LoudnessEnhancer;

/** Playback-thread-owned output state. The optional effect never targets another app or session 0. */
public final class AudioOutput {
    public enum Status { OFF, WAITING, ACTIVE, UNAVAILABLE }
    public static final class State {
        public final int boostDb;
        public final Status status;
        private State(int boostDb, Status status) { this.boostDb = boostDb; this.status = status; }
        public static State pending(int boostDb) {
            int bounded = VolumePolicy.boostDb(boostDb);
            return new State(bounded, bounded == 0 ? Status.OFF : Status.WAITING);
        }
    }
    interface EffectFactory { Effect create(int sessionId); }
    interface Effect {
        void gain(int millibels);
        boolean enable();
        default boolean active() { return true; }
        void release();
    }
    private final EffectFactory factory;
    private Effect effect;
    private int sessionId, boostDb;
    private boolean unavailable, ducked;

    AudioOutput(EffectFactory factory) { this.factory = factory; }
    static AudioOutput androidOutput() {
        return new AudioOutput(session -> {
            LoudnessEnhancer enhancer = new LoudnessEnhancer(session);
            return new Effect() {
                @Override public void gain(int millibels) {
                    if (!enhancer.hasControl()) throw new IllegalStateException("Audio effect control unavailable");
                    enhancer.setTargetGain(millibels);
                }
                @Override public boolean enable() {
                    return enhancer.setEnabled(true) == AudioEffect.SUCCESS && active();
                }
                @Override public boolean active() { return enhancer.hasControl() && enhancer.getEnabled(); }
                @Override public void release() { enhancer.release(); }
            };
        });
    }
    public void setBoostDb(int value) {
        int selected = VolumePolicy.boostDb(value);
        if (selected == boostDb) return;
        boostDb = selected; unavailable = false; apply();
    }
    public void sessionChanged(int value) {
        int next = Math.max(0, value);
        if (next == sessionId) return;
        releaseEffect(); sessionId = next; unavailable = false; apply();
    }
    private void apply() {
        if (boostDb == 0 || sessionId == 0) { releaseEffect(); return; }
        try {
            if (effect == null) effect = factory.create(sessionId);
            effect.gain(VolumePolicy.boostMillibels(boostDb));
            if (!effect.enable()) throw new IllegalStateException("Audio effect rejected");
        } catch (RuntimeException failure) { releaseEffect(); unavailable = true; }
    }
    /** Device routes can revoke effect control without changing the player's session ID. */
    public void verify() {
        if (effect == null) return;
        try { if (effect.active()) return; } catch (RuntimeException ignored) { }
        releaseEffect(); unavailable = true;
    }
    public void duck() { ducked = true; }
    public void focusGranted() { ducked = false; }
    public float playerVolume() { return ducked ? 0.2f : 1f; }
    public State state() {
        return new State(boostDb, boostDb == 0 ? Status.OFF : unavailable ? Status.UNAVAILABLE
                : effect != null ? Status.ACTIVE : Status.WAITING);
    }
    /** Source replacement retains chosen gain and focus ducking, but releases every old native effect. */
    public void release() { releaseEffect(); sessionId = 0; unavailable = false; }
    private void releaseEffect() {
        Effect previous = effect; effect = null;
        if (previous != null) try { previous.release(); } catch (RuntimeException ignored) { }
    }
}
