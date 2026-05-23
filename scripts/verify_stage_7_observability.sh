#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT_DIR"

# ── core:media (pure contracts, policies, algorithms) ──────────────────────

./gradlew --no-daemon -Pkotlin.incremental=false \
  :core:media:test \
  --tests com.opencamera.core.media.ShotExecutorTest \
  --tests com.opencamera.core.media.AlgorithmProcessorTest \
  --tests com.opencamera.core.media.MultiFrameMergeAlgorithmProcessorTest \
  --tests com.opencamera.core.media.PipelineNotesOrderTest \
  --tests com.opencamera.core.media.FrameStreamContractsTest \
  --tests com.opencamera.core.media.LiveTemporalAssemblyPlannerTest \
  --tests com.opencamera.core.media.ResourceBudgetContractsTest \
  --tests com.opencamera.core.media.ResourceAdmissionPolicyTest \
  --tests com.opencamera.core.media.AlgorithmJobSchedulerTest

# ── core:device (capability contracts, shot request translator) ────────────

./gradlew --no-daemon -Pkotlin.incremental=false \
  :core:device:test \
  --tests com.opencamera.core.device.CapabilityContractsTest \
  --tests com.opencamera.core.device.DefaultDeviceShotRequestTranslatorTest \
  --tests com.opencamera.core.device.VideoSpecSelectionTest

# ── core:mode (mode declarations, degradation, strategy) ───────────────────

./gradlew --no-daemon -Pkotlin.incremental=false \
  :core:mode:test \
  --tests com.opencamera.core.mode.ModeProductDeclarationTest \
  --tests com.opencamera.core.mode.ModeCaptureStrategyGraphTest \
  --tests com.opencamera.core.mode.ModeCapabilityDegradationTest \
  --tests com.opencamera.core.mode.ModeCatalogContractsTest

# ── core:session (session state machine, diagnostics, thermal bridge) ──────

./gradlew --no-daemon -Pkotlin.incremental=false \
  :core:session:test \
  --tests com.opencamera.core.session.DefaultCameraSessionTest \
  --tests com.opencamera.core.session.SessionDiagnosticsTest \
  --tests com.opencamera.core.session.ThermalBudgetBridgeTest

# ── app (adapters, monitors, render models, dev log) ──────────────────────

./gradlew --no-daemon -Pkotlin.incremental=false \
  :app:testDebugUnitTest \
  --tests com.opencamera.app.camera.AndroidThermalRuntimeIssueMonitorTest \
  --tests com.opencamera.app.camera.CameraXCaptureAdapterCapabilityDetectionTest \
  --tests com.opencamera.app.camera.CameraXCaptureAdapterRuntimeIssueTest \
  --tests com.opencamera.app.camera.PreviewStartupRuntimeIssueMonitorTest \
  --tests com.opencamera.app.SessionUiRenderModelTest \
  --tests com.opencamera.app.SessionStateRenderTest \
  --tests com.opencamera.app.DevLogRenderModelTest \
  --tests com.opencamera.app.camera.CameraSessionCoordinatorTest

# ── tap-focus CameraX execution anti-stub gate ────────────────────────────

./scripts/verify_tap_focus_camerax_execution.sh

# ── assemble debug APK ────────────────────────────────────────────────────

./gradlew --no-daemon :app:assembleDebug
