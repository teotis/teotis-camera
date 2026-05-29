# Full Clear V2 - Roadmap

## Preconditions

- V1 mode surface and focus-bracket contracts have landed or are ready to merge.
- Stage 7 gates are stable enough to absorb a new product-mode implementation wave.
- Real-device owner can provide vivo X300 evidence after local implementation.

## Suggested Implementation Waves

1. Scene assessment and route contract:
   - define `FullClearSceneAssessment`, route enum, and render model states.
   - no CameraX changes yet.

2. Deep-DOF route:
   - choose wide/ultra-wide/lens-node path when it is clearly better than bracket.
   - validate with diagnostics and preview guidance.

3. Bracket route V2:
   - improve V1 focus bracket with route scoring, motion/stability guard, and latency budget.

4. Fusion report:
   - introduce `FullClearFusionReport` and stricter confidence thresholds.
   - keep best-frame fallback as first-class success-with-degradation.

5. Real-device QA:
   - compare Photo, Scenery, Full Clear V1, and Full Clear V2 on vivo X300 scenes.
   - collect saved images, logs, route metadata, latency, and artifacts.

## Go / No-Go

V2 should proceed to implementation only if the team accepts that the first version may produce better guidance and fallback more often than true fusion. That is still valuable if it saves users from the wrong lens/focus choice during hiking/travel captures.

