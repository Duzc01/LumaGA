#!/usr/bin/env bash
#
# create-release.sh — 完整发布流程：升级版本号 -> 提交推送 -> 构建正式签名
# APK -> 打 tag -> 创建 GitHub Release 并上传安装包。
#
# 构建方式：
#   - 若本地有签名材料（~/Documents/LumaGA-release-signing/），本地构建，
#     无需等 CI；
#   - 否则推送后等待 CI（需要 GitHub Secrets 中的签名配置）。
#
# 用法:
#   scripts/create-release.sh patch   # 小版本发布
#   scripts/create-release.sh minor   # 中版本发布
#   scripts/create-release.sh major   # 大版本发布
#
# Release notes 取自 CHANGELOG.md 中对应版本（## [x.y.z]）的章节；
# 发布前先在 CHANGELOG.md 顶部补好该版本条目并提交。没有条目时回退到
# GitHub 自动生成的 notes。
#
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
GRADLE_FILE="$ROOT/app/build.gradle.kts"
SIGNING_DIR="${LUMA_SIGNING_DIR:-$HOME/Documents/LumaGA-release-signing}"
PROPS_NAME="release-keystore.properties"
KEYSTORE_NAME="release.keystore"

usage() {
  sed -n '2,14p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'
  exit 1
}

[ $# -ge 1 ] || usage
KIND="$1"
case "$KIND" in
  patch | minor | major) ;;
  *) echo "版本类型必须是 patch / minor / major"; usage ;;
esac

command -v gh >/dev/null || { echo "需要 GitHub CLI (gh)"; exit 1; }
gh auth status >/dev/null 2>&1 || { echo "gh 未登录，请先 gh auth login"; exit 1; }
[ -f "$GRADLE_FILE" ] || { echo "找不到 $GRADLE_FILE"; exit 1; }

REMOTE="$(git -C "$ROOT" remote get-url origin | sed -E 's#.*github.com[:/]##; s#\.git$##')"
[ -n "$REMOTE" ] || { echo "无法解析 origin 仓库"; exit 1; }

# 工作区必须干净：版本号由本脚本统一修改，避免混入无关改动
if [ -n "$(git -C "$ROOT" status --porcelain)" ]; then
  echo "工作区有未提交改动，请先处理："
  git -C "$ROOT" status --short
  exit 1
fi

# 1. 升级版本号并推送（触发 CI）
"$ROOT/scripts/bump-version.sh" "$KIND" --push

VERSION_NAME="$(grep -o 'versionName = "[0-9.]*"' "$GRADLE_FILE" | grep -o '[0-9.]*')"
TAG="v$VERSION_NAME"
echo "==> 发布版本 $TAG"

# 2. 构建正式签名 APK
APK="$ROOT/app/build/outputs/apk/release/app-release.apk"
BUILT=0

if [ -f "$SIGNING_DIR/$PROPS_NAME" ] && [ -f "$SIGNING_DIR/$KEYSTORE_NAME" ]; then
  echo "==> 本地构建（使用 ${SIGNING_DIR}）"
  cp "$SIGNING_DIR/$PROPS_NAME" "$ROOT/app/$PROPS_NAME"
  cp "$SIGNING_DIR/$KEYSTORE_NAME" "$ROOT/app/$KEYSTORE_NAME"
  GRADLE_ARGS=("$ROOT/gradlew" -p "$ROOT" :app:assembleRelease --offline -x lint)
  if [ -d "$ROOT/.gradle-home" ]; then
    GRADLE_USER_HOME="$ROOT/.gradle-home" "${GRADLE_ARGS[@]}"
  else
    "${GRADLE_ARGS[@]}"
  fi
  rm -f "$ROOT/app/$PROPS_NAME" "$ROOT/app/$KEYSTORE_NAME"
  [ -f "$APK" ] || { echo "本地构建失败：未生成 $APK"; exit 1; }
  BUILT=1
else
  echo "==> 本地签名材料不可用，等待 CI 构建..."
  HEAD="$(git -C "$ROOT" rev-parse HEAD)"
  for _ in $(seq 1 30); do
    RUN_ID="$(gh -R "$REMOTE" run list --limit 5 --json databaseId,headSha,status --jq \
      ".[] | select(.headSha == \"$HEAD\") | .databaseId" | head -1)"
    [ -n "$RUN_ID" ] && break
    sleep 5
  done
  [ -n "${RUN_ID:-}" ] || { echo "找不到对应 push 的 CI 运行"; exit 1; }
  gh -R "$REMOTE" run watch "$RUN_ID" --exit-status >/dev/null
  DL_DIR="$(mktemp -d)"
  gh -R "$REMOTE" run download "$RUN_ID" --dir "$DL_DIR" >/dev/null
  APK="$(find "$DL_DIR" -name '*.apk' | head -1)"
  [ -n "$APK" ] || { echo "CI artifact 中没有 APK"; exit 1; }
  BUILT=1
fi

# 3. 校验 APK 版本号与预期一致
if command -v aapt >/dev/null; then
  BADGING="$(aapt dump badging "$APK" 2>/dev/null | grep -E "versionCode|versionName")"
  echo "$BADGING"
  echo "$BADGING" | grep -q "versionCode='$((VERSION_NAME))'" 2>/dev/null || \
    echo "$BADGING" | grep -q "versionCode='$(
      IFS='.' read -r M1 M2 M3 <<<"$VERSION_NAME"
      echo $((M1 * 10000 + M2 * 100 + M3))
    )'" || {
      echo "警告: APK 版本号与预期 ($VERSION_NAME) 不一致！"; exit 1
    }
fi

# 4. 打 tag 并推送
if ! git -C "$ROOT" rev-parse -q --verify "refs/tags/$TAG" >/dev/null; then
  git -C "$ROOT" tag -a "$TAG" -m "LumaGA $TAG"
  git -C "$ROOT" push origin "$TAG"
fi

# 5. 创建 GitHub Release（已存在则仅更新 APK）
# 优先使用 CHANGELOG.md 中对应版本章节作为正文；没有该章节则回退到
# 自动生成的 notes。发布前记得先在 CHANGELOG.md 顶部补好新版本条目
# 并提交（脚本要求工作区干净）。
NOTES_FILE=""
if [ -f "$ROOT/CHANGELOG.md" ]; then
  NOTES_FILE="$(mktemp)"
  awk -v ver="$VERSION_NAME" '
    index($0, "## [" ver "]") == 1 { insec = 1; next }
    insec && $0 ~ /^## \[/ { exit }
    insec { print }
  ' "$ROOT/CHANGELOG.md" > "$NOTES_FILE"
  if [ ! -s "$NOTES_FILE" ]; then
    rm -f "$NOTES_FILE"
    NOTES_FILE=""
  fi
fi

RELEASE_ARGS=(--title "LumaGA $TAG")
if [ -n "$NOTES_FILE" ]; then
  RELEASE_ARGS+=(--notes-file "$NOTES_FILE")
  echo "==> 使用 CHANGELOG.md 中 $VERSION_NAME 的条目作为 release notes"
else
  RELEASE_ARGS+=(--generate-notes)
  echo "==> CHANGELOG.md 中没有 $VERSION_NAME 的条目，使用自动生成的 notes"
fi

if gh -R "$REMOTE" release create "$TAG" "${RELEASE_ARGS[@]}" "$APK"; then
  echo "==> Release 已创建"
else
  echo "==> Release 已存在，更新 APK"
  gh -R "$REMOTE" release upload "$TAG" "$APK" --clobber
  if [ -n "$NOTES_FILE" ]; then
    gh -R "$REMOTE" release edit "$TAG" --notes-file "$NOTES_FILE" >/dev/null || true
  fi
fi
[ -z "$NOTES_FILE" ] || rm -f "$NOTES_FILE"

# 6. 验证
gh -R "$REMOTE" release view "$TAG" | head -8
echo
echo "完成: https://github.com/$REMOTE/releases/tag/$TAG"
