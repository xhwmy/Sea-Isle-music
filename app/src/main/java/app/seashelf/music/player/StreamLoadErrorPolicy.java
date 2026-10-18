package app.seashelf.music.player;

import android.annotation.SuppressLint;
import androidx.media3.common.C;
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy;

/** Unknown-duration progressive audio must not use Media3's live-stream rewind-to-zero retry. */
@SuppressLint("UnsafeOptInUsageError")
public final class StreamLoadErrorPolicy extends DefaultLoadErrorHandlingPolicy {
    @Override public long getRetryDelayMsFor(LoadErrorInfo info) {
        // ProgressiveMediaPeriod reports DATA_TYPE_MEDIA even when its internal mode is live.
        // Its configureRetry() resets such streams to t=0. Fail instead, retaining the user's
        // position for an explicit Retry, which opens a fresh source at that position.
        if (info.mediaLoadData.mediaEndTimeMs == C.TIME_UNSET) return C.TIME_UNSET;
        return super.getRetryDelayMsFor(info);
    }
}
