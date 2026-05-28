# Full Clear Mode - Product Definition

## Product Name

- Chinese display name: `全清`
- English/internal display name: `Full Clear`
- Short button label: `全清` or `Clear`
- Suggested code identity: `ModeId.FULL_CLEAR`

## User Problem

When hiking or traveling, the user often frames a close foreground object together with a far landscape: a figurine, handheld model, trail marker, person-scale prop, flower, or nearby subject against mountains, buildings, or sky. Ordinary large-sensor phone capture tends to choose one focus plane. The near object or the distant background becomes soft.

## V1 Promise

Full Clear V1 helps capture foreground and background with the best achievable clarity by:

- guiding the user into a stable close-plus-far composition,
- capturing a dual-focus bracket when device support is available,
- attempting a conservative V1 focus-stack fusion,
- falling back honestly to the best frame when fusion cannot be trusted,
- recording diagnostics so users and agents can tell what actually happened.

## What V1 Must Not Claim

- It must not claim guaranteed foreground/background sharpness.
- It must not claim true depth or vendor HAL focus stacking.
- It must not claim segmentation/depth success from a 2D preview mask or synthetic tests.
- It must not hide fallback states. A best-frame fallback is acceptable only if metadata and pipeline notes say so.

## Primary User Flow

1. User selects `全清`.
2. Preview shows simple guidance: keep the phone steady and place the near subject inside the center-safe region.
3. Shutter triggers a V1 focus bracket: near focus and far/infinity focus when supported.
4. The app tries V1 fusion. If confidence is low, it keeps the best frame and marks the result as degraded.
5. Saved media includes `mode=full-clear`, bracket/fusion status, and fallback reason if any.

## Product Acceptance

Local acceptance requires mode visibility, capture path tests, synthetic fusion/fallback tests, diagnostics, and build success. Product confidence requires vivo X300 real-device images comparing Full Clear against ordinary Photo/Scenery in close-plus-far scenes.

