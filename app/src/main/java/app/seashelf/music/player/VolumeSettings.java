package app.seashelf.music.player;

import android.content.Context;
import android.content.SharedPreferences;

/** App-owned preferences and an immutable status; changing a setting never starts playback or a service. */
public final class VolumeSettings {
    private final SharedPreferences preferences;
    private volatile int boostDb;
    private volatile AudioOutput.State state;
    private Runnable listener;

    public VolumeSettings(Context context) {
        preferences = context.getSharedPreferences("audio_output", Context.MODE_PRIVATE);
        boostDb = VolumePolicy.boostDb(preferences.getInt("boost_db", 0));
        state = AudioOutput.State.pending(boostDb);
    }
    public int boostDb() { return boostDb; }
    public AudioOutput.State state() { return state; }
    public void setBoostDb(int value) {
        Runnable notify;
        synchronized (this) {
            int selected = VolumePolicy.boostDb(value);
            if (selected == boostDb) return;
            boostDb = selected; state = AudioOutput.State.pending(selected);
            preferences.edit().putInt("boost_db", selected).apply();
            notify = listener;
        }
        if (notify != null) notify.run();
    }
    synchronized void attach(Runnable owner) { listener = owner; state = AudioOutput.State.pending(boostDb); }
    synchronized void publish(Runnable owner, AudioOutput.State value) {
        if (listener == owner && value.boostDb == boostDb) state = value;
    }
    synchronized void detach(Runnable owner) {
        if (listener == owner) { listener = null; state = AudioOutput.State.pending(boostDb); }
    }
}
