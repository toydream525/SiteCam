import { LengthMetricsUnit } from '@kit.ArkUI';
import { fileIo as fs } from '@kit.CoreFileKit';
import { image } from '@kit.ImageKit';
import { Snapshot, MediaItem, QUALITY_EDGES, QUALITY_JPEG, safeName, timeText, hydrateMediaFromSnapshot } from './Models';
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

/** Validate persisted vectors before defaults or canonicalization can erase evidence of corruption. */
export function validEditRecipe(raw:EditRecipe):boolean {
  if(!raw||typeof raw!=='object'||Array.isArray(raw))return false;
  // The writer always persists an explicit vector container, even for an empty edit.
  // Transform metadata alone cannot establish that a saved annotation is recoverable.
  if(!Array.isArray(raw.steps)&&!Array.isArray(raw.marks))return false;
  if(raw.steps!==undefined&&(!Array.isArray(raw.steps)||!raw.steps.every((step:EditStep)=>validEditStep(step))))return false;
  if(raw.marks!==undefined&&(!Array.isArray(raw.marks)||!raw.marks.every((mark:Mark)=>validEditMark(mark,mark?.kind))))return false;
  if(raw.rotation!==undefined&&(typeof raw.rotation!=='number'||!Number.isFinite(raw.rotation)))return false;
  if(raw.flipX!==undefined&&typeof raw.flipX!=='boolean'||raw.flipY!==undefined&&typeof raw.flipY!=='boolean')return false;
  for(const n of [raw.cropX,raw.cropY,raw.cropW,raw.cropH])if(n!==undefined&&(typeof n!=='number'||!Number.isFinite(n)||n<0||n>1))return false;
  if(raw.cropW===0||raw.cropH===0||(raw.cropX||0)+(raw.cropW===undefined?1:raw.cropW)>1.000001||(raw.cropY||0)+(raw.cropH===undefined?1:raw.cropH)>1.000001)return false;
  return true;
}
function finiteCoordinate(value:number):boolean{return typeof value==='number'&&Number.isFinite(value)&&value>=0&&value<=1;}
function validEditMark(mark:Mark,kind:string):boolean {
  if(!mark||typeof mark!=='object'||Array.isArray(mark)||typeof mark.kind!=='string')return false;
  const kinds=['arrow','rect','rectangle','circle','freehand','pen','text','mosaic'];if(!kinds.includes(kind)||!kinds.includes(mark.kind))return false;
  const same=mark.kind===kind||(['rect','rectangle'].includes(kind)&&['rect','rectangle'].includes(mark.kind))||(['pen','freehand'].includes(kind)&&['pen','freehand'].includes(mark.kind));if(!same)return false;
  // Mirroring an oversized text box and clamping its visible frame can place its
  // original baseline outside 0..1; that clipped history is valid renderer input.
  if(kind==='text'){if(typeof mark.x!=='number'||!Number.isFinite(mark.x)||typeof mark.y!=='number'||!Number.isFinite(mark.y))return false;}else if(!finiteCoordinate(mark.x)||!finiteCoordinate(mark.y))return false;
  if(mark.color!==undefined&&typeof mark.color!=='string')return false;
  for(const n of [mark.strokeWidth,mark.radius])if(n!==undefined&&(typeof n!=='number'||!Number.isFinite(n)||n<0))return false;
  if(mark.points!==undefined&&(!Array.isArray(mark.points)||mark.points.length%2!==0||!mark.points.every((v:number)=>finiteCoordinate(v))))return false;
  if(kind==='text')return typeof mark.text==='string';
  if(['freehand','pen','mosaic'].includes(kind)&&mark.points&&mark.points.length>=2)return true;
  return finiteCoordinate(mark.endX)&&finiteCoordinate(mark.endY);
}
function validEditStep(step:EditStep):boolean {
  if(!step||typeof step!=='object'||Array.isArray(step)||typeof step.kind!=='string')return false;
  if(['rotate','flipX','flipY'].includes(step.kind))return step.mark===undefined||validEditMark(step.mark,step.mark?.kind);
  if(step.kind==='crop'){const m=step.mark;return !!m&&typeof m==='object'&&!Array.isArray(m)&&finiteCoordinate(m.x)&&finiteCoordinate(m.y)&&finiteCoordinate(m.endX)&&finiteCoordinate(m.endY)&&m.x!==m.endX&&m.y!==m.endY;}
  return validEditMark(step.mark,step.kind);
}
export function loadEditableRecipe(json:string,hasEdited:boolean=false):EditRecipe {
  if(!json){if(hasEdited)throw new Error('该标注缺少可编辑记录，已保留原成品');return new EditRecipe();}
  let parsed:EditRecipe;try{parsed=JSON.parse(json) as EditRecipe;}catch(e){throw new Error('该标注的编辑记录已损坏，已保留原记录与成品');}
  if(!validEditRecipe(parsed))throw new Error('该标注的编辑记录已损坏，已保留原记录与成品');
  return canonicalRecipe(parsed);
}

export class NormalizedPoint { x:number=0; y:number=0; }
/** Maps an annotation from the step where it was drawn into the final image frame. */
export function markPointInOutput(recipe:EditRecipe,stepIndex:number,x:number,y:number):NormalizedPoint {
  const p=new NormalizedPoint();p.x=x;p.y=y;
  const steps:EditStep[]=recipe.steps.slice();
  for(let i=stepIndex+1;i<steps.length;i++){
    const step=steps[i],m=step.mark;
    if(step.kind==='flipX'){p.x=1-p.x;}
    else if(step.kind==='flipY'){p.y=1-p.y;}
    else if(step.kind==='rotate'){const nextX=1-p.y;p.y=p.x;p.x=nextX;}
    else if(step.kind==='crop'){
      const left=Math.max(0,Math.min(1,Math.min(m.x,m.endX))),top=Math.max(0,Math.min(1,Math.min(m.y,m.endY)));
      const right=Math.max(left,Math.min(1,Math.max(m.x,m.endX))),bottom=Math.max(top,Math.min(1,Math.max(m.y,m.endY)));
      p.x=(p.x-left)/Math.max(.000001,right-left);p.y=(p.y-top)/Math.max(.000001,bottom-top);
    }
  }
  return p;
}
/** Inverts the output mapping for a mark so moving it preserves its original step ordering. */
export function markPointFromOutput(recipe:EditRecipe,stepIndex:number,x:number,y:number):NormalizedPoint {
  const p=new NormalizedPoint();p.x=x;p.y=y;
  for(let i=recipe.steps.length-1;i>stepIndex;i--){
    const step=recipe.steps[i],m=step.mark;
    if(step.kind==='flipX'){p.x=1-p.x;}
    else if(step.kind==='flipY'){p.y=1-p.y;}
    else if(step.kind==='rotate'){const oldX=p.y;p.y=1-p.x;p.x=oldX;}
    else if(step.kind==='crop'){
      const left=Math.max(0,Math.min(1,Math.min(m.x,m.endX))),top=Math.max(0,Math.min(1,Math.min(m.y,m.endY)));
      const right=Math.max(left,Math.min(1,Math.max(m.x,m.endX))),bottom=Math.max(top,Math.min(1,Math.max(m.y,m.endY)));
      p.x=left+p.x*(right-left);p.y=top+p.y*(bottom-top);
    }
  }
  return p;
}
/** The text renderer uses bold max(18, width/28) pixels; keep hit testing in normalized image units. */
export function hitTextMark(mark:Mark,x:number,y:number,imageWidth:number,imageHeight:number):boolean {
  const font=Math.max(18,imageWidth/28),left=mark.x*imageWidth,baseline=mark.y*imageHeight;
  const width=textInkWidth(mark.text,font),pad=Math.max(12,font*.35);
  return x*imageWidth>=left-pad&&x*imageWidth<=left+width+pad&&y*imageHeight>=baseline-font-pad&&y*imageHeight<=baseline+pad;
}
export function canonicalRecipe(recipe:EditRecipe):EditRecipe {
  const out=deserializeEditRecipe(JSON.stringify(recipe));const prefix:EditStep[]=[];
  if(out.flipY)prefix.push(new EditStep('flipY'));if(out.flipX)prefix.push(new EditStep('flipX'));
  for(let i=0;i<Math.round(Math.abs(out.rotation)/90)%4;i++)prefix.push(new EditStep('rotate'));
  if(out.cropW<1||out.cropH<1){const step=new EditStep('crop');step.mark.x=out.cropX;step.mark.y=out.cropY;step.mark.endX=out.cropX+out.cropW;step.mark.endY=out.cropY+out.cropH;prefix.push(step);}
  out.steps=prefix.concat(out.steps);for(const mark of out.marks){const step=new EditStep(mark.kind);step.mark=mark;out.steps.push(step);}
  out.flipX=false;out.flipY=false;out.rotation=0;out.cropX=0;out.cropY=0;out.cropW=1;out.cropH=1;out.marks=[];return out;
}
export function textStepAt(recipe:EditRecipe,x:number,y:number,width:number,height:number):number {
  let w=width,h=height;const widths:number[]=[],heights:number[]=[];
  for(const step of recipe.steps){widths.push(w);heights.push(h);if(step.kind==='rotate'){const old=w;w=h;h=old;}else if(step.kind==='crop'){w=Math.max(1,Math.floor(w*Math.abs(step.mark.endX-step.mark.x)));h=Math.max(1,Math.floor(h*Math.abs(step.mark.endY-step.mark.y)));}}
  for(let i=recipe.steps.length-1;i>=0;i--){const mark=recipe.steps[i].mark;if(recipe.steps[i].kind!=='text')continue;const p=markPointFromOutput(recipe,i,x,y);if(hitTextMark(mark,p.x,p.y,widths[i],heights[i]))return i;}return -1;
}
function textInkWidth(text:string,font:number):number {
  const ctx=new OffscreenCanvas(1,1,LengthMetricsUnit.PX).getContext('2d');ctx.font=`bold ${font}px sans-serif`;return Math.max(font*.7,ctx.measureText(text).width);
}
export class TextBounds {left:number=0;top:number=0;right:number=0;bottom:number=0;}
export function textStepBounds(recipe:EditRecipe,index:number,width:number,height:number):TextBounds {
  let w=width,h=height;for(let i=0;i<index;i++){const step=recipe.steps[i];if(step.kind==='rotate'){const old=w;w=h;h=old;}else if(step.kind==='crop'){w=Math.max(1,Math.floor(w*Math.abs(step.mark.endX-step.mark.x)));h=Math.max(1,Math.floor(h*Math.abs(step.mark.endY-step.mark.y)));}}
  const mark=recipe.steps[index].mark,font=Math.max(18,w/28),right=mark.x+textInkWidth(mark.text,font)/w,top=mark.y-font/h,bottom=mark.y+font*.25/h;
  const points=[markPointInOutput(recipe,index,mark.x,top),markPointInOutput(recipe,index,right,top),markPointInOutput(recipe,index,mark.x,bottom),markPointInOutput(recipe,index,right,bottom)];
  const bounds=new TextBounds();bounds.left=Math.min(...points.map(p=>p.x));bounds.right=Math.max(...points.map(p=>p.x));bounds.top=Math.min(...points.map(p=>p.y));bounds.bottom=Math.max(...points.map(p=>p.y));return bounds;
}
/** Constrain translation in the displayed frame, then invert back into the original drawing step. */
export function moveTextStep(recipe:EditRecipe,index:number,dx:number,dy:number,width:number,height:number):void {
  const mark=recipe.steps[index].mark,bounds=textStepBounds(recipe,index,width,height);
  const x=Math.max(-bounds.left,Math.min(1-bounds.right,dx)),y=Math.max(-bounds.top,Math.min(1-bounds.bottom,dy));
  const a=markPointFromOutput(recipe,index,0,0),b=markPointFromOutput(recipe,index,x,y);mark.x+=b.x-a.x;mark.y+=b.y-a.y;
}
function markFrom(v: Mark): Mark { const m=new Mark();m.kind=v.kind||'arrow';m.x=Number(v.x||0);m.y=Number(v.y||0);m.endX=Number(v.endX===undefined?1:v.endX);m.endY=Number(v.endY===undefined?1:v.endY);m.text=v.text||'';m.color=v.color||'#FFB300';m.strokeWidth=Number(v.strokeWidth||0);m.radius=Number(v.radius||0);m.points=Array.isArray(v.points)?v.points.map((n: number)=>Number(n)):[];return m; }
function stepFrom(v: EditStep): EditStep { const s=new EditStep(v.kind||'arrow');s.mark=markFrom(v.mark||new Mark());return s; }

export function parseImportTime(value:string,offset:string=''):number {
  const match=value.match(/^(\d{4}):(\d{2}):(\d{2})[ T](\d{2}):(\d{2}):(\d{2})$/);if(!match)return 0;
  const year=Number(match[1]),month=Number(match[2]),day=Number(match[3]),hour=Number(match[4]),minute=Number(match[5]),second=Number(match[6]);
  const check=new Date(Date.UTC(year,month-1,day,hour,minute,second));if(check.getUTCFullYear()!==year||check.getUTCMonth()!==month-1||check.getUTCDate()!==day||check.getUTCHours()!==hour||check.getUTCMinutes()!==minute||check.getUTCSeconds()!==second)return 0;
  const zone=offset.match(/^([+-])(\d{2}):(\d{2})$/);if(zone){const hours=Number(zone[2]),minutes=Number(zone[3]);if(hours>23||minutes>59)return 0;return check.getTime()-(zone[1]==='-'?-1:1)*(hours*60+minutes)*60000;}
  return new Date(year,month-1,day,hour,minute,second).getTime();
}
export function parseImportTimeField(value:string):number {
  const match=value.trim().match(/^(\d{4})[.\-](\d{2})[.\-](\d{2}) (\d{2}):(\d{2}):(\d{2})$/);if(!match)return 0;
  return parseImportTime(`${match[1]}:${match[2]}:${match[3]} ${match[4]}:${match[5]}:${match[6]}`);
}
export function importOrientation(value:string):number {const code=Number(value);if(Number.isInteger(code)&&code>=1&&code<=8)return code;const labels=['top-left','top-right','bottom-right','bottom-left','left-top','right-top','right-bottom','left-bottom'];return Math.max(1,labels.indexOf(value.toLowerCase().replace(/_/g,'-'))+1);}
async function orientImported(pm:image.PixelMap,code:number):Promise<void>{
  if(code===2)await pm.flip(true,false);else if(code===3)await pm.rotate(180);else if(code===4)await pm.flip(false,true);else if(code===5){await pm.rotate(90);await pm.flip(true,false);}else if(code===6)await pm.rotate(90);else if(code===7){await pm.rotate(90);await pm.flip(false,true);}else if(code===8)await pm.rotate(270);
}
export class ImportedPhoto {path:string='';timestamp:number=0;width:number=0;height:number=0;}
export class ImageProcessor {
  root: string;
  constructor(root: string){this.root=root;try{fs.mkdirSync(this.root);}catch(e){if(!fs.accessSync(this.root))throw e as Error;}}
  // Bounded collision search. Re-running safeName here used to truncate the
  // appended suffix away, so the path never changed and the loop never ended.
  path(name: string): string {const safe=safeName(name);let p=this.root+'/'+safe;for(let n=1;n<=9999&&fs.accessSync(p);n++)p=this.root+'/'+appendName(safe,'_'+n);if(fs.accessSync(p))p=this.root+'/'+appendName(safe,'_'+Date.now().toString(36)+Math.floor(Math.random()*1000));return p;}
  async write(path: string,data: ArrayBuffer): Promise<void>{const f=await fs.open(path,fs.OpenMode.CREATE|fs.OpenMode.READ_WRITE|fs.OpenMode.TRUNC);try{let done=0;while(done<data.byteLength){const n=await fs.write(f.fd,data.slice(done));if(n<=0)throw new Error('文件写入不完整');done+=n;}}finally{await fs.close(f);}}
  async captureLocal(bytes: ArrayBuffer,s: Snapshot,quality: number,rotation: number): Promise<MediaItem>{
    const target=this.path(`${safeName(s.project)}_${s.timestamp}.jpg`);let src:image.ImageSource|null=null,pm:image.PixelMap|null=null;const m=new MediaItem();m.timestamp=s.timestamp;m.snapshot=JSON.stringify(s);m.name=`${safeName(s.project)}_${s.timestamp}.jpg`;m.path=target;hydrateMediaFromSnapshot(m,s);
    try{src=image.createImageSource(bytes);pm=await src.createPixelMap({editable:true});if(rotation)await pm!.rotate(rotation);if(s.mirrorPreview)await pm!.flip(true,false);if(s.captureRatio>0){const size=(await pm!.getImageInfo()).size;const width=Math.min(size.width,Math.floor(size.height*s.captureRatio));const height=Math.min(size.height,Math.floor(size.width/s.captureRatio));await pm!.crop({x:Math.floor((size.width-width)/2),y:Math.floor((size.height-height)/2),size:{width:width,height:height}});}await this.resize(pm!,quality);const info=await pm!.getImageInfo();m.width=info.size.width;m.height=info.size.height;const canvas=new OffscreenCanvas(m.width,m.height,LengthMetricsUnit.PX),ctx=canvas.getContext('2d');ctx.drawImage(pm!,0,0,m.width,m.height);paintWatermark(ctx,m.width,m.height,s);const result=ctx.getPixelMap(0,0,m.width,m.height);try{await this.encode(result,target,quality);}finally{await result.release();}
    }catch(e){await this.removePath(target);throw e as Error;}finally{try{await pm?.release();}finally{await src?.release();}}
    // The encoded file is the only copy of the photo; EXIF must not take it down.
    await this.exifQuiet(target,s);return m;
  }
  async prepareImport(uri:string):Promise<ImportedPhoto>{
    const out=new ImportedPhoto();out.path=this.path('import_'+Date.now()+'.part');const file=await fs.open(uri,fs.OpenMode.READ_ONLY);let source:image.ImageSource|null=null;
    try{source=image.createImageSource(file.fd);const size=(await source.getImageInfo()).size;out.width=size.width;out.height=size.height;
      let offset='';try{offset=await source.getImageProperty(image.PropertyKey.OFFSET_TIME_ORIGINAL);}catch(e){}
      try{out.timestamp=parseImportTime(await source.getImageProperty(image.PropertyKey.DATE_TIME_ORIGINAL),offset);}catch(e){}
      const input=await fs.open(uri,fs.OpenMode.READ_ONLY);try{const copy=await fs.open(out.path,fs.OpenMode.CREATE|fs.OpenMode.READ_WRITE);try{await fs.copyFile(input.fd,copy.fd);}finally{await fs.close(copy);}}finally{await fs.close(input);}return out;
    }catch(e){await this.removePath(out.path);throw e as Error;}finally{try{await source?.release();}finally{await fs.close(file);}}
  }
  /** Decode the selected system photo, normalize EXIF, and encode only the new watermarked result. */
  async importWatermarked(sourcePath:string,s:Snapshot,quality:number,preview:boolean=false):Promise<MediaItem>{
    const target=this.path(`${safeName(s.project)}_${s.timestamp}${preview?'_import_preview.part':'_import.jpg'}`);let src:image.ImageSource|null=null,pm:image.PixelMap|null=null;
    const m=new MediaItem();m.timestamp=s.timestamp;m.snapshot=JSON.stringify(s);m.name=`${safeName(s.project)}_${s.timestamp}_import.jpg`;m.path=target;m.status='READY';m.type='PHOTO';m.projectId=s.projectId;hydrateMediaFromSnapshot(m,s);
    try{src=image.createImageSource(sourcePath);const original=(await src.getImageInfo()).size;let orientation='1';try{orientation=await src.getImageProperty(image.PropertyKey.ORIENTATION);}catch(e){}
      const code=importOrientation(orientation),swap=code>=5&&code<=8;
      const edge=QUALITY_EDGES[Math.max(0,Math.min(3,Math.round(quality)))],finalScale=edge?Math.min(1,edge/Math.max(original.width,original.height)):1;
      const finalW=Math.max(1,Math.floor((swap?original.height:original.width)*finalScale)),finalH=Math.max(1,Math.floor((swap?original.width:original.height)*finalScale));
      const decodeScale=preview?Math.min(finalScale,1280/Math.max(original.width,original.height),1):finalScale;
      pm=await src.createPixelMap({editable:true,desiredSize:{width:Math.max(1,Math.floor(original.width*decodeScale)),height:Math.max(1,Math.floor(original.height*decodeScale))}});
      await orientImported(pm,code);const size=(await pm.getImageInfo()).size;m.width=size.width;m.height=size.height;
      const canvas=new OffscreenCanvas(m.width,m.height,LengthMetricsUnit.PX),ctx=canvas.getContext('2d');ctx.drawImage(pm,0,0,m.width,m.height);ctx.save();ctx.scale(m.width/finalW,m.height/finalH);paintWatermark(ctx,finalW,finalH,s);ctx.restore();const result=ctx.getPixelMap(0,0,m.width,m.height);try{await this.encode(result,target,quality);}finally{await result.release();}}
    catch(e){await this.removePath(target);throw e as Error;}finally{try{await pm?.release();}finally{await src?.release();}}
    await this.exifQuiet(target,s);return m;
  }
  async resize(pm:image.PixelMap,q:number):Promise<void>{const info=await pm!.getImageInfo();const index=Math.max(0,Math.min(3,Math.round(q))),edge=QUALITY_EDGES[index];const scale=edge?Math.min(1,edge/Math.max(info.size.width,info.size.height)):1;if(scale<1)await pm!.scale(scale,scale);}
  async encode(pm:image.PixelMap,path:string,q:number):Promise<void>{const packer=image.createImagePacker();try{const index=Math.max(0,Math.min(3,Math.round(q)));await this.write(path,await packer.packing(pm,{format:'image/jpeg',quality:QUALITY_JPEG[index]}));}finally{await packer.release();}}
  async exif(path:string,s:Snapshot):Promise<void>{const src=image.createImageSource(path);try{const dt=timeText(s.timestamp).replace(/-/g,':');await src.modifyImageProperty(image.PropertyKey.DATE_TIME_ORIGINAL,dt);await src.modifyImageProperty(image.PropertyKey.DATE_TIME,dt);await src.modifyImageProperty(image.PropertyKey.ORIENTATION,'1');if(s.fix){await src.modifyImageProperty(image.PropertyKey.GPS_LATITUDE,degree(s.fix.latitude));await src.modifyImageProperty(image.PropertyKey.GPS_LATITUDE_REF,s.fix.latitude<0?'S':'N');await src.modifyImageProperty(image.PropertyKey.GPS_LONGITUDE,degree(s.fix.longitude));await src.modifyImageProperty(image.PropertyKey.GPS_LONGITUDE_REF,s.fix.longitude<0?'W':'E');}}finally{await src.release();}}
  /** EXIF is best effort: a metadata write failure must never delete the encoded image. */
  private async exifQuiet(target:string,s:Snapshot):Promise<void>{try{await this.exif(target,s);}catch(e){console.warn('SITECAM_EXIF '+target+' '+String(e));}}
  async editLocal(m:MediaItem,recipe:EditRecipe,quality:number):Promise<string>{const target=this.path(`${m.timestamp}_edited.jpg`);let src:image.ImageSource|null=null,pm:image.PixelMap|null=null;try{src=image.createImageSource(m.path);pm=await src.createPixelMap({editable:true});const steps:EditStep[]=recipe.steps.slice();for(const mark of recipe.marks){const step=new EditStep(mark.kind);step.mark=mark;steps.push(step);}if(recipe.cropW<1||recipe.cropH<1){const step=new EditStep('crop');step.mark.x=recipe.cropX;step.mark.y=recipe.cropY;step.mark.endX=recipe.cropX+recipe.cropW;step.mark.endY=recipe.cropY+recipe.cropH;steps.unshift(step);}if(recipe.rotation){const step=new EditStep('rotate');for(let i=0;i<Math.round(Math.abs(recipe.rotation)/90)%4;i++)steps.unshift(step);}if(recipe.flipX){const step=new EditStep('flipX');steps.unshift(step);}if(recipe.flipY){const step=new EditStep('flipY');steps.unshift(step);}
      for(const step of steps){if(step.kind==='rotate'){await pm!.rotate(90);continue;}if(step.kind==='flipX'){await pm!.flip(true,false);continue;}if(step.kind==='flipY'){await pm!.flip(false,true);continue;}const info: image.ImageInfo=await pm!.getImageInfo(); const cw: number=info.size.width; const ch: number=info.size.height;if(step.kind==='crop'){const l=Math.max(0,Math.min(1,Math.min(step.mark.x,step.mark.endX))),t=Math.max(0,Math.min(1,Math.min(step.mark.y,step.mark.endY))),rr=Math.max(l,Math.min(1,Math.max(step.mark.x,step.mark.endX))),bb=Math.max(t,Math.min(1,Math.max(step.mark.y,step.mark.endY)));await pm!.crop({x:Math.floor(l*cw),y:Math.floor(t*ch),size:{width:Math.max(1,Math.floor((rr-l)*cw)),height:Math.max(1,Math.floor((bb-t)*ch))}});continue;}const canvas: OffscreenCanvas=new OffscreenCanvas(cw,ch,LengthMetricsUnit.PX); const ctx: OffscreenCanvasRenderingContext2D=canvas.getContext('2d');ctx.drawImage(pm!,0,0,cw,ch);drawMark(ctx,step.mark,cw,ch);const next: image.PixelMap=ctx.getPixelMap(0,0,cw,ch);await pm!.release();pm=next;}
      await this.resize(pm!,quality);await this.encode(pm!,target,quality);
    }catch(e){await this.removePath(target);throw e as Error;}finally{try{await pm?.release();}finally{await src?.release();}}
    let snap:Snapshot|null=null;if(m.snapshot){try{snap=JSON.parse(m.snapshot) as Snapshot;}catch(e){console.warn('SITECAM_EXIF '+target+' '+String(e));}}
    if(snap)await this.exifQuiet(target,snap);return target;}
  async removePath(path:string):Promise<void>{if(path&&fs.accessSync(path))await fs.unlink(path);}
  async remove(m:MediaItem):Promise<void>{const paths=[m.path,m.source,m.edited];for(let i=0;i<paths.length;i++){const p=paths[i];if(p&&paths.indexOf(p)===i)await this.removePath(p);}}
  /**
   * Startup sweep for intermediate files a killed capture/edit/transcode left behind.
   * Only *.part leftovers and stale (24h) _edited / _watermarked outputs qualify,
   * and a candidate is kept whenever the database still references its path.
   */
  async sweepOrphans(referenced:(path:string)=>Promise<boolean>):Promise<number>{
    let removed=0,names:string[]=[];try{names=await fs.listFile(this.root);}catch(e){return 0;}
    const cutoff=Date.now()/1000-24*3600;
    for(const name of names){
      const path=this.root+'/'+name,leftover=name.endsWith('.part'),stale=name.endsWith('_edited.jpg')||name.endsWith('_watermarked.mp4')||name.endsWith('_import.jpg');
      if(!leftover&&!stale)continue;
      try{const info=await fs.stat(path);if(info.isDirectory())continue;if(stale&&info.mtime>cutoff)continue;if(await referenced(path))continue;await fs.unlink(path);removed++;}catch(e){console.warn('SITECAM_ORPHAN '+path+' '+String(e));}
    }
    return removed;
  }
}
/** Insert a suffix before the extension so every collision yields a new name and the extension survives. */
function appendName(name:string,suffix:string):string{const dot=name.lastIndexOf('.');return dot>0?name.slice(0,dot)+suffix+name.slice(dot):name+suffix;}
/** Image Kit requires EXIF GPS as degree,minute,second text, not rational or decimal degrees. */
function degree(v:number):string{const a=Math.abs(v),d=Math.floor(a),minutes=(a-d)*60,m=Math.floor(minutes),seconds=(minutes-m)*60;return `${d},${m},${seconds.toFixed(3)}`;}
function drawMark(ctx:OffscreenCanvasRenderingContext2D,mark:Mark,cw:number,ch:number):void{
  const sx=mark.x*cw,sy=mark.y*ch,ex=mark.endX*cw,ey=mark.endY*ch;ctx.strokeStyle=mark.color||'#FFB300';ctx.fillStyle=mark.color||'#FFB300';ctx.lineWidth=mark.strokeWidth>0?mark.strokeWidth:Math.max(3,cw/180);const left=Math.max(0,Math.min(sx,ex)),top=Math.max(0,Math.min(sy,ey)),ww=Math.max(1,Math.abs(ex-sx)),hh=Math.max(1,Math.abs(ey-sy));
  if(mark.kind==='arrow'){ctx.beginPath();ctx.moveTo(sx,sy);ctx.lineTo(ex,ey);ctx.stroke();const a=Math.atan2(ey-sy,ex-sx),len=Math.max(12,cw/30);ctx.beginPath();ctx.moveTo(ex-len*Math.cos(a-.5),ey-len*Math.sin(a-.5));ctx.lineTo(ex,ey);ctx.lineTo(ex-len*Math.cos(a+.5),ey-len*Math.sin(a+.5));ctx.stroke();}
  else if(mark.kind==='rect'||mark.kind==='rectangle'){ctx.strokeRect(left,top,ww,hh);}
  else if(mark.kind==='circle'){ctx.beginPath();ctx.arc((sx+ex)/2,(sy+ey)/2,mark.radius>0?mark.radius*cw:Math.sqrt(ww*ww+hh*hh)/2,0,Math.PI*2);ctx.stroke();}
  else if(mark.kind==='freehand'||mark.kind==='pen'){const pts=mark.points;ctx.beginPath();if(pts.length>=2){ctx.moveTo(pts[0]*cw,pts[1]*ch);for(let i=2;i+1<pts.length;i+=2)ctx.lineTo(pts[i]*cw,pts[i+1]*ch);ctx.stroke();}else{ctx.beginPath();ctx.moveTo(sx,sy);ctx.lineTo(ex,ey);ctx.stroke();}}
  else if(mark.kind==='text'){ctx.font=`bold ${Math.max(18,cw/28)}px sans-serif`;ctx.fillText(mark.text,sx,sy);}
  else if(mark.kind==='mosaic'){const tile=Math.max(8,cw/60),pts=mark.points.length>=2?mark.points:[mark.x,mark.y,mark.endX,mark.endY];for(let i=0;i+1<pts.length;i+=2){const px=pts[i]*cw,py=pts[i+1]*ch;const nextX=i+2<pts.length?pts[i+2]*cw:px,nextY=i+3<pts.length?pts[i+3]*ch:py;const distance=Math.max(1,Math.hypot(nextX-px,nextY-py)),count=Math.ceil(distance/tile);for(let j=0;j<=count;j++){const xx=px+(nextX-px)*j/count,yy=py+(nextY-py)*j/count;const d=ctx.getImageData(Math.max(0,Math.min(cw-1,xx)),Math.max(0,Math.min(ch-1,yy)),1,1).data;ctx.fillStyle=`rgb(${d[0]},${d[1]},${d[2]})`;ctx.fillRect(xx-tile/2,yy-tile/2,tile,tile);}}}
}
