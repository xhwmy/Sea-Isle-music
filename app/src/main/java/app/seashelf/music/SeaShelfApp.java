package app.seashelf.music;

import android.app.Application;
import app.seashelf.music.api.SubsonicClient;
import app.seashelf.music.player.PlayerService;
import app.seashelf.music.player.PlaybackSessionStore;
import app.seashelf.music.storage.CredentialStore;
import java.util.concurrent.ExecutorService;

public final class SeaShelfApp extends Application {
    private final RequestExecutors requests = new RequestExecutors();
    private volatile SubsonicClient client;
    private CredentialStore credentials;
    private PlaybackSessionStore playbackSessions;

    @Override public void onCreate() {
        super.onCreate();
        playbackSessions = new PlaybackSessionStore(new java.io.File(getFilesDir(), "playback"),
                java.util.concurrent.Executors.newSingleThreadExecutor(task -> new Thread(task, "SeaShelfSessionStore")));
        credentials = new CredentialStore(this);
        try {
            CredentialStore.Connection saved = credentials.load();
            if (saved != null) client = saved.client();
        } catch (Exception ignored) {
            // The user can re-enter credentials if the device key changed.
        }
    }

    public ExecutorService network() { return requests.api(); }
    public ExecutorService images() { return requests.images(); }
    public CredentialStore credentials() { return credentials; }
    public SubsonicClient client() { return client; }
    public PlaybackSessionStore playbackSessions() { return playbackSessions; }
    public void setClient(SubsonicClient value) { client = value; }
    public void signOut() {
        SubsonicClient previous = client;
        client = null;
        if (previous != null) playbackSessions.clear(previous.playbackAccountKey());
        PlayerService.forgetSession();
        stopService(new android.content.Intent(this, PlayerService.class));
        credentials.clear();
    }
}
