package app.seashelf.music.player;

import app.seashelf.music.api.Lyrics;
import java.util.Collections;

/** Immutable and credential-free; shared by the main page and floating player. */
public final class LyricState {
    public enum Status { EMPTY, LOADING, READY, MISSING, ERROR }
    public final String songId;
    public final Status status;
    public final Lyrics lyrics;

    LyricState(String songId, Status status, Lyrics lyrics) {
        this.songId = songId; this.status = status;
        this.lyrics = lyrics == null ? new Lyrics(false, Collections.emptyList()) : lyrics;
    }
    public static LyricState empty() { return new LyricState("", Status.EMPTY, null); }
    public String message() {
        switch (status) {
            case LOADING: return "正在读取歌词…";
            case MISSING: return "这首歌暂无歌词";
            case ERROR: return "歌词暂时无法加载，点重新读取重试";
            case READY: return lyrics.synced() ? "动态歌词 · 点击歌词可定位" : "静态歌词 · 无逐句时间";
            default: return "选择一首歌开始播放";
        }
    }
}
