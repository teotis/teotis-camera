#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SOURCE_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
TARGET_DIR="/Volumes/Extreme_SSD/project/opencamera-clean"

echo "=== OpenCamera 纯净版仓库导出 ==="
echo "源目录: ${SOURCE_DIR}"
echo "目标目录: ${TARGET_DIR}"
echo ""

# 1. 创建目标目录
if [[ -d "${TARGET_DIR}" ]]; then
    echo "目标目录已存在，将清空后重新创建..."
    rm -rf "${TARGET_DIR}"
fi
mkdir -p "${TARGET_DIR}"

# 2. rsync 复制源码，排除非源码内容
echo "正在复制源码..."
rsync -a \
    --exclude='.git/' \
    --exclude='.gradle/' \
    --exclude='.idea/' \
    --exclude='.codegraph/' \
    --exclude='.tmp/' \
    --exclude='._*' \
    --exclude='.DS_Store' \
    --exclude='codex/' \
    --exclude='docs/' \
    --exclude='specs/' \
    --exclude='scripts/' \
    --exclude='AGENTS.md' \
    --exclude='V2-Readiness-Release-Gate-Report.md' \
    --exclude='local.properties' \
    "${SOURCE_DIR}/" "${TARGET_DIR}/"

# 3. 修改 build.gradle.kts，移除 sharedBuildRoot
echo "正在修改 build.gradle.kts..."
cat > "${TARGET_DIR}/build.gradle.kts" << 'GRADLE'
plugins {
    id("com.android.application") version "8.5.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.24" apply false
    id("org.jetbrains.kotlin.jvm") version "1.9.24" apply false
}
GRADLE

# 4. 初始化 git 仓库
echo "正在初始化 git 仓库..."
cd "${TARGET_DIR}"
git init
git add -A
git commit -m "feat: OpenCamera 初始纯净版"

echo ""
echo "=== 导出完成 ==="
echo "纯净版仓库位于: ${TARGET_DIR}"
echo "共 $(find . -type f | wc -l | tr -d ' ') 个文件"
