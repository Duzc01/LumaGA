#!/usr/bin/env bash
#
# bump-version.sh — 升级 LumaGA 版本号。
#
# versionName 与 versionCode 保持语义对应：1.x.y -> code 1xy
# （例如 1.1.0 -> 110，1.0.1 -> 101），避免手改出错。
#
# 用法:
#   scripts/bump-version.sh patch            # 小版本：1.0.0 -> 1.0.1，code 100 -> 101
#   scripts/bump-version.sh minor            # 中版本：1.0.0 -> 1.1.0，code 100 -> 110（末位归零）
#   scripts/bump-version.sh major            # 大版本：1.0.0 -> 2.0.0，code 100 -> 200（后两位归零）
#   scripts/bump-version.sh patch --commit   # 同时提交
#   scripts/bump-version.sh patch --push     # 提交并推送（隐含 --commit）
#
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
GRADLE_FILE="$ROOT/app/build.gradle.kts"

usage() {
  sed -n '2,13p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'
  exit 1
}

[ -f "$GRADLE_FILE" ] || { echo "找不到 $GRADLE_FILE"; exit 1; }
[ $# -ge 1 ] || usage

KIND="$1"
shift
COMMIT=0
PUSH=0
for arg in "$@"; do
  case "$arg" in
    --commit) COMMIT=1 ;;
    --push) COMMIT=1; PUSH=1 ;;
    *) echo "未知选项: $arg"; usage ;;
  esac
done

case "$KIND" in
  patch | minor | major) ;;
  *) echo "版本类型必须是 patch / minor / major"; usage ;;
esac

# 读取当前版本
VERSION_NAME="$(grep -o 'versionName = "[0-9.]*"' "$GRADLE_FILE" | grep -o '[0-9.]*')"
VERSION_CODE="$(grep -o 'versionCode = [0-9]*' "$GRADLE_FILE" | grep -o '[0-9]*')"
[ -n "$VERSION_NAME" ] && [ -n "$VERSION_CODE" ] || {
  echo "无法从 $GRADLE_FILE 解析版本号"; exit 1
}

IFS='.' read -r MAJOR MINOR PATCH <<<"$VERSION_NAME"
case "$KIND" in
  patch) PATCH=$((PATCH + 1)) ;;
  minor) MINOR=$((MINOR + 1)); PATCH=0 ;;
  major) MAJOR=$((MAJOR + 1)); MINOR=0; PATCH=0 ;;
esac

NEW_NAME="$MAJOR.$MINOR.$PATCH"
NEW_CODE=$((MAJOR * 100 + MINOR * 10 + PATCH))

# 防溢出：patch > 9 时 versionCode 会与下一个 minor 版本冲突
if [ "$PATCH" -gt 9 ]; then
  echo "警告: patch=$PATCH 超过 9，versionCode 将与 minor 版本冲突，请改用 minor/major。" >&2
fi

echo "当前版本: $VERSION_NAME (code $VERSION_CODE)"
echo "升级后:   $NEW_NAME (code $NEW_CODE)"

# 写回并校验
sed -i.bak \
  -e "s/versionCode = [0-9]*/versionCode = $NEW_CODE/" \
  -e "s/versionName = \"[0-9.]*\"/versionName = \"$NEW_NAME\"/" \
  "$GRADLE_FILE"
rm -f "$GRADLE_FILE.bak"

echo "---"
grep -E '^\s+versionCode|^\s+versionName' "$GRADLE_FILE"

if [ "$COMMIT" = 1 ]; then
  git -C "$ROOT" add app/build.gradle.kts
  git -C "$ROOT" commit -m "release: bump version to $NEW_NAME"
  echo "已提交: $NEW_NAME"
fi
if [ "$PUSH" = 1 ]; then
  git -C "$ROOT" push
  echo "已推送"
fi
