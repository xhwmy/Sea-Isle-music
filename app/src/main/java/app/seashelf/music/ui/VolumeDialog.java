package app.seashelf.music.ui;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.media.AudioDeviceCallback;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import app.seashelf.music.SeaShelfApp;
import app.seashelf.music.player.AudioOutput;
import app.seashelf.music.player.VolumePolicy;
import app.seashelf.music.player.VolumeSettings;

/** Adjusts the actual media stream and optional app-only gain. No playback command is sent here. */
public final class VolumeDialog extends Dialog {
    private final Activity activity;
    private final AudioManager audio;
    private final VolumeSettings settings;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private SeekBar media, boost;
    private TextView mediaLabel, systemStatus, boostLabel, effectStatus;
    private boolean draggingMedia, draggingBoost, routeListenerRegistered, visible;
    private int minimum, maximum;
    private String adjustmentMessage = "";
    private final AudioDeviceCallback routes = new AudioDeviceCallback() {
        @Override public void onAudioDevicesAdded(AudioDeviceInfo[] devices) { updateViews(); }
        @Override public void onAudioDevicesRemoved(AudioDeviceInfo[] devices) { updateViews(); }
    };
    private final Runnable poll = new Runnable() {
        @Override public void run() {
            if (!visible) return;
            updateViews(); handler.postDelayed(this, 400);
        }
    };

    public VolumeDialog(Activity activity) {
        super(activity); this.activity = activity;
        audio = (AudioManager) activity.getSystemService(Context.AUDIO_SERVICE);
        settings = ((SeaShelfApp) activity.getApplication()).volumeSettings();
    }
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state); requestWindowFeature(Window.FEATURE_NO_TITLE);
        ScrollView scroll = new ScrollView(activity); scroll.setFillViewport(true);
        scroll.setBackground(UiKit.rounded(UiKit.SURFACE, 24, activity));
        LinearLayout root = UiKit.column(activity);
        root.setPadding(UiKit.dp(activity, 20), UiKit.dp(activity, 12), UiKit.dp(activity, 20), UiKit.dp(activity, 20));
        scroll.addView(root, new ScrollView.LayoutParams(-1, -2));
        LinearLayout header = new LinearLayout(activity); header.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(UiKit.text(activity, "音量", 21, UiKit.TEXT, true), new LinearLayout.LayoutParams(0, -2, 1));
        Button close = UiKit.button(activity, "完成", false); close.setPadding(0, 0, 0, 0);
        close.setContentDescription("关闭音量调节"); close.setOnClickListener(view -> dismiss());
        header.addView(close, new LinearLayout.LayoutParams(UiKit.dp(activity, 64), UiKit.dp(activity, 48)));
        root.addView(header, new LinearLayout.LayoutParams(-1, UiKit.dp(activity, 48)));
        UiKit.gap(root, 20);
        mediaLabel = UiKit.text(activity, "手机媒体音量", 17, UiKit.TEXT, true); root.addView(mediaLabel);
        media = slider("手机媒体音量"); media.setEnabled(false);
        root.addView(media, new LinearLayout.LayoutParams(-1, UiKit.dp(activity, 48)));
        systemStatus = UiKit.text(activity, "", 13, UiKit.MUTED, false); root.addView(systemStatus);
        media.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int index, boolean fromUser) {
                if (fromUser) setSystemVolume(index);
            }
            @Override public void onStartTrackingTouch(SeekBar bar) { draggingMedia = true; }
            @Override public void onStopTrackingTouch(SeekBar bar) { draggingMedia = false; updateViews(); }
        });
        UiKit.gap(root, 28);
        boostLabel = UiKit.text(activity, "歌曲音量增强 · 关闭", 17, UiKit.TEXT, true); root.addView(boostLabel);
        boost = slider("歌曲音量增强"); boost.setMax(VolumePolicy.MAX_BOOST_DB);
        root.addView(boost, new LinearLayout.LayoutParams(-1, UiKit.dp(activity, 48)));
        effectStatus = UiKit.text(activity, "", 13, UiKit.MUTED, false); root.addView(effectStatus);
        boost.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int gain, boolean fromUser) {
                if (fromUser) { settings.setBoostDb(gain); updateEffect(); }
            }
            @Override public void onStartTrackingTouch(SeekBar bar) { draggingBoost = true; }
            @Override public void onStopTrackingTouch(SeekBar bar) { draggingBoost = false; updateEffect(); }
        });
        UiKit.gap(root, 20);
        root.addView(UiKit.text(activity, "增强默认关闭，可按需调到 +6 dB。声音偏小可先调高媒体音量；增强可能造成失真，效果取决于手机和耳机。", 13, UiKit.WARM, false));
        setContentView(scroll);
    }
    private SeekBar slider(String label) {
        SeekBar bar = new SeekBar(activity); bar.setContentDescription(label);
        bar.setProgressTintList(ColorStateList.valueOf(UiKit.ACCENT));
        bar.setThumbTintList(ColorStateList.valueOf(UiKit.ACCENT));
        bar.setMinimumHeight(UiKit.dp(activity, 48)); return bar;
    }
    @Override protected void onStart() {
        super.onStart(); visible = true; Window window = getWindow();
        if (window != null) {
            window.setVolumeControlStream(AudioManager.STREAM_MUSIC);
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setGravity(Gravity.BOTTOM);
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT,
                    Math.min(UiKit.dp(activity, 500), (int) (activity.getResources().getDisplayMetrics().heightPixels * 0.85)));
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            WindowManager.LayoutParams params = window.getAttributes(); params.dimAmount = 0.45f; window.setAttributes(params);
        }
        if (audio != null) try {
            audio.registerAudioDeviceCallback(routes, handler); routeListenerRegistered = true;
        } catch (RuntimeException ignored) { /* Polling still mirrors volume-key and route changes. */ }
        handler.removeCallbacks(poll); poll.run();
    }
    @Override protected void onStop() {
        visible = false;
        handler.removeCallbacksAndMessages(null);
        if (audio != null && routeListenerRegistered) {
            try { audio.unregisterAudioDeviceCallback(routes); } catch (RuntimeException ignored) { }
            routeListenerRegistered = false;
        }
        super.onStop();
    }
    private void updateViews() {
        if (media == null || !visible) return;
        updateSystemVolume(); updateEffect();
    }
    private void updateSystemVolume() {
        try {
            if (audio == null) throw new IllegalStateException("No audio service");
            minimum = Build.VERSION.SDK_INT >= 28 ? audio.getStreamMinVolume(AudioManager.STREAM_MUSIC) : 0;
            maximum = Math.max(minimum, audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC));
            int actual = VolumePolicy.streamIndex(audio.getStreamVolume(AudioManager.STREAM_MUSIC), minimum, maximum);
            boolean fixed = audio.isVolumeFixed() || maximum <= minimum;
            boolean muted = audio.isStreamMute(AudioManager.STREAM_MUSIC);
            if (!draggingMedia) {
                media.setMin(minimum); media.setMax(maximum); media.setProgress(actual);
            }
            media.setEnabled(!fixed);
            String level = muted ? "已静音" : VolumePolicy.streamPercent(actual, maximum) + "%";
            text(mediaLabel, "手机媒体音量 · " + level);
            media.setContentDescription("手机媒体音量，" + level + "，第 " + actual + " 档，共 " + maximum + " 档");
            text(systemStatus, fixed ? "音量由当前输出设备控制，请使用设备上的音量键。"
                    : adjustmentMessage.isEmpty() ? "与手机实体音量键同步，也会影响其他应用的媒体音量。" : adjustmentMessage);
        } catch (RuntimeException unavailable) {
            media.setEnabled(false); text(mediaLabel, "手机媒体音量 · 暂不可用");
            text(systemStatus, "系统暂未允许读取音量，请使用手机或耳机的音量键。");
        }
    }
    private void setSystemVolume(int requested) {
        if (audio == null) return;
        int desired = VolumePolicy.streamIndex(requested, minimum, maximum);
        try {
            audio.setStreamVolume(AudioManager.STREAM_MUSIC, desired, 0);
            int accepted = audio.getStreamVolume(AudioManager.STREAM_MUSIC);
            adjustmentMessage = accepted == desired ? "" : "已按手机当前允许的音量设置，可用音量键查看系统提示。";
        } catch (RuntimeException rejected) {
            adjustmentMessage = "系统未允许这次调整，请使用手机或耳机的音量键。";
        }
        updateSystemVolume();
    }
    private void updateEffect() {
        AudioOutput.State state = settings.state();
        if (!draggingBoost) boost.setProgress(state.boostDb);
        String label = state.boostDb == 0 ? "关闭" : "+" + state.boostDb + " dB";
        text(boostLabel, "歌曲音量增强 · " + label); boost.setContentDescription("歌曲音量增强，" + label);
        switch (state.status) {
            case OFF: text(effectStatus, "增强已关闭，保持歌曲原始音量。"); break;
            case ACTIVE: text(effectStatus, "增强已生效，仅作用于海屿音乐的当前歌曲。"); break;
            case UNAVAILABLE: text(effectStatus, "当前设备或输出不支持增强，歌曲仍以原始音量播放。"); break;
            default: text(effectStatus, "已保存，歌曲开始播放后尝试应用增强。"); break;
        }
        effectStatus.setTextColor(state.status == AudioOutput.Status.UNAVAILABLE ? UiKit.WARM : UiKit.MUTED);
    }
    private static void text(TextView view, String value) {
        if (!value.contentEquals(view.getText())) view.setText(value);
    }
}
