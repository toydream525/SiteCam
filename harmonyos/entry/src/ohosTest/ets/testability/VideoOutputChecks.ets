import { media } from '@kit.MediaKit';
import { image } from '@kit.ImageKit';
import { fileIo as fs } from '@kit.CoreFileKit';
import { inspectVideo, validateVideoOutput } from '../../../main/ets/core/VideoInspection';

export async function checkVideoFrames(source: string, output: string, mask: ArrayBuffer): Promise<void> {
  const before = await inspectVideo(source), after = await inspectVideo(output);
  validateVideoOutput(before, after);
  const file = await fs.open(output, fs.OpenMode.READ_ONLY);
  let generator: media.AVImageGenerator | undefined;
  try {
    generator = await media.createAVImageGenerator();
    generator.fdSrc = {fd:file.fd,offset:0,length:(await fs.stat(file.fd)).size};
    const expected = new Uint8Array(mask);
    for(const millis of [0,after.duration/2,Math.max(0,after.duration-100)]) {
      const frame = await generator.fetchFrameByTime(millis*1000,media.AVImageQueryOptions.AV_IMAGE_QUERY_CLOSEST,{width:320,height:240});
      let normalized: image.PixelMap | undefined;
      try {
        const pack = image.createImagePacker();
        let bytes: ArrayBuffer;
        try {bytes=await pack.packing(frame,{format:'image/png',quality:100});} finally {await pack.release();}
        const decoded=image.createImageSource(bytes);
        try {normalized=await decoded.createPixelMap({desiredPixelFormat:image.PixelMapFormat.RGBA_8888});} finally {await decoded.release();}
        const raw=new ArrayBuffer(normalized.getPixelBytesNumber());await normalized.readPixelsToBuffer(raw);
        const actual=new Uint8Array(raw);let count=0,error=0;
        if(actual.length!==expected.length)throw new Error('抽帧尺寸不一致');
        for(let i=0;i<actual.length;i+=4){if(expected[i+3]>=250){count++;error+=Math.abs(actual[i]-expected[i])+Math.abs(actual[i+1]-expected[i+1])+Math.abs(actual[i+2]-expected[i+2]);}}
        if(count<20||error/(count*3)>45)throw new Error(`第 ${millis}ms 帧水印像素不匹配`);
      } finally {await normalized?.release();await frame.release();}
    }
  } finally {await generator?.release();await fs.close(file);}
}
