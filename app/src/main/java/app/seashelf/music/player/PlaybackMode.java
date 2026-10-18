package app.seashelf.music.player;

/** Completion policy; manual navigation always remains available. */
public enum PlaybackMode {
    SEQUENTIAL("顺序播放"), LIST_LOOP("列表循环"), REPEAT_ONE("单曲循环"),
    SHUFFLE("随机播放"), SINGLE_PLAY("单曲播放");

    private final String label;
    PlaybackMode(String label) { this.label = label; }
    public String label() { return label; }
}
