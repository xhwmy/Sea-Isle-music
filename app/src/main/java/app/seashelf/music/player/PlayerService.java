package app.seashelf.music.player;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaMetadata;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.SystemClock;
import android.util.Log;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.datasource.HttpDataSource;
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.analytics.AnalyticsListener;
import androidx.media3.exoplayer.source.ProgressiveMediaSource;
import app.seashelf.music.SeaShelfApp;
import app.seashelf.music.api.Song;
import app.seashelf.music.api.Lyrics;
import app.seashelf.music.api.SubsonicClient;
import app.seashelf.music.ui.PlayerActivity;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** The playback HandlerThread owns every ExoPlayer operation and every queue transition. */
@SuppressLint("UnsafeOptInUsageError")
public final class PlayerService extends Service {
    public static final String ACTION_QUEUE = "app.seashelf.music.QUEUE";
    public static final String ACTION_RESTORE = "app.seashelf.music.RESTORE";
    public static final String ACTION_TOGGLE = "app.seashelf.music.TOGGLE";
    public static final String ACTION_PLAY = "app.seashelf.music.PLAY";
    public static final String ACTION_PAUSE = "app.seashelf.music.PAUSE";
    public static final String ACTION_RETRY = "app.seashelf.music.RETRY";
    public static final String ACTION_RELOAD_QUEUE = "app.seashelf.music.RELOAD_QUEUE";
    public static final String ACTION_NEXT = "app.seashelf.music.NEXT";
    public static final String ACTION_PREVIOUS = "app.seashelf.music.PREVIOUS";
    public static final String ACTION_SEEK = "app.seashelf.music.SEEK";
    public static final String ACTION_MODE = "app.seashelf.music.MODE";
    public static final String ACTION_SELECT = "app.seashelf.music.SELECT";
    public static final String ACTION_REMOVE = "app.seashelf.music.REMOVE";
    public static final String ACTION_PLAY_NEXT = "app.seashelf.music.PLAY_NEXT";
    public static final String ACTION_STOP = "app.seashelf.music.STOP";
    public static final String ACTION_SHOW_FLOATING = "app.seashelf.music.SHOW_FLOATING";
    public static final String ACTION_RELOAD_LYRICS = "app.seashelf.music.RELOAD_LYRICS";
    private static final String CHANNEL = "music_playback";
    private static final int NOTIFICATION_ID = 1001;
    private static volatile Snapshot last = Snapshot.empty();
    private HandlerThread playbackThread;
    private Handler playback;
    private final PlaybackIntent intentState = new PlaybackIntent();
    private volatile boolean destroyed;
    private volatile boolean foreground;
    private PlaybackQueue queue;
    private volatile SubsonicClient owner;
    private final LyricRequests lyricRequests = new LyricRequests();
    private FloatingPlayerWindow floatingWindow;
    private PlaybackSessionStore store;
    private PlaybackSessionStore.Lease lease;
    private String accountKey;
    private String source = "音乐库";
    private long queueRevision;
    private boolean queueLoading;
    private String queueError = "";
    private ExoPlayer player;
    private MediaSession session;
    private AudioManager audio;
    private AudioFocusRequest focusRequest;
    private AudioOutput audioOutput;
    private VolumeSettings volumeSettings;
    private Runnable volumeListener;
    private boolean prepared;
    private boolean preparing;
    private boolean playing;
    private boolean buffering;
    private final PlaybackSeek seekState = new PlaybackSeek();
    private int bufferedPercent;
    private String error = "";
    private long lastPersist;
    private String metadataKey = "";
    private String notificationKey = "";
    private final BroadcastReceiver noisyReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) { playback.post(() -> pause()); }
    };

    public static void playQueue(Context context, List<Song> songs, int startIndex) {
        startQueue(context, songs, startIndex, false);
    }
    public static void playLibraryQueue(Context context, List<Song> songs, int startIndex) {
        startQueue(context, songs, startIndex, true);
    }
    private static void startQueue(Context context, List<Song> songs, int startIndex, boolean library) {
        Intent command = new Intent(context, PlayerService.class).setAction(ACTION_QUEUE);
        command.putExtra("songs", new ArrayList<>(songs));
        command.putExtra("index", startIndex); command.putExtra("library", library);
        context.startForegroundService(command);
    }
    public static void command(Context context, String action) {
        Intent command = new Intent(context, PlayerService.class).setAction(action);
        if (ACTION_PLAY.equals(action) || ACTION_RETRY.equals(action) || ACTION_SHOW_FLOATING.equals(action)
                || (ACTION_TOGGLE.equals(action) && !last.playRequested)) context.startForegroundService(command);
        else context.startService(command);
    }
    public static void restore(Context context) { command(context, ACTION_RESTORE); }
    public static void seek(Context context, int positionMs) {
        context.startService(new Intent(context, PlayerService.class).setAction(ACTION_SEEK).putExtra("position", positionMs));
    }
    public static void setMode(Context context, PlaybackMode mode) {
        context.startService(new Intent(context, PlayerService.class).setAction(ACTION_MODE).putExtra("mode", mode.name()));
    }
    public static void queueAction(Context context, String action, String songId) {
        Intent command = new Intent(context, PlayerService.class).setAction(action).putExtra("songId", songId);
        if (ACTION_SELECT.equals(action) && (last.song == null || !last.song.id().equals(songId))) context.startForegroundService(command);
        else context.startService(command);
    }
    public static Snapshot snapshot() { return last; }
    public static void forgetSession() { last = Snapshot.empty(); }

    @Override public void onCreate() {
        super.onCreate();
        playbackThread = new HandlerThread("SeaShelfPlayback"); playbackThread.start();
        playback = new Handler(playbackThread.getLooper());
        audio = (AudioManager) getSystemService(AUDIO_SERVICE);
        audioOutput = AudioOutput.androidOutput();
        volumeSettings = app().volumeSettings();
        floatingWindow = new FloatingPlayerWindow(this, () -> !destroyed && hasOwner());
        volumeListener = () -> playback.post(() -> {
            if (!destroyed) syncVolumeSettings();
        });
        volumeSettings.attach(volumeListener);
        AudioAttributes attributes = new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build();
        focusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN).setAudioAttributes(attributes)
                .setOnAudioFocusChangeListener(change -> {
                    if (destroyed || !hasOwner()) return;
                    if (change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT || change == AudioManager.AUDIOFOCUS_LOSS) {
                        intentState.focusLost(change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT);
                        pauseAudio(); update();
                    } else if (change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK) {
                        audioOutput.duck(); applyPlayerVolume();
                    }
                    else if (change == AudioManager.AUDIOFOCUS_GAIN) {
                        audioOutput.focusGranted(); applyPlayerVolume(); intentState.focusGained();
                        if (intentState.wantsPlay()) resume();
                    }
                }, playback).build();
        session = new MediaSession(this, "SeaShelfPlayer");
        session.setCallback(new MediaSession.Callback() {
            @Override public void onPlay() { ensureForeground(); resume(); }
            @Override public void onPause() { pause(); }
            @Override public void onSkipToNext() { next(); }
            @Override public void onSkipToPrevious() { previous(); }
            @Override public void onSeekTo(long pos) { doSeek((int) Math.min(Integer.MAX_VALUE, Math.max(0, pos))); }
            @Override public void onStop() { pause(); stopSelf(); }
        }, playback);
        session.setActive(true);
        ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(
                new NotificationChannel(CHANNEL, "音乐播放", NotificationManager.IMPORTANCE_LOW));
        if (android.os.Build.VERSION.SDK_INT >= 33)
            registerReceiver(noisyReceiver, new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY), RECEIVER_NOT_EXPORTED);
        else registerReceiver(noisyReceiver, new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY));
        playback.post(() -> {
            syncVolumeSettings();
            initialize();
            playback.post(tick);
        });
    }

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (destroyed) return;
            audioOutput.verify(); publishVolumeState();
            if (hasOwner()) {
                last = capture();
                if (playing && SystemClock.elapsedRealtime() - lastPersist >= 5000) {
                    persist(); publishSystemState(last);
                }
            } else if (owner != null) { intentState.stop(); releasePlayer(); lyricRequests.clear(); last = Snapshot.empty(); }
            playback.postDelayed(this, 400);
        }
    };

    private void initialize() {
        SeaShelfApp app = app();
        owner = app.client(); store = app.playbackSessions();
        if (owner == null) return;
        accountKey = owner.playbackAccountKey(); lease = store.activate(accountKey);
        PlaybackSession restored = store.load(lease);
        if (restored == null || !hasOwner()) return;
        int selected = 0;
        for (int i = 0; i < restored.songs().size(); i++)
            if (restored.songs().get(i).id().equals(restored.currentId())) { selected = i; break; }
        queue = new PlaybackQueue(restored.songs(), selected); queue.setMode(restored.mode()); source = restored.source();
        intentState.begin(false, restored.positionMs());
        update(false);
    }

    @Override public int onStartCommand(Intent command, int flags, int startId) {
        if (command != null) {
            String action = command.getAction();
            if (ACTION_QUEUE.equals(action) || ACTION_PLAY.equals(action) || ACTION_RETRY.equals(action)
                    || ACTION_TOGGLE.equals(action) || ACTION_SELECT.equals(action)
                    || (ACTION_SHOW_FLOATING.equals(action) && last.song != null)) ensureForeground();
        }
        playback.post(() -> { if (!destroyed) handleCommand(command); });
        return START_NOT_STICKY;
    }

    private void handleCommand(Intent command) {
        if (command == null || command.getAction() == null) return;
        String action = command.getAction();
        if (!hasOwner()) { last = Snapshot.empty(); stopSelf(); return; }
        if (ACTION_RESTORE.equals(action)) { update(false); return; }
        if (ACTION_QUEUE.equals(action)) {
            Serializable extra = command.getSerializableExtra("songs");
            if (!(extra instanceof ArrayList)) { stopSelf(); return; }
            List<Song> songs = new ArrayList<>();
            for (Object value : (ArrayList<?>) extra) {
                if (!(value instanceof Song)) { stopSelf(); return; }
                songs.add((Song) value);
            }
            try {
                PlaybackMode mode = queue == null ? PlaybackMode.LIST_LOOP : queue.mode();
                queue = new PlaybackQueue(songs, command.getIntExtra("index", 0)); queue.setMode(mode);
                if (queue.current() == null) { clearQueue(); return; }
                queueRevision++; queueLoading = false; queueError = "";
                source = command.getBooleanExtra("library", false) ? "音乐库" : "搜索结果";
                startSong(true, 0);
                if ("音乐库".equals(source)) loadLibraryQueue();
            } catch (IllegalArgumentException invalid) { stopSelf(); }
            return;
        }
        if (queue == null || queue.current() == null) { stopSelf(); return; }
        switch (action) {
            case ACTION_SHOW_FLOATING: ensureForeground(); floatingWindow.show(); break;
            case ACTION_RELOAD_LYRICS: loadLyrics(true); update(false); break;
            case ACTION_TOGGLE: if (intentState.wantsPlay()) pause(); else resume(); break;
            case ACTION_PLAY: resume(); break;
            case ACTION_PAUSE: pause(); break;
            case ACTION_RETRY: startSong(true, capture().positionMs); break;
            case ACTION_RELOAD_QUEUE: if (!queueLoading && "音乐库".equals(source)) loadLibraryQueue(); break;
            case ACTION_NEXT: next(); break;
            case ACTION_PREVIOUS: previous(); break;
            case ACTION_SEEK: doSeek(command.getIntExtra("position", 0)); break;
            case ACTION_MODE:
                try { queue.setMode(PlaybackMode.valueOf(command.getStringExtra("mode"))); update(); }
                catch (IllegalArgumentException | NullPointerException ignored) { }
                break;
            case ACTION_SELECT:
                if (queue.select(command.getStringExtra("songId"))) startSong(true, 0);
                break;
            case ACTION_PLAY_NEXT:
                if (queue.playNext(command.getStringExtra("songId"))) { editedQueue(); update(); }
                break;
            case ACTION_REMOVE: remove(command.getStringExtra("songId")); break;
            case ACTION_STOP: pause(); stopSelf(); break;
            default: break;
        }
    }

    private void editedQueue() { queueRevision++; queueLoading = false; queueError = ""; }
    private void remove(String id) {
        String selected = queue.current().id(); boolean play = intentState.wantsPlay();
        if (!queue.remove(id)) return;
        editedQueue();
        if (queue.current() == null) clearQueue();
        else if (!queue.current().id().equals(selected)) startSong(play, 0);
        else update();
    }
    private void clearQueue() {
        editedQueue(); intentState.stop(); releasePlayer(); queue = null; error = ""; lyricRequests.clear();
        store.clear(accountKey); lease = store.activate(accountKey);
        update(false); stopForeground(STOP_FOREGROUND_REMOVE); foreground = false;
        ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).cancel(NOTIFICATION_ID);
        stopSelf();
    }
    private void loadLibraryQueue() {
        if (queue == null || !hasOwner()) return;
        final long revision = queueRevision;
        final SubsonicClient client = owner;
        queueLoading = true; queueError = ""; update(false);
        app().network().execute(() -> {
            try {
                List<Song> library = client.songs();
                playback.post(() -> {
                    if (destroyed || !hasOwner() || client != owner || revision != queueRevision || queue == null) return;
                    queue.expanded(library); queueLoading = false; update();
                });
            } catch (Exception failure) {
                playback.post(() -> {
                    if (destroyed || !hasOwner() || client != owner || revision != queueRevision || queue == null) return;
                    queueLoading = false; queueError = "列表扩展失败，点击重试"; update(false);
                });
            }
        });
    }

    private void startSong(boolean play, int position) {
        if (queue == null || queue.current() == null || !hasOwner()) return;
        if (!store.isValid(lease)) lease = store.activate(accountKey);
        long request = intentState.begin(play, position);
        prepareSong(request, false);
    }
    private void loadLyrics(boolean reload) {
        if (!hasOwner() || queue == null || queue.current() == null) { lyricRequests.clear(); return; }
        final SubsonicClient client = owner;
        LyricRequests.Ticket ticket = lyricRequests.select(client, queue.current().id(), reload);
        if (ticket == null) return;
        app().network().execute(() -> {
            Lyrics result = null; boolean failed = false;
            try { result = client.lyrics(ticket.songId); } catch (Exception unavailable) { failed = true; }
            final Lyrics fetched = result; final boolean error = failed;
            playback.post(() -> {
                if (destroyed || !hasOwner() || owner != client || queue == null || queue.current() == null
                        || !ticket.songId.equals(queue.current().id())) return;
                if (lyricRequests.complete(ticket, fetched, error)) update(false);
            });
        });
    }
    private void prepareSong(long request, boolean original) {
        if (destroyed || !hasOwner() || !intentState.isCurrent(request) || queue == null) return;
        releasePlayer(); error = ""; preparing = true; buffering = true; update();
        long started = SystemClock.elapsedRealtime();
        try {
            ExoPlayer current = new ExoPlayer.Builder(this).setLooper(playback.getLooper())
                    .setLoadControl(new DefaultLoadControl.Builder()
                            .setBufferDurationsMs(15000, 45000, 1000, 2000)
                            .setPrioritizeTimeOverSizeThresholds(true).build()).build();
            player = current;
            current.setVolume(audioOutput.playerVolume());
            current.setWakeMode(C.WAKE_MODE_LOCAL);
            current.setAudioAttributes(new androidx.media3.common.AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(), false);
            current.addAnalyticsListener(new AnalyticsListener() {
                @Override public void onAudioSessionIdChanged(EventTime eventTime, int audioSessionId) {
                    if (!isCurrent(current, request)) return;
                    audioOutput.sessionChanged(audioSessionId); publishVolumeState();
                }
            });
            final int initialPosition = intentState.positionMs();
            current.addListener(new Player.Listener() {
                @Override public void onPlaybackStateChanged(int state) {
                    if (!isCurrent(current, request)) return;
                    buffering = state == Player.STATE_BUFFERING;
                    playing = current.isPlaying();
                    if (state == Player.STATE_READY) {
                        if (!prepared) {
                            prepared = true; preparing = false;
                            Log.i("SeaShelfPlayback", "prepared_ms=" + (SystemClock.elapsedRealtime() - started)
                                    + " original=" + original);
                            // A user seek during preparation takes precedence over the source's initial position.
                            if (intentState.positionMs() != initialPosition) beginSeek(); else maybeStart();
                        } else if (seekState.active()) finishSeek();
                        // READY also occurs after a rebuffer. ExoPlayer preserves playWhenReady itself;
                        // calling resume here would override a pause made while the network was stalled.
                    } else if (state == Player.STATE_ENDED) {
                        boolean firstTerminal = !prepared;
                        prepared = true; preparing = false;
                        PlaybackSeek.Completion completion = seekState.complete(intentState.positionMs());
                        if (completion == PlaybackSeek.Completion.SEEK_AGAIN
                                || (firstTerminal && intentState.positionMs() != initialPosition)) {
                            beginSeek(); update(); return;
                        }
                        if (!intentState.wantsPlay()) { playing = false; update(); return; }
                        String completed = queue.current().id();
                        Song following = queue.next();
                        if (following == null) { intentState.pause(); pauseAudio(); doSeek(0); }
                        else if (following.id().equals(completed)) { intentState.resume(); doSeek(0); }
                        else { startSong(true, 0); return; }
                    }
                    update();
                }
                @Override public void onIsPlayingChanged(boolean active) {
                    if (!isCurrent(current, request)) return;
                    playing = active; update(false);
                }
                @Override public void onPlayerError(PlaybackException failure) {
                    if (!isCurrent(current, request)) return;
                    Log.w("SeaShelfPlayback", "media_error=" + failure.errorCode + " original=" + original);
                    StreamPolicy.Failure kind = failureKind(failure);
                    if (StreamPolicy.mayUseOriginal(original, kind)) {
                        capture(); prepareSong(request, true);
                    } else if (kind == StreamPolicy.Failure.INVALID_RESPONSE) {
                        fail("电脑服务返回的不是音频，请确认歌曲可播放后重试。");
                    } else fail("歌曲加载失败，请检查网络和电脑音乐服务后重试。");
                }
            });
            // The signed URI exists only in memory. Cache identity excludes all URL/auth parameters.
            String profile = original ? StreamPolicy.ORIGINAL_PROFILE : StreamPolicy.MP3_PROFILE;
            MediaItem item = new MediaItem.Builder()
                    .setUri((original ? owner.originalStreamUrl(queue.current().id())
                            : owner.streamUrl(queue.current().id())).toString())
                    .setCustomCacheKey(StreamPolicy.cacheKey(accountKey, queue.current().id(), profile)).build();
            current.setMediaSource(new ProgressiveMediaSource.Factory(PrivateAudioCache.factory(this), StreamExtractors.create(original))
                    .setContinueLoadingCheckIntervalBytes(64 * 1024)
                    .setLoadErrorHandlingPolicy(new StreamLoadErrorPolicy())
                    .createMediaSource(item), initialPosition);
            current.prepare();
            playback.postDelayed(() -> {
                if (isCurrent(current, request) && !prepared) {
                    // Slow networks must never silently switch to a much larger lossless download.
                    fail("连接歌曲超时，请检查网络后重试。");
                }
            }, 45000);
        } catch (Exception failure) {
            fail("歌曲无法加载，请检查电脑服务和网络后重试。");
        }
    }
    private static StreamPolicy.Failure failureKind(PlaybackException failure) {
        // Inspect transport causes first: an HTTP rejection must never be mistaken for a codec issue.
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof HttpDataSource.InvalidResponseCodeException) return StreamPolicy.Failure.HTTP;
            if (cause instanceof HttpDataSource.InvalidContentTypeException) return StreamPolicy.Failure.INVALID_RESPONSE;
            if (cause instanceof HttpDataSource.HttpDataSourceException) return StreamPolicy.Failure.NETWORK;
        }
        if (failure.errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED)
            return StreamPolicy.Failure.UNSUPPORTED_CONTAINER;
        if (failure.errorCode == PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED
                || failure.errorCode == PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES)
            return StreamPolicy.Failure.UNSUPPORTED_CODEC;
        return StreamPolicy.Failure.OTHER;
    }
    private boolean isCurrent(ExoPlayer candidate, long request) {
        return !destroyed && hasOwner() && player == candidate && intentState.isCurrent(request);
    }
    private void fail(String message) {
        capture(); intentState.stop(); releasePlayer(); error = message; update();
    }
    private void pauseAudio() {
        if (player != null) {
            try { player.pause(); } catch (IllegalStateException ignored) { }
        }
        playing = false;
    }
    private void pause() {
        intentState.pause(); pauseAudio(); audio.abandonAudioFocusRequest(focusRequest); update();
    }
    private void resume() {
        if (queue == null || queue.current() == null || !hasOwner()) return;
        ensureForeground();
        if (player == null) { startSong(true, intentState.positionMs()); return; }
        intentState.resume();
        if (player.getPlaybackState() == Player.STATE_ENDED) doSeek(0); else maybeStart();
        update();
    }
    private void maybeStart() {
        if (!prepared || seekState.active() || !intentState.wantsPlay() || !hasOwner()) return;
        if (audio.requestAudioFocus(focusRequest) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            intentState.pause(); error = "其他应用正在使用音频，请稍后播放。"; return;
        }
        try {
            audioOutput.focusGranted(); applyPlayerVolume();
            player.play(); playing = player.isPlaying(); error = "";
        }
        catch (IllegalStateException invalid) { fail("播放中断，请点击重试。"); }
    }
    private void next() {
        if (queue == null || queue.current() == null) return;
        queue.skipNext(); startSong(true, 0);
    }
    private void previous() {
        if (queue == null || queue.current() == null) return;
        queue.previous(); startSong(true, 0);
    }
    private void doSeek(int positionMs) {
        if (queue == null || queue.current() == null) return;
        int total = queue.current().durationSeconds() * 1000;
        if (player != null && prepared) {
            try { total = Math.max(total, boundedPosition(player.getDuration())); } catch (IllegalStateException ignored) { }
        }
        int target = Math.max(0, total > 0 ? Math.min(positionMs, total) : positionMs);
        intentState.seek(target);
        if (prepared && !seekState.active()) beginSeek();
        update();
    }
    private void beginSeek() {
        if (player == null || !prepared) return;
        try {
            int duration = boundedPosition(player.getDuration());
            int seekApplied = Math.max(0, duration > 0 ? Math.min(intentState.positionMs(), duration) : intentState.positionMs());
            intentState.seek(seekApplied);
            ExoPlayer seekingPlayer = player;
            long operation = seekState.begin(seekApplied);
            player.seekTo(seekApplied);
            // A cached seek may stay READY and produce no state transition.
            playback.post(() -> {
                if (destroyed || player != seekingPlayer || !seekState.isCurrent(operation)) return;
                if (player.getPlaybackState() == Player.STATE_READY) finishSeek();
                else if (player.getPlaybackState() == Player.STATE_ENDED) {
                    // Seeking to an already ended position may emit no state transition.
                    if (seekState.complete(intentState.positionMs()) == PlaybackSeek.Completion.SEEK_AGAIN) beginSeek();
                    update();
                }
            });
            playback.postDelayed(() -> {
                if (!destroyed && player == seekingPlayer && seekState.isCurrent(operation))
                    fail("定位超时，请检查网络后重试。");
            }, 15000);
        } catch (IllegalStateException invalid) { seekState.cancel(); fail("定位失败，请点击重试。"); }
    }
    private void finishSeek() {
        PlaybackSeek.Completion completion = seekState.complete(intentState.positionMs());
        if (completion == PlaybackSeek.Completion.SEEK_AGAIN) beginSeek();
        else if (completion == PlaybackSeek.Completion.SETTLED) maybeStart();
        update();
    }
    private static int boundedPosition(long value) { return (int) Math.min(Integer.MAX_VALUE, Math.max(0L, value)); }
    private void applyPlayerVolume() {
        if (player != null) try { player.setVolume(audioOutput.playerVolume()); } catch (IllegalStateException ignored) { }
    }
    private void syncVolumeSettings() {
        audioOutput.setBoostDb(volumeSettings.boostDb()); publishVolumeState();
    }
    private void publishVolumeState() { volumeSettings.publish(volumeListener, audioOutput.state()); }

    private Snapshot capture() {
        int position = intentState.positionMs();
        int duration = queue == null || queue.current() == null ? 0 : queue.current().durationSeconds() * 1000;
        if (player != null && prepared) {
            try {
                int nativeDuration = boundedPosition(player.getDuration());
                if (nativeDuration > 0) duration = nativeDuration;
                bufferedPercent = Math.max(0, Math.min(100, currentBufferedPercent(duration)));
                if (!seekState.active()) { position = boundedPosition(player.getCurrentPosition()); intentState.seek(position); }
            } catch (IllegalStateException ignored) { }
        }
        return new Snapshot(queue == null ? null : queue.current(), playing, buffering, preparing, seekState.active(),
                position, duration, error, intentState.wantsPlay(), bufferedPercent, queue == null ? -1 : queue.index(),
                queue == null ? Collections.emptyList() : queue.songs(), queue == null ? PlaybackMode.LIST_LOOP : queue.mode(),
                source, queueLoading, queueError, lyricRequests.state());
    }
    private int currentBufferedPercent(int duration) {
        return duration > 0 ? (int) Math.min(100L, player.getBufferedPosition() * 100L / duration) : 0;
    }
    private void update() { update(true); }
    private void update(boolean save) {
        if (destroyed || !hasOwner()) return;
        loadLyrics(false);
        Snapshot state = capture(); last = state; publishSystemState(state);
        if (save) persist();
    }
    @SuppressLint("NotificationPermission") // MediaSession notifications are exempt on Android 13+.
    private void publishSystemState(Snapshot state) {
        int mode = !state.error.isEmpty() ? PlaybackState.STATE_ERROR : (state.preparing || state.seeking || state.buffering)
                && state.playRequested ? PlaybackState.STATE_BUFFERING : state.playing ? PlaybackState.STATE_PLAYING : PlaybackState.STATE_PAUSED;
        session.setPlaybackState(new PlaybackState.Builder().setActions(PlaybackState.ACTION_PLAY | PlaybackState.ACTION_PAUSE
                | PlaybackState.ACTION_SKIP_TO_NEXT | PlaybackState.ACTION_SKIP_TO_PREVIOUS | PlaybackState.ACTION_SEEK_TO | PlaybackState.ACTION_STOP)
                .setState(mode, state.positionMs, state.playing && !state.buffering ? 1f : 0f).build());
        String key = state.song == null ? "" : state.song.id() + ":" + state.durationMs;
        if (!key.equals(metadataKey)) {
            metadataKey = key;
            if (state.song == null) session.setMetadata(null);
            else session.setMetadata(new MediaMetadata.Builder().putString(MediaMetadata.METADATA_KEY_TITLE, state.song.title())
                    .putString(MediaMetadata.METADATA_KEY_ARTIST, state.song.artist()).putString(MediaMetadata.METADATA_KEY_ALBUM, state.song.album())
                    .putLong(MediaMetadata.METADATA_KEY_DURATION, state.durationMs).build());
        }
        String notice = key + ":" + state.playRequested + ":" + state.statusText();
        if (foreground && !notice.equals(notificationKey)) {
            notificationKey = notice;
            ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).notify(NOTIFICATION_ID, notification());
        }
    }
    private void persist() {
        if (!hasOwner() || queue == null || queue.current() == null || !store.isValid(lease)) return;
        Snapshot state = capture();
        store.save(lease, new PlaybackSession(accountKey, state.queue, state.song.id(), state.positionMs, state.mode, state.source));
        lastPersist = SystemClock.elapsedRealtime();
    }
    private boolean hasOwner() { return owner != null && app().client() == owner; }
    private SeaShelfApp app() { return (SeaShelfApp) getApplication(); }
    private void ensureForeground() {
        if (!foreground) { foreground = true; startForeground(NOTIFICATION_ID, notification()); }
    }
    private Notification notification() {
        Snapshot state = last; Song song = state.song;
        PendingIntent open = PendingIntent.getActivity(this, 0, new Intent(this, PlayerActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this, CHANNEL).setSmallIcon(android.R.drawable.ic_media_play)
                .setContentTitle(song == null ? "海屿音乐" : song.title())
                .setContentText(song == null ? "你的私人音乐库" : song.artist() + " · " + state.statusText())
                .setContentIntent(open).setOngoing(state.playRequested).setVisibility(Notification.VISIBILITY_PUBLIC)
                .addAction(new Notification.Action.Builder(android.R.drawable.ic_media_previous, "上一首", action(ACTION_PREVIOUS, 1)).build())
                .addAction(new Notification.Action.Builder(state.playRequested ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play,
                        state.playRequested ? "暂停" : "播放", action(ACTION_TOGGLE, 2)).build())
                .addAction(new Notification.Action.Builder(android.R.drawable.ic_media_next, "下一首", action(ACTION_NEXT, 3)).build())
                .setStyle(new Notification.MediaStyle().setMediaSession(session.getSessionToken()).setShowActionsInCompactView(0, 1, 2)).build();
    }
    private PendingIntent action(String name, int requestCode) {
        return PendingIntent.getService(this, requestCode, new Intent(this, PlayerService.class).setAction(name),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
    private void releasePlayer() {
        bufferedPercent = 0; prepared = false; preparing = false; playing = false; buffering = false; seekState.cancel();
        audioOutput.release(); publishVolumeState();
        if (player != null) {
            ExoPlayer previous = player; player = null;
            previous.release();
        }
    }
    @Override public void onDestroy() {
        destroyed = true; unregisterReceiver(noisyReceiver); floatingWindow.destroy();
        volumeSettings.detach(volumeListener);
        playback.removeCallbacksAndMessages(null);
        playback.post(() -> {
            if (hasOwner()) { intentState.pause(); pauseAudio(); persist(); last = capture(); }
            else last = Snapshot.empty();
            intentState.stop(); releasePlayer(); audio.abandonAudioFocusRequest(focusRequest);
            session.setActive(false); session.release(); playbackThread.quitSafely();
        });
        stopForeground(STOP_FOREGROUND_REMOVE); foreground = false;
        super.onDestroy();
    }
    @Override public IBinder onBind(Intent command) { return null; }

    /** Immutable UI value. Neither Activities nor notification code access ExoPlayer. */
    public static final class Snapshot {
        public final Song song;
        public final boolean playing, buffering, preparing, seeking, playRequested, canNext, queueLoading;
        public final int positionMs, durationMs, bufferedPercent, queuePosition, queueSize;
        public final String error, source, queueError;
        public final List<Song> queue;
        public final PlaybackMode mode;
        public final LyricState lyrics;
        private Snapshot(Song song, boolean playing, boolean buffering, boolean preparing, boolean seeking,
                         int positionMs, int durationMs, String error, boolean playRequested, int bufferedPercent,
                         int index, List<Song> queue, PlaybackMode mode, String source, boolean queueLoading, String queueError, LyricState lyrics) {
            this.song = song; this.playing = playing; this.buffering = buffering; this.preparing = preparing; this.seeking = seeking;
            this.positionMs = positionMs; this.durationMs = durationMs; this.error = error; this.playRequested = playRequested;
            this.bufferedPercent = bufferedPercent; this.queuePosition = index + 1; this.queue = queue; this.queueSize = queue.size();
            this.canNext = queueSize > 1; this.mode = mode; this.source = source; this.queueLoading = queueLoading; this.queueError = queueError;
            this.lyrics = lyrics;
        }
        public String statusText() {
            if (song == null) return "选择一首歌开始播放";
            if (!error.isEmpty()) return error;
            if (!playRequested) return preparing ? "已暂停 · 正在准备" : seeking ? "已暂停 · 正在定位" : "已暂停";
            if (preparing) return "正在连接歌曲…";
            if (seeking) return "正在定位…";
            if (buffering) return "正在缓冲…";
            return playing ? "正在播放" : "准备播放";
        }
        private static Snapshot empty() {
            return new Snapshot(null, false, false, false, false, 0, 0, "", false, 0, -1,
                    Collections.emptyList(), PlaybackMode.LIST_LOOP, "音乐库", false, "", LyricState.empty());
        }
    }
}
