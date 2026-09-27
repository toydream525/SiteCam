#!/bin/bash
set -euo pipefail

# Build the pure-offline HarmonyOS variant in an isolated cache. Online sources
# remain the default and are never rewritten by this script.
SOURCE_DIR="$(cd "$(dirname "$0")/.." && pwd)"
BUILD_DIR="${SITECAM_HARMONY_OFFLINE_BUILD_DIR:-$HOME/Library/Caches/SiteCamHarmonyOfflineBuild}"
DEVECO="${DEVECO_HOME:-/Applications/DevEco-Studio.app/Contents}"
MODE="${1:-release}"
case "$MODE" in
  debug) MODE_LABEL="Debug" ;;
  release) MODE_LABEL="Release" ;;
  *) echo 'Usage: build-offline.sh [debug|release]' >&2; exit 2 ;;
esac

export PATH="$DEVECO/tools/node/bin:$DEVECO/tools/ohpm/bin:$PATH"
export DEVECO_SDK_HOME="$DEVECO/sdk"
OVERLAY_DIR="$SOURCE_DIR/variants/offline"
mkdir -p "$BUILD_DIR"
rsync -a --exclude 'build/' --exclude '.hvigor/' --exclude 'oh_modules/' --exclude '.idea/' --exclude '.cxx/' --exclude 'local.properties' --exclude 'dist/' --exclude 'verification/' "$SOURCE_DIR/" "$BUILD_DIR/"
printf 'sdk.dir=%s/sdk\n' "$DEVECO" > "$BUILD_DIR/local.properties"

cp "$OVERLAY_DIR/entry/src/main/ets/core/BuildVariant.ets" "$BUILD_DIR/entry/src/main/ets/core/BuildVariant.ets"
cp "$OVERLAY_DIR/entry/src/main/ets/core/LocationService.ets" "$BUILD_DIR/entry/src/main/ets/core/LocationService.ets"
cp "$OVERLAY_DIR/entry/src/main/ets/core/ExternalLinkService.ets" "$BUILD_DIR/entry/src/main/ets/core/ExternalLinkService.ets"
cp "$OVERLAY_DIR/entry/src/main/ets/pages/components/SiteCamEasterEggDialog.ets" "$BUILD_DIR/entry/src/main/ets/pages/components/SiteCamEasterEggDialog.ets"

python3 - "$SOURCE_DIR" "$BUILD_DIR" <<'PY'
import json, sys
from pathlib import Path

source, build = map(Path, sys.argv[1:])
privacy_lines = []
for line in (source / 'offline/PRIVACY-POLICY.md').read_text().splitlines():
    line = line.strip()
    if line.startswith('## '):
        privacy_lines.append('§' + line[3:])
    elif line:
        privacy_lines.append(line)
privacy_source = 'export const OFFLINE_PRIVACY_LINES: string[] = [\n'
privacy_source += ''.join('  ' + json.dumps(line, ensure_ascii=False) + ',\n' for line in privacy_lines)
privacy_source += '];\n'
(build / 'entry/src/main/ets/core/OfflinePrivacy.ets').write_text(privacy_source)

app_path = build / 'AppScope/app.json5'
app = json.loads(app_path.read_text())
if app['app']['bundleName'] != 'com.sitecam.app':
    raise SystemExit('Offline build must keep the existing AppGallery bundle name.')
if app['app']['versionName'] != '0.3.5' or app['app']['versionCode'] != 18:
    raise SystemExit('Offline build version must remain 0.3.5 / 18.')
app_path.write_text(json.dumps(app, ensure_ascii=False, indent=2) + '\n')

name_path = build / 'AppScope/resources/base/element/string.json'
names = json.loads(name_path.read_text())
for entry in names['string']:
    if entry['name'] == 'app_name':
        entry['value'] = '工程水印相机'
        break
else:
    raise SystemExit('Missing app_name resource.')
name_path.write_text(json.dumps(names, ensure_ascii=False, indent=2) + '\n')

manifest_path = build / 'entry/src/main/module.json5'
manifest = json.loads(manifest_path.read_text())
module = manifest['module']
allowed = {'ohos.permission.CAMERA', 'ohos.permission.MICROPHONE',
           'ohos.permission.LOCATION', 'ohos.permission.APPROXIMATELY_LOCATION'}
module['requestPermissions'] = [p for p in module.get('requestPermissions', []) if p.get('name') in allowed]
required = {'ohos.permission.CAMERA', 'ohos.permission.MICROPHONE',
            'ohos.permission.LOCATION', 'ohos.permission.APPROXIMATELY_LOCATION'}
if {p.get('name') for p in module['requestPermissions']} != required:
    raise SystemExit('Offline manifest must retain the camera, microphone, and HarmonyOS location permission pair.')
manifest_path.write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + '\n')

strings_path = build / 'entry/src/main/resources/base/element/string.json'
strings = json.loads(strings_path.read_text())
for entry in strings['string']:
    if entry['name'] == 'module_desc':
        entry['value'] = '纯离线工程拍摄与现场资料归档'
    elif entry['name'] == 'permission_location':
        entry['value'] = '用于前台获取系统报告的 GNSS 卫星坐标及可用高度'
strings_path.write_text(json.dumps(strings, ensure_ascii=False, indent=2) + '\n')

forbidden = ('getAddressesFromLocation', 'isGeocoderAvailable', 'ohos.want.action.viewData',
             'startAbility({ uri:', 'NetworkKit', '@kit.NetworkKit', 'HttpRequest')
source_root = build / 'entry/src/main/ets'
for path in source_root.rglob('*.ets'):
    content = path.read_text(errors='ignore')
    for token in forbidden:
        if token in content:
            raise SystemExit(f'Offline source still contains a network/geocoder/external-open implementation marker: {path.name}')
PY

cd "$BUILD_DIR"
ohpm install
"$DEVECO/tools/hvigor/bin/hvigorw" --mode module -p product=default -p module=entry@default -p buildMode="$MODE" assembleHap --no-daemon

OUTPUT="$BUILD_DIR/entry/build/default/outputs/default/entry-default-unsigned.hap"
test -f "$OUTPUT" || { echo 'Expected unsigned offline HAP was not produced.' >&2; exit 1; }
DEST_DIR="$SOURCE_DIR/dist/offline"
mkdir -p "$DEST_DIR"
VERSION="$(python3 -c "import json;print(json.load(open('$SOURCE_DIR/AppScope/app.json5'))['app']['versionName'])")"
DEST="$DEST_DIR/SiteCam-$VERSION-HarmonyOS-Offline-$MODE_LABEL-Unsigned.hap"
cp "$OUTPUT" "$DEST"
echo "$DEST"
