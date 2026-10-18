package app.seashelf.music.ui;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.widget.ImageView;
import app.seashelf.music.SeaShelfApp;
import app.seashelf.music.api.SubsonicClient;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public final class CoverLoader {
    private static final LruCache<String, Bitmap> CACHE = new LruCache<String, Bitmap>(12 * 1024 * 1024) {
        @Override protected int sizeOf(String key, Bitmap bitmap) { return bitmap.getByteCount(); }
    };
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private CoverLoader() { }

    public static void load(ImageView view, SubsonicClient client, String coverId) {
        load(view, client, coverId, 160);
    }

    public static void load(ImageView view, SubsonicClient client, String coverId, int size) {
        view.setImageDrawable(null);
        view.setTag(null);
        if (client == null || coverId == null || coverId.trim().isEmpty()) return;
        String key = client.address().baseUrl() + "|" + coverId + "|" + size;
        view.setTag(key);
        Bitmap cached = CACHE.get(key);
        if (cached != null) {
            view.setImageBitmap(cached);
            return;
        }
        java.lang.ref.WeakReference<ImageView> target = new java.lang.ref.WeakReference<>(view);
        ((SeaShelfApp) view.getContext().getApplicationContext()).images().execute(() -> {
            Bitmap bitmap = CACHE.get(key);
            if (bitmap == null) bitmap = fetch(client.coverArtUrl(coverId, size));
            if (bitmap == null) return;
            CACHE.put(key, bitmap);
            Bitmap result = bitmap;
            MAIN.post(() -> {
                ImageView current = target.get();
                if (current != null && key.equals(current.getTag())) current.setImageBitmap(result);
            });
        });
    }

    private static Bitmap fetch(URL url) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(8000);
            connection.setReadTimeout(12000);
            connection.setInstanceFollowRedirects(false);
            if (connection.getResponseCode() != 200 || connection.getContentLengthLong() > 4 * 1024 * 1024) return null;
            try (InputStream input = connection.getInputStream()) {
                return BitmapFactory.decodeStream(input);
            }
        } catch (Exception ignored) {
            return null;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }
}
