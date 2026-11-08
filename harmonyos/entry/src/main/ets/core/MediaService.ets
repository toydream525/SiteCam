import { taskpool } from '@kit.ArkTS';
import { WindowEnvironment } from './WindowEnvironment';
import { LengthMetricsUnit } from '@kit.ArkUI';
import { fileIo as fs, fileUri, picker } from '@kit.CoreFileKit';
import { image } from '@kit.ImageKit';
import { photoAccessHelper } from '@kit.MediaLibraryKit';
import { common } from '@kit.AbilityKit';
import { uniformTypeDescriptor } from '@kit.ArkData';
import { systemShare } from '@kit.ShareKit';
import { CaptureJobResult, Snapshot, MediaItem, QUALITY_EDGES, QUALITY_JPEG, safeName, timeText } from './Models';
import { paintWatermark } from './WatermarkRenderer';
import { ImageProcessor, EditRecipe } from './ImageProcessor';
import { captureWorker, editWorker, importWorker, issueShareWorker } from './ImageJobs';
import { MediaDeletionCoordinator } from './MediaDeletionCoordinator';
import { store, Store } from './Store';
export { EditRecipe, EditStep, Mark, serializeEditRecipe, deserializeEditRecipe } from './ImageProcessor';
export class MediaService extends ImageProcessor {
  private context: common.UIAbilityContext;
  private deletions:MediaDeletionCoordinator;
  private data:Store;
  constructor(context: common.UIAbilityContext,data:Store=store){super(context.filesDir+'/media');this.context=context;this.data=data;this.deletions=new MediaDeletionCoordinator(this.root,data);}
  /** Startup recovery also sweeps intermediate files whose rows never survived. */
  async recoverDeletions():Promise<void>{await this.deletions.recover();try{await this.cleanupOrphans();}catch(e){console.warn('SITECAM_ORPHAN_SWEEP '+String(e));}}
  async cleanupOrphans():Promise<number>{return await this.sweepOrphans((path:string)=>this.data.pathReferenced(path));}
  async deleteMedia(ids:number[]):Promise<void>{await this.deletions.deleteMedia(ids);}
  async deleteProjects(ids:number[]):Promise<void>{await this.deletions.deleteProjects(ids);}
  async capture(bytes: ArrayBuffer,s: Snapshot,q: number,rotation: number,originalPath:string): Promise<CaptureJobResult> {
    // bytes is a detached copy produced by the camera and unused after this call,
    // so the default transfer keeps the JPEG from being copied into the worker.
    const task=new taskpool.Task(captureWorker,this.root,bytes,JSON.stringify(s),q,rotation,originalPath);
    const result=await taskpool.execute(task) as string;return JSON.parse(result) as CaptureJobResult;
  }
  async edit(m: MediaItem,recipe: EditRecipe,q: number): Promise<string> {
    return await taskpool.execute(new taskpool.Task(editWorker,this.root,JSON.stringify(m),JSON.stringify(recipe),q)) as string;
  }
  async renderImport(path:string,snapshot:Snapshot,quality:number,preview:boolean=false):Promise<MediaItem>{return JSON.parse(await taskpool.execute(new taskpool.Task(importWorker,this.root,path,JSON.stringify(snapshot),quality,preview)) as string) as MediaItem;}
  async selectImport():Promise<string>{const result=await new photoAccessHelper.PhotoViewPicker().select({MIMEType:photoAccessHelper.PhotoViewMIMETypes.IMAGE_TYPE,maxSelectNumber:1});return result.photoUris.length?result.photoUris[0]:'';}
  async shareIssuePhoto(path:string,title:string,status:string,notes:string):Promise<void>{
    const dir=this.context.cacheDir+'/issue-shares';
    if(!fs.accessSync(dir))await fs.mkdir(dir);
    for(const name of await fs.listFile(dir)){
      const cached=dir+'/'+name;
      try{if(Date.now()/1000-(await fs.stat(cached)).mtime>86400)await fs.unlink(cached);}catch(e){}
    }
    const target=dir+'/问题照片_'+Date.now()+'.jpg';
    await taskpool.execute(new taskpool.Task(issueShareWorker,this.root,path,target,title,status,notes));
    try{await this.share([target]);}catch(e){await this.removePath(target);throw e as Error;}
  }
  async albumCopy(m: MediaItem,path: string=m.path): Promise<string> {
    const helper=photoAccessHelper.getPhotoAccessHelper(this.context);
    try {const uris=await helper.showAssetsCreationDialog([fileUri.getUriFromPath(path)],[{title:m.name.replace(/\.[^.]+$/,''),fileNameExtension:m.type==='VIDEO'?'mp4':'jpg',photoType:m.type==='VIDEO'?photoAccessHelper.PhotoType.VIDEO:photoAccessHelper.PhotoType.IMAGE}]);
      if(!uris.length)return '';const input=await fs.open(path,fs.OpenMode.READ_ONLY);try{const output=await fs.open(uris[0],fs.OpenMode.WRITE_ONLY);try{await fs.copyFile(input.fd,output.fd);}finally{await fs.close(output);}}finally{await fs.close(input);}return uris[0];
    }finally{await helper.release();}
  }
  async albumCopies(items:MediaItem[]):Promise<string[]>{
    if(!items.length)return [];
    const helper=photoAccessHelper.getPhotoAccessHelper(this.context);
    try{
      const configs:photoAccessHelper.PhotoCreationConfig[]=items.map((item:MediaItem):photoAccessHelper.PhotoCreationConfig=>({
        title:item.name.replace(/\.[^.]+$/,''),fileNameExtension:item.type==='VIDEO'?'mp4':'jpg',
        photoType:item.type==='VIDEO'?photoAccessHelper.PhotoType.VIDEO:photoAccessHelper.PhotoType.IMAGE
      }));
      const uris=await helper.showAssetsCreationDialog(items.map((item:MediaItem)=>fileUri.getUriFromPath(item.path)),configs);
      for(let i=0;i<uris.length;i++){
        const input=await fs.open(items[i].path,fs.OpenMode.READ_ONLY);
        try{const output=await fs.open(uris[i],fs.OpenMode.WRITE_ONLY);try{await fs.copyFile(input.fd,output.fd);}finally{await fs.close(output);}}finally{await fs.close(input);}
      }
      return uris;
    }finally{await helper.release();}
  }
  private shareType(path: string, forceVideo: boolean = false): uniformTypeDescriptor.UniformDataType {
    if (forceVideo) return uniformTypeDescriptor.UniformDataType.VIDEO;
    const clean = path.toLowerCase().split('?')[0];
    const dot = clean.lastIndexOf('.');
    const ext = dot >= 0 ? clean.substring(dot + 1) : '';
    if (['jpg','jpeg','png','webp','gif','heic','heif','bmp'].includes(ext)) return uniformTypeDescriptor.UniformDataType.IMAGE;
    if (['mp4','mov','m4v','mkv','webm','3gp','avi'].includes(ext)) return uniformTypeDescriptor.UniformDataType.VIDEO;
    return uniformTypeDescriptor.UniformDataType.FILE;
  }
  async share(paths: string[],video: boolean=false,anchor: string='sitecam-share-anchor'): Promise<void> {
    if (!paths.length) throw new Error('没有可分享的资料');
    for(const path of paths){const file=await fs.open(path,fs.OpenMode.READ_ONLY);await fs.close(file);}
    const first = paths[0];
    const data = new systemShare.SharedData({utd:this.shareType(first,video),uri:fileUri.getUriFromPath(first)});
    for (let i=1;i<paths.length;i++) {
      const path = paths[i];
      data.addRecord({utd:this.shareType(path),uri:fileUri.getUriFromPath(path)});
    }
    const controller=new systemShare.ShareController(data);
    const env=AppStorage.get<WindowEnvironment>('windowEnvironment')||new WindowEnvironment();
    await controller.show(this.context,{previewMode:systemShare.SharePreviewMode.DETAIL,selectionMode:systemShare.SelectionMode.BATCH,anchor:anchor});
  }
}
