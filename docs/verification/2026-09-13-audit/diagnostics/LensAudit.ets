import { camera } from '@kit.CameraKit';
import { common } from '@kit.AbilityKit';
import { image } from '@kit.ImageKit';
import { fileIo as fs } from '@kit.CoreFileKit';
import { CameraService } from '../../../../main/ets/core/CameraService';

class DeviceRow {
  id: string=''; position: number=0; type: number=0;
  logical: boolean=false; focalLength: number=0;
  equivalentFocalLengths: number[]=[]; constituents: string[]=[];
}
class ShotRow {
  requested: number=0; serviceClamped: number=0; bytes: number=0;
  width: number=0; height: number=0; focalLength: string='';
  equivalentFocalLength: string=''; digitalZoomRatio: string='';
  lensModel: string=''; error: string='';
}
class LensReport {
  source: string='Unmodified production CameraService; temporary test-module page';
  startedAt: number=Date.now(); finishedAt: number=0;
  devices: DeviceRow[]=[]; selectedDevice: string='';
  minimumZoom: number=1; maximumZoom: number=1; shots: ShotRow[]=[];
  cameraErrors: string[]=[]; error: string='';
}
function pause(ms: number): Promise<void> {return new Promise<void>(resolve=>setTimeout(resolve,ms));}
async function property(src: image.ImageSource,key: image.PropertyKey): Promise<string> {
  try{return await src.getImageProperty(key);}catch(e){return '';}
}
@Entry @Component
struct LensAudit {
  private controller: XComponentController=new XComponentController();
  private running: boolean=false;
  @State status: string='正在检查后置镜头';
  private async run(): Promise<void> {
    if(this.running)return;this.running=true;
    const context=getContext(this) as common.UIAbilityContext;
    const report=new LensReport();const service=new CameraService(context);
    service.onError=(message: string)=>report.cameraErrors.push(message);
    try {
      const manager=camera.getCameraManager(context);const devices=manager.getSupportedCameras();
      for(const device of devices){const row=new DeviceRow();row.id=device.cameraId;row.position=device.cameraPosition;row.type=device.cameraType;row.logical=device.isLogicalCamera||false;row.focalLength=device.lensFocalLength||0;row.equivalentFocalLengths=device.lensEquivalentFocalLength||[];row.constituents=(device.constituentCameraDevices||[]).map((v:camera.CameraDevice)=>v.cameraId);report.devices.push(row);}
      const back=devices.findIndex((v:camera.CameraDevice)=>v.cameraPosition===camera.CameraPosition.CAMERA_POSITION_BACK);
      if(back<0)throw new Error('未开放后置镜头');
      await pause(600);await service.open(this.controller.getXComponentSurfaceId());
      for(let index=0;index<back;index++)await service.switchLens();
      report.selectedDevice=devices[back].cameraId;report.minimumZoom=service.zoomMin;report.maximumZoom=service.zoomMax;
      const values: number[]=[service.zoomMin,1,2,3,5,service.zoomMin,1];
      for(const value of values){
        const row=new ShotRow();row.requested=value;row.serviceClamped=Math.max(service.zoomMin,Math.min(service.zoomMax,value));
        try {
          this.status=`正在检查 ${value.toFixed(2)}×`;
          service.zoom(value);await pause(900);
          const bytes=await service.shoot(service.photoRotation(0));row.bytes=bytes.byteLength;
          const src=image.createImageSource(bytes);
          try{const info=await src.getImageInfo();row.width=info.size.width;row.height=info.size.height;row.focalLength=await property(src,image.PropertyKey.FOCAL_LENGTH);row.equivalentFocalLength=await property(src,image.PropertyKey.FOCAL_LENGTH_IN_35_MM_FILM);row.digitalZoomRatio=await property(src,image.PropertyKey.DIGITAL_ZOOM_RATIO);row.lensModel=await property(src,image.PropertyKey.LENS_MODEL);}finally{await src.release();}
        }catch(e){row.error=(e as Error).message;}
        report.shots.push(row);
      }
    }catch(e){report.error=(e as Error).message;}finally{
      await service.close();report.finishedAt=Date.now();
      const data=JSON.stringify(report,null,2);const file=fs.openSync(context.filesDir+'/lens-audit.json',fs.OpenMode.CREATE|fs.OpenMode.READ_WRITE|fs.OpenMode.TRUNC);
      try{fs.writeSync(file.fd,data);}finally{fs.closeSync(file);}
      console.info('SITECAM_LENS_AUDIT '+data);
      this.status=`镜头检查完成：${report.shots.filter((r:ShotRow)=>!r.error&&r.bytes>0).length}/${report.shots.length} 张返回；范围 ${report.minimumZoom}–${report.maximumZoom}× ${report.error}`;
    }
  }
  build(){Column(){Text(this.status).fontSize(20).padding(24);XComponent({id:'lens-audit-preview',type:XComponentType.SURFACE,controller:this.controller}).width(640).height(480).onLoad(()=>this.run());}.width('100%').height('100%');}
}
