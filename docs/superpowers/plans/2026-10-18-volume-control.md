# Playback volume control

## Goal and delegated choices

Add volume control inside the Android music client and allow a modest optional boost for quiet recordings. The user delegated routine choices previously. Preserve the existing playback/lyrics/queue UI, no ads. The phone is disconnected; the cause of the reported quiet output and actual audible effect cannot be verified on hardware.

Use the existing Java 17 / SDK 34 app and visual tokens. A compact header button labelled 音量 opens a native bottom dialog. The media slider controls STREAM_MUSIC and mirrors physical volume keys/route changes; show actual read-back rather than an invented success value. Set PlayerActivity and dialog volume-key streams to music. An optional 0–6 dB enhancement starts at 0/off and is remembered. Explain briefly that extra gain can distort; hardware effect support may vary. Do not automatically raise system volume or enable enhancement.

## Implementation

- Add pure volume policy/state tests before implementation: bounded gain, system index/percent mapping, and duck/restore semantics. Verify failure and then pass.
- Add VolumeDialog consistent with UiKit, accessible labels and at least 48dp sliders. Refresh current volume only while visible; do not override the thumb during dragging. Handle fixed-volume devices/system limits gracefully and read the accepted stream level back. Dismiss with activity destruction; no lingering timer/dialog.
- Add optional LoudnessEnhancer bound only to the active nonzero audio session, guarded by owner/player/generation. Listen for session changes through the installed Media3 API; release effects on replacement/destruction/error. Failure or unsupported effects must leave normal playback working and show an accurate status. Never use global session 0. Restore settings on a new song/service. Do not create a service or autoplay just to change settings without a queue.
- Keep focus ducking a separate transient factor from user settings. Focus regain/explicit granted play restores normal player volume; changing boost or replacing a song must not erase the chosen setting or leak stale effects. Verify lifecycle ordering against current PlayerService.
- Add normal MODIFY_AUDIO_SETTINGS permission if required. No new SDK dependencies. Version 0.3.2/code13.
- Build all unit tests, lint, instrumentation APK and signed release. Review material risks and signature compatibility. Update README with controls and limitations, deliver APK, commit and push to the existing GitHub main using current network first and existing proxy only if the push fails. Exclude signing, credentials, music and QA files.

## Boundaries

System media-volume steps and loudness depend on the phone/output route. The feature cannot promise sound above hardware limits. DSP support and audible quality need physical testing; compiled instrumentation is not an executed test. Saved credentials and the 0.3.1 cache/queue behavior remain intact.

## Workflow

The enabled dual-model skill requires a focused implementer. Configured aliases are unavailable to the agent tool, so use the inherited supported model and retain parent verification. User authorization replaces routine plan confirmation; no change of product goal is involved.

## Verification

Implementation and independent review completed. The initial Dialog.show/onStart range-initialization race was found and repaired: sliders start disabled, synchronous actual range/readback happens before they can be used, and visibility polling is independent of Dialog.isShowing during onStart. PlayerActivity.onStop dismisses the dialog and clears its timer/device callback. AudioOutput tests cover no global effect, gain bounds, source replacement, disable/failure/rejection, loss of control and duck restoration.

Parent final gates: 61 unit tests, 0 failures/errors/skips; lint 0 errors and 5 existing warnings; :app:assembleRelease and :app:assembleDebugAndroidTest passed. Release metadata 0.3.2/code13; certificate verified to match 0.3.1. The disconnected phone prevented installation, UI touch/audio route checks, and audible enhancement verification. No phone test is claimed.
