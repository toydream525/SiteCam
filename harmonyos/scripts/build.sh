#!/bin/bash
set -euo pipefail
SOURCE_DIR="$(cd "$(dirname "$0")/.." && pwd)"
BUILD_DIR="${SITECAM_HARMONY_BUILD_DIR:-$HOME/Library/Caches/SiteCamHarmonyBuild}"
DEVECO="${DEVECO_HOME:-/Applications/DevEco-Studio.app/Contents}"
export PATH="$DEVECO/tools/node/bin:$DEVECO/tools/ohpm/bin:$PATH"
export DEVECO_SDK_HOME="$DEVECO/sdk"
mkdir -p "$BUILD_DIR"
rsync -a --exclude 'build/' --exclude '.hvigor/' --exclude 'oh_modules/' --exclude 'local.properties' --exclude 'dist/' --exclude 'verification/' "$SOURCE_DIR/" "$BUILD_DIR/"
printf 'sdk.dir=%s/sdk\n' "$DEVECO" > "$BUILD_DIR/local.properties"
cd "$BUILD_DIR"
ohpm install
"$DEVECO/tools/hvigor/bin/hvigorw" --mode module -p product=default -p module=entry@"${2:-default}" -p buildMode="${1:-debug}" assembleHap --no-daemon
mkdir -p "$SOURCE_DIR/dist"
TARGET="${2:-default}"
MODE="${1:-debug}"
cp "entry/build/default/outputs/$TARGET/entry-$TARGET-unsigned.hap" "$SOURCE_DIR/dist/"
mkdir -p "$SOURCE_DIR/dist/$MODE"
cp "entry/build/default/outputs/$TARGET/entry-$TARGET-unsigned.hap" "$SOURCE_DIR/dist/$MODE/"
