package app.seashelf.music.player;

import org.junit.Test;
import static org.junit.Assert.*;

public final class StreamPolicyTest {
    @Test public void meteredNetworkUsesSmallerStreamWithSeparateCacheIdentity() {
        assertEquals(96, StreamPolicy.bitrateForMetered(true));
        assertEquals(192, StreamPolicy.bitrateForMetered(false));
        assertEquals(StreamPolicy.MP3_PROFILE, StreamPolicy.profileForBitrate(192));
        assertNotEquals(StreamPolicy.MP3_PROFILE, StreamPolicy.profileForBitrate(96));
        assertNotEquals(StreamPolicy.cacheKey("account", "song", StreamPolicy.profileForBitrate(96)),
                StreamPolicy.cacheKey("account", "song", StreamPolicy.profileForBitrate(192)));
    }

    @Test public void streamCacheIdentityExistsIndependentlyOfRotatingSignedUrls() {
        String identity = StreamPolicy.cacheKey("account-a", "song-1", StreamPolicy.MP3_PROFILE);
        assertEquals(identity, StreamPolicy.cacheKey("account-a", "song-1", StreamPolicy.MP3_PROFILE));
        assertTrue(identity.matches("audio-[a-f0-9]{64}"));
        assertFalse(identity.contains("account"));
        assertNotEquals(identity, StreamPolicy.cacheKey("account-b", "song-1", StreamPolicy.MP3_PROFILE));
        assertNotEquals(identity, StreamPolicy.cacheKey("account-a", "song-2", StreamPolicy.MP3_PROFILE));
        assertNotEquals(identity, StreamPolicy.cacheKey("account-a", "song-1", StreamPolicy.ORIGINAL_PROFILE));
        assertNotEquals(StreamPolicy.cacheKey("a", "bc", "d"), StreamPolicy.cacheKey("ab", "c", "d"));
    }

    @Test public void slowOrRejectedConnectionsNeverRetryLargerOriginalAudio() {
        assertFalse(StreamPolicy.mayUseOriginal(false, StreamPolicy.Failure.NETWORK));
        assertFalse(StreamPolicy.mayUseOriginal(false, StreamPolicy.Failure.HTTP));
        assertFalse(StreamPolicy.mayUseOriginal(false, StreamPolicy.Failure.INVALID_RESPONSE));
        assertTrue(StreamPolicy.mayUseOriginal(false, StreamPolicy.Failure.UNSUPPORTED_CODEC));
        assertFalse("An unrecognized container may be an untyped XML error", StreamPolicy.mayUseOriginal(false, StreamPolicy.Failure.UNSUPPORTED_CONTAINER));
        for (StreamPolicy.Failure failure : StreamPolicy.Failure.values())
            assertFalse(StreamPolicy.mayUseOriginal(true, failure));
    }

    @Test public void apiErrorsAndHtmlNeverBecomeCachedAudioResponses() {
        assertFalse(StreamPolicy.acceptsContentType("text/xml; charset=utf-8"));
        assertFalse(StreamPolicy.acceptsContentType("application/xml"));
        assertFalse(StreamPolicy.acceptsContentType("application/json"));
        assertFalse(StreamPolicy.acceptsContentType("text/html"));
        assertTrue(StreamPolicy.acceptsContentType("audio/mpeg"));
        assertTrue(StreamPolicy.acceptsContentType("Audio/FLAC; charset=binary"));
        assertTrue(StreamPolicy.acceptsContentType("application/octet-stream"));
        assertTrue(StreamPolicy.acceptsContentType(null));
    }
}
