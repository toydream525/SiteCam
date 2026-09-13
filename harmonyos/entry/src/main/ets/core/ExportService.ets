import { common } from '@kit.AbilityKit';
import { fileIo as fs, fileUri, picker } from '@kit.CoreFileKit';
import zlib from '@ohos.zlib';
import { image } from '@kit.ImageKit';
import { Project, MediaItem, safeName, timeText } from './Models';
import { MediaService } from './MediaService';
interface SnapshotFix {latitude:number;longitude:number;}
interface SnapshotData {timestamp:number;fix:SnapshotFix|null;}
export class ExportRecord {mediaId: number=0;exportPath: string='';annotationExportPath: string='';success: boolean=false;annotationSuccess: boolean=false;issueStatus: string='';bytes: number=0;width:number=0;height:number=0;error: string='';}
export class ExportReport {files: ExportRecord[]=[];failures: string[]=[];copiedMedia: number=0;missingMedia: number=0;copiedAnnotations: number=0;missingAnnotations: number=0;photoProfile: number=-1;}
class ExportProjectInfo {projectId:number=0;projectName:string='';routeName:string='';category:string='';description:string='';address:string='';isCaptureLocked:boolean=false;isArchived:boolean=false;createdAt:number=0;updatedAt:number=0;exportTimestamp:number=0;photoProfile:string|number='KEEP_ORIGINAL';}
class DeliveryFailure {path:string='';error:string='';deliveryStatus:string='FAILED';}
export class ExportService {
  cancelled: boolean=false;
  deliveryFailures:number=0;
  private deliveryErrors:DeliveryFailure[]=[];
  private forcedDeliveryFailures:string[]=[];
  private context: common.UIAbilityContext;
  private media: MediaService;
  constructor(context: common.UIAbilityContext,media: MediaService){this.context=context;this.media=media;}
  async run(projects: Project[],items: MediaItem[],folder: boolean,quality: number,onProgress: (value: string)=>void): Promise<string> {
    this.cancelled=false;this.deliveryFailures=0;this.deliveryErrors=[];const token=Date.now();const stage=this.context.cacheDir+'/export_'+token;await fs.mkdir(stage);let output='';
    try {
      await this.stage(projects,items,stage,quality,onProgress);
      if(folder) {const uris=await new picker.DocumentViewPicker(this.context).save({pickerMode:picker.DocumentPickerMode.DOWNLOAD});if(!uris.length)throw new Error('已取消选择目录');output=new fileUri.FileUri(uris[0]).path+'/SiteCam_'+token;await fs.mkdir(output);await this.copyTree(stage,output,stage);if(this.deliveryFailures>0){await this.writeDeliveryReports(output);throw new Error(`目标目录有 ${this.deliveryFailures} 项无法写入，已保留部分导出结果与失败报告`);}}
      else {const zip=this.context.cacheDir+'/SiteCam_'+token+'.zip';try {await zlib.compressFile(stage,zip,{});const uris=await new picker.DocumentViewPicker(this.context).save({newFileNames:['SiteCam_'+token+'.zip']});if(!uris.length)throw new Error('已取消保存');output=uris[0];const source=await fs.open(zip,fs.OpenMode.READ_ONLY);try{const dest=await fs.open(output,fs.OpenMode.WRITE_ONLY);try{await fs.copyFile(source.fd,dest.fd);}finally{await fs.close(dest);}}finally{await fs.close(source);}}finally{if(fs.accessSync(zip))await fs.unlink(zip);}}
      return output;
    }finally{await fs.rmdir(stage);}
  }
  failures: number=0;
  async stage(projects: Project[],items: MediaItem[],stage: string,quality: number,onProgress: (value: string)=>void): Promise<void> {
    this.failures=0;
      let count=0;
      for(const project of projects) {const dir=stage+'/'+safeName(project.name)+'_p'+project.id;await fs.mkdir(dir);await fs.mkdir(dir+'/Photos');await fs.mkdir(dir+'/Videos');await fs.mkdir(dir+'/Annotations');const projectInfo=new ExportProjectInfo();projectInfo.projectId=project.id;projectInfo.projectName=project.name;projectInfo.routeName=project.route;projectInfo.category=project.category;projectInfo.description=project.description||project.notes;projectInfo.address=project.address;projectInfo.isCaptureLocked=project.locked;projectInfo.isArchived=project.archived;projectInfo.createdAt=project.created;projectInfo.updatedAt=project.updated;projectInfo.exportTimestamp=Date.now();projectInfo.photoProfile=quality<0?'KEEP_ORIGINAL':quality;await this.text(dir+'/project_info.json',JSON.stringify(projectInfo,null,2));const report=new ExportReport();report.photoProfile=quality;let csv='序号,文件名,类型,拍摄时间,工程地点,GPS坐标,是否为问题,问题等级,问题标题,整改要求,标注成品,媒体导出状态,媒体ID,工程,线路,整改状态,导出文件,实际宽,实际高,实际字节,文件交付状态\r\n';
        for(const item of items.filter(m=>m.projectId===project.id)) {
          if(this.cancelled)throw new Error('已取消导出');onProgress(`正在整理 ${++count} / ${items.length}`);const r=new ExportRecord();r.mediaId=item.id;r.issueStatus=item.issueStatus;r.exportPath=(item.type==='VIDEO'?'Videos/':'Photos/')+item.id+'_'+safeName(item.name);
          try {await this.copy(item.path,dir+'/'+r.exportPath,item.type==='PHOTO'?quality:-1,item.snapshot);r.success=true;r.bytes=(await fs.stat(dir+'/'+r.exportPath)).size;r.width=item.width;r.height=item.height;if(item.type==='PHOTO'){const exported=image.createImageSource(dir+'/'+r.exportPath);try{const info=await exported.getImageInfo();r.width=info.size.width;r.height=info.size.height;}finally{await exported.release();}}report.copiedMedia++;}catch(e){r.error=`${item.name}：${(e as Error).message}`;report.failures.push(r.error);report.missingMedia++;}
          if(item.edited){r.annotationExportPath='Annotations/'+item.id+'_edited.jpg';try{await this.copy(item.edited,dir+'/'+r.annotationExportPath,quality,item.snapshot);r.annotationSuccess=true;report.copiedAnnotations++;}catch(e){report.failures.push(`编辑成品缺失：${item.name}`);report.missingAnnotations++;}}
          if(item.type==='VIDEO'&&item.status!=='READY')await this.text(dir+'/'+r.exportPath+'.watermark.json',JSON.stringify(item));
          const gps=item.latitude!==null&&item.longitude!==null?`${item.latitude}, ${item.longitude}`:'';csv+=[count.toString(),item.name,item.type,timeText(item.timestamp),item.addressText,gps,item.isIssue||item.issueTitle?'是':'否',item.severity,item.issueTitle,item.issueNotes,r.annotationSuccess?r.annotationExportPath:'',item.status,item.id,project.name,project.route,r.issueStatus,r.success?r.exportPath:'',r.width,r.height,r.bytes,r.success?'COPIED':'FAILED'].map(v=>'"'+String(v).replace(/"/g,'""')+'"').join(',')+'\r\n';report.files.push(r);
        }
        await this.text(dir+'/photo_index.csv','\uFEFF'+csv);await this.text(dir+'/export_report.json',JSON.stringify(report,null,2));this.failures+=report.failures.length;
      }
  }
  private async text(path: string,value: string): Promise<void> {const f=await fs.open(path,fs.OpenMode.CREATE|fs.OpenMode.READ_WRITE|fs.OpenMode.TRUNC);try{await fs.write(f.fd,value);}finally{await fs.close(f);}}
  private async copy(source: string,dest: string,q: number,snapshot:string=''): Promise<void> {if(q<0){await fs.copyFile(source,dest);return;}const src=image.createImageSource(source);let pm:image.PixelMap|null=null;try{pm=await src.createPixelMap({editable:true});await this.media.resize(pm,q);await this.media.encode(pm,dest,q);}finally{try{await pm?.release();}finally{await src.release();}}if(snapshot)await this.restoreExif(dest,snapshot);}
  private async restoreExif(path:string,snapshotText:string):Promise<void>{try{const snapshot=JSON.parse(snapshotText) as SnapshotData;const src=image.createImageSource(path);try{const dt=timeText(snapshot.timestamp).replace(/-/g,':');await src.modifyImageProperty(image.PropertyKey.DATE_TIME_ORIGINAL,dt);await src.modifyImageProperty(image.PropertyKey.DATE_TIME,dt);await src.modifyImageProperty(image.PropertyKey.ORIENTATION,'1');if(snapshot.fix){await src.modifyImageProperty(image.PropertyKey.GPS_LATITUDE,degree(snapshot.fix.latitude));await src.modifyImageProperty(image.PropertyKey.GPS_LATITUDE_REF,snapshot.fix.latitude<0?'S':'N');await src.modifyImageProperty(image.PropertyKey.GPS_LONGITUDE,degree(snapshot.fix.longitude));await src.modifyImageProperty(image.PropertyKey.GPS_LONGITUDE_REF,snapshot.fix.longitude<0?'W':'E');}}finally{await src.release();}}catch(e){}}
  async deliverTreeForTest(source:string,dest:string,forcedFailures:string[]):Promise<number>{this.deliveryFailures=0;this.deliveryErrors=[];this.forcedDeliveryFailures=forcedFailures;await fs.mkdir(dest);await this.copyTree(source,dest,source);await this.writeDeliveryReports(dest);this.forcedDeliveryFailures=[];return this.deliveryFailures;}
  private async copyTree(source: string,dest: string,root: string): Promise<void> {for(const name of await fs.listFile(source)){if(this.cancelled)throw new Error('已取消导出，目标目录可能包含部分资料');const from=source+'/'+name,to=dest+'/'+name;if((await fs.stat(from)).isDirectory()){await fs.mkdir(to);await this.copyTree(from,to,root);}else{try{const relative=from.substring(root.length+1);if(this.forcedDeliveryFailures.includes(relative))throw new Error('测试注入交付失败');await fs.copyFile(from,to);}catch(e){this.deliveryFailures++;const failure=new DeliveryFailure();failure.path=from.substring(root.length+1);failure.error=(e as Error).message;this.deliveryErrors.push(failure);}}}}
  private async writeDeliveryReports(output:string):Promise<void>{const groups:Map<string,DeliveryFailure[]>=new Map();for(const failure of this.deliveryErrors){const project=failure.path.split('/')[0];let list=groups.get(project);if(!list){list=[];groups.set(project,list);}list.push(failure);}const projects=Array.from(groups.keys());for(const project of projects){const list=groups.get(project);if(!list)continue;const dir=output+'/'+project;await this.text(dir+'/delivery_failures.json',JSON.stringify({deliveryStatus:'FAILED',failureCount:list.length,failures:list},null,2));let csv='文件交付状态,路径,错误\r\n';for(const failure of list)csv+='"FAILED","'+failure.path.replace(/"/g,'""')+'","'+failure.error.replace(/"/g,'""')+'"\r\n';await this.text(dir+'/delivery_failures.csv','\uFEFF'+csv);}}
}
function degree(value:number):string{const a=Math.abs(value),d=Math.floor(a),minutes=(a-d)*60,m=Math.floor(minutes),seconds=(minutes-m)*60;return `${d},${m},${seconds.toFixed(3)}`;}
