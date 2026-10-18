package app.seashelf.music.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import app.seashelf.music.SeaShelfApp;
import app.seashelf.music.api.Album;
import app.seashelf.music.api.SubsonicClient;
import app.seashelf.music.player.PlayerService;
import java.util.List;

public final class LibraryActivity extends Activity {
    private LinearLayout list;
    private TextView status;
    private final RequestGeneration generation = new RequestGeneration();

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        SeaShelfApp app = (SeaShelfApp) getApplication();
        if (app.client() == null) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }
        LinearLayout root = UiKit.screen(this);
        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(top);
        LinearLayout brand = UiKit.column(this);
        top.addView(brand, new LinearLayout.LayoutParams(0, -2, 1));
        TextView eyebrow = UiKit.text(this, "SEA / SHELF", 11, UiKit.ACCENT, true);
        eyebrow.setLetterSpacing(0.18f);
        brand.addView(eyebrow);
        UiKit.gap(brand, 5);
        brand.addView(UiKit.text(this, "海屿音乐", 29, UiKit.TEXT, true));
        Button settings = UiKit.button(this, "设置", false);
        top.addView(settings, new LinearLayout.LayoutParams(UiKit.dp(this, 76), UiKit.dp(this, 46)));
        settings.setOnClickListener(view -> settingsDialog());
        UiKit.gap(root, 22);

        ScrollView scroll = UiKit.scroll(this, root);
        LinearLayout content = UiKit.column(this);
        content.setPadding(0, 0, 0, UiKit.dp(this, 20));
        scroll.addView(content);
        TextView hero = UiKit.text(this, "你的歌，\n此刻在这里。", 32, UiKit.TEXT, true);
        hero.setLineSpacing(UiKit.dp(this, 4), 1f);
        content.addView(hero);
        UiKit.gap(content, 9);
        content.addView(UiKit.text(this, "从电脑上的私藏乐库继续听", 14, UiKit.MUTED, false));
        UiKit.gap(content, 30);
        content.addView(UiKit.section(this, "最近加入", null));
        UiKit.gap(content, 8);
        status = UiKit.text(this, "正在连接音乐库…", 13, UiKit.MUTED, false);
        content.addView(status);
        UiKit.gap(content, 16);
        list = UiKit.column(this);
        content.addView(list);

        root.addView(new MiniPlayer(this), new LinearLayout.LayoutParams(-1, UiKit.dp(this, 64)));
        root.addView(UiKit.bottomNav(this, true, view -> {}, view -> {
            startActivity(new Intent(this, SearchActivity.class));
            finish();
        }));
        loadAlbums();
    }

    private void loadAlbums() {
        SeaShelfApp app = (SeaShelfApp) getApplication();
        SubsonicClient client = app.client();
        long token = generation.next();
        status.setText("正在连接音乐库…");
        app.network().execute(() -> {
            try {
                List<Album> albums = client.albums();
                runOnUiThread(() -> {
                    if (!generation.isCurrent(token) || isFinishing()) return;
                    list.removeAllViews();
                    status.setText(albums.isEmpty() ? "音乐库还没有专辑。请确认电脑端已扫描歌曲。" : "共显示 " + albums.size() + " 张专辑");
                    for (Album album : albums) {
                        LinearLayout card = UiKit.albumCard(this, client, album);
                        list.addView(card, UiKit.cardSpacing(this));
                        card.setOnClickListener(view -> openAlbum(album));
                    }
                });
            } catch (Exception failure) {
                runOnUiThread(() -> {
                    if (!generation.isCurrent(token) || isFinishing()) return;
                    status.setText(ConnectionErrors.explain(failure) + "  点击重试");
                    status.setOnClickListener(view -> loadAlbums());
                });
            }
        });
    }

    private void openAlbum(Album album) {
        Intent intent = new Intent(this, AlbumActivity.class);
        intent.putExtra("id", album.id());
        intent.putExtra("title", album.name());
        intent.putExtra("artist", album.artist());
        intent.putExtra("cover", album.coverArtId());
        startActivity(intent);
    }

    private void settingsDialog() {
        new AlertDialog.Builder(this)
                .setTitle("音乐库设置")
                .setMessage("当前地址：" + ((SeaShelfApp) getApplication()).client().address().baseUrl())
                .setPositiveButton("切换服务器", (dialog, which) -> {
                    ((SeaShelfApp) getApplication()).signOut();
                    startActivity(new Intent(this, LoginActivity.class));
                    finish();
                })
                .setNegativeButton("继续听歌", null)
                .show();
    }

    @Override protected void onDestroy() {
        generation.next();
        super.onDestroy();
    }
}
