# Floating player and synchronized lyrics

## User goal and choices

Add a draggable Android floating player with synchronized lyrics and improve the main lyrics page. Fill missing computer-library lyrics where a trustworthy matching source exists. Previous user instructions delegate routine choices. Preserve volume controls, cache, queue modes, background playback and the original no-ad UI. Do not invent word timing or claim hardware testing without a phone.

Baseline: 0.3.2/code13, 145 available songs; Library has 144 audio files and 59 same-name LRCs, KuGou library has one more audio file. Existing strict local cache importer reports 13 newly matchable files. Navidrome stores relative media paths; resolve against the library table, never arbitrary paths. No phone connected.

## Android design

- Existing Java17/min26/compile34 and UiKit deep-sea palette; no new dependencies. Version0.4.0/code14.
- Add a discoverable floating-player action and lyric reload action without squeezing existing transport or volume controls. A small row in the scrollable detail area is acceptable; do not add large fixed bottom rows.
- Use SYSTEM_ALERT_WINDOW and TYPE_APPLICATION_OVERLAY with a real Settings.canDrawOverlays check. Request special access only from an explicit button, explain what it is, and resume opening only after a pending user request actually receives permission. Denial/revocation/no handler must remain safe. Never bypass Android permission through ADB/appops.
- Integrate overlay lifetime with the existing PlayerService foreground media service rather than starting a second perpetual service. Explicit user opening can foreground a paused existing queue but must not autoplay. Opening without a current song should guide the user rather than create an empty service. Closing the overlay leaves music playing.
- Main-thread WindowManager view: compact draggable header, current/next lyric lines, previous/play-pause/next controls, close and return-to-player action. Use 48dp controls, existing colors, FLAG_NOT_FOCUSABLE and a bounded window; touch outside remains usable. Clamp drag and rotation to current display bounds and remember the position. Do not cover the lock screen; remove the view on lock/screen off, restore only an already enabled window on user unlock. Honor OS overlay hiding/revocation. No automatic overlay on launch/reboot.
- Release view, listeners and all callbacks on close/logout/destroy; reject stale main-handler requests. No leaked overlay after player owner changes. All playback operations still go to PlayerService command queue.
- Publish immutable lyric state from PlayerService so the foreground page and overlay share song, loading/missing/error status and the same real playback position. Load via the existing background API executor; late responses must check owner, song and lyric generation. Lyrics failure must not affect audio. Force reload permits newly imported server lyrics to appear without an app restart. Clear lyric state immediately on song/logout changes.
- Improve line-synced display: normalize valid timestamps, stable ordering and simultaneous bilingual lines; highlight current line, smoothly follow new lines, allow tapping a timed line to seek and temporarily suspend following during manual scrolling. Plain lyrics remain readable and labelled static. No fabricated word-by-word karaoke timing.
- Pure tests before implementation for lyric grouping/timing, bounds, overlay enable/permission/revocation state and stale lyric results where practical. Compile instrumentation tests if added but do not call them executed. Parent runs full unit/lint/release/instrumentation packaging, verifies signing and independent review.

## Computer lyrics task (parent owned)

- Run tested local KRC importer without overwriting existing LRC files.
- Add a conservative Python LRCLIB import utility with dry-run/apply and tests. Read only public song metadata from Navidrome SQLite in read-only mode; validate actual audio path inside the configured library and regular files. No credentials or server mutations in the importer.
- Request only the public lyrics API using title/artist/album/duration, bounded responses/timeouts, polite rate limiting and resumable private request cache. Accept exact normalized title/artist/version and duration within2s; prefer matching album, skip ambiguous matches, do not strip live/remix versions. Validate timestamp syntax/content size and duration range. Only real synced lyrics become LRC; use same-stem TXT for plain lyrics only if clearly labelled and useful, never fake timestamps. No-overwrite exclusive writes and rollback partial files on errors.
- Apply only validated matches to same-name local sidecars, keep downloaded lyric content and private reports outside GitHub source tree. Run a supported Navidrome full scan and confirm recognition without modifying audio. Any external-provider outage/unmatched result must be reported accurately; no replacement goal or guessed lyrics.

## Delivery

Signed APK in ignored dist, source commit/push to existing GitHub main through current network first. Preserve private startup helper, signing, credentials, media and private logs. Record actual downloaded/imported counts separately from unit/platform tests. Phone overlay authorization, touch layout, background/lock behavior and audible controls require device verification; do not claim they passed without connection.

The enabled dual-model skill calls for one focused Android implementer; configured model aliases are unavailable, so use the supported inherited model. Parent handles independent Python/data work, review and final verification.

## Execution and verification, 2026-10-18

- Android 0.4.0/code14 implemented with no new dependencies or advertising code. Shared immutable lyric state, draggable service-owned overlay, real permission flow, lock hiding, line following/tap seek and reload are present; volume/cache/queue paths remain shared.
- Independent review found an edge-coordinate drag issue and overly optimistic lyric duration disambiguation. Both fixed with regression coverage; final read-only review found no remaining material issue.
- Final offline Gradle gate passed: testDebugUnitTest, lintDebug, assembleRelease and assembleDebugAndroidTest. XML reports: 70 tests, zero failures/errors/skips; lint zero errors and five existing warnings. Instrumentation was compiled, not executed.
- All 13 Python lyric-import tests passed. Existing KRC cache added 13 LRCs, online source added 11. There are 83 same-name LRCs for 145 active library records. Of 144 tracks with valid matching metadata/duration, 72 had lyrics before online apply, 11 were written and 61 had no accepted match after a focused retry. One additional track has zero duration and is excluded from online matching. Four initial API503 failures resolved on retry without accepted matches; no final lookup errors remain in the combined report.
- Supported production full scan exited0. Isolated real Navidrome test using one newly imported sidecar returned 39 synchronized lines in time order through getLyricsBySongId with default lyrics configuration. Production credentials were not read. DB lyrics metadata may remain empty because sidecars are served at request time.
- Release APK signature verified against 0.3.2: identical SHA256 certificate a111e5e90ab540cdbf567a8cfaf3a0179599387e9a57b4e24a49d476699b1846. ADB lists no attached phone; no installation or physical overlay/audio/lockscreen test claimed.
