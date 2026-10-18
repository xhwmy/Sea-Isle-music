package app.seashelf.music.ui;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import app.seashelf.music.SeaShelfApp;
import app.seashelf.music.api.Song;
import app.seashelf.music.api.SubsonicClient;
import app.seashelf.music.player.PlayerService;
import java.util.List;
import java.util.Collections;

public final class AlbumActivity extends Activity {
    private LinearLayout songsView;
    private TextView status;
    private final RequestGeneration generation = new RequestGeneration();
    private List<Song> songs = Collections.emptyList();

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        SeaShelfApp app = (SeaShelfApp) getApplication();
        if (app.client() == null) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }
        String id = getIntent().getStringExtra("id");
        if (id == null || id.trim().isEmpty()) { finish(); return; }
        SubsonicClient client = app.client();
        LinearLayout root = UiKit.screen(this);
        Button back = UiKit.button(this, "‹  返回音乐库", false);
        root.addView(back, new LinearLayout.LayoutParams(UiKit.dp(this, 150), UiKit.dp(this, 46)));
        back.setOnClickListener(view -> finish());
        UiKit.gap(root, 20);
        ScrollView scroll = UiKit.scroll(this, root);
        LinearLayout content = UiKit.column(this);
        content.setPadding(0, 0, 0, UiKit.dp(this, 20));
        scroll.addView(content);
        ImageView art = UiKit.cover(this, UiKit.dp(this, 210));
        LinearLayout.LayoutParams artParams = new LinearLayout.LayoutParams(UiKit.dp(this, 210), UiKit.dp(this, 210));
        artParams.gravity = Gravity.CENTER_HORIZONTAL;
        content.addView(art, artParams);
        CoverLoader.load(art, client, getIntent().getStringExtra("cover"), 512);
        UiKit.gap(content, 26);
        content.addView(UiKit.text(this, "专辑", 12, UiKit.ACCENT, true));
        UiKit.gap(content, 8);
        content.addView(UiKit.text(this, value("title", "未命名专辑"), 29, UiKit.TEXT, true));
        UiKit.gap(content, 8);
        content.addView(UiKit.text(this, value("artist", "未知歌手"), 15, UiKit.MUTED, false));
        UiKit.gap(content, 27);
        content.addView(UiKit.section(this, "曲目", null));
        UiKit.gap(content, 8);
        status = UiKit.text(this, "正在载入曲目…", 13, UiKit.MUTED, false);
        content.addView(status);
        UiKit.gap(content, 14);
        songsView = UiKit.column(this);
        content.addView(songsView);
        root.addView(new MiniPlayer(this), new LinearLayout.LayoutParams(-1, UiKit.dp(this, 64)));
        load(id, client);
    }

    private String value(String key, String fallback) {
        String value = getIntent().getStringExtra(key);
        return value == null || value.trim().isEmpty() ? fallback : value;
    }

    private void load(String id, SubsonicClient client) {
        SeaShelfApp app = (SeaShelfApp) getApplication();
        long token = generation.next();
        status.setText("正在载入曲目…");
        app.network().execute(() -> {
            try {
                List<Song> loaded = client.album(id);
                runOnUiThread(() -> {
                    if (!generation.isCurrent(token) || isFinishing()) return;
                    songs = loaded;
                    songsView.removeAllViews();
                    status.setText(loaded.isEmpty() ? "这张专辑没有可播放的歌曲。" : loaded.size() + " 首歌曲");
                    for (int i = 0; i < loaded.size(); i++) {
                        LinearLayout row = UiKit.songRow(this, client, loaded.get(i), i);
                        songsView.addView(row, UiKit.cardSpacing(this));
                        final int position = i;
                        row.setOnClickListener(view -> {
                            PlayerService.playLibraryQueue(this, loaded, position);
                            startActivity(new Intent(this, PlayerActivity.class));
                        });
                    }
                });
            } catch (Exception failure) {
                runOnUiThread(() -> {
                    if (!generation.isCurrent(token) || isFinishing()) return;
                    status.setText(ConnectionErrors.explain(failure) + "  点击重试");
                    status.setOnClickListener(view -> load(id, client));
                });
            }
        });
    }

    @Override protected void onDestroy() {
        generation.next();
        super.onDestroy();
    }
}
