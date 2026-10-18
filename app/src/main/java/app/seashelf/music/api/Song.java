package app.seashelf.music.api;

public final class Song implements java.io.Serializable {
    private static final long serialVersionUID = 1L;
    private final String id;
    private final String title;
    private final String artist;
    private final String album;
    private final String coverArtId;
    private final int durationSeconds;

    public Song(String id, String title, String artist, String album, String coverArtId, int durationSeconds) {
        this.id = id;
        this.title = title;
        this.artist = artist;
        this.album = album;
        this.coverArtId = coverArtId;
        this.durationSeconds = durationSeconds;
    }

    public String id() { return id; }
    public String title() { return title; }
    public String artist() { return artist; }
    public String album() { return album; }
    public String coverArtId() { return coverArtId; }
    public int durationSeconds() { return durationSeconds; }
}
