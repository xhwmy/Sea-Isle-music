package app.seashelf.music.player;

import android.annotation.SuppressLint;
import androidx.media3.extractor.DefaultExtractorsFactory;
import androidx.media3.extractor.mp3.Mp3Extractor;

/** Navidrome's cold CBR MP3 transcode has no Content-Length or Xing seek table. */
@SuppressLint("UnsafeOptInUsageError")
public final class StreamExtractors {
    private StreamExtractors() { }
    public static DefaultExtractorsFactory create(boolean original) {
        return new DefaultExtractorsFactory().setMp3ExtractorFlags(original
                ? Mp3Extractor.FLAG_ENABLE_INDEX_SEEKING
                : Mp3Extractor.FLAG_ENABLE_CONSTANT_BITRATE_SEEKING_ALWAYS);
    }
}
