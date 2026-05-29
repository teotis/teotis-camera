# Package 06 - Real Device Timing Protocol

## Objective

Produce a concrete real-device verification protocol for shutter readiness and sound timing after implementation lands. This package does not perform the device run; it prepares APK, logs, commands, checklist, and pass/fail interpretation.

## Dependencies

- `04-adapter-earliest-ready-signal`
- `05-shutter-sound-and-visible-rearm`

## Allowed Paths

- `docs/plans/capture-readiness-sound-timing-orchestration/outputs/06-real-device-timing-protocol.md`
- `docs/plans/capture-readiness-sound-timing-orchestration/status/06-real-device-timing-protocol.md`
- `codex/documentation.md` only if adding a short residual-risk note after verified local build
- Package scratch path printed by `scratch-path 06-real-device-timing-protocol`

## Forbidden Paths

- Runtime Kotlin source
- Tests
- Other package status files
- `docs/plans/capture-readiness-sound-timing-orchestration/INDEX.md`

## Required Work

Prepare a protocol that lets the device owner answer:

- When does the button enter loading after tap?
- When does the shutter/readiness sound play?
- When is the button visibly available again?
- When is a second tap actually accepted?
- When does saved media/postprocess finish?
- How does that compare with the vendor system camera under the same scene?

The protocol must include:

- Debug APK path expectation.
- Exact `adb install` command.
- Logcat filters or Dev log export steps.
- Suggested slow-motion video/audio capture method if available.
- A small table for recording press-to-sound, press-to-clickable, press-to-second-shot-accepted, and press-to-saved-media times.
- How to report back evidence to Codex without claiming full pass from partial data.

## Acceptance Criteria

- `outputs/06-real-device-timing-protocol.md` exists and is directly usable by the user/device owner.
- Local build command passes or failure is recorded as blocker.
- Status states that real-device QA remains external-assist.

## Verification Commands

```bash
rtk ./gradlew --no-daemon :app:assembleDebug
```

In a worktree, use:

```bash
rtk ./scripts/run_isolated_gradle.sh :app:assembleDebug
```

## Completion

Commit local changes, update coordinator status, then:

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/capture-readiness-sound-timing-orchestration/launchers/orchestrate.sh mark-state 06-real-device-timing-protocol completed --commit <commit-sha> --verification "<commands/results>"
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/capture-readiness-sound-timing-orchestration/launchers/orchestrate.sh advance --from 06-real-device-timing-protocol
```
