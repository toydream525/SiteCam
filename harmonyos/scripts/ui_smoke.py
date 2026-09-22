#!/usr/bin/env python3
"""UI regression for a configured SiteCam emulator; does not grant permissions or enter credentials."""
import argparse
import json
import os
from pathlib import Path
import re
import subprocess
import tempfile
import time
import xml.etree.ElementTree as ET

p = argparse.ArgumentParser()
p.add_argument('--target', required=True)
a = p.parse_args()
root = Path(__file__).resolve().parents[1]
hdc = Path(os.environ.get('DEVECO_HOME', '/Applications/DevEco-Studio.app/Contents')) / 'sdk/default/openharmony/toolchains/hdc'

def cmd(*args):
    return subprocess.run([str(hdc), '-t', a.target, *args], capture_output=True, text=True, check=True).stdout

def nodes():
    cmd('shell', 'uitest', 'dumpLayout', '-p', '/data/local/tmp/sitecam-smoke.json')
    with tempfile.TemporaryDirectory() as d:
        local = Path(d) / 'layout.json'
        cmd('file', 'recv', '/data/local/tmp/sitecam-smoke.json', str(local))
        tree = json.loads(local.read_text())
    out = []
    def visit(item):
        out.append(item.get('attributes', {}))
        for child in item.get('children', []):
            visit(child)
    visit(tree)
    return out

def tap(text):
    matches = [n for n in nodes() if n.get('text') == text or n.get('id') == text]
    if len(matches) != 1:
        raise RuntimeError(f'Expected one visible control: {text}; got {len(matches)}')
    x1, y1, x2, y2 = map(int, re.findall(r'\d+', matches[0]['bounds']))
    cmd('shell', 'uitest', 'uiInput', 'click', str((x1 + x2) // 2), str((y1 + y2) // 2))
    time.sleep(.6)

def back():
    cmd('shell', 'uitest', 'uiInput', 'keyEvent', 'Back')
    time.sleep(.5)

def settings():
    xml = cmd('shell', 'cat', '/data/app/el2/100/base/com.sitecam.app/haps/entry/preferences/sitecam_settings')
    return json.loads(ET.fromstring(xml).find("string[@key='settings']").text)

cmd('shell', 'aa', 'force-stop', 'com.sitecam.app')
cmd('shell', 'aa', 'start', '-b', 'com.sitecam.app', '-a', 'EntryAbility')
time.sleep(1)
# This script targets the configured QA emulator, never system agreements.
initial = nodes()
if any(n.get('text') == '工程包' for n in initial):
    back()
if not any(n.get('id') == 'camera-settings' for n in nodes()):
    raise SystemExit('A configured current project is required in the QA emulator.')
before = settings()
tap('camera-settings')
tap('settings-help')
assert any('工程现场使用指南' in n.get('text', '') for n in nodes()), 'Offline guide missing'
back()
tap('settings-watermark')
tap('更换样式')
tap('watermark-style-INFO_BOARD')
tap('watermark-style-cancel')
back()
after = settings()
assert before['watermark'] == after['watermark'], 'Cancelling the style draft changed the active watermark'
assert before['currentProject'] == after['currentProject'], 'Navigation changed the current project'
back()
tap('camera-gallery')
assert any('项 · 照片' in n.get('text', '') for n in nodes()), 'Gallery did not open'
media = [n for n in nodes() if n.get('id', '').startswith('gallery-media-')]
assert media, 'At least one QA media item is required to test detail navigation'
tap(media[0]['id'])
assert any(n.get('id') == 'detail-save-album' for n in nodes()), 'Media detail did not open'
back()
assert any('项 · 照片' in n.get('text', '') for n in nodes()), 'Detail back did not restore gallery'
back()
tap('camera-orientation')
assert any(n.get('text') == '拍摄方向' for n in nodes()), 'Orientation dialog missing'
back()
tap('camera-project')
if any(n.get('text') == '全部工程包' for n in nodes()):
    tap('全部工程包')
tap('projects-create')
assert any(n.get('text') == '新建工程包' for n in nodes()), 'Project dialog did not open'
tap('取消')
report = {'target': a.target, 'galleryDetailAndBack': True, 'orientationDialog': True, 'projectDialog': True, 'offlineGuide': True, 'watermarkCancel': True, 'currentProjectPreserved': True}
(root / 'verification/ui-smoke.json').write_text(json.dumps(report, ensure_ascii=False, indent=2))
print(json.dumps(report, ensure_ascii=False))
