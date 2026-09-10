import { LengthMetricsUnit } from '@kit.ArkUI';
import { fileIo as fs } from '@kit.CoreFileKit';
import { image } from '@kit.ImageKit';
import { Snapshot, MediaItem, QUALITY_EDGES, QUALITY_JPEG, safeName, timeText } from './Models';
import { paintWatermark } from './WatermarkRenderer';

/** Normalized coordinates are in the actual image rectangle (0..1), never in a letterbox view. */
export class Mark {
  kind: string='arrow'; x: number=.2; y: number=.2; endX: number=.7; endY: number=.7; text: string='';
  color: string='#FFB300'; strokeWidth: number=0; points: number[]=[]; radius: number=0;
}
export class EditStep { kind: string='arrow'; mark: Mark=new Mark(); constructor(kind: string){this.kind=kind;} }
export class EditRecipe {
  steps: EditStep[]=[]; rotation: number=0; flipX: boolean=false; flipY: boolean=false;
  cropX: number=0; cropY: number=0; cropW: number=1; cropH: number=1; marks: Mark[]=[];
}
/** Stable draft representation for UI history and persisted editor state. */
export function serializeEditRecipe(recipe: EditRecipe): string { return JSON.stringify(recipe); }
export function deserializeEditRecipe(json: string): EditRecipe {
  const raw=JSON.parse(json) as EditRecipe, out=new EditRecipe();
  out.rotation=Number(raw.rotation||0);out.flipX=Boolean(raw.flipX);out.flipY=Boolean(raw.flipY);
  out.cropX=Number(raw.cropX||0);out.cropY=Number(raw.cropY||0);out.cropW=Number(raw.cropW===undefined?1:raw.cropW);out.cropH=Number(raw.cropH===undefined?1:raw.cropH);
  out.steps=(raw.steps||[]).map((v: EditStep)=>stepFrom(v));out.marks=(raw.marks||[]).map((v: Mark)=>markFrom(v));return out;
}
function markFrom(v: Mark): Mark { const m=new Mark();m.kind=v.kind||'arrow';m.x=Number(v.x||0);m.y=Number(v.y||0);m.endX=Number(v.endX===undefined?1:v.endX);m.endY=Number(v.endY===undefined?1:v.endY);m.text=v.text||'';m.color=v.color||'#FFB300';m.strokeWidth=Number(v.strokeWidth||0);m.radius=Number(v.radius||0);m.points=Array.isArray(v.points)?v.points.map((n: number)=>Number(n)):[];return m; }
function stepFrom(v: EditStep): EditStep { const s=new EditStep(v.kind||'arrow');s.mark=markFrom(v.mark||new Mark());return s; }

export class ImageProcessor {
  root: string;
  constructor(root: string){this.root=root;try{fs.mkdirSync(this.root);}catch(e){if(!fs.accessSync(this.root))throw e as Error;}}
  path(name: string): string {let p=this.root+'/'+safeName(name),n=1;while(fs.accessSync(p)){p=this.root+'/'+safeName(name.replace(/(\.[^.]+)$/,'_'+n+'$1'));n++;}return p;}
  async write(path: string,data: ArrayBuffer): Promise<void>{const f=await fs.open(path,fs.OpenMode.CREATE|fs.OpenMode.READ_WRITE|fs.OpenMode.TRUNC);try{let done=0;while(done<data.byteLength){const n=await fs.write(f.fd,data.slice(done));if(n<=0)throw new Error('文件写入不完整');done+=n;}}finally{await fs.close(f);}}
  async captureLocal(bytes: ArrayBuffer,s: Snapshot,quality: number,rotation: number): Promise<MediaItem>{
    const target=this.path(`${safeName(s.project)}_${s.timestamp}.jpg`);let src=image.createImageSource(bytes),pm:image.PixelMap|null=null;const m=new MediaItem();m.timestamp=s.timestamp;m.snapshot=JSON.stringify(s);m.name=`${safeName(s.project)}_${s.timestamp}.jpg`;m.path=target;hydrateMedia(m,s);
    try{pm=await src.createPixelMap({editable:true});if(rotation)await pm!.rotate(rotation);if(s.captureRatio>0){const size=(await pm!.getImageInfo()).size;const width=Math.min(size.width,Math.floor(size.height*s.captureRatio));const height=Math.min(size.height,Math.floor(size.width/s.captureRatio));await pm!.crop({x:Math.floor((size.width-width)/2),y:Math.floor((size.height-height)/2),size:{width:width,height:height}});}await this.resize(pm!,quality);const info=await pm!.getImageInfo();m.width=info.size.width;m.height=info.size.height;const canvas=new OffscreenCanvas(m.width,m.height,LengthMetricsUnit.PX),ctx=canvas.getContext('2d');ctx.drawImage(pm!,0,0,m.width,m.height);paintWatermark(ctx,m.width,m.height,s);const result=ctx.getPixelMap(0,0,m.width,m.height);try{await this.encode(result,target,quality);}finally{await result.release();}await this.exif(target,s);return m;
    }catch(e){await this.removePath(target);throw e as Error;}finally{await pm?.release();await src.release();}
  }
  async resize(pm:image.PixelMap,q:number):Promise<void>{const info=await pm!.getImageInfo();const index=Math.max(0,Math.min(3,Math.round(q))),edge=QUALITY_EDGES[index];const scale=edge?Math.min(1,edge/Math.max(info.size.width,info.size.height)):1;if(scale<1)await pm!.scale(scale,scale);}
  async encode(pm:image.PixelMap,path:string,q:number):Promise<void>{const packer=image.createImagePacker();try{const index=Math.max(0,Math.min(3,Math.round(q)));await this.write(path,await packer.packing(pm,{format:'image/jpeg',quality:QUALITY_JPEG[index]}));}finally{await packer.release();}}
  async exif(path:string,s:Snapshot):Promise<void>{const src=image.createImageSource(path);try{const dt=timeText(s.timestamp).replace(/-/g,':');await src.modifyImageProperty(image.PropertyKey.DATE_TIME_ORIGINAL,dt);await src.modifyImageProperty(image.PropertyKey.DATE_TIME,dt);await src.modifyImageProperty(image.PropertyKey.ORIENTATION,'1');if(s.fix){await src.modifyImageProperty(image.PropertyKey.GPS_LATITUDE,degree(s.fix.latitude));await src.modifyImageProperty(image.PropertyKey.GPS_LATITUDE_REF,s.fix.latitude<0?'S':'N');await src.modifyImageProperty(image.PropertyKey.GPS_LONGITUDE,degree(s.fix.longitude));await src.modifyImageProperty(image.PropertyKey.GPS_LONGITUDE_REF,s.fix.longitude<0?'W':'E');}}finally{await src.release();}}
  async editLocal(m:MediaItem,recipe:EditRecipe,quality:number):Promise<string>{const src=image.createImageSource(m.path);let pm:image.PixelMap|null=null;const target=this.path(`${m.timestamp}_edited.jpg`);try{pm=await src.createPixelMap({editable:true});const steps:EditStep[]=recipe.steps.slice();for(const mark of recipe.marks){const step=new EditStep(mark.kind);step.mark=mark;steps.push(step);}if(recipe.cropW<1||recipe.cropH<1){const step=new EditStep('crop');step.mark.x=recipe.cropX;step.mark.y=recipe.cropY;step.mark.endX=recipe.cropX+recipe.cropW;step.mark.endY=recipe.cropY+recipe.cropH;steps.unshift(step);}if(recipe.rotation){const step=new EditStep('rotate');for(let i=0;i<Math.round(Math.abs(recipe.rotation)/90)%4;i++)steps.unshift(step);}if(recipe.flipX){const step=new EditStep('flipX');steps.unshift(step);}if(recipe.flipY){const step=new EditStep('flipY');steps.unshift(step);}
      for(const step of steps){if(step.kind==='rotate'){await pm!.rotate(90);continue;}if(step.kind==='flipX'){await pm!.flip(true,false);continue;}if(step.kind==='flipY'){await pm!.flip(false,true);continue;}const info: image.ImageInfo=await pm!.getImageInfo(); const cw: number=info.size.width; const ch: number=info.size.height;if(step.kind==='crop'){const l=Math.max(0,Math.min(1,Math.min(step.mark.x,step.mark.endX))),t=Math.max(0,Math.min(1,Math.min(step.mark.y,step.mark.endY))),rr=Math.max(l,Math.min(1,Math.max(step.mark.x,step.mark.endX))),bb=Math.max(t,Math.min(1,Math.max(step.mark.y,step.mark.endY)));await pm!.crop({x:Math.floor(l*cw),y:Math.floor(t*ch),size:{width:Math.max(1,Math.floor((rr-l)*cw)),height:Math.max(1,Math.floor((bb-t)*ch))}});continue;}const canvas: OffscreenCanvas=new OffscreenCanvas(cw,ch,LengthMetricsUnit.PX); const ctx: OffscreenCanvasRenderingContext2D=canvas.getContext('2d');ctx.drawImage(pm!,0,0,cw,ch);drawMark(ctx,step.mark,cw,ch);const next: image.PixelMap=ctx.getPixelMap(0,0,cw,ch);await pm!.release();pm=next;}
      await this.resize(pm!,quality);await this.encode(pm!,target,quality);if(m.snapshot){const snap=JSON.parse(m.snapshot) as Snapshot;await this.exif(target,snap);}return target;
    }catch(e){await this.removePath(target);throw e as Error;}finally{await pm?.release();await src.release();}}
  async removePath(path:string):Promise<void>{if(path&&fs.accessSync(path))await fs.unlink(path);}
  async remove(m:MediaItem):Promise<void>{const paths=[m.path,m.source,m.edited];for(let i=0;i<paths.length;i++){const p=paths[i];if(p&&paths.indexOf(p)===i)await this.removePath(p);}}
}
/** Image Kit requires EXIF GPS as degree,minute,second text, not rational or decimal degrees. */
function degree(v:number):string{const a=Math.abs(v),d=Math.floor(a),minutes=(a-d)*60,m=Math.floor(minutes),seconds=(minutes-m)*60;return `${d},${m},${seconds.toFixed(3)}`;}
function hydrateMedia(m: MediaItem,s: Snapshot): void {
  m.projectId=s.projectId;
  m.latitude=s.fix ? s.fix.latitude : null;
  m.longitude=s.fix ? s.fix.longitude : null;
  m.altitude=s.fix ? s.fix.altitude : null;
  m.locationAccuracy=s.fix ? s.fix.accuracy : null;
  m.locationTimestamp=s.fix ? s.fix.timestamp : null;
  m.locationStatus=s.locationStatus || 'UNAVAILABLE';
  m.addressText=s.address || (s.fix ? s.fix.address : '') || '';
  m.createdAt=s.captureTimestamp || s.timestamp;
}
function drawMark(ctx:OffscreenCanvasRenderingContext2D,mark:Mark,cw:number,ch:number):void{
  const sx=mark.x*cw,sy=mark.y*ch,ex=mark.endX*cw,ey=mark.endY*ch;ctx.strokeStyle=mark.color||'#FFB300';ctx.fillStyle=mark.color||'#FFB300';ctx.lineWidth=mark.strokeWidth>0?mark.strokeWidth:Math.max(3,cw/180);const left=Math.max(0,Math.min(sx,ex)),top=Math.max(0,Math.min(sy,ey)),ww=Math.max(1,Math.abs(ex-sx)),hh=Math.max(1,Math.abs(ey-sy));
  if(mark.kind==='arrow'){ctx.beginPath();ctx.moveTo(sx,sy);ctx.lineTo(ex,ey);ctx.stroke();const a=Math.atan2(ey-sy,ex-sx),len=Math.max(12,cw/30);ctx.beginPath();ctx.moveTo(ex-len*Math.cos(a-.5),ey-len*Math.sin(a-.5));ctx.lineTo(ex,ey);ctx.lineTo(ex-len*Math.cos(a+.5),ey-len*Math.sin(a+.5));ctx.stroke();}
  else if(mark.kind==='rect'||mark.kind==='rectangle'){ctx.strokeRect(left,top,ww,hh);}
  else if(mark.kind==='circle'){ctx.beginPath();ctx.arc((sx+ex)/2,(sy+ey)/2,mark.radius>0?mark.radius*cw:Math.sqrt(ww*ww+hh*hh)/2,0,Math.PI*2);ctx.stroke();}
  else if(mark.kind==='freehand'||mark.kind==='pen'){const pts=mark.points;ctx.beginPath();if(pts.length>=2){ctx.moveTo(pts[0]*cw,pts[1]*ch);for(let i=2;i+1<pts.length;i+=2)ctx.lineTo(pts[i]*cw,pts[i+1]*ch);ctx.stroke();}else{ctx.beginPath();ctx.moveTo(sx,sy);ctx.lineTo(ex,ey);ctx.stroke();}}
  else if(mark.kind==='text'){ctx.font=`bold ${Math.max(18,cw/28)}px sans-serif`;ctx.fillText(mark.text,sx,sy);}
  else if(mark.kind==='mosaic'){const tile=Math.max(8,cw/60),pts=mark.points.length>=2?mark.points:[mark.x,mark.y,mark.endX,mark.endY];for(let i=0;i+1<pts.length;i+=2){const px=pts[i]*cw,py=pts[i+1]*ch;const nextX=i+2<pts.length?pts[i+2]*cw:px,nextY=i+3<pts.length?pts[i+3]*ch:py;const distance=Math.max(1,Math.hypot(nextX-px,nextY-py)),count=Math.ceil(distance/tile);for(let j=0;j<=count;j++){const xx=px+(nextX-px)*j/count,yy=py+(nextY-py)*j/count;const d=ctx.getImageData(Math.max(0,Math.min(cw-1,xx)),Math.max(0,Math.min(ch-1,yy)),1,1).data;ctx.fillStyle=`rgb(${d[0]},${d[1]},${d[2]})`;ctx.fillRect(xx-tile/2,yy-tile/2,tile,tile);}}}
}
