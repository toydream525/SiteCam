#!/usr/bin/env python3
"""Run the packaged HarmonyOS runtime checks on an explicitly selected developer emulator."""
import argparse
import json
import os
from pathlib import Path
import subprocess
import time

parser = argparse.ArgumentParser()
parser.add_argument('--main-hap', required=True, type=Path, help='Existing matching main/default HAP containing libsitecam.so')
parser.add_argument('--test-hap', required=True, type=Path, help='Existing matching ohosTest HAP using the same application/signing identity')
parser.add_argument('--output', type=Path, help='New result JSON path; existing files are never overwritten')
parser.add_argument('--target', required=True, help='HDC target, for example 127.0.0.1:5557')
args = parser.parse_args()
root = Path(__file__).resolve().parents[1]
deveco = Path(os.environ.get('DEVECO_HOME', '/Applications/DevEco-Studio.app/Contents'))
hdc = deveco / 'sdk/default/openharmony/toolchains/hdc'

def run(*parts):
    return subprocess.run([str(hdc), '-t', args.target, *parts], check=True, capture_output=True, text=True).stdout

# Use caller-supplied packages without rebuilding, signing, or overwriting dist.
packages = [('Main/default', args.main_hap.resolve()), ('Test', args.test_hap.resolve())]
if packages[0][1] == packages[1][1]:
    parser.error('--main-hap and --test-hap must be different packages')
for label, package in packages:
    if not package.is_file() or package.suffix.lower() != '.hap':
        parser.error(f'{label} HAP does not exist: {package}')
output = (args.output or root / 'verification' / f'runtime-{time.time_ns()}.json').resolve()
if output.exists():
    raise SystemExit(f'Result path already exists; existing evidence preserved: {output}')
# Native libraries are supplied by the main HAP, so this order is required.
for label, package in packages:
    try:
        installation = run('install', str(package))
    except subprocess.CalledProcessError as error:
        raise SystemExit(f'{label} HAP installation failed; no runtime results accepted.\n{error.stdout}\n{error.stderr}') from error
    print(installation)
    if 'install bundle successfully' not in installation:
        raise SystemExit(f'{label} HAP installation failed; no runtime results accepted.')
# The suite uses namespaced databases and test-module files; it does not seed the application.
run('shell', 'aa', 'force-stop', 'com.sitecam.app')
remote = '/data/app/el2/100/base/com.sitecam.app/haps/entry_test/files/verification.json'
run('shell', 'rm', '-f', remote)
print(run('shell', 'aa', 'start', '-b', 'com.sitecam.app', '-a', 'TestAbility', '-m', 'entry_test'))
for _ in range(60):
    status = run('shell', 'ls', remote)
    if 'No such file' not in status and remote in status:
        break
    time.sleep(1)
else:
    raise SystemExit('Runtime checks did not finish within 60 seconds; inspect the test app.')
output.parent.mkdir(parents=True, exist_ok=True)
run('file', 'recv', remote, str(output))
results = json.loads(output.read_text())
print(f'Runtime result: {output}')
print(json.dumps(results, ensure_ascii=False, indent=2))
print('Manual checks: use the two buttons in TestAbility for system gallery and directory export.')
# Only the exact missing-codec condition is an explicit suite skip; other video errors fail.
raise SystemExit(0 if all(item['pass'] for item in results) else 1)
