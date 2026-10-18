package app.seashelf.music.player;

/** Android uses discrete media-volume indices and millibels for LoudnessEnhancer. */
public final class VolumePolicy {
    public static final int MAX_BOOST_DB = 6;
    private VolumePolicy() { }
    public static int boostDb(int value) { return Math.max(0, Math.min(MAX_BOOST_DB, value)); }
    public static int boostMillibels(int value) { return boostDb(value) * 100; }
    public static int streamIndex(int value, int minimum, int maximum) {
        int upper = Math.max(0, maximum), lower = Math.max(0, Math.min(minimum, upper));
        return Math.max(lower, Math.min(upper, value));
    }
    public static int streamPercent(int index, int maximum) {
        return maximum <= 0 ? 0 : (int) Math.round(streamIndex(index, 0, maximum) * 100.0 / maximum);
    }
}
