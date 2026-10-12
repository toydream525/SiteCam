import { LengthMetricsUnit } from '@kit.ArkUI';
import { Snapshot, Field, Watermark, migrateWatermarkFields } from '../../../main/ets/core/Models';
import { layoutWatermark, Layout } from '../../../main/ets/core/WatermarkRenderer';

function check(value:boolean,message:string):void {if(!value)throw new Error(message);}
const measurementCanvas=new OffscreenCanvas(1,1,LengthMetricsUnit.PX);
function layout(s:Snapshot,w:number,h:number):Layout {return layoutWatermark(measurementCanvas.getContext('2d'),w,h,s);}
function complete(s:Snapshot,w:number,h:number):Layout {
  const r=layout(s,w,h);
  check(r.x>=-.1&&r.y>=-.1&&r.x+r.w<=w+.1&&r.y+r.h<=h+.1,'plate bounds');
  for(const l of r.lines)check(l.x>=r.x-1&&l.x+l.width<=r.x+r.w+1&&l.y-l.ascent>=r.y-1&&l.y+l.descent<=r.y+r.h+1,'text bounds '+l.key);
  for(const key of ['project','address']) {
    const enabled=s.watermark.fields.some((f:Field)=>f.key===key&&f.enabled);
    const text=r.lines.filter(l=>l.key===key&&l.role==='value').map(l=>l.text).join('');
    const prefix=s.watermark.style==='ENGINEERING_BLUE'&&s.presentationVersion===2&&key==='project'?'工程名称: ':'';
    const expected=prefix+(key==='project'?s.project:s.address).replace(/\n/g,'');
    check(enabled?text===expected:text==='', 'field content/switch '+key);
  }
  return r;
}
export class WatermarkWidthChecks {
  static async forStyle(style:string):Promise<void> {
    const s=new Snapshot();s.project='工程甲';s.address='一区';s.watermark.style=style;
    s.watermark.fields=[new Field('project','工程名称'),new Field('address','现场位置')];
    const old=JSON.parse('{"style":"CLASSIC","fontScale":1,"fields":[]}') as Watermark;
    const migrated=migrateWatermarkFields(old);check(migrated.widthRatio===.6,'old template width default');
    migrated.widthRatio=.35;check(migrateWatermarkFields(migrated).widthRatio===.35,'width reset on repeated migration');
    for(const ratio of [.3,.6,.9])for(const size of [[1280,960],[960,1280],[1600,2560],[2560,1600],[240,180]])for(const font of [.6,1,2]) {
      s.watermark.widthRatio=ratio;s.watermark.fontScale=font;
      const small=complete(s,size[0],size[1]);check(small.w<=Math.min(size[0],size[1])*ratio+1,'custom width limit');
      const long=JSON.parse(JSON.stringify(s)) as Snapshot;long.address='一区现场地址'.repeat(12);long.watermark.fields.push(new Field('custom_note','现场备注','钢筋绑扎检查'.repeat(6)));
      const large=complete(long,size[0],size[1]);check(large.w<=Math.min(size[0],size[1])*ratio+1,'long custom width limit');
      check(large.lines.filter(l=>l.key==='custom_note'&&l.role==='value').map(l=>l.text).join('')==='钢筋绑扎检查'.repeat(6),'custom content lost');
    }
    const legacy=JSON.parse(JSON.stringify(s).replace(/,"widthRatio":[0-9.]+/,'')) as Snapshot;
    check(legacy.watermark.widthRatio===undefined,'parsed old snapshot gained width');
    s.watermark.widthRatio=.6;
    for(const size of [[1280,960],[960,1280],[960,540],[240,180]])for(const font of [.6,1,2])for(const position of ['BOTTOM_LEFT','TOP_RIGHT']) {
      s.watermark.fontScale=font;s.watermark.position=position;
      const small=complete(s,size[0],size[1]);await new Promise<void>(resolve=>setTimeout(resolve,50));
      const long=JSON.parse(JSON.stringify(s)) as Snapshot;long.project='工程甲'.repeat(12);long.address='一区现场地址'.repeat(18);
      const large=complete(long,size[0],size[1]);check(small.w<=large.w+1,'short plate wider than long');await new Promise<void>(resolve=>setTimeout(resolve,50));
      const only=JSON.parse(JSON.stringify(s)) as Snapshot;only.watermark.fields[1].enabled=false;complete(only,size[0],size[1]);await new Promise<void>(resolve=>setTimeout(resolve,50));
    }
    s.watermark.fontScale=1;s.watermark.position='BOTTOM_LEFT';s.address='一区\n二区';const multi=complete(s,1280,960);s.address='一区二区';const single=complete(s,1280,960);check(multi.w<=single.w+1,'explicit newline width');
    if(['CLASSIC','MINIMAL','TIME_LOCATION'].includes(style))check(single.w<960*.75,'short plate retains fixed empty width');
    if(style==='CLASSIC'){const r=layout(s,1280,960);for(const key of ['project','address']){const lines=r.lines.filter(l=>l.key===key);check(lines.length===2&&lines[0].y===lines[1].y,'short label/value unnecessarily wrapped');}}
  }
}
