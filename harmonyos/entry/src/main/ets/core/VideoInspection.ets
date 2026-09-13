import { media } from '@kit.MediaKit';
import { fileIo as fs } from '@kit.CoreFileKit';

export class VideoInfo {
  width: number = 0;
  height: number = 0;
  rotation: number = 0;
  duration: number = 0;
  audio: boolean = false;
}
export async function inspectVideo(path: string): Promise<VideoInfo> {
  const file = await fs.open(path, fs.OpenMode.READ_ONLY);
  let extractor: media.AVMetadataExtractor | undefined;
  try {
    extractor = await media.createAVMetadataExtractor();
    extractor.fdSrc = {fd:file.fd,offset:0,length:(await fs.stat(file.fd)).size};
    const meta = await extractor.fetchMetadata();
    const info = new VideoInfo();
    info.width = Number(meta.videoWidth); info.height = Number(meta.videoHeight);
    info.rotation = Number(meta.videoOrientation || '0'); info.duration = Number(meta.duration || '0');
    info.audio = meta.hasAudio === 'yes' || meta.hasAudio === 'true' || meta.hasAudio === '1';
    if (!(info.width > 0 && info.height > 0 && info.duration > 0)) throw new Error('视频尺寸或时长无效');
    return info;
  } finally {try{await extractor?.release();}finally{await fs.close(file);}}
}
export function validateVideoOutput(source: VideoInfo, output: VideoInfo): void {
  const w = source.rotation % 180 ? source.height : source.width;
  const h = source.rotation % 180 ? source.width : source.height;
  if (output.width !== w || output.height !== h || output.rotation % 360 !== 0) throw new Error('视频成品方向或尺寸不一致');
  if (Math.abs(output.duration-source.duration) > Math.max(150,source.duration*.01)) throw new Error('视频成品时长不完整');
  if (source.audio !== output.audio) throw new Error('视频成品音轨不一致');
}
