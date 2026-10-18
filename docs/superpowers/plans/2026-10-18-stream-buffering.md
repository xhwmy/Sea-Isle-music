# Stream buffering implementation plan

> Implement with the dual-model workflow; use an inherited-model implementer because the configured aliases are not available to the agent tool. Routine decisions and implementation are already authorized by the user.

## Goal and evidence

Reduce startup waiting and repeated network reads while preserving the existing queue, controls, lyrics and background playback. Publish reviewed source to the user's empty `xhwmy/Sea-Isle-music` repository.

The current Android MediaPlayer HTTP path has no persistent audio cache. Signed URL salts change between requests. A 20 second preparation timeout silently retries the original lossless file. An isolated real Navidrome/FFmpeg test on 2026-10-18 delivered the first MP3 bytes in 79 ms, reducing 26,554,001 bytes to 4,918,715 bytes. Cold transcoding has no Content-Length; cached range requests work. Previously observed phone preparation times were several seconds. The phone is currently disconnected, so mobile-network latency cannot be reproduced or claimed fixed on hardware.

## Implementation

- [x] Add regression coverage for stable account/song/profile cache identity and failure policy. Verify failure before implementation.
- [x] Replace the native HTTP playback path with Media3 ExoPlayer using explicit audio buffering (1000 ms startup, 2000 ms rebuffer; 15–45 seconds read ahead). Keep 192 kbps MP3, queue ownership and user intent; do not use estimated Content-Length as an exact size.
- [x] Add a process-wide private SimpleCache with a 256 MiB LRU bound and hashed account/song/profile keys, excluding rotating signed URL parameters. Use the standard persistent database index; wrap upstream getUri() to return the original in-memory request URI, preventing signed redirects from entering persistent metadata.
- [x] Keep original fallback only for confirmed decoder format/capability errors. Container parsing alone does not prove audio. Never fallback for timeout, 401/403, XML or network error. Log timing and numeric error codes, never signed URLs or credentials. Preserve retry position and user pause intent.
- [x] Keep player operations on the playback HandlerThread, wake locks, audio focus, system session, seek coalescing, completion modes and old-callback guards. First prepared event fires once per source; later READY events finish seeks/rebuffer without restarting a song.
- [x] Add instrumentation covering cache reuse with rotated authentication, distinct accounts/profiles and server XML errors. Compile it even if a device is unavailable; explicitly record unrun hardware tests.
- [x] Build unit tests, lint, debug instrumentation APK and signed release 0.3.1/code12. Verify signature matches existing app.
- [ ] Review playback changes, repair material findings, prepare portable README/build instructions, audit public tree for secrets/private artifacts, and push only source to the empty GitHub repository through current network first. Preserve local history and unrelated untracked VBS.

## Compatibility and publication

Use the existing Java 17 / compile SDK 34 build; select Media3 1.4.1 to avoid an unrelated SDK/AGP migration. Only core ExoPlayer, datasource and cache dependencies; no advertising SDKs. Existing local release signing stays private. Public login must not hardcode the owner's endpoint; local saved credentials survive the update. APK is delivered locally; signing keys, private QA fixtures and music are excluded from GitHub. Publish a clean initial snapshot if historical private environment notes cannot be safely included; keep the original local branch and commits intact.

## Acceptance limits

Final gates: 51 unit tests passed; lint 0 errors and 5 existing warnings; release and instrumentation APK packaging passed using normal Gradle dependency resolution. The initial lint opt-in issue and duplicate JUnit packaging issue were repaired; no lint baseline or broad error suppression was introduced. The release certificate matches the previous version.

Independent review findings repaired: unknown-length MP3 is now seekable; unsafe live-style retry is disabled for unknown-duration progressive media, preserving position for explicit retry; untyped parse failures do not trigger original fallback; paused seeks to ENDED settle and invalidate their timeout while retaining newer coalesced targets. Three pure-Java seek state regressions pass. Platform cache, extractor and retry-policy tests compiled but were not executed without a connected device.

- [ ] Physical phone playback over mobile data, repeated song startup, seek, interruption and background playback remain pending until the device reconnects. Do not translate configured startup buffering into a measured startup guarantee.
