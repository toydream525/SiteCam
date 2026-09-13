import { camera } from '@kit.CameraKit';
import { media } from '@kit.MediaKit';
import { image } from '@kit.ImageKit';
import { common } from '@kit.AbilityKit';
import { fileIo as fs } from '@kit.CoreFileKit';
import { deviceInfo } from '@kit.BasicServicesKit';
import { nextSupportedFlashMode } from './FlashMode';
import { CameraLensCapability, LensRole, buildReferenceZoomPresets, isValidZoomRatio, lensRoleFromPublicFacts, nearbyZoomPointFocalLength, referenceZoomFactor, referenceZoomFromSession, sessionZoomFromReference } from './CameraCapability';

interface CameraDiagnosticsLens {
  cameraId: string;
  cameraPosition: number;
  cameraType: number;
  logical: boolean;
  role: LensRole;
  hardwareTelephoto: boolean;
  appAccessible: boolean;
  photoOutputCandidate: boolean;
  videoOutputCandidate: boolean;
  photoSessionVerified: boolean;
  videoSessionVerified: boolean;
  active: boolean;
  equivalentFocalLengths: number[];
  focalLength: number;
  constituentRoles: LensRole[];
  referenceZoomFactor: number;
  referenceZoomMapped: boolean;
}

interface CameraDiagnosticsZoom {
  requested: number;
  ratio: number;
  sessionRatio: number;
  min: number;
  max: number;
  sessionMin: number;
  sessionMax: number;
  referenceFactor: number;
  referenceMapped: boolean;
  presets: number[];
  pointRatios: number[];
  pointEquivalentFocalLengths: number[];
  equivalentFocalLength: number;
}

interface CameraDiagnosticsFlash {
  supported: boolean;
  offSupported: boolean;
  autoSupported: boolean;
  onSupported: boolean;
  torchSupported: boolean;
  mode: string;
  requested: string;
  diagnostics: string;
}

interface CameraDiagnosticsRecord {
  updatedAt: number;
  session: string;
  activeLensRole: LensRole;
  activeLensEquivalentFocalLength: number;
  activeLensPhysicalConfirmed: boolean;
  hardwareTelephotoAvailable: boolean;
  accessibleTelephotoAvailable: boolean;
  lenses: CameraDiagnosticsLens[];
  zoom: CameraDiagnosticsZoom;
  flash: CameraDiagnosticsFlash;
  lastError: string;
}

export class CameraService {
  private manager: camera.CameraManager;
  private input: camera.CameraInput|null=null;
  private session: camera.PhotoSession|camera.VideoSession|null=null;
  private preview: camera.PreviewOutput|null=null;
  private photo: camera.PhotoOutput|null=null;
  private video: camera.VideoOutput|null=null;
  private recorder: media.AVRecorder|null=null;
  private outputFile: fs.File|null=null;
  private devices: camera.CameraDevice[]=[];
  private allDevices: camera.CameraDevice[]=[];
  private sdkApiVersion: number=Number(deviceInfo.sdkApiVersion);
  private videoMode: boolean=false;
  private requestedZoom: number=1;
  private sessionZoomMin: number=1;
  private sessionZoomMax: number=1;
  private sessionZoomRatio: number=1;
  private zoomReferenceFactor: number=1;
  private zoomReferenceMapped: boolean=false;
  private zoomPointRatios: number[]=[];
  private zoomPointEquivalentFocalLengths: number[]=[];
  private zoomSessionToken: number=0;
  private zoomRequestToken: number=0;
  private zoomReadbackTimer: number=-1;
  private diagnosticsPath: string='';
  private diagnosticsWriteQueue: Promise<void>=Promise.resolve();
  private diagnosticsError: string='';
  private index: number=0;
  private surface: string='';
  private previewRotation: number=-1;
  private displayRotation: number=-1;
  private pending: ((bytes: ArrayBuffer)=>void)|null=null;
  private failed: ((error: Error)=>void)|null=null;
  private captureTimeout: number=-1;
  private captureToken: number=0;
  zoomMin: number=1;zoomMax: number=1;zoomRatio: number=1;zoomPresets: number[]=[1];flash: boolean=false;
  lensCapabilities: CameraLensCapability[]=[];
  activeLensRole: LensRole='UNKNOWN';
  activeLensEquivalentFocalLength: number=0;
  /** Equivalent focal length describes the current view, not a confirmed physical member. */
  zoomEquivalentFocalLength: number=0;
  activeLensPhysicalConfirmed: boolean=false;
  hardwareTelephotoAvailable: boolean=false;
  accessibleTelephotoAvailable: boolean=false;
  /** Supported flash modes exposed to the camera page. */
  flashMode: string='off';
  flashSupported: boolean=false;
  flashOffSupported: boolean=false;
  flashAutoSupported: boolean=false;
  flashOnSupported: boolean=false;
  flashTorchSupported: boolean=false;
  /** Hardware/session facts for the settings diagnostics and field reports. */
  flashDiagnostics: string='session=none';
  private requestedFlashMode: string='off';
  previewWidth: number=4;previewHeight: number=3;
  recording: boolean=false;
  photoSupported: boolean=false;
  videoSupported: boolean=false;
  get canSwitchLens(): boolean{return this.canSwitchLensForMode(this.videoMode);}
  /**
   * The preview remains a photo session while the user is waiting to start a
   * video.  Selectable lenses therefore need the requested capture output and
   * a photo preview output; a photo-only member must never be offered for a
   * pending video capture.
   */
  canSwitchLensForMode(videoMode: boolean): boolean {
    return this.lensSwitchCandidates(videoMode).length>1;
  }
  private lensSwitchCandidates(videoMode: boolean): number[] {
    const candidates: number[]=[];
    for(let i=0;i<this.lensCapabilities.length;i++) {
      const capability=this.lensCapabilities[i];
      const requestedOutput=videoMode?capability.videoOutputCandidate:capability.photoOutputCandidate;
      if(requestedOutput&&capability.photoOutputCandidate)candidates.push(i);
    }
    return candidates;
  }
  videoWidth: number=0;videoHeight: number=0;
  onError: (message: string)=>void=()=>{};
  /** Called only after a valid, current-session zoom readback. */
  onZoomChanged: (value: number)=>void=()=>{};
  constructor(context: common.UIAbilityContext) {
    this.manager=camera.getCameraManager(context);
    this.diagnosticsPath=context.filesDir+'/camera-diagnostics.json';
    this.allDevices=this.manager.getSupportedCameras();
    const publicDevices: camera.CameraDevice[]=[];
    for(const device of this.allDevices) {
      if(device.cameraType===camera.CameraType.CAMERA_TYPE_TRUE_DEPTH)continue;
      publicDevices.push(device);
    }
    const described=publicDevices.map((device: camera.CameraDevice)=>this.describeDevice(device));
    const order:number[]=described.map((_: CameraLensCapability,index: number)=>index);
    order.sort((a: number,b: number)=>this.cameraPriority(described[a])-this.cameraPriority(described[b]));
    this.devices=order.map((index: number)=>publicDevices[index]);
    this.lensCapabilities=order.map((index: number)=>described[index]);
    this.configureReferenceZoomFactors();
    this.recomputeTelephotoFacts();
    this.scheduleDiagnosticsWrite();
  }
  private cameraPriority(capability: CameraLensCapability): number {
    const isBack=capability.cameraPosition===Number(camera.CameraPosition.CAMERA_POSITION_BACK);
    const isFront=capability.cameraPosition===Number(camera.CameraPosition.CAMERA_POSITION_FRONT);
    if(isBack&&capability.logical)return 0;
    if(isBack&&capability.role==='MAIN')return 10;
    if(isBack)return 20;
    if(isFront)return 100;
    return 110;
  }
  private configureReferenceZoomFactors(): void {
    let mainFocal=0;
    for(const capability of this.lensCapabilities) {
      const isBack=capability.cameraPosition===Number(camera.CameraPosition.CAMERA_POSITION_BACK);
      if(!isBack)continue;
      if(!capability.logical&&capability.role==='MAIN'&&capability.equivalentFocalLengths.length){mainFocal=capability.equivalentFocalLengths[0];break;}
      if(capability.logical){
        for(let i=0;i<capability.constituentRoles.length;i++){
          if(capability.constituentRoles[i]==='MAIN'&&capability.constituentEquivalentFocalLengths[i]>0){mainFocal=capability.constituentEquivalentFocalLengths[i];break;}
        }
        if(mainFocal>0)break;
      }
    }
    for(const capability of this.lensCapabilities) {
      capability.referenceZoomFactor=1;
      capability.referenceZoomMapped=false;
      const isBack=capability.cameraPosition===Number(camera.CameraPosition.CAMERA_POSITION_BACK);
      if(!isBack||capability.logical||!mainFocal||!capability.equivalentFocalLengths.length)continue;
      const factor=referenceZoomFactor(mainFocal,capability.equivalentFocalLengths[0]);
      if(factor>0){capability.referenceZoomFactor=factor;capability.referenceZoomMapped=true;}
    }
  }
  private describeDevice(device: camera.CameraDevice): CameraLensCapability {
    const out=new CameraLensCapability();
    out.cameraId=device.cameraId;
    out.cameraPosition=Number(device.cameraPosition);
    out.cameraType=Number(device.cameraType);
    out.connectionType=Number(device.connectionType);
    let equivalent:number[]=[];
    let focal=0;
    if(this.sdkApiVersion>=24) {
      try {equivalent=(device.lensEquivalentFocalLength||[]).filter((value: number)=>Number.isFinite(value)&&value>0);}catch(e){}
      try {focal=Number(device.lensFocalLength||0);if(!Number.isFinite(focal)||focal<0)focal=0;}catch(e){focal=0;}
    }
    out.equivalentFocalLengths=equivalent;
    out.focalLength=focal;
    out.logical=this.sdkApiVersion>=24&&device.isLogicalCamera===true;
    out.role=lensRoleFromPublicFacts(out.cameraType,equivalent);
    if(out.logical&&this.sdkApiVersion>=24) {
      try {
        const members=device.constituentCameraDevices||[];
        out.constituentRoles=members.map((value: camera.CameraDevice)=>{
          const memberEquivalent=(value.lensEquivalentFocalLength||[]).filter((f: number)=>Number.isFinite(f)&&f>0);
          out.constituentEquivalentFocalLengths.push(memberEquivalent.length?memberEquivalent[0]:0);
          return lensRoleFromPublicFacts(Number(value.cameraType),memberEquivalent);
        });
        // A logical group can contain a telephoto member, but its active
        // physical member is not exposed by this API. Keep the group role
        // unknown so zoom points cannot be mistaken for a lens switch.
        if(out.constituentRoles.length>1)out.role='UNKNOWN';
      }catch(e){out.constituentRoles=[];}
    }
    out.hardwareTelephoto=out.role==='TELEPHOTO'||out.constituentRoles.includes('TELEPHOTO');
    return out;
  }
  private recomputeTelephotoFacts(): void {
    this.hardwareTelephotoAvailable=this.lensCapabilities.some((value: CameraLensCapability)=>value.hardwareTelephoto);
    this.accessibleTelephotoAvailable=this.lensCapabilities.some((value: CameraLensCapability)=>{
      const candidate=this.videoMode?value.videoOutputCandidate:value.photoOutputCandidate;
      return value.appAccessible&&!value.logical&&value.role==='TELEPHOTO'&&candidate;
    });
  }
  /**
   * Keep a small, pullable diagnostic record for devices where console logs
   * are unavailable.  It deliberately contains camera capability and state
   * only; no project, location, image, or audio data is written here.
   */
  private diagnosticsSnapshot(): string {
    const lenses: CameraDiagnosticsLens[]=this.lensCapabilities.map((value: CameraLensCapability): CameraDiagnosticsLens=>({
      cameraId:value.cameraId,
      cameraPosition:value.cameraPosition,
      cameraType:value.cameraType,
      logical:value.logical,
      role:value.role,
      hardwareTelephoto:value.hardwareTelephoto,
      appAccessible:value.appAccessible,
      photoOutputCandidate:value.photoOutputCandidate,
      videoOutputCandidate:value.videoOutputCandidate,
      photoSessionVerified:value.photoSessionVerified,
      videoSessionVerified:value.videoSessionVerified,
      active:value.active,
      equivalentFocalLengths:value.equivalentFocalLengths,
      focalLength:value.focalLength,
      constituentRoles:value.constituentRoles,
      referenceZoomFactor:value.referenceZoomFactor,
      referenceZoomMapped:value.referenceZoomMapped
    }));
    const zoom: CameraDiagnosticsZoom={
      requested:this.requestedZoom,
      ratio:this.zoomRatio,
      sessionRatio:this.sessionZoomRatio,
      min:this.zoomMin,
      max:this.zoomMax,
      sessionMin:this.sessionZoomMin,
      sessionMax:this.sessionZoomMax,
      referenceFactor:this.zoomReferenceFactor,
      referenceMapped:this.zoomReferenceMapped,
      presets:this.zoomPresets,
      pointRatios:this.zoomPointRatios,
      pointEquivalentFocalLengths:this.zoomPointEquivalentFocalLengths,
      equivalentFocalLength:this.zoomEquivalentFocalLength
    };
    const flash: CameraDiagnosticsFlash={
      supported:this.flashSupported,
      offSupported:this.flashOffSupported,
      autoSupported:this.flashAutoSupported,
      onSupported:this.flashOnSupported,
      torchSupported:this.flashTorchSupported,
      mode:this.flashMode,
      requested:this.requestedFlashMode,
      diagnostics:this.flashDiagnostics
    };
    const record: CameraDiagnosticsRecord={
      updatedAt:Date.now(),
      session:this.session?(this.videoMode?'video':'photo'):'closed',
      activeLensRole:this.activeLensRole,
      activeLensEquivalentFocalLength:this.activeLensEquivalentFocalLength,
      activeLensPhysicalConfirmed:this.activeLensPhysicalConfirmed,
      hardwareTelephotoAvailable:this.hardwareTelephotoAvailable,
      accessibleTelephotoAvailable:this.accessibleTelephotoAvailable,
      lenses,
      zoom,
      flash,
      lastError:this.diagnosticsError
    };
    return JSON.stringify(record);
  }
  private scheduleDiagnosticsWrite(): void {
    if(!this.diagnosticsPath)return;
    const contents=this.diagnosticsSnapshot();
    const path=this.diagnosticsPath;
    this.diagnosticsWriteQueue=this.diagnosticsWriteQueue.catch(()=>{}).then(async()=>{
      const file=await fs.open(path,fs.OpenMode.CREATE|fs.OpenMode.READ_WRITE|fs.OpenMode.TRUNC);
      try {await fs.write(file.fd,contents);}finally{await fs.close(file);}
    }).catch((e: Error)=>{this.diagnosticsError='diagnostics-write:'+String(e);});
  }
  private recordDiagnosticsError(error: Error|string): void {
    this.diagnosticsError=String(error);
    this.scheduleDiagnosticsWrite();
  }
  private outputBindable(device: camera.CameraDevice, mode: camera.SceneMode): boolean {
    try {
      const caps=this.manager.getSupportedOutputCapability(device,mode);
      if(!caps.previewProfiles.length)return false;
      if(mode===camera.SceneMode.NORMAL_VIDEO)return caps.videoProfiles.some((value)=>value.format===camera.CameraFormat.CAMERA_FORMAT_YUV_420_SP);
      return caps.photoProfiles.length>0;
    }catch(e){return false;}
  }
  private refreshLensOutputCapabilities(): void {
    for(let i=0;i<this.devices.length;i++) {
      const capability=this.lensCapabilities[i];
      if(!capability)continue;
      capability.photoOutputCandidate=this.outputBindable(this.devices[i],camera.SceneMode.NORMAL_PHOTO);
      capability.videoOutputCandidate=this.outputBindable(this.devices[i],camera.SceneMode.NORMAL_VIDEO);
    }
    this.recomputeTelephotoFacts();
  }
  async open(surface: string,videoPath: string='',audio: boolean=false,rotation: number=0): Promise<void> {
    const wantedFlashMode=this.requestedFlashMode,wantedZoom=this.requestedZoom;this.videoMode=videoPath.length>0;await this.close();this.requestedFlashMode=wantedFlashMode;this.requestedZoom=wantedZoom;this.surface=surface;if(!this.devices.length)throw new Error('设备没有可用摄像头');const device=this.devices[this.index];const mode=videoPath?camera.SceneMode.NORMAL_VIDEO:camera.SceneMode.NORMAL_PHOTO;
    const caps=this.manager.getSupportedOutputCapability(device,mode);this.refreshLensOutputCapabilities();const selectedCapability=this.lensCapabilities[this.index];this.zoomReferenceFactor=selectedCapability?.referenceZoomMapped?selectedCapability.referenceZoomFactor:1;this.zoomReferenceMapped=!!selectedCapability?.referenceZoomMapped;this.recomputeTelephotoFacts();this.photoSupported=!videoPath&&!!selectedCapability?.photoOutputCandidate;this.videoSupported=!!selectedCapability?.videoOutputCandidate;this.input=this.manager.createCameraInput(device);if(!this.input)throw new Error('当前设备未提供可用相机输入');await this.input.open();this.input.on('error',device,()=>this.onError('相机被占用或已断开'));
    let ratio=4/3;
    if(videoPath) {
      const profiles=caps.videoProfiles.filter(p=>p.format===camera.CameraFormat.CAMERA_FORMAT_YUV_420_SP);
      profiles.sort((a,b)=>Math.abs(a.size.width*a.size.height-1920*1080)-Math.abs(b.size.width*b.size.height-1920*1080));const profile=profiles[0];if(!profile)throw new Error('当前设备不支持录像配置');this.videoWidth=profile.size.width;this.videoHeight=profile.size.height;ratio=profile.size.width/profile.size.height;
      this.outputFile=await fs.open(videoPath,fs.OpenMode.CREATE|fs.OpenMode.READ_WRITE|fs.OpenMode.TRUNC);this.recorder=await media.createAVRecorder();this.recorder.on('error',()=>this.onError('录像中断，请检查已有资料'));
      const cfg: media.AVRecorderConfig={videoSourceType:media.VideoSourceType.VIDEO_SOURCE_TYPE_SURFACE_YUV,profile:{fileFormat:media.ContainerFormatType.CFT_MPEG_4,videoBitrate:8000000,videoCodec:media.CodecMimeType.VIDEO_AVC,videoFrameWidth:this.videoWidth,videoFrameHeight:this.videoHeight,videoFrameRate:Math.min(30,profile.frameRateRange.max)},url:`fd://${this.outputFile.fd}`};
      if(audio){cfg.audioSourceType=media.AudioSourceType.AUDIO_SOURCE_TYPE_MIC;cfg.profile.audioBitrate=128000;cfg.profile.audioChannels=1;cfg.profile.audioCodec=media.CodecMimeType.AUDIO_AAC;cfg.profile.audioSampleRate=48000;}
      await this.recorder.prepare(cfg);this.video=this.manager.createVideoOutput(profile,await this.recorder.getInputSurface());
      if(!this.video)throw new Error('当前设备未提供录像输出');
    } else {const profiles=caps.photoProfiles.filter(p=>p.format===camera.CameraFormat.CAMERA_FORMAT_JPEG);profiles.sort((a,b)=>{const da=Math.abs(a.size.width/a.size.height-4/3),db=Math.abs(b.size.width/b.size.height-4/3);return Math.abs(da-db)>.02?da-db:b.size.width*b.size.height-a.size.width*a.size.height;});const profile=profiles[0]||caps.photoProfiles[0];if(profile)ratio=profile.size.width/profile.size.height;this.photo=profile?this.manager.createPhotoOutput(profile):this.manager.createPhotoOutput();
      this.photoSupported=!!this.photo;this.photo?.on('photoAvailable',async(err,value)=>{if(err){this.reject(new Error(`照片返回失败：${err.code}`));return;}try {const component=await value.main.getComponent(image.ComponentType.JPEG);const bytes=component.byteBuffer.slice(0);if(this.captureTimeout>=0){clearTimeout(this.captureTimeout);this.captureTimeout=-1;}this.pending?.(bytes);this.pending=null;this.failed=null;}catch(e){this.reject(e as Error);}finally{await value.main.release();}});
    }
    console.info('SITECAM_PROFILES photo='+caps.photoProfiles.map((v)=>v.size.width+'x'+v.size.height).join(',')+' preview='+caps.previewProfiles.map((v)=>v.size.width+'x'+v.size.height).join(','));const previews=caps.previewProfiles.slice().sort((a,b)=>{const da=Math.abs(a.size.width/a.size.height-ratio),db=Math.abs(b.size.width/b.size.height-ratio);return Math.abs(da-db)>.02?da-db:Math.abs(a.size.width-1280)-Math.abs(b.size.width-1280);});const pp=previews[0];if(!pp)throw new Error('没有可用预览配置');this.previewWidth=pp.size.width;this.previewHeight=pp.size.height;
    this.preview=this.manager.createPreviewOutput(pp,surface);if(!this.preview)throw new Error('当前设备未提供相机预览输出');this.session=videoPath?this.manager.createSession(camera.SceneMode.NORMAL_VIDEO) as camera.VideoSession:this.manager.createSession(camera.SceneMode.NORMAL_PHOTO) as camera.PhotoSession;if(!this.session)throw new Error('当前设备未提供原生相机会话，请在支持相机的设备上拍摄');this.session!.beginConfig();this.session!.addInput(this.input);this.session!.addOutput(this.preview);if(this.photo)this.session!.addOutput(this.photo);if(this.video)this.session!.addOutput(this.video);await this.session!.commitConfig();if(this.video&&this.recorder){const videoRotation=this.video.getVideoRotation(((rotation%360)+360)%360);await this.recorder.updateRotation(videoRotation);}try{const previewRotation=this.preview.getPreviewRotation(rotation);console.info(`SITECAM_PREVIEW_ROTATION_REQUEST display=${rotation} result=${String(previewRotation)}`);if(previewRotation===undefined)console.warn('SITECAM_PREVIEW_ROTATION_UNAVAILABLE');else{this.preview.setPreviewRotation(previewRotation,true);this.previewRotation=previewRotation;this.displayRotation=rotation;}}catch(e){console.warn('SITECAM_PREVIEW_ROTATION '+(e as Error).message);}console.info(`SITECAM_PREVIEW_SIZE ${this.previewWidth}x${this.previewHeight} surface=${surface}`);await this.session!.start();
    for(const capability of this.lensCapabilities)capability.active=false;const activeCapability=this.lensCapabilities[this.index];if(activeCapability){activeCapability.active=true;if(videoPath)activeCapability.videoSessionVerified=true;else activeCapability.photoSessionVerified=true;}if(videoPath)this.videoSupported=!!this.video;else this.photoSupported=!!this.photo;console.info('SITECAM_CAMERA rotation0='+this.photoRotation(0));this.sessionZoomMin=1;this.sessionZoomMax=1;this.sessionZoomRatio=1;this.zoomMin=1;this.zoomMax=1;this.zoomRatio=1;this.zoomEquivalentFocalLength=0;this.flash=false;this.flashMode='off';this.flashSupported=false;this.flashOffSupported=false;this.flashAutoSupported=false;this.flashOnSupported=false;this.flashTorchSupported=false;this.flashDiagnostics=`session=${videoPath?'video':'photo'} hasFlash=unknown`;try{const zoom=this.session!.getZoomRatioRange();if(zoom&&zoom.length){this.sessionZoomMin=Number.isFinite(zoom[0])&&zoom[0]>0?zoom[0]:1;this.sessionZoomMax=Number.isFinite(zoom[1])&&zoom[1]>=this.sessionZoomMin?zoom[1]:this.sessionZoomMin;}}catch(e){console.info('SITECAM_ZOOM_RANGE_UNAVAILABLE '+String(e));}this.zoomMin=referenceZoomFromSession(this.sessionZoomMin,this.zoomReferenceFactor);this.zoomMax=referenceZoomFromSession(this.sessionZoomMax,this.zoomReferenceFactor);this.readZoomPointInfos();const physicalTelephoto=!!activeCapability&&!activeCapability.logical&&activeCapability.role==='TELEPHOTO'&&(videoPath?activeCapability.videoSessionVerified:activeCapability.photoSessionVerified);this.zoomPresets=buildReferenceZoomPresets(this.sessionZoomMin,this.sessionZoomMax,this.zoomReferenceFactor,[],physicalTelephoto);if(!this.zoomPresets.length)this.zoomPresets=[this.zoomMin];const actualZoom=this.readZoomRatio();if(actualZoom!==null){this.sessionZoomRatio=actualZoom;this.zoomRatio=referenceZoomFromSession(actualZoom,this.zoomReferenceFactor);}this.zoom(wantedZoom);this.readFlashCapabilities();if(wantedFlashMode!=='off'&&!this.setFlashMode(wantedFlashMode,false))console.info('SITECAM_FLASH_RESTORE_UNCONFIRMED requested='+wantedFlashMode);this.updateActiveLensState(this.sessionZoomRatio);this.scheduleDiagnosticsWrite();
  }
  getActualPreviewRotation(): number { return this.previewRotation; }
  orientPreview(displayRotation: number): void {if(!this.preview||displayRotation===this.displayRotation)return;const rotation=this.preview.getPreviewRotation(displayRotation);console.info(`SITECAM_PREVIEW_ROTATION_UPDATE display=${displayRotation} result=${String(rotation)}`);if(rotation===undefined){console.warn('SITECAM_PREVIEW_ROTATION_UNAVAILABLE');return;}this.preview.setPreviewRotation(rotation,true);this.previewRotation=rotation;this.displayRotation=displayRotation;}
  photoRotation(degrees: number): number {const rotation=this.photo?.getPhotoRotation(degrees);return rotation===undefined?0:rotation;}
  private readZoomRatio(): number|null {
    if(!this.session)return null;
    try {
      const value=(this.session as camera.Zoom).getZoomRatio();
      // getZoomRatio may return a CameraErrorCode number on failure even
      // though the declaration is numeric.  Accept only a value in the
      // current session range; never expose 740xxxx as a zoom ratio.
      return isValidZoomRatio(value,this.sessionZoomMin,this.sessionZoomMax)?value:null;
    } catch(e) {
      return null;
    }
  }
  private readZoomPointInfos(): void {this.zoomPointRatios=[];this.zoomPointEquivalentFocalLengths=[];if(this.sdkApiVersion<26||!this.session)return;try{const infos=(this.session as camera.ZoomQuery).getZoomPointInfos();for(const info of infos){if(Number.isFinite(info.zoomRatio)&&info.zoomRatio>0){this.zoomPointRatios.push(info.zoomRatio);this.zoomPointEquivalentFocalLengths.push(Number.isFinite(info.equivalentFocalLength)?info.equivalentFocalLength:0);}}}catch(e){console.info('SITECAM_ZOOM_POINTS_UNAVAILABLE '+String(e));this.recordDiagnosticsError('zoom-points:'+String(e));}}
  private updateActiveLensState(ratio: number): void {const current=this.lensCapabilities[this.index];this.activeLensRole='UNKNOWN';this.activeLensEquivalentFocalLength=0;this.activeLensPhysicalConfirmed=false;this.zoomEquivalentFocalLength=nearbyZoomPointFocalLength(ratio,this.zoomPointRatios,this.zoomPointEquivalentFocalLengths);if(current&&!current.logical&&current.role!=='UNKNOWN'){this.activeLensRole=current.role;this.activeLensEquivalentFocalLength=current.equivalentFocalLengths.length===1?current.equivalentFocalLengths[0]:0;this.activeLensPhysicalConfirmed=current.equivalentFocalLengths.length<=1;}}
  private clearZoomReadback(): void {
    if(this.zoomReadbackTimer>=0){clearTimeout(this.zoomReadbackTimer);this.zoomReadbackTimer=-1;}
  }
  private applyZoomReadback(actual: number,requestToken: number,commitRequested: boolean=true): void {
    if(requestToken!==this.zoomRequestToken)return;
    this.sessionZoomRatio=actual;
    this.zoomRatio=referenceZoomFromSession(actual,this.zoomReferenceFactor);
    // A passive sync immediately after open can observe the previous frame
    // while the SDK is still applying the new request. Keep the requested
    // intent until the bounded readback confirms that request.
    if(commitRequested)this.requestedZoom=this.zoomRatio;
    this.updateActiveLensState(actual);
    this.onZoomChanged(this.zoomRatio);
    this.scheduleDiagnosticsWrite();
  }
  private scheduleZoomReadback(sessionToken: number,requestToken: number,targetSession: number): void {
    this.clearZoomReadback();
    const delays:number[]=[34,68,120];
    const poll=(attempt: number): void => {
      if(sessionToken!==this.zoomSessionToken||requestToken!==this.zoomRequestToken||!this.session)return;
      this.zoomReadbackTimer=setTimeout(()=>{
        this.zoomReadbackTimer=-1;
        if(sessionToken!==this.zoomSessionToken||requestToken!==this.zoomRequestToken||!this.session)return;
        const actual=this.readZoomRatio();
        const closeEnough=actual!==null&&Math.abs(actual-targetSession)<=Math.max(0.02,targetSession*0.015);
        console.info(`SITECAM_ZOOM_READBACK request=${requestToken} attempt=${attempt+1} target=${targetSession} actual=${actual===null?'unknown':actual}`);
        if(actual!==null&&(closeEnough||attempt===delays.length-1)){
          this.applyZoomReadback(actual,requestToken);
          return;
        }
        if(attempt+1<delays.length)poll(attempt+1);
        else if(actual===null)this.recordDiagnosticsError('zoom-readback-unknown');
      },delays[attempt]);
    };
    poll(0);
  }
  syncZoom(): number {
    const actual=this.readZoomRatio();
    if(actual!==null)this.applyZoomReadback(actual,this.zoomRequestToken,this.zoomReadbackTimer<0);
    return this.zoomRatio;
  }
  async shoot(rotation: number=0,onAccepted?:()=>void): Promise<ArrayBuffer> {if(!this.photo||this.pending)throw new Error('相机尚未就绪');const token=++this.captureToken;console.info(`SITECAM_FLASH_CAPTURE mode=${this.flashMode} supported=${this.flashSupported}`);return new Promise<ArrayBuffer>((resolve,reject)=>{this.pending=resolve;this.failed=reject;this.photo!.capture({quality:camera.QualityLevel.QUALITY_LEVEL_HIGH,rotation:rotation as camera.ImageRotation}).then(()=>{if(token!==this.captureToken)return;try{onAccepted?.();}catch(e){console.warn('SITECAM_SHUTTER_CALLBACK '+String(e));}}).catch((e: Error)=>this.reject(e));this.captureTimeout=setTimeout(()=>{if(this.pending&&token===this.captureToken)this.reject(new Error('拍摄超时，请重试'));},15000);});}
  private reject(e: Error): void {if(this.captureTimeout>=0){clearTimeout(this.captureTimeout);this.captureTimeout=-1;}this.failed?.(e);this.pending=null;this.failed=null;}
  zoom(value: number): number {
    if(!this.session)return this.zoomRatio;
    const requested=Number.isFinite(value)?value:this.zoomRatio;
    const target=Math.max(this.zoomMin,Math.min(this.zoomMax,requested));
    const sessionTarget=sessionZoomFromReference(target,this.zoomReferenceFactor,this.sessionZoomMin,this.sessionZoomMax);
    const session=this.session;
    const sessionToken=this.zoomSessionToken;
    const requestToken=++this.zoomRequestToken;
    this.clearZoomReadback();
    try {
      session.setZoomRatio(sessionTarget);
      this.requestedZoom=target;
      console.info(`SITECAM_ZOOM_REQUEST request=${requestToken} reference=${target} session=${sessionTarget} range=${this.sessionZoomMin}-${this.sessionZoomMax}`);
      this.scheduleDiagnosticsWrite();
      // The SDK documents a one-to-two-frame delay before getZoomRatio is
      // authoritative.  A bounded follow-up readback prevents an old
      // request from overwriting a newer click or a newly opened session.
      this.scheduleZoomReadback(sessionToken,requestToken,sessionTarget);
    } catch(e) {
      this.recordDiagnosticsError('zoom-set:'+String(e));
      console.warn('SITECAM_ZOOM_SET '+String(e));
    }
    return this.zoomRatio;
  }
  focus(x: number,y: number): void {if(this.session?.isFocusModeSupported(camera.FocusMode.FOCUS_MODE_AUTO)){let fx=x;let fy=y;const r=((this.previewRotation%360)+360)%360;if(r===90){fx=1-y;fy=x;}else if(r===180){fx=1-x;fy=1-y;}else if(r===270){fx=y;fy=1-x;}this.session!.setFocusMode(camera.FocusMode.FOCUS_MODE_AUTO);this.session!.setFocusPoint({x:Math.max(0,Math.min(1,fx)),y:Math.max(0,Math.min(1,fy))});}}
  private flashEnum(mode: string): camera.FlashMode {if(mode==='on')return camera.FlashMode.FLASH_MODE_OPEN;if(mode==='auto')return camera.FlashMode.FLASH_MODE_AUTO;if(mode==='torch')return camera.FlashMode.FLASH_MODE_ALWAYS_OPEN;return camera.FlashMode.FLASH_MODE_CLOSE;}
  private flashName(mode: camera.FlashMode): string|null {
    const value=Number(mode);
    if(value===Number(camera.FlashMode.FLASH_MODE_CLOSE))return 'off';
    if(value===Number(camera.FlashMode.FLASH_MODE_OPEN))return 'on';
    if(value===Number(camera.FlashMode.FLASH_MODE_AUTO))return 'auto';
    if(value===Number(camera.FlashMode.FLASH_MODE_ALWAYS_OPEN))return 'torch';
    return null;
  }
  private actualFlashMode(): string|null {
    if(!this.session)return null;
    try {
      const raw=this.session.getFlashMode();
      const actual=this.flashName(raw);
      if(actual===null)console.warn(`SITECAM_FLASH_READBACK_INVALID value=${String(raw)}`);
      return actual;
    } catch(e) {
      console.warn('SITECAM_FLASH_READBACK '+String(e));
      return null;
    }
  }
  private readFlashCapabilities(): void {
    this.flashSupported=false;this.flashOffSupported=false;this.flashAutoSupported=false;this.flashOnSupported=false;this.flashTorchSupported=false;
    this.flashMode='off';this.flash=false;
    if(!this.session){this.flashDiagnostics='session=none';this.scheduleDiagnosticsWrite();return;}
    const modeName=this.videoMode?'video':'photo';
    let hasFlashResult='unknown';let offResult='unknown';let autoResult='unknown';let onResult='unknown';let torchResult='unknown';let readback='unknown';
    try {
      const hasFlash=this.session.hasFlash();
      this.flashSupported=hasFlash===true;
      hasFlashResult=hasFlash===true?'true':hasFlash===false?'false':`invalid(${String(hasFlash)})`;
    } catch(e) {
      hasFlashResult='error';
      console.warn('SITECAM_FLASH_HAS '+String(e));
    }
    if(this.flashSupported){
      try {const result=this.session.isFlashModeSupported(camera.FlashMode.FLASH_MODE_CLOSE);this.flashOffSupported=result===true;offResult=result===true?'true':result===false?'false':`invalid(${String(result)})`;}catch(e){offResult='error';console.warn('SITECAM_FLASH_SUPPORT off '+String(e));}
      try {const result=this.session.isFlashModeSupported(camera.FlashMode.FLASH_MODE_AUTO);this.flashAutoSupported=result===true;autoResult=result===true?'true':result===false?'false':`invalid(${String(result)})`;}catch(e){autoResult='error';console.warn('SITECAM_FLASH_SUPPORT auto '+String(e));}
      try {const result=this.session.isFlashModeSupported(camera.FlashMode.FLASH_MODE_OPEN);this.flashOnSupported=result===true;onResult=result===true?'true':result===false?'false':`invalid(${String(result)})`;}catch(e){onResult='error';console.warn('SITECAM_FLASH_SUPPORT on '+String(e));}
      try {const result=this.session.isFlashModeSupported(camera.FlashMode.FLASH_MODE_ALWAYS_OPEN);this.flashTorchSupported=result===true;torchResult=result===true?'true':result===false?'false':`invalid(${String(result)})`;}catch(e){torchResult='error';console.warn('SITECAM_FLASH_SUPPORT torch '+String(e));}
      const actual=this.actualFlashMode();
      if(actual!==null){this.flashMode=actual;this.flash=actual!=='off';readback=actual;}
    }
    this.flashDiagnostics=`session=${modeName} hasFlash=${hasFlashResult} off=${offResult} auto=${autoResult} on=${onResult} torch=${torchResult} readback=${readback}`;
    console.info('SITECAM_FLASH_CAPABILITY '+this.flashDiagnostics);
    this.scheduleDiagnosticsWrite();
  }
  setFlash(enabled: boolean): void {this.setFlashMode(enabled?'on':'off');}
  setFlashMode(mode: string, remember: boolean=true): boolean {
    const canonical=mode==='auto'||mode==='on'||mode==='torch'?mode:'off';
    if(!this.session)return false;
    if(this.flashSupported!==true){
      this.flashDiagnostics=`${this.flashDiagnostics} set=${canonical}:hasFlash-not-confirmed`;
      console.info('SITECAM_FLASH_SET '+this.flashDiagnostics);
      this.scheduleDiagnosticsWrite();
      return false;
    }
    const target=this.flashEnum(canonical);
    const previous=this.flashMode;
    try {
      const supported=this.session.isFlashModeSupported(target);
      if(supported!==true){
        this.flashDiagnostics=`${this.flashDiagnostics} set=${canonical}:unsupported(${String(supported)})`;
        console.info('SITECAM_FLASH_SET '+this.flashDiagnostics);
        this.scheduleDiagnosticsWrite();
        return false;
      }
      this.session.setFlashMode(target);
      const actual=this.actualFlashMode();
      if(actual===null){
        // No readback means no confirmation. Keep the last confirmed icon and
        // state instead of turning a hardware error into a fake OFF result.
        this.flashDiagnostics=`${this.flashDiagnostics} set=${canonical}:readback-unknown`;
        console.warn('SITECAM_FLASH_SET '+this.flashDiagnostics);
        this.scheduleDiagnosticsWrite();
        return false;
      }
      this.flashMode=actual;this.flash=this.flashMode!=='off';
      this.flashDiagnostics=`${this.flashDiagnostics} set=${canonical}:readback=${actual}`;
      console.info('SITECAM_FLASH_SET '+this.flashDiagnostics);
      this.scheduleDiagnosticsWrite();
      if(actual!==canonical)return false;
      if(remember)this.requestedFlashMode=actual;
      return true;
    } catch(e) {
      this.flashMode=previous;this.flash=this.flashMode!=='off';
      this.flashDiagnostics=`${this.flashDiagnostics} set=${canonical}:error`;
      this.recordDiagnosticsError('flash-set:'+String(e));
      console.warn('SITECAM_FLASH_SET '+this.flashDiagnostics+' '+String(e));
      return false;
    }
  }
  setTorch(enabled: boolean): void {this.setFlashMode(enabled?'torch':'off');}
  toggleTorch(): string {const wanted=this.flashMode==='torch'?'off':'torch';this.setFlashMode(wanted);return this.flashMode;}
  cycleFlash(): string {if(!this.flashSupported)return this.flashMode;const modes:string[]=[];if(this.flashOffSupported)modes.push('off');if(this.flashAutoSupported)modes.push('auto');if(this.flashOnSupported)modes.push('on');if(!modes.length)return this.flashMode;const next=nextSupportedFlashMode(this.flashMode,modes);this.setFlashMode(next);return this.flashMode;}
  async switchLens(videoMode: boolean=this.videoMode): Promise<void> {if(this.recording||this.videoMode)throw new Error('录像期间不能切换镜头');const candidates=this.lensSwitchCandidates(videoMode);if(candidates.length<2)return;const previousIndex=this.index,previousZoom=this.requestedZoom,previousFlash=this.requestedFlashMode;const currentPosition=candidates.indexOf(previousIndex);const nextIndex=candidates[(currentPosition<0?0:currentPosition+1)%candidates.length];if(nextIndex===previousIndex)return;this.index=nextIndex;const degrees=this.displayRotation>=0?this.displayRotation:0;try{await this.open(this.surface,'',false,degrees);}catch(e){this.index=previousIndex;this.requestedZoom=previousZoom;this.requestedFlashMode=previousFlash;try{await this.open(this.surface,'',false,degrees);}catch(restoreError){console.warn('SITECAM_LENS_RESTORE '+String(restoreError));}throw e as Error;}}
  async startRecording(): Promise<void> {if(!this.recorder||!this.video)throw new Error('录像尚未就绪');await this.video.start();await this.recorder.start();this.recording=true;}
  async stopRecording(): Promise<void> {if(!this.recording)return;this.recording=false;await this.video?.stop();await this.recorder?.stop();}
  async close(): Promise<void> {
    const preserveFlash=this.requestedFlashMode;this.zoomSessionToken++;this.zoomRequestToken++;this.clearZoomReadback();this.previewRotation=-1;this.displayRotation=-1;this.captureToken++;this.reject(new Error('相机已关闭'));
    if(this.recording){try{await this.stopRecording();}catch(e){this.recording=false;}}
    try {await this.session?.stop();}catch(e){}
    try {await this.session?.release();}catch(e){}this.session=null;
    try {await this.photo?.release();}catch(e){}this.photo=null;
    try {await this.video?.release();}catch(e){}this.video=null;
    try {await this.preview?.release();}catch(e){}this.preview=null;
    try {await this.input?.close();}catch(e){}this.input=null;
    try {await this.recorder?.release();}catch(e){}this.recorder=null;
    if(this.outputFile){await fs.close(this.outputFile);this.outputFile=null;}for(const capability of this.lensCapabilities)capability.active=false;this.activeLensRole='UNKNOWN';this.activeLensEquivalentFocalLength=0;this.activeLensPhysicalConfirmed=false;this.zoomEquivalentFocalLength=0;this.flashMode='off';this.flash=false;this.flashSupported=false;this.flashOffSupported=false;this.flashAutoSupported=false;this.flashOnSupported=false;this.flashTorchSupported=false;this.flashDiagnostics='session=closed';this.requestedFlashMode=preserveFlash==='torch'?'off':preserveFlash;this.scheduleDiagnosticsWrite();
  }
}
