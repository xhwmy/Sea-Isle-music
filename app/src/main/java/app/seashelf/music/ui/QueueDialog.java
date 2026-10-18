package app.seashelf.music.ui;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.TextView;
import app.seashelf.music.api.Song;
import app.seashelf.music.player.PlayerService;
import java.util.Collections;
import java.util.List;

/** Native bottom queue with recycled rows; no per-refresh artwork or list reconstruction. */
public final class QueueDialog extends Dialog {
    private final Activity activity;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final QueueAdapter adapter = new QueueAdapter();
    private TextView title, subtitle, loadState;
    private ListView list;
    private String scrolledSong;
    private boolean firstRefresh = true;

    public QueueDialog(Activity activity) { super(activity); this.activity = activity; }
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout root = UiKit.column(activity);
        root.setPadding(UiKit.dp(activity, 16), UiKit.dp(activity, 12), UiKit.dp(activity, 16), UiKit.dp(activity, 12));
        root.setBackground(UiKit.rounded(UiKit.SURFACE, 24, activity));
        LinearLayout header = new LinearLayout(activity); header.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(header, new LinearLayout.LayoutParams(-1, UiKit.dp(activity, 48)));
        title = UiKit.text(activity, "播放队列", 20, UiKit.TEXT, true);
        header.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        Button close = UiKit.button(activity, "收起", false); close.setPadding(0, 0, 0, 0);
        close.setContentDescription("收起播放队列");
        header.addView(close, new LinearLayout.LayoutParams(UiKit.dp(activity, 64), UiKit.dp(activity, 48)));
        close.setOnClickListener(view -> dismiss());
        subtitle = UiKit.text(activity, "", 12, UiKit.MUTED, false);
        subtitle.setPadding(0, UiKit.dp(activity, 6), 0, UiKit.dp(activity, 12));
        root.addView(subtitle, new LinearLayout.LayoutParams(-1, -2));
        loadState = UiKit.text(activity, "", 12, UiKit.WARM, false);
        loadState.setGravity(Gravity.CENTER_VERTICAL); loadState.setMinHeight(UiKit.dp(activity, 48));
        loadState.setOnClickListener(view -> {
            if (!PlayerService.snapshot().queueError.isEmpty()) PlayerService.command(activity, PlayerService.ACTION_RELOAD_QUEUE);
        });
        root.addView(loadState, new LinearLayout.LayoutParams(-1, -2));
        list = new ListView(activity); list.setDivider(null); list.setDividerHeight(UiKit.dp(activity, 4));
        list.setAdapter(adapter); list.setChoiceMode(ListView.CHOICE_MODE_NONE);
        root.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));
        TextView empty = UiKit.text(activity, "队列已清空，去音乐库选择歌曲。", 15, UiKit.MUTED, false);
        empty.setGravity(Gravity.CENTER); root.addView(empty, new LinearLayout.LayoutParams(-1, 0, 1));
        list.setEmptyView(empty);
        setContentView(root);
    }
    @Override protected void onStart() {
        super.onStart();
        Window window = getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setGravity(Gravity.BOTTOM);
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT,
                    (int) (activity.getResources().getDisplayMetrics().heightPixels * 0.72));
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            WindowManager.LayoutParams params = window.getAttributes(); params.dimAmount = 0.45f; window.setAttributes(params);
        }
        refresh();
    }
    @Override protected void onStop() { handler.removeCallbacksAndMessages(null); super.onStop(); }
    private void refresh() {
        PlayerService.Snapshot state = PlayerService.snapshot();
        setText(title, "播放队列 · " + state.queueSize + " 首");
        setText(subtitle, state.source + " · " + state.mode.label() + (state.song == null ? "" : " · 当前 " + state.queuePosition + " / " + state.queueSize));
        String loading = state.queueLoading ? "正在扩展音乐库队列…" : state.queueError;
        setText(loadState, loading);
        int visibility = loading.isEmpty() ? View.GONE : View.VISIBLE;
        if (loadState.getVisibility() != visibility) loadState.setVisibility(visibility);
        loadState.setEnabled(!state.queueError.isEmpty());
        adapter.update(state);
        String id = state.song == null ? null : state.song.id();
        if (id != null && (firstRefresh || !id.equals(scrolledSong))) {
            list.setSelection(Math.max(0, state.queuePosition - 2)); scrolledSong = id;
        }
        firstRefresh = false;
        handler.postDelayed(this::refresh, 400);
    }
    private static void setText(TextView view, String text) { if (!text.contentEquals(view.getText())) view.setText(text); }

    private final class QueueAdapter extends BaseAdapter {
        private List<Song> songs = Collections.emptyList();
        private String currentId;
        void update(PlayerService.Snapshot state) {
            String selected = state.song == null ? null : state.song.id();
            if (songs == state.queue && java.util.Objects.equals(selected, currentId)) return;
            songs = state.queue; currentId = selected; notifyDataSetChanged();
        }
        @Override public int getCount() { return songs.size(); }
        @Override public Song getItem(int position) { return songs.get(position); }
        @Override public long getItemId(int position) { return position; }
        @Override public View getView(int position, View recycled, ViewGroup parent) {
            Holder holder;
            if (recycled == null) {
                LinearLayout row = new LinearLayout(activity); row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(UiKit.dp(activity, 8), UiKit.dp(activity, 8), 0, UiKit.dp(activity, 8));
                row.setMinimumHeight(UiKit.dp(activity, 72));
                holder = new Holder();
                holder.number = UiKit.text(activity, "", 12, UiKit.MUTED, false);
                holder.number.setGravity(Gravity.CENTER);
                row.addView(holder.number, new LinearLayout.LayoutParams(UiKit.dp(activity, 32), -2));
                LinearLayout labels = UiKit.column(activity);
                holder.title = UiKit.text(activity, "", 15, UiKit.TEXT, true);
                holder.title.setSingleLine(true); holder.title.setEllipsize(android.text.TextUtils.TruncateAt.END);
                holder.artist = UiKit.text(activity, "", 12, UiKit.MUTED, false);
                holder.artist.setSingleLine(true); holder.artist.setEllipsize(android.text.TextUtils.TruncateAt.END);
                labels.addView(holder.title); UiKit.gap(labels, 5); labels.addView(holder.artist);
                row.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));
                holder.menu = UiKit.text(activity, "⋮", 25, UiKit.MUTED, true); holder.menu.setGravity(Gravity.CENTER);
                row.addView(holder.menu, new LinearLayout.LayoutParams(UiKit.dp(activity, 48), UiKit.dp(activity, 48)));
                row.setTag(holder); recycled = row;
            } else holder = (Holder) recycled.getTag();
            Song song = getItem(position); boolean selected = song.id().equals(currentId);
            setText(holder.number, selected ? "♫" : Integer.toString(position + 1));
            holder.number.setTextColor(selected ? UiKit.ACCENT : UiKit.MUTED);
            setText(holder.title, song.title()); holder.title.setTextColor(selected ? UiKit.ACCENT : UiKit.TEXT);
            setText(holder.artist, song.artist());
            recycled.setBackground(UiKit.rounded(selected ? UiKit.ELEVATED : UiKit.SURFACE, 14, activity));
            recycled.setContentDescription((selected ? "当前歌曲，" : "") + song.title() + "，" + song.artist());
            recycled.setOnClickListener(view -> PlayerService.queueAction(activity, PlayerService.ACTION_SELECT, song.id()));
            holder.menu.setContentDescription(song.title() + "，队列操作");
            holder.menu.setOnClickListener(view -> {
                PopupMenu menu = new PopupMenu(activity, view);
                menu.getMenu().add(0, 1, 0, "下一首播放").setEnabled(!song.id().equals(currentId));
                menu.getMenu().add(0, 2, 1, "移出队列");
                menu.setOnMenuItemClickListener(item -> {
                    PlayerService.queueAction(activity, item.getItemId() == 1 ? PlayerService.ACTION_PLAY_NEXT : PlayerService.ACTION_REMOVE, song.id());
                    return true;
                });
                menu.show();
            });
            return recycled;
        }
    }
    private static final class Holder { TextView number, title, artist, menu; }
}
