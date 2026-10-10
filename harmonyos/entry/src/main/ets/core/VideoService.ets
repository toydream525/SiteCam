import { inspectVideo, validateVideoOutput } from './VideoInspection';
import nativeVideo from 'libsitecam.so';
import { image } from '@kit.ImageKit';
import { MediaItem, Snapshot } from './Models';
import { watermarkPixelMap } from './WatermarkRenderer';
import { MediaService } from './MediaService';
import { store } from './Store';
export class VideoService {
  running: boolean=false;
  private generation:number=0;
  private cancelledGeneration:number=-1;
  private nativeActive:boolean=false;
  private committing:boolean=false;
  cancel(): void {
    if(!this.running||this.committing)return;
    this.cancelledGeneration=this.generation;
    if(this.nativeActive)nativeVideo.cancel();
  }
  private checkCancelled(generation:number):void{if(generation!==this.generation||this.cancelledGeneration===generation)throw new Error('已取消视频处理');}
  async process(m: MediaItem,files: MediaService,progress: (v: number)=>void): Promise<void> {
    if(this.running)throw new Error('请等待当前视频处理结束');this.running=true;this.committing=false;const generation=++this.generation;let timer: number=-1;let output='';let committed=false;
    let previous: MediaItem|null=null;let current: MediaItem|null=null;
    try {
      // Always reload the row before starting. The detail pane may contain a
      // stale copy after a project move, edit, or split-pane refresh.
      current=await store.mediaById(m.id);this.checkCancelled(generation);if(!current)throw new Error('媒体已删除，无法处理视频');
      previous=JSON.parse(JSON.stringify(current)) as MediaItem;
      const sourcePath=current.source||current.path;
      if(!sourcePath)throw new Error('视频源文件不存在');const sourceInfo=await inspectVideo(sourcePath);this.checkCancelled(generation);const snap=JSON.parse(current.snapshot||m.snapshot) as Snapshot;
      await store.updateMediaStatus(current.id,'PROCESSING');this.checkCancelled(generation);m.status='PROCESSING';
      const width=sourceInfo.rotation%180?sourceInfo.height:sourceInfo.width;const height=sourceInfo.rotation%180?sourceInfo.width:sourceInfo.height;m.width=width;m.height=height;m.duration=sourceInfo.duration;m.rotation=sourceInfo.rotation;
      if(!width||!height)throw new Error('无法读取视频尺寸');const pm=watermarkPixelMap(width,height,snap);let rgba: ArrayBuffer;
      // Native reads the first width*height*4 bytes as tightly packed RGBA, so
      // any row padding the PixelMap reports is removed here.
      try {const row=width*4,bytes=pm.getPixelBytesNumber(),stride=pm.getBytesNumberPerRow(),tight=new ArrayBuffer(width*height*4);rgba=tight;
        if(bytes===row*height)await pm.readPixelsToBuffer(tight);
        else {if(stride<row||bytes<stride*height)throw new Error('水印像素缓冲区无效');const padded=new ArrayBuffer(bytes);await pm.readPixelsToBuffer(padded);const src=new Uint8Array(padded),dst=new Uint8Array(tight);for(let y=0;y<height;y++)dst.set(src.subarray(y*stride,y*stride+row),y*row);}
      }finally{await pm.release();}
      this.checkCancelled(generation);
      output=files.path(`${current.timestamp}_watermarked.mp4`);timer=setInterval(()=>progress(nativeVideo.progress()),250);
      this.nativeActive=true;
      try{await nativeVideo.transcode(sourcePath,output,rgba,width,height);}finally{this.nativeActive=false;}
      this.checkCancelled(generation);const outputInfo=await inspectVideo(output);this.checkCancelled(generation);
      validateVideoOutput(sourceInfo,outputInfo);const name=output.substring(output.lastIndexOf('/')+1);
      // This update only touches processing columns. A concurrent move or
      // issue edit therefore survives, and a deleted row is reported.
      // Once this commit starts cancellation cannot retract a completed row.
      this.checkCancelled(generation);this.committing=true;
      await store.updateMediaProcessingResult(current.id,output,name,width,height,sourceInfo.duration,0,'READY');committed=true;
      m.path=output;m.name=name;m.status='READY';m.rotation=0;progress(1);
    }catch(e){const failure=e as Error;if(!committed){if(output){try{await files.removePath(output);}catch(_){} }m.status='FAILED';if(previous)try{await store.updateMediaStatus(previous.id,'FAILED');}catch(_){} }throw failure;}finally{if(timer>=0)clearInterval(timer);this.nativeActive=false;this.committing=false;this.running=false;}
  }
}
