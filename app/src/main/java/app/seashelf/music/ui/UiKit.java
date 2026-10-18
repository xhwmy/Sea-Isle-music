package app.seashelf.music.ui;

import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import app.seashelf.music.api.Album;
import app.seashelf.music.api.Song;
import app.seashelf.music.api.SubsonicClient;

/** The deep-sea visual system shared by the app screens. */
public final class UiKit {
    public static final int BACK = Color.rgb(11, 23, 41);
    public static final int SURFACE = Color.rgb(23, 42, 64);
    public static final int ELEVATED = Color.rgb(34, 57, 80);
    public static final int ACCENT = Color.rgb(116, 217, 208);
    public static final int TEXT = Color.rgb(246, 249, 251);
    public static final int MUTED = Color.rgb(160, 181, 198);
    public static final int WARM = Color.rgb(244, 185, 143);

    private UiKit() { }

    public static int dp(Context context, float value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    public static LinearLayout screen(Activity activity) {
        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[] {BACK, Color.rgb(12, 31, 49), BACK}));
        root.setPadding(dp(activity, 20), dp(activity, 12), dp(activity, 20), dp(activity, 12));
        activity.setContentView(root);
        return root;
    }

    public static ScrollView scroll(Context context, LinearLayout parent) {
        ScrollView scroll = new ScrollView(context);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        parent.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        return scroll;
    }

    public static LinearLayout column(Context context) {
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    public static TextView text(Context context, String value, float sp, int color, boolean bold) {
        TextView text = new TextView(context);
        text.setText(value);
        text.setTextColor(color);
        text.setTextSize(sp);
        text.setTypeface(Typeface.create(bold ? "sans-serif-medium" : "sans-serif", Typeface.NORMAL));
        text.setIncludeFontPadding(false);
        return text;
    }

    public static void gap(LinearLayout parent, int dp) {
        View space = new View(parent.getContext());
        parent.addView(space, new LinearLayout.LayoutParams(1, UiKit.dp(parent.getContext(), dp)));
    }

    public static GradientDrawable rounded(int color, float radius, Context context) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(color);
        shape.setCornerRadius(dp(context, radius));
        return shape;
    }

    public static Button button(Context context, String value, boolean primary) {
        Button button = new Button(context);
        button.setAllCaps(false);
        button.setText(value);
        button.setTextSize(15);
        button.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        button.setTextColor(primary ? BACK : TEXT);
        button.setBackground(rounded(primary ? ACCENT : ELEVATED, 17, context));
        button.setPadding(dp(context, 18), dp(context, 10), dp(context, 18), dp(context, 10));
        button.setMinHeight(dp(context, 52));
        return button;
    }

    public static EditText input(Context context, String hint, int type) {
        EditText input = new EditText(context);
        input.setTextColor(TEXT);
        input.setHintTextColor(MUTED);
        input.setTextSize(16);
        input.setHint(hint);
        input.setSingleLine(true);
        input.setInputType(type);
        input.setBackground(rounded(SURFACE, 16, context));
        input.setPadding(dp(context, 16), 0, dp(context, 16), 0);
        input.setMinHeight(dp(context, 56));
        return input;
    }

    public static TextView section(Context context, String title, String detail) {
        TextView text = text(context, title, 23, TEXT, true);
        text.setContentDescription(title + (detail == null ? "" : "，" + detail));
        return text;
    }

    public static LinearLayout albumCard(Context context, SubsonicClient client, Album album) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(context, 12), dp(context, 12), dp(context, 12), dp(context, 12));
        card.setBackground(rounded(SURFACE, 20, context));
        ImageView art = cover(context, dp(context, 72));
        card.addView(art, new LinearLayout.LayoutParams(dp(context, 72), dp(context, 72)));
        CoverLoader.load(art, client, album.coverArtId());
        LinearLayout labels = column(context);
        labels.setPadding(dp(context, 14), 0, 0, 0);
        card.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));
        TextView title = text(context, album.name().trim().isEmpty() ? "未命名专辑" : album.name(), 17, TEXT, true);
        title.setMaxLines(1);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        labels.addView(title);
        gap(labels, 5);
        labels.addView(text(context, album.artist().trim().isEmpty() ? "未知歌手" : album.artist(), 13, MUTED, false));
        TextView arrow = text(context, "›", 28, ACCENT, false);
        card.addView(arrow);
        return card;
    }

    public static LinearLayout songRow(Context context, SubsonicClient client, Song song, int position) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(context, 10), dp(context, 10), dp(context, 10), dp(context, 10));
        row.setBackground(rounded(SURFACE, 17, context));
        ImageView art = cover(context, dp(context, 52));
        row.addView(art, new LinearLayout.LayoutParams(dp(context, 52), dp(context, 52)));
        CoverLoader.load(art, client, song.coverArtId());
        LinearLayout labels = column(context);
        labels.setPadding(dp(context, 12), 0, dp(context, 4), 0);
        row.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));
        TextView title = text(context, song.title().trim().isEmpty() ? "未命名歌曲" : song.title(), 16, TEXT, true);
        title.setMaxLines(1);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        labels.addView(title);
        gap(labels, 4);
        labels.addView(text(context, song.artist().trim().isEmpty() ? "未知歌手" : song.artist(), 12, MUTED, false));
        TextView play = text(context, "▶", 18, ACCENT, true);
        row.addView(play);
        return row;
    }

    public static ImageView cover(Context context, int size) {
        ImageView image = new ImageView(context);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setBackground(rounded(ELEVATED, 13, context));
        image.setClipToOutline(true);
        image.setMinimumWidth(size);
        image.setMinimumHeight(size);
        image.setContentDescription("专辑封面");
        return image;
    }

    public static LinearLayout bottomNav(Context context, boolean librarySelected,
                                         View.OnClickListener library, View.OnClickListener search) {
        LinearLayout nav = new LinearLayout(context);
        nav.setGravity(Gravity.CENTER);
        nav.setPadding(0, dp(context, 8), 0, dp(context, 4));
        TextView home = text(context, "♫  音乐库", 15, librarySelected ? ACCENT : MUTED, true);
        home.setGravity(Gravity.CENTER);
        home.setPadding(0, dp(context, 10), 0, dp(context, 10));
        home.setOnClickListener(library);
        nav.addView(home, new LinearLayout.LayoutParams(0, dp(context, 46), 1));
        TextView find = text(context, "⌕  搜索", 15, librarySelected ? MUTED : ACCENT, true);
        find.setGravity(Gravity.CENTER);
        find.setPadding(0, dp(context, 10), 0, dp(context, 10));
        find.setOnClickListener(search);
        nav.addView(find, new LinearLayout.LayoutParams(0, dp(context, 46), 1));
        return nav;
    }

    public static LinearLayout.LayoutParams cardSpacing(Context context) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = dp(context, 10);
        return params;
    }
}
