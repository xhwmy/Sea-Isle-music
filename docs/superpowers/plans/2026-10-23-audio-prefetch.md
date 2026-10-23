# Audio Prefetch Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Keep the current song buffered ahead and make the next song locally available before transition.

**Architecture:** Media3 remains the playback and disk-cache owner. A dedicated, cancelable background `CacheWriter` prefetches only the next queue song after the current song is fully buffered. Navidrome's transcoding cache is enlarged independently.

**Tech Stack:** Android Java 17, Media3 1.4.1, JUnit 4, Navidrome 0.64.2 on Windows.

**Spec:** `docs/superpowers/specs/2026-10-23-audio-prefetch.md`

## Global Constraints

- Preserve the existing app ID, user data, signing key, and authenticated URL privacy.
- No extra phone app or VPN; the existing Tailscale Funnel remains the remote path.
- Do not prefetch a second song while the current song is still downloading.

## Review Focus

- Shuffle lookahead must match the song played after completion.
- Pause, queue edit, and sign-out must cancel stale background work.
- A failed prefetch must not interrupt current audio or loop requests rapidly.
- Cache metadata must never persist signed stream or redirect URLs.
- Short clips, one-song queues, and unknown durations must avoid unnecessary prefetch.

---

### Task 1: Current-song buffering and queue lookahead

**Files:** `StreamPolicy.java`, `StreamPolicyTest.java`, `PlaybackQueue.java`, `PlaybackQueueTest.java`, `PlayerService.java`.

- [ ] Add failing unit tests for the 8–10 minute buffer window, 3-second rebuffer threshold, and queue lookahead in sequential, repeat, and shuffle modes.
- [ ] Run focused tests and observe the expected failures.
- [ ] Implement buffer policy and lookahead; use them in playback.
- [ ] Run focused and full unit tests.

### Task 2: Next-song disk prefetch

**Files:** `NextSongPrefetcher.java`, `PlayerService.java`, `PrivateAudioCache.java`, `NextSongPrefetcherTest.java`, `AudioCacheIntegrationTest.java`.

- [ ] Add failing tests for eligibility, duplicate suppression, cancellation, and cache-writer reuse.
- [ ] Run focused tests and observe expected failures.
- [ ] Implement one cancelable background prefetch using the existing cache and hashed key.
- [ ] Run focused and full unit tests; compile Android instrumentation tests.

### Task 3: Server cache and release

**Files:** `../Navidrome/navidrome.toml`, `app/build.gradle`, `README.md`.

- [ ] Check disk free space and current server/task state, then set `TranscodingCacheSize = "1GB"`.
- [ ] Restart the existing Navidrome task and verify effective config plus local `/ping`.
- [ ] Build signed APK, run lint/unit tests, and install/test on an authorized connected phone if available.
- [ ] Commit the app changes and push the previously authorized GitHub remote, trying direct network first and existing proxy only if direct push fails.
