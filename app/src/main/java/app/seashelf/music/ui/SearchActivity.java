package app.seashelf.music.ui;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import app.seashelf.music.SeaShelfApp;
import app.seashelf.music.api.Album;
import app.seashelf.music.api.Artist;
import app.seashelf.music.api.SearchResults;
import app.seashelf.music.api.Song;
import app.seashelf.music.api.SubsonicClient;
import app.seashelf.music.player.PlayerService;
import java.util.List;

public final class SearchActivity extends Activity {
    private final Handler main = new Handler(Looper.getMainLooper());
    private final RequestGeneration generation = new RequestGeneration();
    private Runnable pending;
    private TextView status;
    private LinearLayout results;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        SeaShelfApp app = (SeaShelfApp) getApplication();
        if (app.client() == null) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }
        LinearLayout root = UiKit.screen(this);
        UiKit.gap(root, 10);
        root.addView(UiKit.text(this, "在你的音乐里寻找", 28, UiKit.TEXT, true));
        UiKit.gap(root, 8);
        root.addView(UiKit.text(this, "搜歌曲、歌手或专辑", 14, UiKit.MUTED, false));
        UiKit.gap(root, 20);
        EditText query = UiKit.input(this, "想听什么？", InputType.TYPE_CLASS_TEXT);
        root.addView(query, new LinearLayout.LayoutParams(-1, UiKit.dp(this, 56)));
        UiKit.gap(root, 12);
        ScrollView scroll = UiKit.scroll(this, root);
        LinearLayout content = UiKit.column(this);
        content.setPadding(0, 0, 0, UiKit.dp(this, 20));
        scroll.addView(content);
        status = UiKit.text(this, "输入歌名或歌手，开始搜索。", 13, UiKit.MUTED, false);
        content.addView(status);
        UiKit.gap(content, 16);
        results = UiKit.column(this);
        content.addView(results);
        root.addView(new MiniPlayer(this), new LinearLayout.LayoutParams(-1, UiKit.dp(this, 64)));
        root.addView(UiKit.bottomNav(this, false, view -> {
            startActivity(new Intent(this, LibraryActivity.class));
            finish();
        }, view -> {}));
        query.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (pending != null) main.removeCallbacks(pending);
                long token = generation.next();
                String value = s.toString().trim();
                if (value.isEmpty()) {
                    results.removeAllViews();
                    status.setText("输入歌名或歌手，开始搜索。");
                } else {
                    pending = () -> search(value, token);
                    main.postDelayed(pending, 350);
                }
            }
            @Override public void afterTextChanged(Editable s) { }
        });
    }

    private void search(String query, long token) {
        SeaShelfApp app = (SeaShelfApp) getApplication();
        SubsonicClient client = app.client();
        status.setText("正在搜索…");
        app.network().execute(() -> {
            try {
                SearchResults found = client.search(query);
                runOnUiThread(() -> {
                    if (!generation.isCurrent(token) || isFinishing()) return;
                    render(found, client);
                });
            } catch (Exception failure) {
                runOnUiThread(() -> {
                    if (!generation.isCurrent(token) || isFinishing()) return;
                    results.removeAllViews();
                    status.setText(ConnectionErrors.explain(failure));
                });
            }
        });
    }

    private void render(SearchResults found, SubsonicClient client) {
        results.removeAllViews();
        int count = found.artists().size() + found.albums().size() + found.songs().size();
        status.setText(count == 0 ? "没有找到结果，试试别的歌名或歌手。" : "找到 " + count + " 个结果");
        if (!found.artists().isEmpty()) {
            results.addView(UiKit.section(this, "歌手", null));
            UiKit.gap(results, 12);
            for (Artist artist : found.artists()) {
                TextView row = UiKit.text(this, "♫   " + artist.name() + "    ›", 17, UiKit.TEXT, true);
                row.setBackground(UiKit.rounded(UiKit.SURFACE, 17, this));
                row.setPadding(UiKit.dp(this, 17), UiKit.dp(this, 17), UiKit.dp(this, 17), UiKit.dp(this, 17));
                results.addView(row, UiKit.cardSpacing(this));
                row.setOnClickListener(view -> openArtist(artist));
            }
            UiKit.gap(results, 14);
        }
        if (!found.albums().isEmpty()) {
            results.addView(UiKit.section(this, "专辑", null));
            UiKit.gap(results, 12);
            for (Album album : found.albums()) {
                LinearLayout row = UiKit.albumCard(this, client, album);
                results.addView(row, UiKit.cardSpacing(this));
                row.setOnClickListener(view -> openAlbum(album));
            }
            UiKit.gap(results, 14);
        }
        if (!found.songs().isEmpty()) {
            results.addView(UiKit.section(this, "歌曲", null));
            UiKit.gap(results, 12);
            List<Song> songs = found.songs();
            for (int i = 0; i < songs.size(); i++) {
                LinearLayout row = UiKit.songRow(this, client, songs.get(i), i);
                results.addView(row, UiKit.cardSpacing(this));
                final int position = i;
                row.setOnClickListener(view -> {
                    PlayerService.playQueue(this, songs, position);
                    startActivity(new Intent(this, PlayerActivity.class));
                });
            }
        }
    }

    private void openArtist(Artist artist) {
        SeaShelfApp app = (SeaShelfApp) getApplication();
        SubsonicClient client = app.client();
        long token = generation.next();
        status.setText("正在载入 " + artist.name() + " 的专辑…");
        results.removeAllViews();
        app.network().execute(() -> {
            try {
                List<Album> albums = client.artist(artist.id());
                runOnUiThread(() -> {
                    if (!generation.isCurrent(token) || isFinishing()) return;
                    status.setText(albums.isEmpty() ? "这位歌手暂无专辑。" : artist.name() + " 的专辑");
                    for (Album album : albums) {
                        LinearLayout row = UiKit.albumCard(this, client, album);
                        results.addView(row, UiKit.cardSpacing(this));
                        row.setOnClickListener(view -> openAlbum(album));
                    }
                });
            } catch (Exception failure) {
                runOnUiThread(() -> {
                    if (generation.isCurrent(token)) status.setText(ConnectionErrors.explain(failure));
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

    @Override protected void onDestroy() {
        generation.next();
        if (pending != null) main.removeCallbacks(pending);
        super.onDestroy();
    }
}
