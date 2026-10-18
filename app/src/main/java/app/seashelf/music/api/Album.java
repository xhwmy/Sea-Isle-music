package app.seashelf.music.api;

public final class Album {
    private final String id;
    private final String name;
    private final String artist;
    private final String coverArtId;
    private final int songCount;

    public Album(String id, String name, String artist, String coverArtId, int songCount) {
        this.id = id;
        this.name = name;
        this.artist = artist;
        this.coverArtId = coverArtId;
        this.songCount = songCount;
    }

    public String id() { return id; }
    public String name() { return name; }
    public String artist() { return artist; }
    public String coverArtId() { return coverArtId; }
    public int songCount() { return songCount; }
}
