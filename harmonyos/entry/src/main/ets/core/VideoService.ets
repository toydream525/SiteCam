import { inspectVideo, validateVideoOutput } from './VideoInspection';
import nativeVideo from 'libsitecam.so';
import { media } from '@kit.MediaKit';
import { fileIo as fs } from '@kit.CoreFileKit';
import { image } from '@kit.ImageKit';
import { MediaItem, Snapshot } from './Models';
import { watermarkPixelMap } from './WatermarkRenderer';
import { MediaService } from './MediaService';
import { store } from './Store';
export class VideoService {
  running: boolean=false;
  cancel(): void {nativeVideo.cancel();}
  async process(m: MediaItem,files: MediaService,progress: (v: number)=>void): Promise<void> {
    if(this.running)throw new Error('请等待当前视频处理结束');this.running=true;let timer: number=-1;let output='';const previous=JSON.parse(JSON.stringify(m)) as MediaItem;
    try {if(!m.source&&!m.path)throw new Error('视频源文件不存在');const sourceInfo=await inspectVideo(m.source||m.path);const snap=JSON.parse(m.snapshot) as Snapshot;hydrateMedia(m,snap);m.status='PROCESSING';await store.putMedia(m);const input=await fs.open(m.source||m.path,fs.OpenMode.READ_ONLY);const extractor=await media.createAVMetadataExtractor();
      try {extractor.fdSrc={fd:input.fd,offset:0,length:(await fs.stat(input.fd)).size};const meta=await extractor.fetchMetadata();const w=Number(meta.videoWidth),h=Number(meta.videoHeight),rotation=Number(meta.videoOrientation||'0');m.width=rotation%180?h:w;m.height=rotation%180?w:h;m.duration=Number(meta.duration||'0');m.rotation=rotation;}finally{await extractor.release();await fs.close(input);}
      if(!m.width||!m.height)throw new Error('无法读取视频尺寸');const pm=watermarkPixelMap(m.width,m.height,snap);let rgba: ArrayBuffer;
      try {rgba=new ArrayBuffer(pm.getPixelBytesNumber());await pm.readPixelsToBuffer(rgba);}finally{await pm.release();}
      output=files.path(`${m.timestamp}_watermarked.mp4`);timer=setInterval(()=>progress(nativeVideo.progress()),250);await nativeVideo.transcode(m.source||m.path,output,rgba,m.width,m.height);
      validateVideoOutput(sourceInfo,await inspectVideo(output));m.path=output;m.name=output.substring(output.lastIndexOf('/')+1);m.status='READY';m.rotation=0;await store.putMedia(m);progress(1);
    }catch(e){const failure=e as Error;if(output){try{await files.removePath(output);}catch(_){}}m.id=previous.id;m.path=previous.path;m.source=previous.source;m.edited=previous.edited;m.name=previous.name;m.status='FAILED';m.width=previous.width;m.height=previous.height;m.duration=previous.duration;m.rotation=previous.rotation;try{await store.putMedia(m);}catch(_){/* preserve the original processing error */}throw failure;}finally{if(timer>=0)clearInterval(timer);this.running=false;}
  }
}
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
