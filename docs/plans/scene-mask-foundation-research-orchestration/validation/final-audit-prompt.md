# Final Integration Audit

## Context

- INDEX: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/scene-mask-foundation-research-orchestration/INDEX.md`
- Packages:
  - `/Volumes/Extreme_SSD/project/open_camera/docs/plans/scene-mask-foundation-research-orchestration/packages/01-backend-capability-matrix.md`
  - `/Volumes/Extreme_SSD/project/open_camera/docs/plans/scene-mask-foundation-research-orchestration/packages/02-current-implementation-audit.md`
  - `/Volumes/Extreme_SSD/project/open_camera/docs/plans/scene-mask-foundation-research-orchestration/packages/03-product-architecture-design.md`
  - `/Volumes/Extreme_SSD/project/open_camera/docs/plans/scene-mask-foundation-research-orchestration/packages/04-verification-real-device-protocol.md`
  - `/Volumes/Extreme_SSD/project/open_camera/docs/plans/scene-mask-foundation-research-orchestration/packages/99-integration-audit.md`
- Status files: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/scene-mask-foundation-research-orchestration/status/*.md`

## Audit Steps

1. Read `INDEX.md` and all package docs.
2. Read status files for packages 01-04.
3. Run:
   ```bash
   rtk git status --short
   rtk rg -n "SceneMask|PreviewSceneMask|MlKit|MaskAware|scene-mask" app core docs/plans codex
   ```
4. For each package, check every acceptance criterion.
5. Compare backend capability claims against current implementation findings.
6. Compare product architecture against OpenCamera boundaries:
   - UI renders and dispatches only.
   - Session Kernel does not own mask pixels.
   - Device adapter owns CameraX/Camera2.
   - Media pipeline owns saved-photo postprocessing.
   - Any hardware/ML capability is supported/degraded/unsupported.
7. Check for overclaims:
   - 2D mask presented as depth.
   - preview mask presented as saved-output truth.
   - beta/unbundled backend presented as mandatory support.
   - Color Lab/Portrait UI saying supported while pipeline notes say no mask applied.
8. Output one of:
   - **PASS** — research is coherent and ready to become an implementation repair package.
   - **PARTIAL** — useful direction but specific evidence gaps remain.
   - **FAIL** — foundation or claims are unsound.

## Evidence Required

- Per-package acceptance criteria status.
- Backend support matrix summary.
- Current implementation readiness summary.
- Product/architecture decision.
- Verification protocol decision.
- Final recommendation:
  - proceed to implementation planning;
  - run more research;
  - repair current implementation first;
  - defer.

