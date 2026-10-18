package app.seashelf.music.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import app.seashelf.music.SeaShelfApp;
import app.seashelf.music.api.Lyrics;
import app.seashelf.music.api.SubsonicClient;
import app.seashelf.music.player.PlaybackMode;
import app.seashelf.music.player.PlayerService;
import java.util.ArrayList;
import java.util.List;

public final class PlayerActivity extends Activity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private ImageView cover;
    private ScrollView lyricsScroll;
    private LinearLayout lyricLines;
    private TextView lyricPreview;
    private Button lyricsToggle;
    private final List<TextView> lyricViews = new ArrayList<>();
    private final RequestGeneration lyricsGeneration = new RequestGeneration();
    private Lyrics lyrics;
    private int highlightedLine = -2;
    private boolean showingLyrics;
    private TextView title, artist, elapsed, duration, error, source;
    private Button toggle, previous, next, mode, queueButton, retry;
    private SeekBar seek;
    private String displayedSongId;
    private boolean dragging;
    private QueueDialog queueDialog;
    private VolumeDialog volumeDialog;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        setVolumeControlStream(AudioManager.STREAM_MUSIC);
        if (((SeaShelfApp) getApplication()).client() == null) {
            startActivity(new Intent(this, LoginActivity.class)); finish(); return;
        }
        showingLyrics = state != null && state.getBoolean("lyrics", false);
        LinearLayout root = UiKit.screen(this);
        root.setPadding(UiKit.dp(this, 16), UiKit.dp(this, 8), UiKit.dp(this, 16), UiKit.dp(this, 10));
        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(header, new LinearLayout.LayoutParams(-1, UiKit.dp(this, 48)));
        Button back = compactButton("‹", false, "返回音乐库"); back.setTextSize(29);
        header.addView(back, new LinearLayout.LayoutParams(UiKit.dp(this, 48), -1));
        back.setOnClickListener(view -> {
            if (isTaskRoot()) startActivity(new Intent(this, LibraryActivity.class)); finish();
        });
        source = UiKit.text(this, "音乐库", 12, UiKit.MUTED, false);
        source.setGravity(Gravity.CENTER);
        source.setOnClickListener(view -> showQueue());
        source.setContentDescription("查看播放队列");
        header.addView(source, new LinearLayout.LayoutParams(0, -1, 1));
        Button volume = compactButton("音量", false, "打开音量调节"); volume.setTextSize(13);
        header.addView(volume, new LinearLayout.LayoutParams(UiKit.dp(this, 56), -1));
        volume.setOnClickListener(view -> showVolume());
        lyricsToggle = compactButton(showingLyrics ? "封面" : "歌词", false, "切换封面和歌词");
        header.addView(lyricsToggle, new LinearLayout.LayoutParams(UiKit.dp(this, 64), -1));
        lyricsToggle.setOnClickListener(view -> {
            showingLyrics = !showingLyrics; updateLyricsVisibility();
            if (showingLyrics && highlightedLine >= 0 && highlightedLine < lyricViews.size()) {
                TextView current = lyricViews.get(highlightedLine);
                lyricsScroll.post(() -> lyricsScroll.scrollTo(0, Math.max(0, current.getTop() - lyricsScroll.getHeight() / 2)));
            }
        });

        ScrollView details = UiKit.scroll(this, root);
        LinearLayout content = UiKit.column(this);
        content.setGravity(Gravity.CENTER_HORIZONTAL);
        content.setPadding(0, UiKit.dp(this, 10), 0, UiKit.dp(this, 8));
        details.addView(content);
        title = UiKit.text(this, "选择一首歌开始播放", 23, UiKit.TEXT, true);
        title.setGravity(Gravity.CENTER); title.setLineSpacing(UiKit.dp(this, 2), 1f);
        content.addView(title, new LinearLayout.LayoutParams(-1, -2));
        UiKit.gap(content, 6);
        artist = UiKit.text(this, "", 14, UiKit.MUTED, false); artist.setGravity(Gravity.CENTER);
        content.addView(artist, new LinearLayout.LayoutParams(-1, -2));
        UiKit.gap(content, 12);
        FrameLayout artStage = new FrameLayout(this);
        artStage.setBackground(UiKit.rounded(UiKit.SURFACE, 24, this));
        content.addView(artStage, new LinearLayout.LayoutParams(-1, UiKit.dp(this, 200)));
        cover = UiKit.cover(this, 0);
        artStage.addView(cover, new FrameLayout.LayoutParams(UiKit.dp(this, 200), UiKit.dp(this, 200), Gravity.CENTER));
        lyricsScroll = new ScrollView(this); lyricsScroll.setFillViewport(true);
        lyricLines = UiKit.column(this); lyricLines.setGravity(Gravity.CENTER_HORIZONTAL);
        lyricLines.setPadding(UiKit.dp(this, 18), UiKit.dp(this, 18), UiKit.dp(this, 18), UiKit.dp(this, 18));
        lyricsScroll.addView(lyricLines);
        artStage.addView(lyricsScroll, new FrameLayout.LayoutParams(-1, -1));
        UiKit.gap(content, 10);
        lyricPreview = UiKit.text(this, "", 13, UiKit.ACCENT, false);
        lyricPreview.setGravity(Gravity.CENTER); lyricPreview.setMaxLines(2);
        content.addView(lyricPreview, new LinearLayout.LayoutParams(-1, -2));
        updateLyricsVisibility();
        // Titles can wrap and the upper area can scroll on very small screens. Transport and lyrics access stay pinned.
        details.addOnLayoutChangeListener((view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            int available = details.getHeight() - title.getHeight() - artist.getHeight() - UiKit.dp(this, 75);
            int stageHeight = Math.max(UiKit.dp(this, 112), Math.min(UiKit.dp(this, 300), available));
            if (artStage.getLayoutParams().height != stageHeight) {
                artStage.getLayoutParams().height = stageHeight; artStage.requestLayout();
            }
            int size = Math.min(Math.max(1, details.getWidth() - UiKit.dp(this, 16)), stageHeight);
            if (cover.getLayoutParams().width != size) {
                cover.getLayoutParams().width = size; cover.getLayoutParams().height = size; cover.requestLayout();
            }
        });

        LinearLayout transport = UiKit.column(this);
        root.addView(transport, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout status = new LinearLayout(this); status.setGravity(Gravity.CENTER_VERTICAL);
        transport.addView(status, new LinearLayout.LayoutParams(-1, -2));
        error = UiKit.text(this, "", 12, UiKit.MUTED, false); error.setGravity(Gravity.CENTER);
        error.setMinHeight(UiKit.dp(this, 32)); error.setMaxLines(2);
        status.addView(error, new LinearLayout.LayoutParams(0, -2, 1));
        retry = compactButton("重试", false, "重新加载当前歌曲");
        status.addView(retry, new LinearLayout.LayoutParams(UiKit.dp(this, 64), UiKit.dp(this, 48)));
        retry.setVisibility(View.GONE); retry.setOnClickListener(view -> PlayerService.command(this, PlayerService.ACTION_RETRY));
        seek = new SeekBar(this); seek.setContentDescription("播放进度");
        seek.setProgressTintList(ColorStateList.valueOf(UiKit.ACCENT));
        seek.setThumbTintList(ColorStateList.valueOf(UiKit.ACCENT));
        transport.addView(seek, new LinearLayout.LayoutParams(-1, UiKit.dp(this, 48)));
        LinearLayout times = new LinearLayout(this);
        elapsed = UiKit.text(this, "0:00", 12, UiKit.MUTED, false);
        duration = UiKit.text(this, "0:00", 12, UiKit.MUTED, false);
        elapsed.setTypeface(android.graphics.Typeface.MONOSPACE); duration.setTypeface(android.graphics.Typeface.MONOSPACE);
        times.addView(elapsed, new LinearLayout.LayoutParams(0, -2, 1)); times.addView(duration);
        transport.addView(times);
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                if (fromUser) text(elapsed, format(progress));
            }
            @Override public void onStartTrackingTouch(SeekBar bar) { dragging = true; }
            @Override public void onStopTrackingTouch(SeekBar bar) {
                PlayerService.seek(PlayerActivity.this, bar.getProgress()); dragging = false;
            }
        });
        UiKit.gap(transport, 8);
        LinearLayout controls = new LinearLayout(this); controls.setGravity(Gravity.CENTER_VERTICAL);
        transport.addView(controls, new LinearLayout.LayoutParams(-1, UiKit.dp(this, 72)));
        mode = compactButton("循环", false, "播放模式"); mode.setTextSize(12);
        previous = compactButton("|◀", false, "上一首");
        toggle = compactButton("▶", true, "播放"); toggle.setTextSize(26);
        next = compactButton("▶|", false, "下一首");
        queueButton = compactButton("队列", false, "打开播放队列"); queueButton.setTextSize(12);
        controls.addView(mode, controlParams(1, 48));
        controls.addView(previous, controlParams(1, 52));
        controls.addView(toggle, controlParams(1.3f, 66));
        controls.addView(next, controlParams(1, 52));
        controls.addView(queueButton, controlParams(1, 48));
        mode.setOnClickListener(view -> showModes());
        previous.setOnClickListener(view -> PlayerService.command(this, PlayerService.ACTION_PREVIOUS));
        toggle.setOnClickListener(view -> PlayerService.command(this, PlayerService.ACTION_TOGGLE));
        next.setOnClickListener(view -> PlayerService.command(this, PlayerService.ACTION_NEXT));
        queueButton.setOnClickListener(view -> showQueue());
        PlayerService.restore(this);
    }
    private Button compactButton(String label, boolean primary, String description) {
        Button button = UiKit.button(this, label, primary);
        button.setPadding(0, 0, 0, 0); button.setMinWidth(0); button.setMinimumWidth(0);
        button.setMinHeight(UiKit.dp(this, 48)); button.setMinimumHeight(UiKit.dp(this, 48));
        button.setContentDescription(description); return button;
    }
    private LinearLayout.LayoutParams controlParams(float weight, int height) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, UiKit.dp(this, height), weight);
        params.leftMargin = UiKit.dp(this, 3); params.rightMargin = UiKit.dp(this, 3); return params;
    }
    private void updateLyricsVisibility() {
        cover.setVisibility(showingLyrics ? View.GONE : View.VISIBLE);
        lyricsScroll.setVisibility(showingLyrics ? View.VISIBLE : View.GONE);
        text(lyricsToggle, showingLyrics ? "封面" : "歌词");
    }
    private void showModes() {
        PlaybackMode[] modes = PlaybackMode.values(); String[] labels = new String[modes.length];
        for (int i = 0; i < modes.length; i++) labels[i] = modes[i].label();
        new AlertDialog.Builder(this).setTitle("播放模式")
                .setSingleChoiceItems(labels, PlayerService.snapshot().mode.ordinal(), (dialog, which) -> {
                    PlayerService.setMode(this, modes[which]); dialog.dismiss();
                }).setNegativeButton("取消", null).show();
    }
    private void showQueue() {
        if (queueDialog != null && queueDialog.isShowing()) return;
        queueDialog = new QueueDialog(this); queueDialog.show();
    }
    private void showVolume() {
        if (volumeDialog != null && volumeDialog.isShowing()) return;
        volumeDialog = new VolumeDialog(this); volumeDialog.show();
    }
    @Override protected void onResume() { super.onResume(); if (title != null) refresh(); }
    @Override protected void onPause() { handler.removeCallbacksAndMessages(null); super.onPause(); }
    @Override protected void onStop() {
        if (volumeDialog != null) { volumeDialog.dismiss(); volumeDialog = null; }
        super.onStop();
    }
    @Override protected void onSaveInstanceState(Bundle out) { out.putBoolean("lyrics", showingLyrics); super.onSaveInstanceState(out); }

    private void refresh() {
        PlayerService.Snapshot state = PlayerService.snapshot();
        boolean hasSong = state.song != null;
        enabled(toggle, hasSong); enabled(lyricsToggle, hasSong); enabled(seek, hasSong);
        enabled(previous, hasSong); enabled(next, hasSong); enabled(mode, hasSong);
        text(source, state.source + (hasSong ? " · " + state.queuePosition + " / " + state.queueSize : " · 队列为空"));
        description(source, state.source + "，播放队列共 " + state.queueSize + " 首，点击查看");
        text(error, state.statusText());
        error.setTextColor(state.error.isEmpty() ? UiKit.MUTED : UiKit.WARM);
        int retryVisibility = state.error.isEmpty() ? View.GONE : View.VISIBLE;
        if (retry.getVisibility() != retryVisibility) retry.setVisibility(retryVisibility);
        text(toggle, state.playRequested ? "Ⅱ" : "▶");
        description(toggle, state.playRequested ? "暂停" : "播放");
        text(mode, modeLabel(state.mode)); description(mode, "播放模式：" + state.mode.label());
        if (!hasSong) {
            if (displayedSongId != null) {
                displayedSongId = null; lyricsGeneration.next(); lyrics = null; showLyricMessage("选择一首歌开始播放");
            }
            text(title, "选择一首歌开始播放"); text(artist, ""); text(lyricPreview, "");
        } else {
            text(title, state.song.title()); text(artist, state.song.artist());
            if (!state.song.id().equals(displayedSongId)) {
                displayedSongId = state.song.id();
                CoverLoader.load(cover, ((SeaShelfApp) getApplication()).client(), state.song.coverArtId(), 512);
                loadLyrics(displayedSongId);
            }
            updateActiveLyric(state.positionMs);
        }
        if (!dragging) {
            int total = Math.max(1, state.durationMs);
            if (seek.getMax() != total) seek.setMax(total);
            int progress = Math.max(0, Math.min(state.positionMs, total));
            if (seek.getProgress() / 1000 != progress / 1000 || Math.abs(seek.getProgress() - progress) > 1000)
                seek.setProgress(progress);
            int buffered = (int) (total * (state.bufferedPercent / 100.0));
            if (seek.getSecondaryProgress() != buffered) seek.setSecondaryProgress(buffered);
            text(elapsed, format(state.positionMs)); text(duration, format(state.durationMs));
        }
        handler.postDelayed(this::refresh, 250);
    }
    private static String modeLabel(PlaybackMode mode) {
        switch (mode) {
            case SEQUENTIAL: return "顺序";
            case REPEAT_ONE: return "单曲循环";
            case SHUFFLE: return "随机";
            case SINGLE_PLAY: return "单曲播放";
            default: return "循环";
        }
    }
    private static void text(TextView view, String value) { if (!value.contentEquals(view.getText())) view.setText(value); }
    private static void description(View view, String value) {
        if (view.getContentDescription() == null || !value.contentEquals(view.getContentDescription())) view.setContentDescription(value);
    }
    private static void enabled(View view, boolean value) { if (view.isEnabled() != value) view.setEnabled(value); }

    private void loadLyrics(String songId) {
        lyrics = null;
        highlightedLine = -2;
        lyricPreview.setText("正在读取歌词…");
        showLyricMessage("正在读取歌词…");
        long token = lyricsGeneration.next();
        SeaShelfApp app = (SeaShelfApp) getApplication();
        SubsonicClient client = app.client();
        app.network().execute(() -> {
            try {
                Lyrics fetched = client.lyrics(songId);
                runOnUiThread(() -> {
                    if (!lyricsGeneration.isCurrent(token) || isFinishing() || isDestroyed() || client != app.client() || !songId.equals(displayedSongId)) return;
                    lyrics = fetched;
                    renderLyrics();
                });
            } catch (Exception failure) {
                runOnUiThread(() -> {
                    if (!lyricsGeneration.isCurrent(token) || isFinishing() || isDestroyed() || client != app.client() || !songId.equals(displayedSongId)) return;
                    lyricPreview.setText("歌词暂时无法加载");
                    showLyricMessage("歌词暂时无法加载，请检查电脑端连接。");
                });
            }
        });
    }

    private void showLyricMessage(String message) {
        lyricLines.removeAllViews();
        lyricViews.clear();
        TextView view = UiKit.text(this, message, 16, UiKit.MUTED, false);
        view.setGravity(Gravity.CENTER);
        lyricLines.addView(view, new LinearLayout.LayoutParams(-1, -2));
    }

    private void renderLyrics() {
        highlightedLine = -2;
        if (lyrics == null || lyrics.lines().isEmpty()) {
            lyricPreview.setText("这首歌暂无歌词");
            showLyricMessage("这首歌暂无歌词。\n可在电脑的歌曲旁添加同名 .lrc 文件后重新扫描。");
            return;
        }
        lyricLines.removeAllViews();
        lyricViews.clear();
        for (Lyrics.Line line : lyrics.lines()) {
            TextView view = UiKit.text(this, line.text(), 17, UiKit.MUTED, false);
            view.setGravity(Gravity.CENTER);
            view.setPadding(0, UiKit.dp(this, 10), 0, UiKit.dp(this, 10));
            lyricLines.addView(view, new LinearLayout.LayoutParams(-1, -2));
            lyricViews.add(view);
        }
        updateActiveLyric(PlayerService.snapshot().positionMs);
    }

    private void updateActiveLyric(int positionMs) {
        if (lyrics == null || lyrics.lines().isEmpty()) return;
        int active = lyrics.activeLine(positionMs);
        if (active == highlightedLine) return;
        if (highlightedLine >= 0 && highlightedLine < lyricViews.size()) {
            lyricViews.get(highlightedLine).setTextColor(UiKit.MUTED);
            lyricViews.get(highlightedLine).setTextSize(17);
        }
        highlightedLine = active;
        if (active >= 0) {
            TextView current = lyricViews.get(active);
            current.setTextColor(UiKit.TEXT);
            current.setTextSize(21);
            lyricPreview.setText(lyrics.lines().get(active).text());
            if (showingLyrics) lyricsScroll.post(() -> lyricsScroll.smoothScrollTo(0,
                    Math.max(0, current.getTop() - lyricsScroll.getHeight() / 2)));
        } else {
            lyricPreview.setText(lyrics.synced() ? "歌词即将开始" : "歌词已加载，点开查看");
        }
    }

    @Override protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        lyricsGeneration.next();
        if (queueDialog != null) { queueDialog.dismiss(); queueDialog = null; }
        if (volumeDialog != null) { volumeDialog.dismiss(); volumeDialog = null; }
        super.onDestroy();
    }

    private String format(int millis) {
        int seconds = Math.max(0, millis / 1000);
        return String.format(java.util.Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60);
    }
}
