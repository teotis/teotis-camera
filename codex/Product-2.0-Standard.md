# OpenCamera Product 2.0 Standard

> Last updated: 2026-05-23  
> Purpose: This is the canonical product standard for judging whether OpenCamera can be treated as a 2.0 product. Read this first whenever a task mentions `2.0`, `V2`, `Readiness`, `GO / NO GO`, `真机验收`, `产品验收`, or `2.0 标准`.

## 1. Product Positioning

OpenCamera 2.0 is not a clone of an OEM system camera and should not pretend to have private ISP, BSP, or vendor algorithm privileges.

OpenCamera 2.0 should be judged as:

- A professional Android camera app built on public Android / CameraX / app-side media processing capabilities.
- A product cockpit over a clear four-layer camera architecture: `Mode Plugin -> Session Kernel -> Device Adapter -> Media Pipeline`.
- A camera experience that values honest capability governance: unsupported and degraded features must be visible as such.
- A verifiable product baseline: preview, controls, capture, saved media, thumbnail, diagnostics, and recovery must agree with each other.
- A foundation for future high-end algorithms, not a demo that hides placeholder behavior behind polished UI.

The project should reference Apple, OPPO, vivo, Google, Xiaomi, Huawei, and other mature camera products at the level of product semantics: predictable cockpit, clear hierarchy, smooth feedback, trustworthy output, and graceful degradation. It should not copy brand-specific UI or claim private hardware-level capabilities.

## 2. Core 2.0 Promise

The minimum product promise is:

> Users can open the camera, understand the cockpit, change visible settings, capture photo/video, trust the preview-to-output relationship, open the saved result, and recover from common lifecycle/device interruptions without encountering fake features, silent failures, or confusing UI state.

This promise has five top-level gates:

1. UI design logic is self-consistent.
2. User interaction is smooth and predictable.
3. Reachable features are effective, honestly unsupported, or clearly degraded.
4. Input/output chains are complete and trustworthy.
5. Stability, recovery, diagnostics, and release evidence are strong enough for real device use.

## 3. Decision Levels

Use these decision levels consistently:

- `GO`: Product 2.0 can be treated as reached. `P0=0`; main-path `P1<=2`; remaining P1 items have owners, short closure paths, and do not damage the main capture/record/recovery flows.
- `CONDITIONAL GO - LOCAL/TEXT GATE`: Local code, text evidence, contracts, unit tests, integration checks, and build gates pass, but real-device and multimodal evidence is still pending.
- `CONDITIONAL GO - DEVICE GATE`: Real-device smoke passes on the current target device, but broader device matrix, long-run, thermal, or visual polish evidence remains incomplete.
- `NO GO`: Any P0 exists, or any golden path cannot be proven end to end, or a visible first-class feature is fake/placeholder without honest degradation.

Never declare final product `GO` from unit tests alone. Product 2.0 includes visual coherence, interaction feel, saved-output correctness, and real-device evidence.

## 4. Severity Rules

- `P0` blocker: core path broken, output untrustworthy, high-probability crash, fake first-class feature, silent save/postprocess failure, permission/recovery dead end, or preview/output mismatch that misleads the user.
- `P1` critical: core path works but experience is visibly broken, confusing, slow, or inconsistent; workaround exists but should not ship as 2.0 polish.
- `P2` important: usable but rough, inconsistent, poorly worded, or missing diagnostics/edge coverage.
- `P3` optimization: polish or future improvement that does not affect 2.0 entry.

P0 always blocks `GO`. Multiple related P1 issues may be promoted to P0 if they combine into a broken golden path.

## 5. Golden Paths

### 5.1 Photo Capture

Pass standard:

- Shutter tap maps to a single session-owned intent and shot request.
- Capture is blocked or degraded honestly when permissions, lifecycle, storage, device capability, or active operation prevents success.
- Preview frame ratio, overlay frame, saved JPEG crop, thumbnail, and user feedback share the same product truth.
- Color Lab, style/filter, watermark, selfie mirror, frame crop, portrait/document rendering, and other postprocess effects are either applied to the saved output or explicitly marked unavailable/degraded.
- Save success means the user can open the resulting media or receives a visible failure/degraded result.
- Postprocess failure cannot be silently converted into a successful-looking final result.

Fail signals:

- Saved photo has different crop/content than the visible capture frame.
- Color/filter/watermark appears in preview or UI but not in saved media without warning.
- Thumbnail uses raw preview feedback when final output will be postprocessed differently.
- `ShotCompleted` hides postprocess, sidecar, or MediaStore failure.

### 5.2 Video Recording

Pass standard:

- Start/stop recording has clear state transitions: requesting, recording, stopping, saved/failed.
- UI cannot imply recording is active before the session/device confirms it.
- Output file is openable after completion.
- Video-specific features are either applied, written as honest sidecar/metadata, or clearly presented as not burned into frames.
- Long recording, foreground/background behavior, thermal degradation, and stop-time save feedback are observable.

Fail signals:

- Start/stop has no reliable feedback.
- Output path exists in UI but cannot be opened.
- Watermark/filter/sidecar claims exceed actual video output behavior.

### 5.3 Mode Switch And Capture

Pass standard:

- Mode selection is exact, not cyclic surprise behavior.
- Each mode has an explicit capability declaration and capture policy.
- Mode switch updates session graph, device request, UI state, and output metadata consistently.
- Unsupported mode features are unavailable or visibly degraded, not fake.

Fail signals:

- Tapping a mode changes UI only.
- Old mode settings leak into new mode behavior.
- RAW, Night multi-frame, Live motion, Pro controls, or similar entries appear first-class but do not alter output or do not disclose degradation.

### 5.4 Permission / Lifecycle / Recovery

Pass standard:

- Camera permission, microphone permission, permanent denial, foreground/background, host detach/attach, preview stall, provider failure, and thermal critical issues have clear session/device ownership.
- Recovery attempts are traceable and bounded; they cannot form hidden retry loops.
- Users see understandable feedback when recovery is happening or cannot proceed.
- Diagnostics can explain the most recent failure/recovery path.

Fail signals:

- Permanent permission denial leaves the user stuck without a system settings route.
- Preview stalls indefinitely without recovery or visible state.
- Provider/thermal/recovery events exist only as logs and do not affect session state.

## 6. UI And Interaction Standard

### 6.1 Cockpit Hierarchy

2.0 cockpit should feel like a camera-first product surface:

- Preview is the primary canvas.
- Top bar carries identity and a very small set of high-level actions.
- Right rail carries primary labs/actions only when they are essential and distinct.
- Bottom cockpit owns mode track, shutter, thumbnail, lens switch, and zoom in a visually unified control area.
- Quick settings and labs open as coherent panels, not scattered floating fragments.
- Dev/diagnostics must be visually secondary and never compete with user-facing capture actions.

### 6.2 Visual Logic

Pass standard:

- Buttons, panels, rails, mode track, zoom, and shutter share one visual grammar.
- Chinese text must not be clipped, vertically broken, or hidden behind controls.
- Narrow-screen layouts must preserve meaning before decorative density.
- Icons and labels must match user concepts, not internal engineering names.
- Panel hierarchy must make parent/child state obvious.

Fail signals:

- User-visible English or enum labels leak into Chinese UI, except intentional product names.
- A first-class button opens a differently named internal route.
- Controls overlap, clipped text appears, or buttons have inconsistent sizes/styles without product reason.
- Bottom controls feel like unrelated islands instead of one cockpit.

### 6.3 Interaction Grammar

Pass standard:

- Tapping a visible option selects that exact option.
- Direct manipulation controls, such as Color Lab palette or tap-to-focus, update state immediately and persist when expected.
- Back, outside tap, close button, and mode switch have consistent panel dismissal rules.
- Controls disabled during capture/recording explain why or fail silently only when the user cannot interact with them anyway.
- Hit targets are large enough for real-device use.

Fail signals:

- Palette/slider snaps back or does not affect session settings.
- Shortcut panel items lose critical values when expanded.
- Mode labels are unreadable while the camera is in use.
- UI routing depends on hidden global state instead of a single route owner.

## 7. Feature Availability Standard

Every visible feature must be classified as one of:

- `supported`: user action changes real session/device/media behavior and can be tested.
- `degraded`: feature has a real fallback, and UI/output/metadata make the limitation clear.
- `unsupported`: feature is unavailable on this device/app stage and should not look active.

Rules:

- No fake first-class entries.
- Metadata-only behavior is not equivalent to visual output behavior unless the UI says so.
- Placeholder algorithms must not be marketed as real algorithms.
- Device-dependent capabilities must be detected or declared conservatively.
- High-cost features such as RAW/DNG, true Night multi-frame merge, Live motion, frame-level video watermark burn-in, provider death matrix, and thermal/long-run device matrix require explicit product decisions before they can block or enter 2.0.

## 8. Preview / Output / Thumbnail Consistency

2.0 treats visual consistency as a product contract:

- Preview crop, frame overlay, saved crop, thumbnail, and gallery-open result must describe the same intended capture.
- If final output will include postprocess that raw preview cannot show, raw preview feedback must not be presented as final thumbnail.
- Color Lab/style/filter/watermark/frame ratio/selfie mirror must share a render truth or be honestly delayed until saved output.
- Thumbnails must prefer saved media over raw preview snapshots when output differs.
- Gallery open should use the saved media URI/source, not stale file paths or preview-only state.

Final GO requires real-device evidence: screen recording, saved JPEG/video, thumbnail behavior, and visual comparison.

## 9. Stability And Observability Standard

2.0 is not just feature completeness; it is operability.

Pass standard:

- Stage 7 observability script passes for the current local gate.
- Session diagnostics can explain current state, latest preview start, recovery trace, runtime issue, and performance budget.
- Runtime issues from device/camera/provider/thermal/preview stall are structured, not just free text.
- Recovery failure is visible and bounded.
- Performance budgets exist for cold start, foreground resume, recovery, and reconfigure; device-specific tuning can remain an external risk if documented.

Required local gate:

```bash
rtk ./scripts/verify_stage_7_observability.sh
```

Device-only residual risks must remain explicitly labeled: provider death/restart, thermal chamber behavior, long-run recovery, and multi-device performance baselines.

## 10. Evidence Requirements

Use four evidence classes:

- Static evidence: code, contracts, render models, state machines, resource strings, docs.
- Dynamic evidence: unit tests, integration tests, Gradle tasks, verification scripts.
- Visual evidence: screenshots, screen recordings, panel state captures, saved media previews.
- Result evidence: JPEG/video files, sidecars, MediaStore URI behavior, metadata, logs, diagnostics.

Minimum local/text gate evidence:

- Focused tests for touched behavior.
- Relevant app/core tests.
- `:app:assembleDebug`.
- Stage 7 script when stability/session/device/media behavior is touched.
- Updated status documentation when the change affects product readiness.

Minimum final product GO evidence:

- Current APK built and installed on target device.
- Vertical and landscape first-screen screenshots.
- Style, Color Lab, quick settings, settings, Dev/diagnostics panel screenshots.
- Photo capture recording with thumbnail transition.
- Saved JPEG samples for default, frame ratio, Color Lab/style, watermark, selfie mirror, portrait/document when applicable.
- Video start/stop recording and output open verification.
- Permission denial/recovery and foreground/background recovery smoke.
- Diagnostics snapshot for at least one normal capture and one recovery path.

## 11. Review Matrix

| Gate | Pass Standard | Blocks GO If |
| --- | --- | --- |
| UI design logic | Top/rail/bottom/panel hierarchy matches 2.0 cockpit and readable localized copy | Clipped text, leaked internal names, contradictory routing, broken cockpit composition |
| Interaction smoothness | Exact taps, predictable panels, clear disabled/recovery states, usable hit targets | Direct manipulation fails, panels conflict, mode/zoom chooses wrong target, permission dead end |
| Reachable feature effectiveness | Every visible feature is supported/degraded/unsupported with owner and tests | Fake entry, placeholder presented as real, metadata-only behavior marketed as visual output |
| IO chain | Intent -> session -> device -> media -> postprocess -> thumbnail/gallery is traceable | Save/postprocess/sidecar failure hidden, output cannot open, thumbnail/output mismatch |
| Stability/observability | Diagnostics/recovery/runtime issue/watchdog/perf budget gates pass locally | Preview stall, recovery loop, unobservable provider/thermal failure, no user-facing recovery state |
| Multimodal/device QA | Current APK has screenshots/recordings/saved-media evidence | Final GO requested without current real-device evidence |

## 12. Retrieval Rule For Future Agents

When future work asks for any of the following, read this document first:

- `2.0 标准`
- `产品 2.0`
- `V2 readiness`
- `整体达标`
- `GO / NO GO`
- `真机验收`
- `多模态验收`
- `UI 设计逻辑自洽`
- `交互流畅`
- `可触及功能有效可用`
- `输入输出链路通畅`

Then read the current status in `codex/documentation.md` and the latest relevant readiness or real-device report before making a judgment.

## 13. Related Documents

- `codex/documentation.md`: living project status and latest verified loops.
- `codex/v2_ui/`: UI/interaction 2.0 design material.
- `codex/capability_kernel_v2/`: capability kernel 2.0 architecture material.
- `codex/V2-Readiness-Final-Local-Gate-Review.md`: latest local/text gate judgment.
- `V2-Readiness-Release-Gate-Report.md`: release gate report at repo root.
- `codex/Fifth-Recording-Hard10-Multimodal-QA-Report.md`: real-device visual/product QA baseline.
- `codex/agent_plans/`: task packages and specialized audit plans.
