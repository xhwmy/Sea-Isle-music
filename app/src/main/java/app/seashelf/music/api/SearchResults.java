package app.seashelf.music.api;

import java.util.List;
import java.util.ArrayList;
import java.util.Collections;

public final class SearchResults {
    private final List<Artist> artists;
    private final List<Album> albums;
    private final List<Song> songs;

    public SearchResults(List<Artist> artists, List<Album> albums, List<Song> songs) {
        this.artists = Collections.unmodifiableList(new ArrayList<>(artists));
        this.albums = Collections.unmodifiableList(new ArrayList<>(albums));
        this.songs = Collections.unmodifiableList(new ArrayList<>(songs));
    }

    public List<Artist> artists() { return artists; }
    public List<Album> albums() { return albums; }
    public List<Song> songs() { return songs; }
}
