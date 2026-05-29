# Codex Document Asset Cleanup P1

Date: 2026-05-29

## Purpose

Reduce `codex/` context noise without losing product standards, current status, or historical evidence that is still useful for acceptance and agent handoff.

This is a migration plan, not an immediate deletion approval. P1 work should run after the P0 cleanup has removed deterministic residue: AppleDouble files, misleading old absolute paths in active status docs, and the empty `codex/plan.md` entry.

## Current Facts

- `docs/plans/` is the canonical location for handoff, orchestration, feature-plan, and research packages.
- `codex/documentation.md` remains the current living status document, but it mixes current state with long historical archive entries.
- `codex/Product-2.0-Standard.md` remains the canonical 2.0 product/readiness standard.
- `codex/v2_ui/` and `codex/capability_kernel_v2/` are still referenced by current 2.0 product and readiness docs.
- `codex/artifacts/`, `codex/visual_checks/`, and `codex/v2_ui/reference_images/` contain tracked evidence/reference assets rather than source rules.
- Several root-level readiness reports are historical point-in-time reviews; they are useful as evidence, but risky as default model context because older `NO GO` conclusions can be mistaken for current state.

## Protect

Keep these active or easy to discover:

- `codex/documentation.md`: current status only, eventually shortened.
- `codex/Product-2.0-Standard.md`: product/readiness standard.
- `codex/implement.md`: local execution guidance while it remains referenced by project docs.
- `codex/prompt.md`: long-range architecture intent, read only when broader context is needed.
- `codex/v2_ui/00_v2_ui_index.md` and `codex/capability_kernel_v2/00_capability_kernel_v2_index.md`: design package indexes.

Do not delete `codex/v2_ui/` or `codex/capability_kernel_v2/` until every live reference has either been migrated to `docs/plans/` or summarized in a smaller current standard.

## Experiment

Run the migration in small, reversible steps:

1. Create `codex/archive/` with a short `README.md` explaining that archived files are historical evidence, not default instructions.
2. Move point-in-time readiness/audit reports into `codex/archive/readiness/`:
   - `Feature-Availability-Audit.md`
   - `IO-Chain-Audit.md`
   - `Stability-Observability-Audit.md`
   - `V2-Readiness-Hard10-Controller-Review.md`
   - `V2-Readiness-Post-Fix-Review.md`
   - `V2-Readiness-Final-Local-Gate-Review.md`
   - `Fifth-Recording-Hard10-Multimodal-QA-Report.md`
3. Move evidence assets into an evidence location:
   - Preferred: `docs/evidence/`
   - Acceptable if tightly coupled to a package: `docs/plans/<package>/evidence/`
   - Candidates: `codex/artifacts/`, `codex/visual_checks/`, `codex/v2_ui/reference_images/`
4. Keep only indexes or README files in old locations when necessary, with links to the new locations.
5. Update references in `codex/documentation.md`, `codex/Product-2.0-Standard.md`, and `docs/plans/INDEX.md`.

## Defer

Do not shrink `codex/documentation.md` in the same pass as file moves. Its historical archive is noisy, but it is still the best single source for prior decisions. After references are stable, do a separate pass:

- Keep the top current-status section.
- Move old dated entries into `codex/archive/status/`.
- Replace long historical paragraphs with a compact index of links.

Do not change `codex/prompt.md` or `codex/implement.md` semantics during the asset migration unless the user explicitly requests instruction cleanup.

## Acceptance Checks

Before considering P1 complete:

```bash
rtk rg -n "codex/(Feature-Availability-Audit|IO-Chain-Audit|Stability-Observability-Audit|V2-Readiness|Fifth-Recording)" codex docs/plans AGENTS.md
rtk rg -n "project/codex_camera|New_Camera" codex AGENTS.md CLAUDE.md
rtk rg --files -g "._*" codex docs
rtk git status --short -- codex docs/plans docs/evidence
```

Expected result:

- No AppleDouble files.
- No active `codex/` references to old absolute workspace paths.
- Every moved report or asset has a replacement link or index entry.
- `Product-2.0-Standard.md` still points to the current product standard and evidence locations.

## Stop Gates

Stop and ask the user before continuing if:

- A report is still referenced by active implementation packages and moving it would create broad churn.
- A visual/evidence asset is needed for an active real-device QA package and its new location is ambiguous.
- A proposed change would rewrite `codex/prompt.md`, `codex/implement.md`, or project stage rules.
- The migration would require deleting tracked evidence rather than moving or indexing it.
