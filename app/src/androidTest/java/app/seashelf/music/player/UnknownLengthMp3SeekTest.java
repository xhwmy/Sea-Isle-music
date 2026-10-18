package app.seashelf.music.player;

import android.annotation.SuppressLint;
import android.net.Uri;
import androidx.media3.common.C;
import androidx.media3.datasource.ByteArrayDataSource;
import androidx.media3.datasource.DataSpec;
import androidx.media3.extractor.DefaultExtractorInput;
import androidx.media3.extractor.DiscardingTrackOutput;
import androidx.media3.extractor.Extractor;
import androidx.media3.extractor.ExtractorOutput;
import androidx.media3.extractor.PositionHolder;
import androidx.media3.extractor.SeekMap;
import androidx.media3.extractor.TrackOutput;
import androidx.media3.extractor.mp3.Mp3Extractor;
import junit.framework.TestCase;

/** Valid CBR MP3 frame headers, deliberately no Xing metadata and no input length. */
@SuppressLint("UnsafeOptInUsageError")
public final class UnknownLengthMp3SeekTest extends TestCase {
    public void testTranscodedColdMp3ExposesSeekPointsBeforeEntireDownload() throws Exception {
        Mp3Extractor mp3 = null;
        for (Extractor extractor : StreamExtractors.create(false).createExtractors())
            if (extractor instanceof Mp3Extractor) mp3 = (Mp3Extractor) extractor;
        assertNotNull(mp3);
        byte[] bytes = new byte[626 * 8];
        for (int offset = 0; offset < bytes.length; offset += 626) {
            bytes[offset] = (byte) 0xff; bytes[offset + 1] = (byte) 0xfb;
            bytes[offset + 2] = (byte) 0xb0; bytes[offset + 3] = 0; // 192 kbps, 44.1 kHz.
        }
        ByteArrayDataSource source = new ByteArrayDataSource(bytes);
        source.open(new DataSpec(Uri.parse("memory://synthetic-cbr.mp3")));
        try {
            final SeekMap[] map = new SeekMap[1];
            mp3.init(new ExtractorOutput() {
                @Override public TrackOutput track(int id, int type) { return new DiscardingTrackOutput(); }
                @Override public void endTracks() { }
                @Override public void seekMap(SeekMap value) { map[0] = value; }
            });
            DefaultExtractorInput input = new DefaultExtractorInput(source, 0, C.LENGTH_UNSET);
            PositionHolder seek = new PositionHolder();
            for (int reads = 0; reads < 10 && map[0] == null; reads++) mp3.read(input, seek);
            assertNotNull(map[0]);
            assertTrue("Cold MP3 must not silently map every seek back to zero", map[0].isSeekable());
            assertTrue(map[0].getSeekPoints(5_000_000).first.position > 0);
            assertEquals(C.TIME_UNSET, map[0].getDurationUs()); // No fabricated Content-Length/duration.
        } finally { source.close(); mp3.release(); }
    }
}
