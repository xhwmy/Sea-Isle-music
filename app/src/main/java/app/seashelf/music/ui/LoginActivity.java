package app.seashelf.music.ui;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.util.Log;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import app.seashelf.music.SeaShelfApp;
import app.seashelf.music.api.ServerAddress;
import app.seashelf.music.api.SubsonicClient;

public final class LoginActivity extends Activity {
    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SeaShelfApp app = (SeaShelfApp) getApplication();
        if (app.client() != null) {
            startActivity(new Intent(this, LibraryActivity.class));
            finish();
            return;
        }
        LinearLayout root = UiKit.screen(this);
        ScrollView scroll = UiKit.scroll(this, root);
        LinearLayout content = UiKit.column(this);
        content.setPadding(0, UiKit.dp(this, 56), 0, UiKit.dp(this, 20));
        scroll.addView(content);

        TextView mark = UiKit.text(this, "SEA / SHELF     •     私人音乐库", 12, UiKit.ACCENT, true);
        mark.setLetterSpacing(0.14f);
        content.addView(mark);
        UiKit.gap(content, 30);
        TextView title = UiKit.text(this, "把喜欢的歌，\n带到任何地方。", 34, UiKit.TEXT, true);
        title.setLineSpacing(UiKit.dp(this, 5), 1f);
        content.addView(title);
        UiKit.gap(content, 14);
        TextView sub = UiKit.text(this, "连接你电脑上的 Navidrome，打开自己的音乐世界。", 15, UiKit.MUTED, false);
        sub.setLineSpacing(UiKit.dp(this, 4), 1f);
        content.addView(sub);
        UiKit.gap(content, 42);

        content.addView(UiKit.text(this, "服务器地址", 13, UiKit.MUTED, true));
        UiKit.gap(content, 10);
        EditText address = UiKit.input(this, "https://你的音乐地址.ts.net", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        content.addView(address, new LinearLayout.LayoutParams(-1, UiKit.dp(this, 56)));
        UiKit.gap(content, 20);
        content.addView(UiKit.text(this, "用户名", 13, UiKit.MUTED, true));
        UiKit.gap(content, 10);
        EditText username = UiKit.input(this, "Navidrome 用户名", InputType.TYPE_CLASS_TEXT);
        content.addView(username, new LinearLayout.LayoutParams(-1, UiKit.dp(this, 56)));
        UiKit.gap(content, 20);
        content.addView(UiKit.text(this, "密码", 13, UiKit.MUTED, true));
        UiKit.gap(content, 10);
        EditText password = UiKit.input(this, "Navidrome 密码", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        content.addView(password, new LinearLayout.LayoutParams(-1, UiKit.dp(this, 56)));
        UiKit.gap(content, 22);
        TextView error = UiKit.text(this, "", 13, UiKit.WARM, false);
        error.setMinHeight(UiKit.dp(this, 32));
        content.addView(error);
        Button connect = UiKit.button(this, "连接音乐库", true);
        content.addView(connect, new LinearLayout.LayoutParams(-1, UiKit.dp(this, 56)));
        UiKit.gap(content, 22);
        TextView tip = UiKit.text(this, "在公司网络内可输入电脑地址；外出时请输入电脑端配置好的 HTTPS 音乐地址。", 12, UiKit.MUTED, false);
        tip.setLineSpacing(UiKit.dp(this, 3), 1f);
        content.addView(tip);

        connect.setOnClickListener(view -> {
            String rawAddress = address.getText().toString().trim();
            String user = username.getText().toString().trim();
            String secret = password.getText().toString();
            ServerAddress parsed;
            try {
                parsed = ServerAddress.parse(rawAddress);
                if (user.isEmpty() || secret.isEmpty()) throw new IllegalArgumentException("请输入用户名和密码。");
            } catch (Exception invalid) {
                error.setText(ConnectionErrors.explain(invalid));
                return;
            }
            connect.setEnabled(false);
            connect.setText("正在连接…");
            error.setText("");
            SubsonicClient candidate = new SubsonicClient(parsed, user, secret);
            app.network().execute(() -> {
                try {
                    candidate.ping();
                    app.credentials().save(rawAddress, user, secret);
                    runOnUiThread(() -> {
                        app.setClient(candidate);
                        startActivity(new Intent(this, LibraryActivity.class));
                        finish();
                    });
                } catch (Exception failure) {
                    Log.e("SeaShelfLogin", "Navidrome connection failed", failure);
                    runOnUiThread(() -> {
                        connect.setEnabled(true);
                        connect.setText("连接音乐库");
                        error.setText(ConnectionErrors.explain(failure));
                    });
                }
            });
        });
    }
}
