package app.seashelf.music.ui;

import app.seashelf.music.api.ApiError;
import java.security.GeneralSecurityException;

public final class ConnectionErrors {
    private ConnectionErrors() { }

    public static String explain(Throwable error) {
        if (error instanceof ApiError) {
            ApiError api = (ApiError) error;
            if (api.code() == 40 || api.code() == 41) return "账号或密码不正确，请重新输入。";
            if (api.code() >= 500) return "电脑上的音乐服务暂时不可用，请确认 Navidrome 正在运行。";
            return "音乐服务返回错误：" + api.getMessage();
        }
        if (error instanceof IllegalArgumentException) return error.getMessage();
        if (error instanceof GeneralSecurityException) return "手机无法安全保存登录信息，请重试。";
        return "连接不到电脑。请检查地址、网络和电脑上的 Navidrome 服务。";
    }
}
