# 99 Integration Audit

## Package ID

`99-integration-audit`

## Goal

Verify that packages 00-05 together solve the real-device UX polish findings without cross-package regressions, hidden state owners, or false completion claims.

## Owner

Codex retained final audit.

## Dependencies

Run only after all package status files contain evidence.

## Allowed Paths

- Read all repository files.
- Read all `docs/plans/real-device-ux-polish-orchestration/status/*.md`.
- Fix only tiny, obvious documentation/status omissions in this orchestration directory if needed.
- Runtime code fixes require explicit user approval unless they are trivial integration omissions inside already changed package files.

## Audit Steps

1. Re-read `INDEX.md` and packages 00-05.
2. Re-read all status files.
3. Check `rtk git status --short --untracked-files=all`, `rtk git diff --stat`, and recent log.
4. For every package, mark each acceptance criterion as met, unmet, or unverifiable.
5. Check file ownership violations and duplicated reset/copy/navigation/dismiss implementations.
6. Run focused package verification or verify the reported output is current.
7. Run integration-level verification if the workspace is merge-clean enough:

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.SessionCockpitRenderModelTest --tests com.opencamera.app.CockpitPanelRouterTest --tests com.opencamera.app.SessionUiRenderModelTest --tests com.opencamera.app.SessionSettingsManagerTest --tests com.opencamera.app.DevLogRenderModelTest
rtk ./gradlew --no-daemon :app:assembleDebug
rtk ./scripts/verify_stage_7_observability.sh
```

8. Record whether full Stage 7 verification is meaningful given any existing unresolved conflicts.
9. Output one of: `PASS`, `PARTIAL`, or `FAIL`.

## Acceptance Criteria

- Humanistic and Portrait are visible through the actual catalog/mode track chain.
- Style copy is clean and invalid strings are absent.
- Settings Portrait/Watermark row taps route to the intended third-level pages.
- Quick outside tap dismissal works without triggering unintended capture/focus/mode actions.
- User adjustments persist and Reset restores defaults coherently.
- Dev logs are capped and cleanable by type.
- No package created a hidden second session/settings owner.
- Real-device smoke gaps are explicitly listed instead of silently passed.
