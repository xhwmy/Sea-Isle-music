package app.seashelf.music.ui;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import app.seashelf.music.SeaShelfApp;
import app.seashelf.music.player.PlayerService;

/** Compact playback control shared by browsing and search screens. */
public final class MiniPlayer extends LinearLayout {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final TextView title;
    private final TextView subtitle;
    private final TextView toggle;
    private final ImageView cover;
    private String displayedSongId;

    public MiniPlayer(Context context) {
        super(context);
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        setPadding(UiKit.dp(context, 10), UiKit.dp(context, 8), UiKit.dp(context, 10), UiKit.dp(context, 8));
        setBackground(UiKit.rounded(UiKit.ELEVATED, 19, context));
        cover = UiKit.cover(context, UiKit.dp(context, 44));
        addView(cover, new LayoutParams(UiKit.dp(context, 44), UiKit.dp(context, 44)));
        LinearLayout labels = UiKit.column(context);
        labels.setPadding(UiKit.dp(context, 12), 0, 0, 0);
        addView(labels, new LayoutParams(0, -2, 1));
        title = UiKit.text(context, "", 14, UiKit.TEXT, true);
        title.setSingleLine(true);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        labels.addView(title);
        UiKit.gap(labels, 4);
        subtitle = UiKit.text(context, "", 11, UiKit.MUTED, false);
        labels.addView(subtitle);
        toggle = UiKit.text(context, "▶", 22, UiKit.ACCENT, true);
        toggle.setGravity(Gravity.CENTER);
        addView(toggle, new LayoutParams(UiKit.dp(context, 48), UiKit.dp(context, 48)));
        toggle.setOnClickListener(view -> PlayerService.command(getContext(), PlayerService.ACTION_TOGGLE));
        toggle.setContentDescription("播放");
        setOnClickListener(view -> getContext().startActivity(new Intent(getContext(), PlayerActivity.class)));
        setVisibility(GONE);
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        // Any browsing entry can be recreated directly after process death, including search and albums.
        if (((SeaShelfApp) getContext().getApplicationContext()).client() != null) PlayerService.restore(getContext());
        refresh();
    }

    @Override protected void onDetachedFromWindow() {
        handler.removeCallbacksAndMessages(null);
        super.onDetachedFromWindow();
    }

    private void refresh() {
        PlayerService.Snapshot state = PlayerService.snapshot();
        if (state.song == null) {
            setVisibility(GONE);
            displayedSongId = null;
        } else {
            setVisibility(VISIBLE);
            text(title, state.song.title());
            text(subtitle, state.playing && !state.buffering ? state.song.artist() : state.statusText());
            text(toggle, state.playRequested ? "Ⅱ" : "▶");
            String description = state.playRequested ? "暂停" : "播放";
            if (toggle.getContentDescription() == null || !description.contentEquals(toggle.getContentDescription()))
                toggle.setContentDescription(description);
            if (!state.song.id().equals(displayedSongId)) {
                displayedSongId = state.song.id();
                CoverLoader.load(cover, ((SeaShelfApp) getContext().getApplicationContext()).client(), state.song.coverArtId());
            }
        }
        handler.postDelayed(this::refresh, 800);
    }
    private static void text(TextView view, String value) { if (!value.contentEquals(view.getText())) view.setText(value); }
}
