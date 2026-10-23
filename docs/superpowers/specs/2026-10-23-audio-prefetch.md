# Playback prefetch design

The user wants phone playback to withstand mobile-network fluctuation without repeatedly pausing. Existing SeaShelf playback uses a 90–180 second Media3 buffer, a 256 MB disk cache, and retries transient errors. Navidrome 0.64.2 serves many FLAC tracks as 96 kbps MP3 transcodes and its default 100 MB transcoding cache is almost full.

## Design

- Increase the local Navidrome transcoding cache to 1 GB. Keep the existing music library and transcoding profile. Restart the configured server task and verify the effective setting and `/ping` response.
- Change Media3's current-track buffer target to 8–10 minutes, keeping its existing `SimpleCache`. Typical songs will reach end of file while playing and remain on disk. Reduce the post-rebuffer start threshold from 8 to 3 seconds; keep the 3-second initial start threshold. The target is best effort and cannot overcome sustained bandwidth below the stream bitrate.
- Once the current song has buffered to its known end, prefetch the queue's next automatic song into the same private `SimpleCache`, using its hashed account/song/profile cache key and a short-timeout `CacheWriter`. Run one background prefetch at a time, cancel on pause, song or queue change, account change, and service destruction. Do not run next-song traffic while the current song is still loading.
- Add a non-mutating next-song selection API to `PlaybackQueue`. Shuffle may reserve the next random choice so the prefetched song is the one actually played. Single play and repeat one do not prefetch a different song.
- Add buffer/reconnect diagnostic logs without URLs, credentials, song IDs, or filenames.

## Acceptance

- Existing unit tests remain green. New tests cover buffer parameters, queue lookahead including shuffle and queue edits, and prefetch scheduling/cancellation boundaries.
- Android instrumentation tests cover full-byte prefetch into the shared cache and use of cached bytes on playback, without a production server.
- A signed 0.4.3 APK builds with the existing signing key. If no phone is attached, report that installation and real mobile-network verification remain untested.
