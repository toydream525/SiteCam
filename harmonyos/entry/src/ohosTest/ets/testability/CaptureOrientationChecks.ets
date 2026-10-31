import { common } from '@kit.AbilityKit';
import { fileIo as fs } from '@kit.CoreFileKit';
import { image } from '@kit.ImageKit';
import { MediaService } from '../../../main/ets/core/MediaService';
import { Snapshot, Field } from '../../../main/ets/core/Models';

export class CaptureOrientationResult {
  code:number=1;mirror:boolean=false;pass:boolean=false;message:string='';
  width:number=0;height:number=0;sourceOrientation:string='';output:string='';
}
class Pixels {width:number=0;height:number=0;rgba:Uint8Array=new Uint8Array(0);}

/** Exercises the production task-pool capture path with asymmetric JPEG/EXIF fixtures. */
export class CaptureOrientationChecks {
  static async run(context:common.UIAbilityContext):Promise<CaptureOrientationResult[]> {
    const service=new MediaService(context),results:CaptureOrientationResult[]=[];
    const folder=context.cacheDir+'/capture_orientation_'+Date.now();await fs.mkdir(folder);
    const epoch=Date.now();let reference:Uint8Array|null=null;
    try {
      for(const mirror of [false,true])for(const code of [1,2,3,4,5,6,7,8]) {
        const result=new CaptureOrientationResult();result.code=code;result.mirror=mirror;
        const input=folder+'/'+code+'_'+mirror+'.jpg';
        try {
          await CaptureOrientationChecks.fixture(service,input,code);
          const source=image.createImageSource(input);
          try{result.sourceOrientation=await source.getImageProperty(image.PropertyKey.ORIENTATION);}finally{await source.release();}
          const handle=await fs.open(input,fs.OpenMode.READ_ONLY),buffer=new ArrayBuffer((await fs.stat(input)).size);
          try{if(await fs.read(handle.fd,buffer)!==buffer.byteLength)throw new Error('Fixture read incomplete');}finally{await fs.close(handle);}
          const snapshot=new Snapshot();snapshot.project='方向回归专用';snapshot.timestamp=epoch+code+(mirror?100:0);
          snapshot.captureRatio=4/3;snapshot.mirrorPreview=mirror;snapshot.watermark.widthRatio=.3;
          snapshot.watermark.fontScale=.6;snapshot.watermark.textColor='#FFFFFF';
          snapshot.watermark.fields=[new Field('project','工程','方向回归')];
          const output=await service.capture(buffer,snapshot,3,0);result.output=output.path;
          const decoded=await CaptureOrientationChecks.read(output.path);result.width=decoded.width;result.height=decoded.height;
          if(decoded.width!==320||decoded.height!==240)throw new Error('Unexpected final dimensions');
          const colors:number[][]=mirror?[[30,180,50],[220,40,30],[25,50,210],[230,180,30]]:[[220,40,30],[30,180,50],[230,180,30],[25,50,210]];
          const points:number[][]=[[80,60],[240,60],[80,144],[240,144]];
          for(let i=0;i<points.length;i++) {
            const offset=(points[i][1]*320+points[i][0])*4;
            for(let channel=0;channel<3;channel++)if(Math.abs(decoded.rgba[offset+channel]-colors[i][channel])>30)throw new Error('Scene corner '+i+' has wrong orientation');
          }
          const mask=new Uint8Array(320*240);let white=0;
          for(let i=0;i<mask.length;i++){const p=i*4;mask[i]=decoded.rgba[p]>205&&decoded.rgba[p+1]>205&&decoded.rgba[p+2]>205?1:0;white+=mask[i];}
          if(white<5)throw new Error('Watermark missing');
          if(reference){let changed=0;for(let i=0;i<mask.length;i++)if(mask[i]!==reference[i])changed++;if(changed>Math.max(12,white*.12))throw new Error('Watermark moved or rotated with image');}else reference=mask;
          result.pass=true;
        }catch(e){result.message=(e as Error).message;}
        results.push(result);
      }
    }finally{await fs.rmdir(folder);}
    return results;
  }
  private static async fixture(service:MediaService,path:string,code:number):Promise<void> {
    const raw=new Uint8Array(320*240*4);
    const colors:number[][]=[[220,40,30],[30,180,50],[230,180,30],[25,50,210]];
    for(let y=0;y<240;y++)for(let x=0;x<320;x++){const c=colors[(y<120?0:2)+(x<160?0:1)],i=(y*320+x)*4;raw[i]=c[0];raw[i+1]=c[1];raw[i+2]=c[2];raw[i+3]=255;}
    const pm=await image.createPixelMap(raw.buffer,{editable:true,srcPixelFormat:image.PixelMapFormat.RGBA_8888,pixelFormat:image.PixelMapFormat.RGBA_8888,size:{width:320,height:240}});
    try {
      // Store inverse-oriented pixels; the EXIF tag describes how to recover the upright scene.
      if(code===2)await pm.flip(true,false);else if(code===3)await pm.rotate(180);else if(code===4)await pm.flip(false,true);
      else if(code===5){await pm.flip(true,false);await pm.rotate(270);}else if(code===6)await pm.rotate(270);
      else if(code===7){await pm.flip(false,true);await pm.rotate(270);}else if(code===8)await pm.rotate(90);
      const packer=image.createImagePacker();try{await service.write(path,await packer.packing(pm,{format:'image/jpeg',quality:98}));}finally{await packer.release();}
    }finally{await pm.release();}
    const source=image.createImageSource(path);try{await source.modifyImageProperty(image.PropertyKey.ORIENTATION,String(code));}finally{await source.release();}
  }
  private static async read(path:string):Promise<Pixels> {
    const source=image.createImageSource(path);let map:image.PixelMap|null=null;
    try{map=await source.createPixelMap({desiredPixelFormat:image.PixelMapFormat.RGBA_8888});const info=await map.getImageInfo();const out=new Pixels();out.width=info.size.width;out.height=info.size.height;const data=new ArrayBuffer(map.getPixelBytesNumber());await map.readPixelsToBuffer(data);out.rgba=new Uint8Array(data);return out;}
    finally{try{await map?.release();}finally{await source.release();}}
  }
}
