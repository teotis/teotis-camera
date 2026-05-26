# Status Files

Coordinator truth lives here, in the main checkout plan directory.

- Package agents may edit only their own `status/<package-id>.md` plus their own row in `state.tsv`.
- Do not rely on worktree-local copies of these files.
- `state.tsv` is machine-readable scheduler state.
- Markdown files are human evidence packs.

