import { inspectVideo, validateVideoOutput } from './VideoInspection';
import nativeVideo from 'libsitecam.so';
import { image } from '@kit.ImageKit';
import { MediaItem, Snapshot } from './Models';
import { watermarkPixelMap } from './WatermarkRenderer';
import { MediaService } from './MediaService';
import { store } from './Store';
export class VideoService {
  running: boolean=false;
  cancel(): void {nativeVideo.cancel();}
  async process(m: MediaItem,files: MediaService,progress: (v: number)=>void): Promise<void> {
    if(this.running)throw new Error('请等待当前视频处理结束');this.running=true;let timer: number=-1;let output='';
    let previous: MediaItem|null=null;let current: MediaItem|null=null;
    try {
      // Always reload the row before starting. The detail pane may contain a
      // stale copy after a project move, edit, or split-pane refresh.
      current=await store.mediaById(m.id);if(!current)throw new Error('媒体已删除，无法处理视频');
      previous=JSON.parse(JSON.stringify(current)) as MediaItem;
      const sourcePath=current.source||current.path;
      if(!sourcePath)throw new Error('视频源文件不存在');const sourceInfo=await inspectVideo(sourcePath);const snap=JSON.parse(current.snapshot||m.snapshot) as Snapshot;
      await store.updateMediaStatus(current.id,'PROCESSING');m.status='PROCESSING';
      const width=sourceInfo.rotation%180?sourceInfo.height:sourceInfo.width;const height=sourceInfo.rotation%180?sourceInfo.width:sourceInfo.height;m.width=width;m.height=height;m.duration=sourceInfo.duration;m.rotation=sourceInfo.rotation;
      if(!width||!height)throw new Error('无法读取视频尺寸');const pm=watermarkPixelMap(width,height,snap);let rgba: ArrayBuffer;
      try {rgba=new ArrayBuffer(pm.getPixelBytesNumber());await pm.readPixelsToBuffer(rgba);}finally{await pm.release();}
      output=files.path(`${current.timestamp}_watermarked.mp4`);timer=setInterval(()=>progress(nativeVideo.progress()),250);await nativeVideo.transcode(sourcePath,output,rgba,width,height);
      validateVideoOutput(sourceInfo,await inspectVideo(output));const name=output.substring(output.lastIndexOf('/')+1);
      // This update only touches processing columns. A concurrent move or
      // issue edit therefore survives, and a deleted row is reported.
      await store.updateMediaProcessingResult(current.id,output,name,width,height,sourceInfo.duration,0,'READY');
      m.path=output;m.name=name;m.status='READY';m.rotation=0;progress(1);
    }catch(e){const failure=e as Error;if(output){try{await files.removePath(output);}catch(_){} }m.status='FAILED';if(previous)try{await store.updateMediaStatus(previous.id,'FAILED');}catch(_){}throw failure;}finally{if(timer>=0)clearInterval(timer);this.running=false;}
  }
}
