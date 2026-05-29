# Full Clear V2 - Competitive And Technical Research

## Source List

- Apple Support: [Take macro photos and videos with your iPhone camera](https://support.apple.com/en-ie/guide/iphone/iphfaacf2eb0/ios)
- Apple iPhone product pages: [iPhone 16 Pro](https://www.apple.com/iphone-16-pro/) and [iPhone 16](https://www.apple.com/iphone-16/)
- vivo product page: [vivo X300 Pro](https://www.vivo.com/en/products/x300pro)
- vivo China product family pages: [vivo X300 series](https://www.vivo.com.cn/vivo/x300)
- OPPO product pages: [Find X8 Pro](https://www.oppo.com/en/smartphones/series-find-x/find-x8-pro/) and [Find X7 Ultra](https://www.oppo.com/en/smartphones/series-find-x/find-x7-ultra/)
- Android API docs: [CaptureRequest.LENS_FOCUS_DISTANCE](https://developer.android.com/reference/android/hardware/camera2/CaptureRequest#LENS_FOCUS_DISTANCE), [CONTROL_AF_MODE](https://developer.android.com/reference/android/hardware/camera2/CaptureRequest#CONTROL_AF_MODE), [Camera2Interop.Extender](https://developer.android.com/reference/androidx/camera/camera2/interop/Camera2Interop.Extender)
- Research entry point: [Multi-focus image fusion survey on PubMed](https://pubmed.ncbi.nlm.nih.gov/33974542/)

## Apple Lessons

Apple's public macro flow is not marketed as a manual focus-stack feature. The visible pattern is automatic close-up adaptation plus a user-facing control to prevent unwanted automatic switching. That matters for Full Clear V2: the app should not expose "near diopter / far infinity / fusion confidence" as primary UI. It should expose intent and confidence: close subject detected, background target held, keep steady, capture complete, fallback used.

V2 design lesson: make the automatic route strong, but provide an override when the automatic lens/focus choice feels wrong.

## vivo Lessons

vivo's X-series messaging emphasizes dedicated imaging hardware, ZEISS tuning, high-resolution main/telephoto cameras, and telephoto/macro reach. This suggests vendor quality comes from a hardware-plus-ISP stack, not simply app-layer postprocessing.

V2 design lesson: on third-party Android/CameraX, Full Clear should be capability-adaptive. It can use wide/ultra-wide/telephoto choices and focus brackets when exposed, but it should not assume access to proprietary multi-frame ISP fusion.

## OPPO Lessons

OPPO's Find X-series messaging emphasizes multi-camera coverage, Hasselblad color, and advanced telephoto systems. Like vivo, the public product model leans on optical path choice and vendor image processing rather than exposing focus stack controls.

V2 design lesson: product UX should choose between optical strategies: ultra-wide/deep-DOF, main-camera bracket, telemacro/telephoto close subject, or conservative fallback.

## Android Lessons

Android exposes manual lens focus distance through Camera2 request keys and CameraX interop can attach Camera2 options to CameraX builders. The project already uses this style for manual capture controls. V2 must design around the fact that CameraX builder-level options are easier than reliable per-frame low-latency focus changes. A robust V2 may need a capability matrix for:

- continuous CameraControl focus/metering,
- Camera2Interop focus-distance override,
- rebind-per-focus degraded path,
- physical camera/lens node switching,
- unsupported/default best-frame path.

## Algorithm Lessons

Multi-focus image fusion is a real research area, but handheld phone use adds alignment, motion, subject edge, exposure, and lens-breathing problems. V2 should require confidence scoring and fallback, not only a fusion algorithm.

Preferred V2 algorithm stance:

- local sharpness maps are acceptable for initial fusion,
- alignment confidence is required before replacing pixels,
- moving subjects and edge halos must degrade to best-frame or user warning,
- segmentation can improve masks but cannot be required for product correctness,
- diagnostics must preserve why a result was fused or not fused.

