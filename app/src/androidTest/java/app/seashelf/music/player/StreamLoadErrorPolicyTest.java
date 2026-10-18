package app.seashelf.music.player;

import android.annotation.SuppressLint;
import android.net.Uri;
import androidx.media3.common.C;
import androidx.media3.datasource.DataSpec;
import androidx.media3.exoplayer.source.LoadEventInfo;
import androidx.media3.exoplayer.source.MediaLoadData;
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy;
import java.io.IOException;
import junit.framework.TestCase;

@SuppressLint("UnsafeOptInUsageError")
public final class StreamLoadErrorPolicyTest extends TestCase {
    public void testUnknownDurationReadErrorCannotEnterMedia3RewindRetry() {
        StreamLoadErrorPolicy policy = new StreamLoadErrorPolicy();
        assertEquals("Unknown-length audio must fail instead of calling configureRetry and resetting to zero",
                C.TIME_UNSET, policy.getRetryDelayMsFor(error(C.TIME_UNSET)));
        assertEquals("Finite media keeps the normal initial retry", 0L, policy.getRetryDelayMsFor(error(180000)));
    }
    private static LoadErrorHandlingPolicy.LoadErrorInfo error(long endTimeMs) {
        DataSpec spec = new DataSpec(Uri.parse("http://127.0.0.1/synthetic-audio"));
        LoadEventInfo event = new LoadEventInfo(1, spec, 0);
        MediaLoadData media = new MediaLoadData(C.DATA_TYPE_MEDIA, C.TRACK_TYPE_AUDIO, null,
                C.SELECTION_REASON_UNKNOWN, null, 30000, endTimeMs);
        return new LoadErrorHandlingPolicy.LoadErrorInfo(event, media, new IOException("synthetic read failure"), 1);
    }
}
