package app.seashelf.music.player;

import android.app.KeyguardManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.ScrollView;
import android.widget.Toast;
import app.seashelf.music.api.Lyrics;
import app.seashelf.music.ui.PlayerActivity;
import app.seashelf.music.ui.UiKit;
import java.util.function.BooleanSupplier;

/** Main-thread window owned by the existing music service, never a separate perpetual service. */
final class FloatingPlayerWindow {
    private final Context context;
    private final BooleanSupplier validOwner;
    private final WindowManager windows;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final FloatingWindowPolicy policy = new FloatingWindowPolicy();
    private final SharedPreferences preferences;
    private LinearLayout view;
    private TextView title, current, next;
    private Button toggle;
    private WindowManager.LayoutParams layout;
    private int appliedX = Integer.MIN_VALUE, appliedY, appliedWidth, appliedHeight;
    private boolean destroyed, screenOff;
    private final BroadcastReceiver visibilityReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context ignored, Intent intent) {
            if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) { screenOff = true; detach(); }
            if (Intent.ACTION_SCREEN_ON.equals(intent.getAction()) || Intent.ACTION_USER_PRESENT.equals(intent.getAction())) screenOff = false;
            main.removeCallbacks(refresh); main.post(refresh);
        }
    };
    FloatingPlayerWindow(Context context, BooleanSupplier validOwner) {
        this.context = context; this.validOwner = validOwner;
        windows = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        preferences = context.getSharedPreferences("floating_position", Context.MODE_PRIVATE);
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_OFF); filter.addAction(Intent.ACTION_USER_PRESENT);
        filter.addAction(Intent.ACTION_SCREEN_ON); filter.addAction(Intent.ACTION_CONFIGURATION_CHANGED);
        if (Build.VERSION.SDK_INT >= 33) context.registerReceiver(visibilityReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        else context.registerReceiver(visibilityReceiver, filter);
    }
    void show() {
        main.post(() -> {
            if (destroyed || !validOwner.getAsBoolean()) return;
            FloatingWindowPolicy.Request request = policy.request(Settings.canDrawOverlays(context), PlayerService.snapshot().song != null);
            if (request != FloatingWindowPolicy.Request.OPEN) {
                Toast.makeText(context, request == FloatingWindowPolicy.Request.NO_SONG ? "请先选择一首歌曲" : "请在播放页开启悬浮窗权限", Toast.LENGTH_SHORT).show();
                return;
            }
            main.removeCallbacks(refresh); refresh.run();
        });
    }
    void close() { policy.close(); main.removeCallbacks(refresh); detach(); }
    void destroy() {
        destroyed = true; close(); main.removeCallbacksAndMessages(null);
        context.unregisterReceiver(visibilityReceiver);
    }
    private final Runnable refresh = new Runnable() {
        @Override public void run() {
            if (destroyed) return;
            if (!validOwner.getAsBoolean()) { close(); return; }
            PlayerService.Snapshot state = PlayerService.snapshot();
            boolean visible = policy.visible(Settings.canDrawOverlays(context), unlocked(), state.song != null);
            if (!visible) detach();
            else {
                if (view == null && !attach()) return;
                bound(); if (view != null) render(state);
            }
            if (policy.enabled()) main.postDelayed(this, 250);
        }
    };
    private boolean unlocked() {
        PowerManager power = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        KeyguardManager keyguard = (KeyguardManager) context.getSystemService(Context.KEYGUARD_SERVICE);
        return !screenOff && power.isInteractive() && !keyguard.isKeyguardLocked();
    }
    private boolean attach() {
        LinearLayout root = UiKit.column(context);
        root.setPadding(UiKit.dp(context, 10), UiKit.dp(context, 4), UiKit.dp(context, 10), UiKit.dp(context, 8));
        root.setBackground(UiKit.rounded(UiKit.SURFACE, 20, context));
        root.setElevation(UiKit.dp(context, 10));
        LinearLayout header = new LinearLayout(context); header.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(header, new LinearLayout.LayoutParams(-1, UiKit.dp(context, 48)));
        title = UiKit.text(context, "海屿音乐 · 拖动移动", 12, UiKit.MUTED, true);
        title.setSingleLine(true); title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        title.setGravity(Gravity.CENTER_VERTICAL); title.setContentDescription("拖动移动悬浮窗");
        header.addView(title, new LinearLayout.LayoutParams(0, -1, 1));
        Button back = button("↗", "返回完整播放页", false);
        header.addView(back, new LinearLayout.LayoutParams(UiKit.dp(context, 48), -1));
        back.setOnClickListener(ignored -> {
            try { context.startActivity(new Intent(context, PlayerActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)); }
            catch (RuntimeException unavailable) { Toast.makeText(context, "请从桌面打开海屿音乐", Toast.LENGTH_SHORT).show(); }
            close();
        });
        Button close = button("×", "关闭悬浮窗，继续播放音乐", false);
        header.addView(close, new LinearLayout.LayoutParams(UiKit.dp(context, 48), -1));
        close.setOnClickListener(ignored -> close());
        ScrollView lyricArea = new ScrollView(context); lyricArea.setFillViewport(true);
        root.addView(lyricArea, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout lyricContent = UiKit.column(context); lyricContent.setGravity(Gravity.CENTER);
        lyricArea.addView(lyricContent);
        current = UiKit.text(context, "", 18, UiKit.ACCENT, true);
        current.setGravity(Gravity.CENTER); current.setMaxLines(3); current.setMinHeight(UiKit.dp(context, 56));
        current.setPadding(0, UiKit.dp(context, 5), 0, UiKit.dp(context, 5));
        lyricContent.addView(current, new LinearLayout.LayoutParams(-1, -2));
        next = UiKit.text(context, "", 13, UiKit.MUTED, false);
        next.setGravity(Gravity.CENTER); next.setMaxLines(2); next.setMinHeight(UiKit.dp(context, 38));
        lyricContent.addView(next, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout controls = new LinearLayout(context); controls.setGravity(Gravity.CENTER);
        root.addView(controls, new LinearLayout.LayoutParams(-1, UiKit.dp(context, 48)));
        addTransport(controls, "|◀", "上一首", PlayerService.ACTION_PREVIOUS, false);
        toggle = addTransport(controls, "▶", "播放", PlayerService.ACTION_TOGGLE, true);
        addTransport(controls, "▶|", "下一首", PlayerService.ACTION_NEXT, false);
        int screenWidth = screen()[0];
        layout = new WindowManager.LayoutParams(Math.min(UiKit.dp(context, 336), Math.max(1, screenWidth - UiKit.dp(context, 16))),
                Math.min(UiKit.dp(context, 240), Math.max(1, screen()[1] - UiKit.dp(context, 48))), WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT);
        layout.gravity = Gravity.TOP | Gravity.START;
        layout.x = preferences.getInt("x", UiKit.dp(context, 8)); layout.y = preferences.getInt("y", UiKit.dp(context, 100));
        drag(title);
        view = root;
        try { windows.addView(root, layout); }
        catch (RuntimeException denied) {
            view = null; policy.close(); main.removeCallbacks(refresh);
            Toast.makeText(context, "悬浮窗未能打开，请检查显示在其他应用上层的权限", Toast.LENGTH_LONG).show(); return false;
        }
        root.post(() -> { if (view == root && !destroyed) bound(); });
        return true;
    }
    private Button addTransport(LinearLayout parent, String label, String description, String action, boolean primary) {
        Button button = button(label, description, primary);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -1, 1);
        params.leftMargin = UiKit.dp(context, 3); params.rightMargin = UiKit.dp(context, 3);
        parent.addView(button, params);
        button.setOnClickListener(ignored -> {
            if (validOwner.getAsBoolean()) PlayerService.command(context, action); else close();
        });
        return button;
    }
    private Button button(String label, String description, boolean primary) {
        Button button = UiKit.button(context, label, primary);
        button.setPadding(0, 0, 0, 0); button.setMinWidth(0); button.setMinimumWidth(0);
        button.setMinHeight(UiKit.dp(context, 48)); button.setMinimumHeight(UiKit.dp(context, 48));
        button.setContentDescription(description); return button;
    }
    private void render(PlayerService.Snapshot state) {
        setText(title, state.song.title() + " · " + state.song.artist());
        setText(toggle, state.playRequested ? "Ⅱ" : "▶");
        toggle.setContentDescription(state.playRequested ? "暂停" : "播放");
        LyricState lyric = state.lyrics;
        if (lyric.status != LyricState.Status.READY) {
            setText(current, lyric.message()); setText(next, state.statusText()); return;
        }
        Lyrics value = lyric.lyrics;
        if (!value.synced()) {
            setText(current, "静态歌词 · 返回播放页查看"); setText(next, state.statusText()); return;
        }
        int index = value.activeLine(state.positionMs);
        setText(current, index < 0 ? "歌词即将开始" : value.lines().get(index).text());
        setText(next, index + 1 < value.lines().size() ? value.lines().get(index + 1).text() : "歌曲即将结束");
    }
    private static void setText(TextView view, String text) { if (!text.contentEquals(view.getText())) view.setText(text); }
    private void drag(View handle) {
        handle.setOnTouchListener(new View.OnTouchListener() {
            float startX, startY; int initialX, initialY; boolean moved;
            @Override public boolean onTouch(View ignored, MotionEvent event) {
                if (layout == null || view == null) return false;
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        startX = event.getRawX(); startY = event.getRawY(); initialX = layout.x; initialY = layout.y; moved = false; return true;
                    case MotionEvent.ACTION_MOVE:
                        if (Math.abs(event.getRawX() - startX) + Math.abs(event.getRawY() - startY) > ViewConfiguration.get(context).getScaledTouchSlop()) moved = true;
                        if (moved) { layout.x = initialX + Math.round(event.getRawX() - startX); layout.y = initialY + Math.round(event.getRawY() - startY); bound(); }
                        return true;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        if (!moved) handle.performClick();
                        preferences.edit().putInt("x", layout.x).putInt("y", layout.y).apply(); return true;
                    default: return false;
                }
            }
        });
    }
    private int[] screen() {
        if (Build.VERSION.SDK_INT >= 30) {
            android.graphics.Rect bounds = windows.getCurrentWindowMetrics().getBounds();
            return new int[] {bounds.width(), bounds.height()};
        }
        DisplayMetrics metrics = new DisplayMetrics(); windows.getDefaultDisplay().getRealMetrics(metrics);
        return new int[] {metrics.widthPixels, metrics.heightPixels};
    }
    private void bound() {
        if (view == null || layout == null) return;
        int[] screen = screen();
        int width = Math.min(UiKit.dp(context, 336), Math.max(1, screen[0] - UiKit.dp(context, 16)));
        int height = Math.min(UiKit.dp(context, 240), Math.max(1, screen[1] - UiKit.dp(context, 48)));
        FloatingBounds.Position position = FloatingBounds.clamp(layout.x, layout.y, width, height, screen[0], screen[1], UiKit.dp(context, 24));
        layout.width = width; layout.height = height;
        layout.x = position.x; layout.y = position.y;
        if (appliedWidth == width && appliedHeight == height && appliedX == position.x && appliedY == position.y) return;
        try {
            windows.updateViewLayout(view, layout);
            appliedX = layout.x; appliedY = layout.y; appliedWidth = width; appliedHeight = height;
        }
        catch (RuntimeException revoked) { policy.close(); detach(); }
    }
    private void detach() {
        LinearLayout previous = view; view = null;
        if (previous != null) {
            try { windows.removeViewImmediate(previous); } catch (RuntimeException ignored) { }
        }
        title = null; current = null; next = null; toggle = null; layout = null;
        appliedX = Integer.MIN_VALUE;
    }
}
