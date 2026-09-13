import { LengthMetricsUnit } from '@kit.ArkUI';
import { image } from '@kit.ImageKit';
import { Snapshot, STYLES, Field, Style, Fix } from './Models';

export class InkLine { text: string=''; x: number=0; y: number=0; width: number=0; size: number=0; color: string=''; bold: boolean=false; key: string=''; role: string='value'; ascent: number=0; descent: number=0; }
export class InkRect { x: number=0; y: number=0; w: number=0; h: number=0; color: string=''; }
export class Layout { x: number=0; y: number=0; w: number=0; h: number=0; header: number=0; radius: number=0; accentWidth: number=0; lines: InkLine[]=[]; rects: InkRect[]=[]; background: string=''; accent: string=''; }
export type DrawingContext = CanvasRenderingContext2D | OffscreenCanvasRenderingContext2D;

export function resolveFields(s: Snapshot): Field[] {
  const out: Field[]=[];
  for (const f of s.watermark.fields) {
    if (!f.enabled) continue;
    // Elevation is a location measurement, so a saved text override must not
    // outlive the capture fix or make an unavailable value look valid.
    let value=f.key==='elevation' ? formatElevation(s.fix,s.locationStatus) : f.value;
    if (!value) switch (f.key) {
      case 'project': value=s.project; break; case 'category': value=s.category; break;
      case 'time': value=watermarkTime(s.timestamp); break; case 'address': value=s.address; break;
      case 'gps': value=s.fix ? `${s.fix.latitude.toFixed(6)}, ${s.fix.longitude.toFixed(6)}${s.locationStatus==='STALE'?'（旧定位）':''}` : ''; break;
      case 'user': value=s.watermark.user; break;
    }
    if (value) out.push(new Field(f.key,s.presentationVersion===2 ? (f.label || builtInLabel(f.key,f.label)) : builtInLabel(f.key,f.label),value));
  }
  if (s.presentationVersion!==2 && s.watermark.style==='ENGINEERING_BLUE') out.sort((a: Field,b: Field)=>rank(a.key,'project','time')-rank(b.key,'project','time'));
  if (s.presentationVersion!==2 && s.watermark.style==='TIME_LOCATION') out.sort((a: Field,b: Field)=>rank(a.key,'time','address')-rank(b.key,'time','address'));
  return out;
}
export function formatElevation(fix: Fix|null,locationStatus: string): string {
  const altitude=fix?.altitude;
  if(locationStatus!=='FRESH'||altitude===null||altitude===undefined||!Number.isFinite(altitude))return '暂不可用';
  return `${altitude.toFixed(1)} m`;
}
function builtInLabel(key: string, fallback: string): string { switch(key){case 'project':return '工程名称';case 'category':return '工程类型';case 'time':return '拍摄时间';case 'address':return '现场位置';case 'elevation':return '海拔';case 'gps':return 'GPS定位';case 'user':return '拍摄人员';default:return fallback;} }
function watermarkTime(t: number): string { const d=new Date(t),p=(n:number)=>n.toString().padStart(2,'0'); return `${d.getFullYear()}.${p(d.getMonth()+1)}.${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`; }
function rank(k: string,a: string,b: string): number { return k===a?0:k===b?1:2; }
export function wrapText(ctx: DrawingContext,text: string,width: number): string[] {
  const result: string[]=[];
  for (const paragraph of text.split('\n')) { if (!paragraph.length) { result.push(''); continue; } let row=''; for (const ch of Array.from(paragraph)) { if (row && ctx.measureText(row+ch).width>width) { result.push(row); row=''; } row+=ch; } result.push(row); }
  return result;
}

export function layoutWatermark(ctx: DrawingContext,w: number,h: number,s: Snapshot): Layout {
  const marginDp=s.watermark.marginDp>=0&&s.watermark.marginDp<10000?s.watermark.marginDp:16; const snapshotScale=s.watermark.fontScale>=.5&&s.watermark.fontScale<=2.2?s.watermark.fontScale:1; const margin=Math.min(marginDp*3*Math.max(.45,Math.min(w,h)/1080)*snapshotScale,Math.min(w,h)*.08);
  const normal=build(ctx,w,h,s,1,margin); const maxH=h-margin*2;
  if(normal.h<=maxH) return normal; let low=.001,high=1,result=build(ctx,w,h,s,low,margin);
  for(let i=0;i<32;i++){const mid=(low+high)/2;const test=build(ctx,w,h,s,mid,margin);if(test.h<=maxH){low=mid;result=test;}else high=mid;} return result;
}
function build(ctx: DrawingContext,w: number,h: number,snap: Snapshot,scale: number,margin: number): Layout {
  const cfg=snap.watermark,id=cfg.style==='SITE_PHOTO'?'MINIMAL':cfg.style,style=STYLES.find((v: Style)=>v.id===id)||STYLES[0];
  const light=['ENGINEERING_BLUE','CONSTRUCTION_TABLE','ACCEPTANCE'].includes(id),legacy=['CLASSIC','MINIMAL','INFO_BOARD'].includes(id);
  const fontScale=cfg.fontScale>=.5&&cfg.fontScale<=2.2?cfg.fontScale:1; const base=Math.max(.45,Math.min(w,h)/1080)*fontScale*scale;
  const pad=22*base,gap=(legacy?14:7)*base,size=(legacy?26:30)*base;
  const cardW=Math.max(pad*3,Math.min(w-margin*2,Math.min(w,h)*(light?.82:.9))),inner=Math.max(.001,cardW-pad*2);
  const r=new Layout();r.w=cardW;r.background=light?'#F5F8FB':(id==='MINIMAL'?'#18232C':'#16212B');r.accent=id==='ENGINEERING_BLUE'?'#1262AE':id==='CONSTRUCTION_TABLE'?'#455F78':id==='INSPECTION'?'#168D80':id==='ACCEPTANCE'?'#173C68':'#FFB300';
  const fg=light?'#172D42':'#FFFFFF',label=light?'#315776':'#CFD8DC';let y=pad;let header=0;
  const title=id==='INFO_BOARD'?'工程施工现场留档记录':id==='CONSTRUCTION_TABLE'?'施工记录':id==='INSPECTION'?'巡检记录':id==='ACCEPTANCE'?'工程验收 · 现场记录':'';
  const add=(text: string,x: number,top: number,maxW: number,font: number,color: string,bold: boolean,key: string,role: string): number=>{ctx.font=`${bold?'bold':'normal'} ${font}px sans-serif`;let cursor=top;for(const part of wrapText(ctx,text,Math.max(.001,maxW))){const mt=ctx.measureText(part||'国');const asc=Math.max(mt.fontBoundingBoxAscent||0,mt.actualBoundingBoxAscent||0,font*.9);const desc=Math.max(mt.fontBoundingBoxDescent||0,mt.actualBoundingBoxDescent||0,font*.25);const l=new InkLine();l.text=part;l.x=x;l.y=cursor+asc;l.width=mt.width;l.size=font;l.color=color;l.bold=bold;l.key=key;l.role=role;l.ascent=asc;l.descent=desc;r.lines.push(l);cursor+=asc+desc+gap;}return cursor;};
  if(title){y=add(title,pad,y,inner,id==='INFO_BOARD'?32*base:size*1.12,id==='INFO_BOARD'?'#17212A':'#FFFFFF',true,'','heading')+pad*.5;header=y;y+=pad*.5;}
  const fields=resolveFields(snap);
  for(let i=0;i<fields.length;i++){const f=fields[i],rowTop=y,blue=id==='ENGINEERING_BLUE'&&(f.key==='project'||f.key==='time'),big=id==='TIME_LOCATION'&&f.key==='time',highlight=id==='CLASSIC'&&i===0,font=highlight?34*base:size*(big?1.75:blue?1.12:1);
    if(blue){if(snap.presentationVersion===2){const top=y;y=add(f.label+': '+f.value,pad,y,inner,font,'#FFFFFF',true,f.key,'value');const bg=new InkRect();bg.x=pad-gap;bg.y=top-gap/2;bg.w=inner+gap*2;bg.h=y-top+gap/2;bg.color=r.accent;r.rects.push(bg);}else{y=add(f.value,pad,y,inner,font,'#FFFFFF',true,f.key,'value');header=y+gap;}}
    else if(light){const lw=inner*(id==='ACCEPTANCE'?.32:.27),a=add(f.label,pad,y,lw-gap,size,label,true,f.key,'label'),b=add(f.value,pad+lw+gap,y,inner-lw-gap,size,fg,false,f.key,'value');y=Math.max(a,b);line(r,pad+lw,rowTop-gap/2,Math.max(base,.2),y-(rowTop-gap/2),'#C0CEDA');}
    else if(id==='MINIMAL'||id==='TIME_LOCATION'){if((snap.presentationVersion===2||!['project','category','time','address','gps','user'].includes(f.key))&&f.label)y=add(f.label,pad,y,inner,size*.85,label,true,f.key,'label');y=add(f.value,pad,y,inner,font,fg,big,f.key,'value');}
    else {
      const labelText=f.label+': ';
      ctx.font='bold '+font+'px sans-serif';
      const labelMetrics: TextMetrics=ctx.measureText(labelText);
      const labelWidth=labelMetrics.width;
      ctx.font=(highlight?'bold':'normal')+' '+font+'px sans-serif';
      const valueMetrics: TextMetrics=ctx.measureText(f.value);
      const valueWidth=valueMetrics.width;
      const fits=labelWidth+valueWidth<=inner && f.value.indexOf('\n')<0;
      if(fits){
        const asc=Math.max(valueMetrics.fontBoundingBoxAscent||0,valueMetrics.actualBoundingBoxAscent||0,font*.9);
        const desc=Math.max(valueMetrics.fontBoundingBoxDescent||0,valueMetrics.actualBoundingBoxDescent||0,font*.25);
        const l=new InkLine();l.text=labelText;l.x=pad;l.y=y+asc;l.width=labelWidth;l.size=font;l.color=label;l.bold=true;l.key=f.key;l.role='label';l.ascent=asc;l.descent=desc;r.lines.push(l);
        const v=new InkLine();v.text=f.value;v.x=pad+labelWidth;v.y=y+asc;v.width=valueWidth;v.size=font;v.color=highlight?r.accent:fg;v.bold=highlight;v.key=f.key;v.role='value';v.ascent=asc;v.descent=desc;r.lines.push(v);
        y+=asc+desc+gap;
      }else{
        y=add(f.label,pad,y,inner,font,label,true,f.key,'label');
        y=add(f.value,pad,y,inner,font,highlight?r.accent:fg,highlight,f.key,'value');
      }
    }
    if(light||id==='INSPECTION'){line(r,pad,y,cardW-pad*2,Math.max(base,.2),light?'#C0CEDA':'#528E89');y+=gap;}
  }
  r.h=y+pad;r.accentWidth=5*base;r.x=cfg.position.endsWith('RIGHT')?w-margin-r.w:margin;r.y=cfg.position.startsWith('TOP')?margin:h-margin-r.h;r.header=header;r.radius=id==='MINIMAL'?12*base:3*base;for(const l of r.lines){l.x+=r.x;l.y+=r.y;}for(const d of r.rects){d.x+=r.x;d.y+=r.y;}return r;
}
function roundedPath(ctx: DrawingContext,x:number,y:number,w:number,h:number,radius:number): void { const r0=Math.min(radius,Math.min(w,h)/2);ctx.beginPath();ctx.moveTo(x+r0,y);ctx.lineTo(x+w-r0,y);ctx.quadraticCurveTo(x+w,y,x+w,y+r0);ctx.lineTo(x+w,y+h-r0);ctx.quadraticCurveTo(x+w,y+h,x+w-r0,y+h);ctx.lineTo(x+r0,y+h);ctx.quadraticCurveTo(x,y+h,x,y+h-r0);ctx.lineTo(x,y+r0);ctx.quadraticCurveTo(x,y,x+r0,y);ctx.closePath(); }
function line(r: Layout,x: number,y: number,w: number,h: number,color: string): void {const d=new InkRect();d.x=x;d.y=y;d.w=w;d.h=h;d.color=color;r.rects.push(d);}
export function paintWatermark(ctx: DrawingContext,w: number,h: number,s: Snapshot): Layout {const r=layoutWatermark(ctx,w,h,s);ctx.save();ctx.globalAlpha=Math.max(0,Math.min(1,s.watermark.opacity));ctx.fillStyle=r.background;roundedPath(ctx,r.x,r.y,r.w,r.h,r.radius);ctx.fill();ctx.globalAlpha=1;if(r.header){ctx.fillStyle=r.accent;ctx.fillRect(r.x,r.y,r.w,r.header);}if(s.watermark.style==='CLASSIC'){ctx.fillStyle=r.accent;ctx.fillRect(r.x,r.y,r.accentWidth,r.h);}for(const d of r.rects){ctx.fillStyle=d.color;ctx.fillRect(d.x,d.y,d.w,d.h);}for(const l of r.lines){ctx.font=`${l.bold?'bold':'normal'} ${l.size}px sans-serif`;ctx.fillStyle=l.color;ctx.fillText(l.text,l.x,l.y);}ctx.restore();return r;}
export function watermarkPixelMap(w: number,h: number,s: Snapshot): image.PixelMap {const canvas=new OffscreenCanvas(w,h,LengthMetricsUnit.PX);const ctx=canvas.getContext('2d');paintWatermark(ctx,w,h,s);return ctx.getPixelMap(0,0,w,h);}
