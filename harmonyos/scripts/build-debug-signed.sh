#!/bin/bash
set -euo pipefail

# Reuse the retained debug signing identity; credentials stay outside the repository.
SOURCE_DIR="$(cd "$(dirname "$0")/.." && pwd)"
BUILD_DIR="${SITECAM_HARMONY_BUILD_DIR:-$HOME/Library/Caches/SiteCamHarmonyDebugBuild}"
SIGNING_CONFIG="${SITECAM_HARMONY_SIGNING_CONFIG:-$HOME/.ohos/sitecam-debug/signing-config.json}"
DEVECO="${DEVECO_HOME:-/Applications/DevEco-Studio.app/Contents}"
export PATH="$DEVECO/tools/node/bin:$DEVECO/tools/ohpm/bin:$PATH"
export DEVECO_SDK_HOME="$DEVECO/sdk"
test -f "$SIGNING_CONFIG" || { echo 'Missing existing local debug signing configuration.' >&2; exit 1; }
mkdir -p "$BUILD_DIR"
rsync -a --delete --exclude 'build/' --exclude '.hvigor/' --exclude 'oh_modules/' --exclude '.idea/' --exclude '.cxx/' --exclude 'local.properties' --exclude 'dist/' --exclude 'verification/' "$SOURCE_DIR/" "$BUILD_DIR/"
printf 'sdk.dir=%s/sdk\n' "$DEVECO" > "$BUILD_DIR/local.properties"
python3 - "$BUILD_DIR/build-profile.json5" "$SIGNING_CONFIG" <<'PY'
import json, sys
from pathlib import Path
path = Path(sys.argv[1])
profile = json.loads(path.read_text())
signing = json.loads(Path(sys.argv[2]).read_text())
for field in ('storeFile', 'certpath', 'profile'):
    if not Path(signing['material'][field]).is_file():
        raise SystemExit('Missing signing material: ' + field)
profile['app']['signingConfigs'] = [signing]
for product in profile['app']['products']:
    if product['name'] == 'default':
        product['signingConfig'] = signing['name']
path.chmod(0o600)
path.write_text(json.dumps(profile, ensure_ascii=False, indent=2) + '\n')
PY
cd "$BUILD_DIR"
ohpm install
"$DEVECO/tools/hvigor/bin/hvigorw" --mode module -p module=entry@default -p product=default -p buildMode=debug assembleHap --no-daemon
python3 - "$SOURCE_DIR" "$BUILD_DIR" <<'PY'
import json, shutil, sys
from pathlib import Path
source, build = map(Path, sys.argv[1:])
version = json.loads((source / 'AppScope/app.json5').read_text())['app']['versionName']
output = source / 'dist/debug'
output.mkdir(parents=True, exist_ok=True)
for suffix, root in [('hap', build / 'entry/build/default/outputs/default')]:
    candidates = list(root.glob('*-signed.' + suffix))
    if len(candidates) != 1:
        raise SystemExit('Expected one signed .' + suffix + ' under ' + str(root))
    dest = output / ('SiteCam-' + version + '-HarmonyOS-Debug-Signed.' + suffix)
    shutil.copyfile(candidates[0], dest)
    print(dest)
PY
