# Playback experience implementation plan

> **For agentic workers:** Use superpowers:subagent-driven-development or superpowers:executing-plans to implement task-by-task. Parent retains USB deployment and final acceptance.

**Goal:** Deliver a cohesive Kugou-inspired playback flow and signed SeaShelf 0.3.0, with queue controls, playback modes and paused progress restoration.

**Architecture:** Extend the pure Java queue domain and add a small session codec/store. PlayerService owns MediaPlayer on its existing HandlerThread, translates explicit commands into domain transitions, publishes immutable snapshots and persists immutable session snapshots. Android UI renders those snapshots and dispatches commands; a recycled queue dialog manages queue actions. Keep XML API and current credential/stream fallback.

**Tech stack:** Existing Java 17 Android framework API 26+, Gradle 8.2.1, JUnit4 local jars. No new dependency runtime.

**Spec:** docs/superpowers/specs/2026-10-18-playback-experience.md

## Global constraints

- Human delegated implementation choices; proceed without a plan approval question.
- Preserve computer service, library files, signed credentials and unrelated untracked VBS.
- Do not print tokens/passwords/signed URLs. Do not install Android tests repeatedly. Parent alone installs final release once ready.
- Current skill's requested fable/sonnet aliases are unavailable to the callable model tool; use an available inherited-model implementation agent rather than claiming an unavailable model was invoked. Parent validates. Work in current continuation branch feature/android-client; no path-changing checkout because runtime service paths and USB artifacts use this workspace.
- VersionCode 11 / versionName 0.3.0.
- Explicit user clarification: exclude all advertising, promotions, VIP/renewal flows, commerce/recommendation feeds and ad/tracking SDKs; reference playback controls, queues, modes and lyrics only.

## Review focus

1. Current queue item removed while buffering; no invalid MediaPlayer operation or index.
2. Old library expansion / playback persistence after queue edits or sign-out must not restore deleted songs/session.
3. Rapid next/previous, retry or seek must not start an old song or ignore pause.
4. Single song, empty queue and random-mode history must have defined behavior.
5. Restored session wrong server/account/corrupt/truncated must be rejected, and restart never auto-plays.

### Task 1: Queue domain and modes

Files: player/PlaybackQueue.java; new player/PlaybackMode.java; player tests.
Interfaces: preserve API used by current UI until service update. Add mode-aware automatic completion/manual skips, indexed/ID selection, remove and play-next operations, immutable list snapshots, and deterministic injectable randomness for tests. Queue replacement/expansion preserves current ID/mode and deduplicates.
- [x] Add boundary/history/edit tests; observe meaningful failing tests.
- [x] Implement smallest cohesive queue model and run all unit tests.

### Task 2: Session persistence and service integration

Files: PlayerService.java; PlaybackIntent.java if necessary; new pure Java session model/codec; Android private session store; SubsonicClient identity accessor if needed; sign-out integration.
Interfaces: extend Snapshot with mode, queue source and immutable queue list, accurate status/progress; public command helpers for mode, select, remove, play-next, restore/clear. Store identity includes base server and username, no secrets. Restore paused before processing PLAY/QUEUE and support pending seek before prepared. Persist mode/queue/position without UI IO and ensure clear can't be undone by queued writes.
- [x] Codec tests exercise roundtrip, corrupt/bounds and account mismatch; queue/focus/intent tests as appropriate.
- [x] Implement, preserve fallback/foreground notification, generation guards and wake/noisy behavior. Add only sanitized diagnostics.
- [x] Run tests/lint, inspect thread/state paths.

### Task 3: Player UI, queue sheet and entry points

Files: ui/PlayerActivity.java; new ui/QueueDialog.java (or similarly small focused class); MiniPlayer.java; LibraryActivity.java/SeaShelfApp restore hook; UiKit only if necessary.
Interfaces consume service Snapshot + command helpers from task 2. Cover/lyrics/title content uses flexible remaining space; pinned progress/transport; mode button cycles or opens choice menu; queue button opens recycled ListView bottom dialog. Queue menu select/play-next/remove; current highlight and auto-scroll. Native touch descriptions Chinese. Restore mini player appears after startup and tapping it opens player at saved position without autoplay. Preserve lyrics generation guards.
- [x] Implement layout and all real actions; no placeholder controls.
- [x] Build and inspect code for narrow/long-title layout risks.

### Task 4: Verification and delivery prep

- [x] Run `C:\gradle-8.2.1\bin\gradle.bat :app:testDebugUnitTest :app:lintDebug :app:assembleRelease --offline -Pandroid.aapt2FromMavenOverride=C:\Users\windows\AppData\Local\Android\Sdk\build-tools\34.0.0\aapt2.exe` (execution may need approved escalation for Gradle cache).
- [x] Verify release signature, copy to dist/SeaShelf-0.3.0.apk. Do not deploy to phone; parent does physical acceptance. Record outcomes/limitations in concise implementation notes, not claimed physical tests. Do not commit until parent review/deployment findings handled.
- [ ] Parent reviews diff, runs one fresh-context review, installs and tests actual device. Fix concrete findings, update README and commit.

## Parent handoff, 2026-10-18

- Fresh independent review completed; concrete shuffle cycle, pending-clear load, owned-dialog lifecycle and restored-entry issues fixed and re-reviewed.
- Final artifact and 45 passing unit tests verified. Signed release certificate matches prior installed version.
- Parent attempted authorized USB update, but ADB device list was empty. Human explicitly reported temporarily unable to reconnect. Version 0.3.0 is NOT installed; physical seek/resume/touch/layout acceptance remains unchecked above. No alternative deployment or simulated acceptance was substituted.
- README and local version record saved for continuation when the human reconnects the phone. Unrelated untracked VBS preserved.
