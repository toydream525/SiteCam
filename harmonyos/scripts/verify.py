#!/usr/bin/env python3
"""Run the packaged HarmonyOS runtime checks on an explicitly selected developer emulator."""
import argparse
import json
import os
from pathlib import Path
import subprocess
import time

parser = argparse.ArgumentParser()
parser.add_argument('--target', required=True, help='HDC target, for example 127.0.0.1:5557')
args = parser.parse_args()
root = Path(__file__).resolve().parents[1]
deveco = Path(os.environ.get('DEVECO_HOME', '/Applications/DevEco-Studio.app/Contents'))
hdc = deveco / 'sdk/default/openharmony/toolchains/hdc'

def run(*parts):
    return subprocess.run([str(hdc), '-t', args.target, *parts], check=True, capture_output=True, text=True).stdout

subprocess.run([str(root / 'scripts/build.sh'), 'debug', 'ohosTest'], check=True)
print(run('install', str(root / 'dist/debug/entry-ohosTest-unsigned.hap')))
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
output = root / 'verification'
output.mkdir(exist_ok=True)
run('file', 'recv', '/data/app/el2/100/base/com.sitecam.app/haps/entry_test/files/verification.json', str(output / 'api26-results.json'))
results = json.loads((output / 'api26-results.json').read_text())
print(json.dumps(results, ensure_ascii=False, indent=2))
print('Manual checks: use the two buttons in TestAbility for system gallery and directory export.')
# Missing hardware/codec is a failure, not a successful video test.
raise SystemExit(0 if all(item['pass'] for item in results) else 1)
