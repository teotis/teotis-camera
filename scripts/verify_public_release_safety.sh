#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
TARGET_DIR="${1:-${REPO_ROOT}/public/teotis-camera}"

DENY_IDENTITY='dingren|dingren@|xiaomi|/Users/|/Volumes/Extreme|丁仁'
DENY_BRAND='Apple|iPhone|vivo|Vivo|Xiaomi|MIUI|MiuiCamera|miuicamera|Leica|Hasselblad|竞品|参考|学习|借鉴|复刻|对标|系统相机'
DENY_SECRET='AKIA[0-9A-Z]{16}|-----BEGIN (RSA |OPENSSH |EC |DSA )?PRIVATE KEY-----|ghp_[A-Za-z0-9_]{20,}|sk-[A-Za-z0-9]{20,}'

findings=0
warnings=0

note() {
    printf '[public-safety] %s\n' "$*"
}

fail() {
    findings=$((findings + 1))
    printf '[public-safety] FINDING: %s\n' "$*" >&2
}

warn() {
    warnings=$((warnings + 1))
    printf '[public-safety] warning: %s\n' "$*" >&2
}

capture_grep() {
    local label="$1"
    local pattern="$2"
    local cmd_output
    shift 2
    if cmd_output="$("$@" 2>/dev/null | grep -E -i "$pattern" || true)" && [[ -n "${cmd_output}" ]]; then
        fail "${label}"
        printf '%s\n' "${cmd_output}" >&2
    fi
}

if [[ ! -d "${TARGET_DIR}" ]]; then
    fail "target directory does not exist: ${TARGET_DIR}"
    exit 1
fi

if [[ ! -d "${TARGET_DIR}/.git" ]]; then
    fail "target is not a Git repository: ${TARGET_DIR}"
    exit 1
fi

note "target: ${TARGET_DIR}"

capture_grep \
    "git author/committer identity contains private identity" \
    "${DENY_IDENTITY}" \
    git -C "${TARGET_DIR}" log --all --format='%H %an <%ae> %cn <%ce> %s'

effective_name="$(git -C "${TARGET_DIR}" config --get user.name || true)"
effective_email="$(git -C "${TARGET_DIR}" config --get user.email || true)"
if [[ -z "${effective_name}" || -z "${effective_email}" ]]; then
    fail "public repo must set local user.name and user.email"
elif printf '%s\n%s\n' "${effective_name}" "${effective_email}" | grep -E -i "${DENY_IDENTITY}" >/dev/null; then
    fail "public repo effective git identity is private: ${effective_name} <${effective_email}>"
fi

FORBIDDEN_TRACKED='(^|/)(AGENTS\.md|CLAUDE\.md|GEMINI\.md|local\.properties|V2-Readiness-Release-Gate-Report\.md|pragmatic_renewal_architect_report\.html|structural_abstraction_architect_report\.html)$|^(codex|scripts|specs)/'

if git -C "${TARGET_DIR}" ls-files | grep -E "${FORBIDDEN_TRACKED}" >/dev/null; then
    fail "forbidden internal files are tracked"
    git -C "${TARGET_DIR}" ls-files | grep -E "${FORBIDDEN_TRACKED}" >&2
fi

if find "${TARGET_DIR}" \
    -path "${TARGET_DIR}/.git" -prune -o \
    -type d -name build -print | grep . >/tmp/public-safety-build-dirs.$$; then
    fail "generated build directories exist in public repo"
    sed -n '1,120p' /tmp/public-safety-build-dirs.$$ >&2
fi
rm -f /tmp/public-safety-build-dirs.$$

if git -C "${TARGET_DIR}" grep -I -n -E -i "${DENY_IDENTITY}" -- . >/tmp/public-safety-identity.$$ 2>/dev/null; then
    fail "tracked files contain private identity or local paths"
    sed -n '1,120p' /tmp/public-safety-identity.$$ >&2
fi

if git -C "${TARGET_DIR}" grep -I -n -E -i "${DENY_BRAND}" -- . >/tmp/public-safety-brand.$$ 2>/dev/null; then
    fail "tracked files contain unreviewed competitor/reference terms"
    sed -n '1,120p' /tmp/public-safety-brand.$$ >&2
fi

if git -C "${TARGET_DIR}" grep -I -n -E "${DENY_SECRET}" -- . >/tmp/public-safety-secrets.$$ 2>/dev/null; then
    fail "tracked files contain high-confidence secret material"
    sed -n '1,120p' /tmp/public-safety-secrets.$$ >&2
fi

rm -f /tmp/public-safety-identity.$$ /tmp/public-safety-brand.$$ /tmp/public-safety-secrets.$$

if find "${TARGET_DIR}" \( -name '*.env' -o -name '*.key' -o -name '*.pem' -o -name '*.keystore' \) -print | grep . >/tmp/public-safety-files.$$; then
    fail "sensitive-looking files exist in public repo"
    sed -n '1,120p' /tmp/public-safety-files.$$ >&2
fi
rm -f /tmp/public-safety-files.$$

if [[ -d "${TARGET_DIR}/docs/assets" ]]; then
    while IFS= read -r -d '' asset; do
        if strings "${asset}" | grep -E -i "${DENY_IDENTITY}|${DENY_BRAND}" >/tmp/public-safety-asset.$$; then
            fail "asset metadata/string content contains private identity or competitor terms: ${asset}"
            sed -n '1,40p' /tmp/public-safety-asset.$$ >&2
        fi
        rm -f /tmp/public-safety-asset.$$
    done < <(find "${TARGET_DIR}/docs/assets" -type f \( -name '*.jpg' -o -name '*.jpeg' -o -name '*.png' -o -name '*.webp' \) -print0)
else
    warn "no docs/assets directory found"
fi

if git -C "${TARGET_DIR}" status --short --ignored | grep -E '^\?\? |^!! ' | grep -E '(^|[[:space:]/])(\.env|.*\.key|.*\.pem|local\.properties|AGENTS\.md|CLAUDE\.md|GEMINI\.md|pragmatic_renewal_architect_report\.html|structural_abstraction_architect_report\.html)$' >/tmp/public-safety-untracked.$$; then
    fail "sensitive untracked/ignored files exist"
    sed -n '1,120p' /tmp/public-safety-untracked.$$ >&2
fi
rm -f /tmp/public-safety-untracked.$$

if [[ "${findings}" -gt 0 ]]; then
    note "failed with ${findings} finding(s), ${warnings} warning(s)"
    exit 1
fi

note "passed with ${warnings} warning(s)"
