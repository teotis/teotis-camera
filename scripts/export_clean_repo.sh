#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SOURCE_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
TARGET_DIR="${OPENCAMERA_PUBLIC_REPO:-${SOURCE_DIR}/public/teotis-camera}"
PUBLIC_GIT_NAME="${OPENCAMERA_PUBLIC_GIT_NAME:-Teotis}"
PUBLIC_GIT_EMAIL="${OPENCAMERA_PUBLIC_GIT_EMAIL:-teotis@users.noreply.github.com}"

echo "=== Teotis Camera public export ==="
echo "source: ${SOURCE_DIR}"
echo "target: ${TARGET_DIR}"
echo ""

if [[ "${TARGET_DIR}" == "${SOURCE_DIR}" || "${TARGET_DIR}" == "${SOURCE_DIR}/"*"/.." ]]; then
    echo "Refusing unsafe target: ${TARGET_DIR}" >&2
    exit 1
fi

mkdir -p "${TARGET_DIR}"

if [[ ! -d "${TARGET_DIR}/.git" ]]; then
    echo "Initializing public repository metadata..."
    git -C "${TARGET_DIR}" init
    git -C "${TARGET_DIR}" remote add origin git@github.com:teotis/teotis-camera.git || true
fi

git -C "${TARGET_DIR}" config user.name "${PUBLIC_GIT_NAME}"
git -C "${TARGET_DIR}" config user.email "${PUBLIC_GIT_EMAIL}"

echo "Removing generated build outputs from public target..."
find "${TARGET_DIR}" \
    -path "${TARGET_DIR}/.git" -prune -o \
    -type d -name build -prune -exec rm -rf {} +
find "${TARGET_DIR}" -maxdepth 1 -type f \( \
    -name 'pragmatic_renewal_architect_report.html' -o \
    -name 'structural_abstraction_architect_report.html' \
    \) -delete

echo "Syncing source files..."
rsync -a --delete \
    --exclude='.git/' \
    --exclude='.gradle/' \
    --exclude='.idea/' \
    --exclude='build/' \
    --exclude='*/build/' \
    --exclude='.codegraph/' \
    --exclude='.tmp/' \
    --exclude='._*' \
    --exclude='.DS_Store' \
    --exclude='.claude/' \
    --exclude='.worktrees/' \
    --exclude='public/' \
    --exclude='codex/' \
    --exclude='docs/' \
    --exclude='specs/' \
    --exclude='scripts/' \
    --exclude='AGENTS.md' \
    --exclude='CLAUDE.md' \
    --exclude='GEMINI.md' \
    --exclude='V2-Readiness-Release-Gate-Report.md' \
    --exclude='pragmatic_renewal_architect_report.html' \
    --exclude='structural_abstraction_architect_report.html' \
    --exclude='local.properties' \
    --exclude='README.md' \
    --exclude='README_EN.md' \
    --exclude='LICENSE' \
    --exclude='NOTICE' \
    --exclude='AUTHORS' \
    "${SOURCE_DIR}/" "${TARGET_DIR}/"

find "${TARGET_DIR}" -name '._*' -delete

echo "Writing public root Gradle config..."
cat > "${TARGET_DIR}/build.gradle.kts" << 'GRADLE'
plugins {
    id("com.android.application") version "8.5.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.24" apply false
    id("org.jetbrains.kotlin.jvm") version "1.9.24" apply false
}

val sharedBuildRoot = file(
    (project.findProperty("opencamera.buildRoot") as? String)
        ?: System.getProperty("opencamera.buildRoot")
        ?: System.getenv("OPENCAMERA_BUILD_ROOT")
        ?: System.getenv("CODEX_BUILD_ROOT")
        ?: "${System.getProperty("user.home")}/.codex-build/TeotisCamera"
)

allprojects {
    val projectBuildPath = if (path == ":") {
        "root"
    } else {
        path.removePrefix(":").replace(':', '/')
    }
    layout.buildDirectory.set(sharedBuildRoot.resolve(projectBuildPath))
}
GRADLE

if [[ -f "${TARGET_DIR}/settings.gradle.kts" ]]; then
    perl -0pi -e 's/rootProject\.name\s*=\s*"OpenCamera"/rootProject.name = "TeotisCamera"/g' "${TARGET_DIR}/settings.gradle.kts"
fi

cat > "${TARGET_DIR}/.gitignore" << 'GITIGNORE'
.gradle/
build/
*/build/
local.properties
._*
.DS_Store
__pycache__/
*.pyc
*.iml
.idea/

# Git worktrees
.worktrees/
.claude/worktrees/
GITIGNORE

find "${TARGET_DIR}" \
    -path "${TARGET_DIR}/.git" -prune -o \
    -name '._*' -delete

echo "Running public release safety check..."
"${SCRIPT_DIR}/verify_public_release_safety.sh" "${TARGET_DIR}"

echo ""
echo "=== export complete ==="
echo "public repository: ${TARGET_DIR}"
echo "effective git identity: $(git -C "${TARGET_DIR}" config user.name) <$(git -C "${TARGET_DIR}" config user.email)>"
echo "tracked/public files: $(git -C "${TARGET_DIR}" ls-files | wc -l | tr -d ' ')"
