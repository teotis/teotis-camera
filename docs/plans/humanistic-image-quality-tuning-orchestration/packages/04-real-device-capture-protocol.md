# Package 04 — Real-Device Capture Protocol

## Package ID

`04-real-device-capture-protocol`

## Goal

Define a practical vivo real-device capture and review protocol for validating Humanistic / Color Lab image-quality tuning. The protocol should let the user compare system camera and OpenCamera samples without reducing the decision to vague taste or impossible parity.

## Allowed Paths

- Read-only: `docs/plans/**`, `scripts/**`, `app/src/**`, `core/**`, `feature/**`.
- Writable: `docs/plans/humanistic-image-quality-tuning-orchestration/status/04-real-device-capture-protocol.md` only.

## Forbidden Paths

- Do not edit runtime code, tests, resources, shared docs, `INDEX.md`, package docs, or other status files.
- Do not require uploading user photos or using cloud analysis.

## Dependencies

Wait for packages 01, 02, and 03.

## Parallel Safety

Not safe to run before dependencies. Writes only its own status file.

## Tasks

1. Read packages 01-03 status files.
2. Define a capture protocol for dusk/night scenes:
   - scene categories;
   - system camera vs OpenCamera order;
   - focus/exposure tap policy;
   - zoom/focal setting;
   - Color Lab / Humanistic style variants;
   - sample naming;
   - required screen recording/logcat/metadata evidence.
3. Define a review rubric using package 02 scorecard and package 03 feasibility constraints.
4. Define pass/partial/fail thresholds that are realistic:
   - pass means OpenCamera improved in the chosen style direction and no longer looks like raw frame plus basic filter;
   - partial means some dimensions improve but one or two regress;
   - fail means capture reliability breaks or image artifacts are worse.
5. Define log/diagnostic collection commands and what each proves.
6. Define how to preserve user privacy: local-only review, no cloud upload, sample paths outside git.

## Acceptance Criteria

- Status file contains a step-by-step real-device protocol.
- Protocol includes at least four scene categories: dusk sky + lamps, night street with signs, indoor warm mixed light, backlit subject or portrait-like subject.
- Protocol includes a sample naming scheme and evidence checklist.
- Protocol includes a scorecard and concrete pass/partial/fail thresholds.
- Protocol explicitly states that desktop/unit tests cannot replace final visual QA.

## Verification Commands

```bash
rtk rg -n "adb|logcat|real-device|visual QA|Color Lab|Humanistic|ShotCompleted|ShotFailed|thumbnail|MediaStore|algorithm-render|postprocess" docs/plans scripts app core feature
rtk git status --short
```

Possible device commands for the eventual tester, not required if no device is attached:

```bash
rtk adb devices
rtk adb logcat -d | rg "OpenCamera|capture|Shot|algorithm-render|postprocess|ImageCapture|ColorLab|Humanistic"
```

## Expected Evidence Pack

- Worktree path and branch, if any.
- Commands run and short output summary.
- Capture protocol.
- Scorecard and thresholds.
- Evidence checklist.
- Privacy/local-storage guidance.
- Unresolved risks.
- Self-certification that only the allowed status file was touched.

## Stop Gates

Stop and ask before requiring network upload, cloud scoring, destructive device actions, or changing app code to collect evidence.
