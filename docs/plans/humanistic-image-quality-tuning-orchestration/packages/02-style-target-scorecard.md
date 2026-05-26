# Package 02 — Style Target Scorecard

## Package ID

`02-style-target-scorecard`

## Goal

Define a realistic product style target for Humanistic / Color Lab dusk and night output. The output should be a scorecard and style language that favors stable exposure, restrained highlight rolloff, quieter shadows, natural local contrast, and recognizable but not excessive color, without promising vendor-camera parity.

## Allowed Paths

- Read-only: `docs/plans/**`, `core/settings/**`, `core/effect/**`, `feature/mode-humanistic/**`, `feature/mode-photo/**`, `app/src/**`.
- Writable: `docs/plans/humanistic-image-quality-tuning-orchestration/status/02-style-target-scorecard.md` only.

## Forbidden Paths

- Do not edit runtime code, tests, resources, shared docs, `INDEX.md`, package docs, or other status files.
- Do not import external image assets into the repo.

## Dependencies

None. Can run in parallel with package 01.

## Parallel Safety

Safe. This package writes only its own status file.

## Product Constraints

- Avoid "match vivo/system camera" language.
- Do not require RAW, multi-frame HDR, vendor ISP access, or private SDKs.
- Keep the target compatible with existing two-axis Color Lab semantics and Humanistic style profiles.
- Style should be usable for real photos, not just dramatic sample thumbnails.

## Tasks

1. Read existing Humanistic, Rendering 2.0, and Color Lab plans.
2. Define 3-4 practical style profiles or target states for dusk/night:
   - neutral baseline;
   - humanistic warm deep;
   - city night cool deep;
   - soft dusk airy/warm, if justified.
3. For each style target, specify:
   - exposure intent;
   - highlight rolloff intent;
   - shadow/noise intent;
   - local contrast/sharpening intent;
   - white balance/color bias;
   - saturation/chroma limits;
   - skin/neutral/gray protection;
   - preview/saved honesty requirements.
4. Produce a 1-5 scorecard rubric that a human reviewer can apply to side-by-side samples.
5. Define rejection examples: neon over-saturation, crushed shadows, gray objects tinted, halos, plastic denoise, crunchy sharpening, inconsistent preview/saved look.

## Acceptance Criteria

- Status file contains a concise style target matrix with measurable or reviewable criteria.
- Scorecard covers the user-observed dimensions: sky layers, lamp highlights, shadow noise, local contrast, white balance, color tendency, sharpness/texture, overall atmosphere.
- The target explicitly says what is acceptable even if vivo still looks better.
- The target separates subjective taste criteria from deterministic implementation checks.
- The target can be consumed by package 03 and package 04 without asking the user for another broad product decision.

## Verification Commands

```bash
rtk rg -n "Humanistic|Color Lab|ColorLab|PerceptualColorRecipe|tone|chroma|highlight|shadow|warm|cool|vivo|X300|visual QA|real-device" docs/plans core feature app
rtk git status --short
```

## Expected Evidence Pack

- Worktree path and branch, if any.
- Commands run and short output summary.
- Style target matrix.
- Human review scorecard.
- Explicit non-goals and parity disclaimers.
- Unresolved product questions, if any.
- Self-certification that only the allowed status file was touched.

## Stop Gates

Stop and ask before requiring new UI controls, claiming vendor parity, or turning a taste preference into a mandatory implementation without evidence.
