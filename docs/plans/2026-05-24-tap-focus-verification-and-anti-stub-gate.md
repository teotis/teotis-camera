# Tap Focus Verification And Anti-Stub Gate Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` or `superpowers:executing-plans` to implement this plan task-by-task. Use `rtk` for every shell command. This plan can run after or alongside `2026-05-24-tap-focus-camerax-execution-repair.md`.

**Goal:** Make the tap-focus repair verifiable in the current workspace and prevent the old CameraX stub from passing local acceptance again.

**Architecture:** Keep behavior tests focused on pure helpers, session/coordinator forwarding, and source gates. Real CameraX `FocusMeteringAction` execution remains compile/assemble plus real-device smoke because CameraX camera runtime is not available in JVM unit tests.

**Tech Stack:** Gradle unit tests, `rtk rg` source gates, existing Stage 7 verification script.

---

## Current Verification Blocker

The focused test command currently does not reach the tests because `:app:compileDebugKotlin` fails first:

```text
app/src/main/java/com/opencamera/app/SessionCockpitRenderModel.kt:7:34 Unresolved reference: SavedMediaType
app/src/main/java/com/opencamera/app/SessionCockpitRenderModel.kt:12:34 Unresolved reference: SavedMediaType
```

Source evidence:

- `SavedMediaType` is declared in `core/session/src/main/kotlin/com/opencamera/core/session/SessionContracts.kt`.
- `app/src/main/java/com/opencamera/app/SessionCockpitRenderModel.kt` imports it twice from `com.opencamera.core.media`.

This is not the tap-focus root cause, but it blocks all app compile/test verification.

## Task 1: Clear The Compile Preflight Blocker

**Files:**

- Modify: `app/src/main/java/com/opencamera/app/SessionCockpitRenderModel.kt`

- [ ] **Step 1: Replace the wrong duplicate imports**

Change the imports at the top of `SessionCockpitRenderModel.kt` from:

```kotlin
import com.opencamera.core.media.SavedMediaType
import com.opencamera.core.media.StillCaptureResolutionPreset
import com.opencamera.core.effect.FrameEffect
import com.opencamera.core.media.FrameRatio
import com.opencamera.core.media.LivePhotoBundle
import com.opencamera.core.media.SavedMediaType
```

to:

```kotlin
import com.opencamera.core.effect.FrameEffect
import com.opencamera.core.media.FrameRatio
import com.opencamera.core.media.LivePhotoBundle
import com.opencamera.core.media.StillCaptureResolutionPreset
import com.opencamera.core.session.SavedMediaType
```

- [ ] **Step 2: Compile**

Run:

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:compileDebugKotlin
```

Expected: the `SavedMediaType` unresolved reference is gone. If compilation now fails elsewhere, record the next exact file and error before continuing.

## Task 2: Add A Focused Source Gate Script

**Files:**

- Create: `scripts/verify_tap_focus_camerax_execution.sh`

- [ ] **Step 1: Create the script**

```bash
#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

ADAPTER="app/src/main/java/com/opencamera/app/camera/CameraXCaptureAdapter.kt"

if rg -n "CameraX FocusMeteringAction not yet implemented" "$ADAPTER"; then
  echo "Tap-focus CameraX execution is still stubbed." >&2
  exit 1
fi

if ! rg -n "startFocusAndMetering" "$ADAPTER" >/dev/null; then
  echo "Tap-focus CameraX execution does not call startFocusAndMetering." >&2
  exit 1
fi

if ! rg -n "FocusMeteringAction\\.FLAG_AF|FocusMeteringAction\\.FLAG_AE" "$ADAPTER" >/dev/null; then
  echo "Tap-focus CameraX execution does not configure AF/AE metering flags." >&2
  exit 1
fi

rtk ./gradlew --no-daemon -Pkotlin.incremental=false \
  :app:testDebugUnitTest \
  --tests com.opencamera.app.camera.PreviewMeteringActionPlannerTest \
  --tests com.opencamera.app.camera.CameraSessionCoordinatorTest

rtk ./gradlew --no-daemon :app:assembleDebug
```

- [ ] **Step 2: Make it executable**

Run:

```bash
rtk chmod +x scripts/verify_tap_focus_camerax_execution.sh
```

- [ ] **Step 3: Run it**

Run:

```bash
rtk ./scripts/verify_tap_focus_camerax_execution.sh
```

Expected before the CameraX repair lands: fail with the stub or missing `startFocusAndMetering` message. Expected after the repair lands: pass.

## Task 3: Add The Gate To Stage 7 Verification

**Files:**

- Modify: `scripts/verify_stage_7_observability.sh`

- [ ] **Step 1: Inspect the script**

Run:

```bash
rtk sed -n '1,220p' scripts/verify_stage_7_observability.sh
```

- [ ] **Step 2: Add the tap-focus gate near other app focused checks**

Add this line before the final `:app:assembleDebug` if the script already has an assemble step, or before the script exits if assemble is delegated elsewhere:

```bash
rtk ./scripts/verify_tap_focus_camerax_execution.sh
```

If `verify_stage_7_observability.sh` uses one large Gradle command rather than smaller commands, keep the source gate as a separate command before the Gradle block so the stub failure is easy to diagnose.

- [ ] **Step 3: Run Stage 7 verification**

Run:

```bash
rtk ./scripts/verify_stage_7_observability.sh
```

Expected: pass after the CameraX execution repair lands and the compile blocker is cleared.

## Task 4: Update Living Status

**Files:**

- Modify: `codex/documentation.md`

- [ ] **Step 1: Add a recent loop entry**

Add a concise entry under `Recent effective loops` or the most recent dated section that records:

```markdown
## 2026-05-24：点击预览对焦 CameraX 执行修复

- 目标：修复此前 tap focus/AE 链路只落到 session/device/coordinator，但 `CameraXCaptureAdapter` 仍返回 `UNSUPPORTED` stub 的问题。
- 核心结果：`DeviceCommand.ApplyPreviewMetering` 现在通过 CameraX `FocusMeteringAction` 执行 AF+AE metering，并在 AE-only、unsupported、failed 情况下回传结构化 `PreviewMeteringResultStatus`。
- 验证：`rtk ./scripts/verify_tap_focus_camerax_execution.sh` 与 `rtk ./scripts/verify_stage_7_observability.sh` 通过；真机仍需 smoke 复验具体机型的 AF/AE 区域支持。
```

Only add the verification line after the commands actually pass. If they do not pass, record exact failing command and blocker instead of claiming success.

## Acceptance

- `:app:compileDebugKotlin` is no longer blocked by the wrong `SavedMediaType` import.
- A dedicated tap-focus verification script fails on the old stub and passes after the repair.
- Stage 7 verification includes the anti-stub gate.
- `codex/documentation.md` no longer implies CameraX tap-focus execution is complete unless the repair and verification actually passed.
