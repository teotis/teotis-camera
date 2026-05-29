# Full Clear V2 - Product Definition

## Product Promise

Full Clear V2 should feel like a camera mode for travel objects and landscapes, not a technical focus-stack tool. It should help users capture a close subject and distant background with high confidence by selecting the best available optical and computational path.

## V2 User-Facing States

- `Ready`: close subject and background are plausible.
- `Move back`: close subject is too close for the current lens.
- `Use wide`: ultra-wide/deep-depth route is predicted to be better than bracket.
- `Hold steady`: bracket or fusion path needs stability.
- `Processing`: fusion or best-frame selection is running.
- `Best frame saved`: V2 chose a conservative fallback.
- `Full Clear fused`: V2 produced a fused result with acceptable confidence.
- `Unsupported`: device cannot provide enough focus/lens control for V2; route to wide/deep-DOF advice.

## V2 Capability Matrix

| Capability | Product Meaning | Support Semantics |
|---|---|---|
| Deep-DOF lens path | use ultra-wide or a lens node with naturally larger depth of field | supported/degraded/unsupported |
| Focus bracket path | capture near/far frames with controllable focus | supported/degraded-rebind/unsupported |
| Lens-aware path | choose wide/main/tele/physical node based on subject distance and background | supported/degraded/unsupported |
| Alignment confidence | reject handheld motion and lens-breathing artifacts | required for fused output |
| Fusion confidence | decide fused vs best-frame | required for fused output |
| Saved diagnostics | user/agent can audit what happened | required |

## V2 Product Boundary

V2 may say: "Full Clear tries to keep close subject and background clear."  
V2 must not say: "Full Clear guarantees both planes are sharp."

