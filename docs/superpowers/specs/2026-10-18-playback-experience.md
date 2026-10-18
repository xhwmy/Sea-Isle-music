# Playback experience specification

User asked to optimize the entire Android playback flow, using Kugou as a reference. Human has already delegated routine choices and authorized implementation and USB updates. Do not request plan approval again. Reference: https://www.kugou.com/kf/user/html/help.html, which documents five playback modes and distinguishes network buffering from playback failures. This is a reference for behavior, not a promise to replicate Kugou's proprietary product.

## Outcome

SeaShelf should behave like a complete personal music player: a visible, editable queue; clearly defined manual versus automatic navigation; persisted modes and listening progress; accurate loading/pause/seek/error feedback; and a compact responsive playback page. Existing browse, search, lyrics, credential handling, public server address and quality fallback must work.

## Behavior

- Five modes: sequential, list loop (default), repeat one, shuffle, single play. Auto completion respects mode; manual next/previous still selects a different song where possible, including in repeat one/single modes. Shuffle visits every track once per cycle and previous retraces actual history. Mode changes preserve current song and position.
- Queue sheet: show current song, total, active mode; tap any row to play; row menu offers play next and remove. Removing a different track does not restart audio; removing current track selects a valid successor; removing the last clears playback safely. Moving an item to play next must not duplicate it or change current audio. Select current song must not reset position. Use ListView/recycling for 145+ rows.
- Album-started queue keeps chosen album then appends library without duplicates. Explicit queue edits during asynchronous library expansion must not be undone by old callbacks. Search-started queue remains search results. Queue source is visible (music library or search results).
- Persist queue, selected song, mode and position in private atomic storage scoped to server + account identity. No credentials or signed URLs in session files or logs. Restart restores paused, never autoplay. Explicit sign-out clears this account's playback session, and old pending writes/callbacks cannot republish it.
- Prepare honors pause/play intent; seek while preparing is retained and applied before playback starts; fallback to original audio retains pending seek. Rapid queue switches reject old callbacks. Buffering/paused/error/retry are distinct. Retry current song retains its saved position. Permanent audio focus loss pauses; transient loss may resume only absent subsequent user pause. Earphone disconnect pauses.
- Persist at transitions and at a bounded interval while playing; avoid UI-thread IO and repeated metadata/notification rebuilds every tick. Keep immutable snapshots for UI and enforce MediaPlayer access on its playback thread.
- Compact page: fully visible title/artist, cover/lyrics stage, status, seek/time row, main three controls, mode and queue access. Transport stays pinned. Responsive small screens/long titles must not cut off the lyrics toggle as 0.2.8 did. Keep sea/navy palette; clear Chinese labels/content descriptions, >=48dp actionable targets. Keep functional lyrics synchronization and lyric view.
- Existing signed APK upgrade path; target 0.3.0 / versionCode 11. No unrelated source or computer-service changes; preserve untracked scripts/start-navidrome-hidden.vbs.

## Visual direction

Subject: a private collection listened to on Android away from the PC. Job: know what is playing and control what plays next.
Palette: deep sea #0B1C2C, raised navy #203B50, sea glass #70D6CD, pale type #F2F7FA, muted slate #9AB0BD, warm error #E4AE8E. Use existing UiKit equivalents where available.
Type: system sans medium for titles and controls, system sans normal for body/artist, tabular/default monospace for timing. Remove oversized marketing eyebrow on the player; use compact source label. Reduce cover responsively based on available height; no large decorative halo if it crowds content. Keep sea-glass progress and current-queue highlight as signature. Use native Dialog anchored bottom for queue.

## Evidence required

Meaningful pure-Java unit tests for mode boundaries, single/empty queues, shuffle history/no immediate repeats, duplicate expansion, edits, session roundtrip/corrupt input/account separation, and pause/seek intent where testable. Unit tests/lint/release signing gate. Parent tests actual touch play/pause, both skips, queue selection, mode switching, restart without autoplay and restored position on connected Android device. Report physical-device limits truthfully; no simulated output claims.

## Limits

User clarification: no advertising screens, startup ads, promotions, VIP/renewal banners, commerce/recommendation feeds or advertising/tracking SDKs. Reference only playback controls, queue, modes and lyrics.

No replacement of Navidrome/Funnel, new paid dependencies, phone VPN, forced downloads, or promise of instant audio on arbitrary mobile networks. Next-song audio preloading is not required for this iteration; don't add an unverified cache/second MediaPlayer. Focus on coherent, tested control logic.
