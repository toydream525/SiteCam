#!/usr/bin/env python3
"""Checks the production small-cover controls in the explicit test-only size preview.
This is a rendered component test, not proof of a real clamshell outer display.
"""
import argparse, json, os, re, subprocess, tempfile, time
from pathlib import Path
p=argparse.ArgumentParser();p.add_argument('--target',required=True);args=p.parse_args()
hdc=Path(os.environ.get('DEVECO_HOME','/Applications/DevEco-Studio.app/Contents'))/'sdk/default/openharmony/toolchains/hdc'
def run(*parts):return subprocess.run([str(hdc),'-t',args.target,*parts],check=True,capture_output=True,text=True).stdout
def nodes():
    run('shell','uitest','dumpLayout','-p','/data/local/tmp/sitecam-cover-ui.json')
    with tempfile.TemporaryDirectory() as d:
        f=Path(d)/'ui.json';run('file','recv','/data/local/tmp/sitecam-cover-ui.json',str(f));tree=json.loads(f.read_text())
    out=[]
    def visit(n):
        out.append(n.get('attributes',{}))
        for child in n.get('children',[]):visit(child)
    visit(tree);return out
def box(n):return list(map(int,re.findall(r'\d+',n['bounds'])))
def tap(key):
    matches=[n for n in nodes() if n.get('id')==key or n.get('text')==key]
    assert len(matches)==1,(key,len(matches))
    x1,y1,x2,y2=box(matches[0]);run('shell','uitest','uiInput','click',str((x1+x2)//2),str((y1+y2)//2));time.sleep(.5)
tap('小外屏布局预览');report=[]
for width,height in [(96,96),(120,180),(180,120),(327,327),(320,240)]:
    ns=nodes();byid={n.get('id'):n for n in ns if n.get('id')}
    frame=box(byid['cover-qa-frame']);dock=box(byid['camera-controls-cover']);shutter=box(byid['camera-shutter-cover'])
    assert dock[2]==frame[2] and dock[0]>frame[0],('not a right dock',frame,dock)
    assert abs(shutter[1]+shutter[3]-frame[1]-frame[3])<=2,('shutter not vertically centered',shutter,frame)
    assert dock[0]<=shutter[0]<shutter[2]<=dock[2] and dock[1]<=shutter[1]<shutter[3]<=dock[3]
    for key in ['camera-gallery-cover','camera-switch-lens-cover']:
        assert (key in byid)==(height>=156),('secondary controls do not fit',key,width,height)
        if key in byid:
            b=box(byid[key]);assert dock[0]<=b[0]<b[2]<=dock[2] and dock[1]<=b[1]<b[3]<=dock[3]
    tap('camera-shutter-cover');after=next(n['text'] for n in nodes() if n.get('id')=='cover-qa-status')
    assert f'点击 {len(report)+1}' in after,('shutter did not respond',after)
    report.append({'sizeVp':[width,height],'rightDock':True,'shutterCentered':True,'captureCallback':True,'framePx':frame,'dockPx':dock})
    tap('下一尺寸')
tap('返回测试')
print(json.dumps({'kind':'simulated-size-production-component','realSmallCoverVerified':False,'cases':report},ensure_ascii=False,indent=2))
