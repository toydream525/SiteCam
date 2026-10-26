import { camera } from '@kit.CameraKit';
import { media } from '@kit.MediaKit';
import { image } from '@kit.ImageKit';
import { common } from '@kit.AbilityKit';
import { fileIo as fs } from '@kit.CoreFileKit';
import { BusinessError, deviceInfo } from '@kit.BasicServicesKit';
import { nextSupportedFlashMode } from './FlashMode';
import { CameraLensCapability, LensRole, buildReferenceZoomPresets, buildSessionZoomPresets, isValidZoomRatio, lensRoleFromPublicFacts, nativeLensForZoom, nearbyZoomPointFocalLength, referenceZoomFactor, referenceZoomFromSession, sessionZoomFromReference } from './CameraCapability';

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

interface PhotoRequest {
  token:number;
  outputEpoch:number;
  timeout:number;
  resolve:(bytes:ArrayBuffer)=>void;
  reject:(error:Error)=>void;
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
  private snapshotEnabled:boolean=true;
  private snapshotShutterSpeed:number=250;
  private shortExposureActive:boolean=false;
  private lowLightFallback:boolean=false;
  private exposureRetryAfter:number=0;
  private captureRequest:PhotoRequest|null=null;
  private captureToken:number=0;
  private photoOutputEpoch:number=0;
  private photoSessionReady:boolean=false;
  private photoRecovery:Promise<void>|null=null;
  private lifecycleGeneration:number=0;
  private lifecycleWork:Promise<void>=Promise.resolve();
  /** Session-scoped lens exclusions; the device fact appAccessible stays untouched. */
  private lensSessionUnusable:boolean[]=[];
  private diagnosticsPending:boolean=false;
  onFocusChanged:(state:string)=>void=()=>{};
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
      if(capability.appAccessible&&!this.lensSessionUnusable[i]&&requestedOutput&&capability.photoOutputCandidate)candidates.push(i);
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
    this.allDevices=this.discoverPublicDevices();
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
    this.lensSessionUnusable=this.lensCapabilities.map((_: CameraLensCapability)=>false);
    this.configureReferenceZoomFactors();
    this.recomputeTelephotoFacts();
    this.scheduleDiagnosticsWrite();
  }
  private discoverPublicDevices(): camera.CameraDevice[] {
    const devices=this.manager.getSupportedCameras().slice();
    const append=(device:camera.CameraDevice):void=>{if(device&&!devices.some((known:camera.CameraDevice)=>known.cameraId===device.cameraId))devices.push(device);};
    const types:camera.CameraType[]=[camera.CameraType.CAMERA_TYPE_WIDE_ANGLE,camera.CameraType.CAMERA_TYPE_ULTRA_WIDE,camera.CameraType.CAMERA_TYPE_TELEPHOTO];
    for(const position of [camera.CameraPosition.CAMERA_POSITION_BACK,camera.CameraPosition.CAMERA_POSITION_FRONT]){
      if(this.sdkApiVersion>=23){
        try{for(const device of this.manager.getCameraDevices(position,types,camera.ConnectionType.CAMERA_CONNECTION_BUILT_IN)||[])append(device);}catch(e){console.info('SITECAM_EXTRA_CAMERAS '+String(e));}
      }else if(this.sdkApiVersion>=18){
        for(const type of types){try{append(this.manager.getCameraDevice(position,type));}catch(e){console.info('SITECAM_EXTRA_CAMERA '+String(e));}}
      }
    }
    return devices;
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

  nativeZoomPresets(videoMode: boolean=this.videoMode): number[] {
    const active=this.lensCapabilities[this.index];
    if(!active||!this.session)return [];
    const own=active.logical||videoMode&&!active.videoOutputCandidate?[]:buildReferenceZoomPresets(this.sessionZoomMin,this.sessionZoomMax,this.zoomReferenceFactor,[],true);
    if(this.recording||this.videoMode||(!active.logical&&!active.referenceZoomMapped))return own;
    const values=own.slice();
    for(let i=0;i<this.lensCapabilities.length;i++){
      const lens=this.lensCapabilities[i];
      if(lens.logical||this.lensSessionUnusable[i]||lens.cameraPosition!==active.cameraPosition||!lens.appAccessible||!lens.referenceZoomMapped||!lens.photoOutputCandidate||(videoMode&&!lens.videoOutputCandidate))continue;
      if(!values.some((value:number)=>Math.abs(value-lens.referenceZoomFactor)<0.0001))values.push(lens.referenceZoomFactor);
    }
    values.sort((a:number,b:number)=>a-b);
    return values;
  }

  availableZoomPresets(videoMode:boolean=this.videoMode):number[] {
    const active=this.lensCapabilities[this.index];
    if(!active||!this.session||(videoMode&&!active.videoOutputCandidate))return [];
    const values=this.nativeZoomPresets(videoMode);
    for(const value of buildSessionZoomPresets(this.sessionZoomMin,this.sessionZoomMax,this.zoomReferenceFactor,this.zoomPointRatios)){
      if(!values.some((existing:number)=>Math.abs(existing-value)<0.0001))values.push(value);
    }
    return values.sort((a:number,b:number)=>a-b);
  }

  private zoomLensIndex(value: number, videoMode: boolean): number {
    if(this.recording||this.videoMode)return this.index;
    return nativeLensForZoom(this.usableLensView(),this.index,value,videoMode);
  }
  /**
   * nativeLensForZoom only knows device facts, so a lens this session already
   * failed to bind is passed as a copy with appAccessible cleared. Indexes are
   * preserved for the caller.
   */
  private usableLensView(): CameraLensCapability[] {
    if(!this.lensSessionUnusable.some((value: boolean)=>value))return this.lensCapabilities;
    return this.lensCapabilities.map((lens: CameraLensCapability,index: number)=>{
      if(!this.lensSessionUnusable[index])return lens;
      const copy=JSON.parse(JSON.stringify(lens)) as CameraLensCapability;
      copy.appAccessible=false;
      return copy;
    });
  }
  requiresNativeLensSwitch(value: number, videoMode: boolean): boolean {
    return this.zoomLensIndex(value,videoMode)!==this.index;
  }
  async zoomAcrossLenses(value: number, videoMode: boolean): Promise<number> {
    const nextIndex=this.zoomLensIndex(value,videoMode);
    if(nextIndex===this.index)return this.zoom(value);
    await this.switchToLens(nextIndex,value);
    return this.zoomRatio;
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
    if(!this.diagnosticsPath||this.diagnosticsPending)return;
    // One full JSON snapshot per burst: a pinch gesture would otherwise write
    // the whole record on every gesture frame.
    this.diagnosticsPending=true;
    setTimeout(()=>{this.diagnosticsPending=false;this.writeDiagnostics();},500);
  }
  private writeDiagnostics(): void {
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
  private enqueueLifecycle(action:()=>Promise<void>):Promise<void>{const next=this.lifecycleWork.catch(()=>{}).then(action);this.lifecycleWork=next;return next;}
  private requireLifecycle(generation:number):void{if(generation!==this.lifecycleGeneration)throw new Error('相机操作已取消');}
  async open(surface: string,videoPath: string='',audio: boolean=false,rotation: number=0): Promise<void> {
    const generation=++this.lifecycleGeneration,wantedFlashMode=this.requestedFlashMode,wantedZoom=this.requestedZoom;
    this.invalidatePhoto(new Error('相机会话已切换'));
    await this.enqueueLifecycle(async()=>{
      this.requireLifecycle(generation);
      try{await this.openNow(surface,videoPath,audio,rotation,generation,wantedFlashMode,wantedZoom);}
      catch(e){await this.closeNow();throw e as Error;}
    });
  }
  private async openNow(surface:string,videoPath:string,audio:boolean,rotation:number,generation:number,wantedFlashMode:string,wantedZoom:number):Promise<void>{
    this.videoMode=videoPath.length>0;await this.closeNow();this.requireLifecycle(generation);this.requestedFlashMode=wantedFlashMode;this.requestedZoom=wantedZoom;this.surface=surface;if(!this.devices.length)throw new Error('设备没有可用摄像头');const device=this.devices[this.index];const mode=videoPath?camera.SceneMode.NORMAL_VIDEO:camera.SceneMode.NORMAL_PHOTO;
    const caps=this.manager.getSupportedOutputCapability(device,mode);this.refreshLensOutputCapabilities();const selectedCapability=this.lensCapabilities[this.index];this.zoomReferenceFactor=selectedCapability?.referenceZoomMapped?selectedCapability.referenceZoomFactor:1;this.zoomReferenceMapped=!!selectedCapability?.referenceZoomMapped;this.recomputeTelephotoFacts();this.photoSupported=!videoPath&&!!selectedCapability?.photoOutputCandidate;this.videoSupported=!!selectedCapability?.videoOutputCandidate;this.input=this.manager.createCameraInput(device);if(!this.input)throw new Error('当前设备未提供可用相机输入');await this.input.open();this.requireLifecycle(generation);this.input.on('error',device,()=>{if(generation!==this.lifecycleGeneration)return;this.zoomSessionToken++;this.zoomRequestToken++;this.clearZoomReadback();this.invalidatePhoto(new Error('相机被占用或已断开'));this.onError('相机被占用或已断开');});
    let ratio=4/3;
    if(videoPath) {
      const profiles=caps.videoProfiles.filter(p=>p.format===camera.CameraFormat.CAMERA_FORMAT_YUV_420_SP);
      profiles.sort((a,b)=>Math.abs(a.size.width*a.size.height-1920*1080)-Math.abs(b.size.width*b.size.height-1920*1080));const profile=profiles[0];if(!profile)throw new Error('当前设备不支持录像配置');this.videoWidth=profile.size.width;this.videoHeight=profile.size.height;ratio=profile.size.width/profile.size.height;
      this.outputFile=await fs.open(videoPath,fs.OpenMode.CREATE|fs.OpenMode.READ_WRITE|fs.OpenMode.TRUNC);this.requireLifecycle(generation);this.recorder=await media.createAVRecorder();this.requireLifecycle(generation);this.recorder.on('error',()=>{if(generation===this.lifecycleGeneration)this.onError('录像中断，请检查已有资料');});
      const cfg: media.AVRecorderConfig={videoSourceType:media.VideoSourceType.VIDEO_SOURCE_TYPE_SURFACE_YUV,profile:{fileFormat:media.ContainerFormatType.CFT_MPEG_4,videoBitrate:8000000,videoCodec:media.CodecMimeType.VIDEO_AVC,videoFrameWidth:this.videoWidth,videoFrameHeight:this.videoHeight,videoFrameRate:Math.max(profile.frameRateRange.min,Math.min(30,profile.frameRateRange.max))},url:`fd://${this.outputFile.fd}`};
      if(audio){cfg.audioSourceType=media.AudioSourceType.AUDIO_SOURCE_TYPE_MIC;cfg.profile.audioBitrate=128000;cfg.profile.audioChannels=1;cfg.profile.audioCodec=media.CodecMimeType.AUDIO_AAC;cfg.profile.audioSampleRate=48000;}
      await this.recorder.prepare(cfg);this.requireLifecycle(generation);const recorderSurface=await this.recorder.getInputSurface();this.requireLifecycle(generation);this.video=this.manager.createVideoOutput(profile,recorderSurface);
      if(!this.video)throw new Error('当前设备未提供录像输出');
    } else {const profiles=caps.photoProfiles.filter(p=>p.format===camera.CameraFormat.CAMERA_FORMAT_JPEG);profiles.sort((a,b)=>{const da=Math.abs(a.size.width/a.size.height-4/3),db=Math.abs(b.size.width/b.size.height-4/3);return Math.abs(da-db)>.02?da-db:this.snapshotEnabled?Math.abs(a.size.width*a.size.height-2560*1920)-Math.abs(b.size.width*b.size.height-2560*1920):b.size.width*b.size.height-a.size.width*a.size.height;});const profile=profiles[0]||caps.photoProfiles[0];if(profile)ratio=profile.size.width/profile.size.height;this.photo=profile?this.manager.createPhotoOutput(profile):this.manager.createPhotoOutput();
      this.photoSupported=!!this.photo;const outputEpoch=this.photoOutputEpoch;
      this.photo?.on('photoAvailable',async(err,value)=>{
        // The closure belongs to this output. A timed-out output is never reused.
        const request=this.captureRequest;
        if(err){if(request&&request.outputEpoch===outputEpoch&&outputEpoch===this.photoOutputEpoch)this.failCapture(request,new Error(`照片返回失败：${err.code}`));return;}
        try{
          if(!request||request.outputEpoch!==outputEpoch||outputEpoch!==this.photoOutputEpoch)return;
          const component=await value.main.getComponent(image.ComponentType.JPEG);
          if(this.captureRequest!==request||outputEpoch!==this.photoOutputEpoch)return;
          const bytes=component.byteBuffer.slice(0);this.clearCapture(request);request.resolve(bytes);
        }catch(e){if(request)this.failCapture(request,e as Error);}
        finally{await value.main.release();}
      });
    }
    console.info('SITECAM_PROFILES photo='+caps.photoProfiles.map((v)=>v.size.width+'x'+v.size.height).join(',')+' preview='+caps.previewProfiles.map((v)=>v.size.width+'x'+v.size.height).join(','));const previews=caps.previewProfiles.slice().sort((a,b)=>{const da=Math.abs(a.size.width/a.size.height-ratio),db=Math.abs(b.size.width/b.size.height-ratio);return Math.abs(da-db)>.02?da-db:Math.abs(a.size.width-1280)-Math.abs(b.size.width-1280);});const pp=previews[0];if(!pp)throw new Error('没有可用预览配置');this.previewWidth=pp.size.width;this.previewHeight=pp.size.height;
    this.preview=this.manager.createPreviewOutput(pp,surface);if(!this.preview)throw new Error('当前设备未提供相机预览输出');this.session=videoPath?this.manager.createSession(camera.SceneMode.NORMAL_VIDEO) as camera.VideoSession:this.manager.createSession(camera.SceneMode.NORMAL_PHOTO) as camera.PhotoSession;if(!this.session)throw new Error('当前设备未提供原生相机会话，请在支持相机的设备上拍摄');this.session!.beginConfig();this.session!.addInput(this.input);this.session!.addOutput(this.preview);if(this.photo)this.session!.addOutput(this.photo);if(this.video)this.session!.addOutput(this.video);await this.session!.commitConfig();this.requireLifecycle(generation);if(this.video&&this.recorder){const videoRotation=this.video.getVideoRotation(((rotation%360)+360)%360);await this.recorder.updateRotation(videoRotation);this.requireLifecycle(generation);}try{const previewRotation=this.preview.getPreviewRotation(rotation);console.info(`SITECAM_PREVIEW_ROTATION_REQUEST display=${rotation} result=${String(previewRotation)}`);if(previewRotation===undefined)console.warn('SITECAM_PREVIEW_ROTATION_UNAVAILABLE');else{this.preview.setPreviewRotation(previewRotation,true);this.previewRotation=previewRotation;this.displayRotation=rotation;}}catch(e){console.warn('SITECAM_PREVIEW_ROTATION '+(e as Error).message);}console.info(`SITECAM_PREVIEW_SIZE ${this.previewWidth}x${this.previewHeight} surface=${surface}`);this.session.on('focusStateChange',(error,state)=>{
      if(generation!==this.lifecycleGeneration)return;
      if(error){this.onFocusChanged('unfocused');return;}
      if(state===camera.FocusState.FOCUS_STATE_SCAN)this.onFocusChanged('focusing');
      else if(state===camera.FocusState.FOCUS_STATE_FOCUSED)this.onFocusChanged('focused');
      else if(state===camera.FocusState.FOCUS_STATE_UNFOCUSED)this.onFocusChanged('unfocused');
    });await this.session!.start();this.requireLifecycle(generation);
    this.lowLightFallback=false;this.shortExposureActive=false;this.applySnapshotControls();
    if(!videoPath&&this.sdkApiVersion>=24){
      try{const photoSession=this.session as camera.PhotoSession;const iso=photoSession.getSupportedIsoRange();const maxIso=Math.max(...iso);photoSession.onIsoInfoChange((info:camera.IsoInfo)=>{if(generation!==this.lifecycleGeneration||!this.snapshotEnabled||!info.iso)return;if(this.shortExposureActive&&info.iso>=maxIso*.9){this.lowLightFallback=true;this.exposureRetryAfter=Date.now()+2000;this.applySnapshotControls();}else if(this.lowLightFallback&&Date.now()>=this.exposureRetryAfter&&info.iso<maxIso*.25){this.lowLightFallback=false;this.applySnapshotControls();}});}catch(e){console.info('SITECAM_SNAPSHOT_ISO '+String(e));}
    }
    for(const capability of this.lensCapabilities)capability.active=false;const activeCapability=this.lensCapabilities[this.index];if(activeCapability){activeCapability.active=true;activeCapability.appAccessible=true;if(videoPath)activeCapability.videoSessionVerified=true;else activeCapability.photoSessionVerified=true;}if(videoPath)this.videoSupported=!!this.video;else this.photoSupported=!!this.photo;console.info('SITECAM_CAMERA rotation0='+this.photoRotation(0));this.sessionZoomMin=1;this.sessionZoomMax=1;this.sessionZoomRatio=1;this.zoomMin=1;this.zoomMax=1;this.zoomRatio=1;this.zoomEquivalentFocalLength=0;this.flash=false;this.flashMode='off';this.flashSupported=false;this.flashOffSupported=false;this.flashAutoSupported=false;this.flashOnSupported=false;this.flashTorchSupported=false;this.flashDiagnostics=`session=${videoPath?'video':'photo'} hasFlash=unknown`;try{const zoom=this.session!.getZoomRatioRange();if(zoom&&zoom.length){this.sessionZoomMin=Number.isFinite(zoom[0])&&zoom[0]>0?zoom[0]:1;this.sessionZoomMax=Number.isFinite(zoom[1])&&zoom[1]>=this.sessionZoomMin?zoom[1]:this.sessionZoomMin;}}catch(e){console.info('SITECAM_ZOOM_RANGE_UNAVAILABLE '+String(e));}this.zoomMin=referenceZoomFromSession(this.sessionZoomMin,this.zoomReferenceFactor);this.zoomMax=referenceZoomFromSession(this.sessionZoomMax,this.zoomReferenceFactor);this.readZoomPointInfos();this.zoomPresets=this.availableZoomPresets();const actualZoom=this.readZoomRatio();if(actualZoom!==null){this.sessionZoomRatio=actualZoom;this.zoomRatio=referenceZoomFromSession(actualZoom,this.zoomReferenceFactor);}this.zoom(wantedZoom);this.readFlashCapabilities();if(wantedFlashMode!=='off'&&!this.setFlashMode(wantedFlashMode,false))console.info('SITECAM_FLASH_RESTORE_UNCONFIRMED requested='+wantedFlashMode);this.updateActiveLensState(this.sessionZoomRatio);this.photoSessionReady=!!this.photo;this.scheduleDiagnosticsWrite();
  }
  previewIsMirrored():boolean{return this.devices[this.index]?.cameraPosition===camera.CameraPosition.CAMERA_POSITION_FRONT;}
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
    // A pending readback owns the value; writing here would push the previous
    // frame back and make the UI jump to the already-left zoom step.
    if(actual!==null&&this.zoomReadbackTimer<0)this.applyZoomReadback(actual,this.zoomRequestToken,true);
    return this.zoomRatio;
  }
  async configureSnapshot(enabled:boolean,shutterSpeed:number):Promise<void>{
    const reopen=enabled!==this.snapshotEnabled&&!!this.session&&!this.videoMode;
    this.setSnapshotMode(enabled,shutterSpeed);
    if(reopen)await this.open(this.surface,'',false,this.displayRotation>=0?this.displayRotation:0);
  }
  setSnapshotMode(enabled:boolean,shutterSpeed:number):void {
    this.snapshotEnabled=enabled;this.snapshotShutterSpeed=[125,250,500,1000].includes(shutterSpeed)?shutterSpeed:250;
    this.applySnapshotControls();
  }
  private applySnapshotControls():void {
    if(!this.session||this.videoMode)return;
    const session=this.session as camera.PhotoSession;
    try{if(session.isFocusModeSupported(camera.FocusMode.FOCUS_MODE_CONTINUOUS_AUTO))session.setFocusMode(camera.FocusMode.FOCUS_MODE_CONTINUOUS_AUTO);}catch(e){}
    if(this.sdkApiVersion>=21&&this.photo){
      try{const priority=this.snapshotEnabled?camera.PhotoQualityPrioritization.SPEED:camera.PhotoQualityPrioritization.HIGH_QUALITY;if(this.photo.isPhotoQualityPrioritizationSupported(priority))this.photo.setPhotoQualityPrioritization(priority);}catch(e){console.info('SITECAM_SNAPSHOT_SPEED '+String(e));}
    }
    if(this.sdkApiVersion<24)return;
    try{
      if(!this.snapshotEnabled||this.lowLightFallback){
        if(this.shortExposureActive&&session.isExposureModeSupported(camera.ExposureMode.EXPOSURE_MODE_CONTINUOUS_AUTO))session.setExposureMode(camera.ExposureMode.EXPOSURE_MODE_CONTINUOUS_AUTO);
        this.shortExposureActive=false;return;
      }
      const isoRange=session.getSupportedIsoRange();
      // A device without automatic ISO must retain automatic exposure, rather
      // than applying a fast shutter that turns a dark scene black.
      if(!isoRange.includes(0)||!session.isExposureModeSupported(camera.ExposureMode.EXPOSURE_MODE_MANUAL))return;
      const exposure=session.getSupportedExposureDurationRange();
      if(exposure.length<2)return;
      session.setExposureMode(camera.ExposureMode.EXPOSURE_MODE_MANUAL);
      session.setIso(0);
      session.setExposureDuration(Math.max(exposure[0],Math.min(exposure[exposure.length-1],Math.round(1000000/this.snapshotShutterSpeed))));
      this.shortExposureActive=true;
    }catch(e){
      this.shortExposureActive=false;
      try{session.setExposureMode(camera.ExposureMode.EXPOSURE_MODE_CONTINUOUS_AUTO);}catch(restore){}
      console.info('SITECAM_SNAPSHOT_EXPOSURE '+String(e));
    }
  }
  async shoot(rotation:number=0,onAccepted?:()=>void):Promise<ArrayBuffer>{
    if(this.photoRecovery)await this.photoRecovery;
    if(!this.photoSessionReady||!this.photo||this.captureRequest)throw new Error('相机尚未就绪');
    const photo=this.photo,token=++this.captureToken,outputEpoch=this.photoOutputEpoch;
    console.info(`SITECAM_FLASH_CAPTURE mode=${this.flashMode} supported=${this.flashSupported}`);
    return new Promise<ArrayBuffer>((resolve,reject)=>{
      const request:PhotoRequest={token:token,outputEpoch:outputEpoch,timeout:-1,resolve:resolve,reject:reject};this.captureRequest=request;
      request.timeout=setTimeout(()=>this.failCapture(request,new Error('拍摄超时，请重试')),15000);
      try{photo.capture({quality:camera.QualityLevel.QUALITY_LEVEL_HIGH,rotation:rotation as camera.ImageRotation}).then(()=>{
        if(token!==this.captureToken||outputEpoch!==this.photoOutputEpoch)return;
        try{onAccepted?.();}catch(e){console.warn('SITECAM_SHUTTER_CALLBACK '+String(e));}
      }).catch((e:Error)=>this.failCapture(request,e));}catch(e){this.failCapture(request,e as Error);}
    });
  }
  private clearCapture(request:PhotoRequest):void{
    if(request.timeout>=0){clearTimeout(request.timeout);request.timeout=-1;}
    if(this.captureRequest===request)this.captureRequest=null;
  }
  private invalidatePhoto(error:Error):void{
    this.captureToken++;this.photoOutputEpoch++;this.photoSessionReady=false;
    const request=this.captureRequest;if(request){this.clearCapture(request);request.reject(error);}
  }
  private failCapture(request:PhotoRequest,error:Error):void{
    if(this.captureRequest!==request||request.outputEpoch!==this.photoOutputEpoch)return;
    this.clearCapture(request);
    // API 12 Photo has no capture ID: rebuild the output before accepting a
    // retry, so an event from the failed request cannot acquire its resolver.
    const rotation=this.displayRotation>=0?this.displayRotation:0;
    const recovery=this.open(this.surface,'',false,rotation),generation=this.lifecycleGeneration;
    this.photoRecovery=recovery;
    void recovery.then(()=>{
      if(this.photoRecovery===recovery)this.photoRecovery=null;
      request.reject(error);
    },()=>{
      if(this.photoRecovery===recovery)this.photoRecovery=null;
      if(generation===this.lifecycleGeneration)this.onError('相机恢复失败，请重新进入相机页');
      request.reject(error);
    });
  }
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
  focus(x:number,y:number):void{
    const session=this.session;if(!session){this.onFocusChanged('unsupported');return;}
    try{
      if(session.isFocusModeSupported(camera.FocusMode.FOCUS_MODE_AUTO)!==true){this.onFocusChanged('unsupported');return;}
      let fx=x,fy=y;const r=((this.previewRotation%360)+360)%360;
      if(r===90){fx=1-y;fy=x;}else if(r===180){fx=1-x;fy=1-y;}else if(r===270){fx=y;fy=1-x;}
      this.onFocusChanged('focusing');session.setFocusMode(camera.FocusMode.FOCUS_MODE_AUTO);
      session.setFocusPoint({x:Math.max(0,Math.min(1,fx)),y:Math.max(0,Math.min(1,fy))});
    }catch(e){this.onFocusChanged('unfocused');console.warn('SITECAM_FOCUS '+String(e));}
  }
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
  async switchLens(videoMode:boolean=this.videoMode):Promise<void>{
    if(this.recording||this.videoMode)throw new Error('录像期间不能切换镜头');const candidates=this.lensSwitchCandidates(videoMode);if(candidates.length<2)return;
    const currentPosition=candidates.indexOf(this.index);
    const nextIndex=candidates[(currentPosition<0?0:currentPosition+1)%candidates.length];
    await this.switchToLens(nextIndex,this.requestedZoom);
  }
  private async switchToLens(nextIndex: number, wantedZoom: number): Promise<void> {
    if(nextIndex===this.index)return;
    if(this.recording||this.videoMode)throw new Error('录像期间不能切换镜头');
    const previousIndex=this.index,previousZoom=this.requestedZoom,previousFlash=this.requestedFlashMode;
    this.index=nextIndex;this.requestedZoom=wantedZoom;const degrees=this.displayRotation>=0?this.displayRotation:0;const opening=this.open(this.surface,'',false,degrees),generation=this.lifecycleGeneration;
    try{await opening;}catch(e){
      if(generation!==this.lifecycleGeneration)throw e as Error;
      // Only a bind/config failure is a session-level lens fact; a surface,
      // recorder, or fs failure says nothing about this lens.
      const code=(e as BusinessError).code;
      if(code===7400101||code===7400102||code===7400103)this.lensSessionUnusable[nextIndex]=true;
      this.index=previousIndex;this.requestedZoom=previousZoom;this.requestedFlashMode=previousFlash;
      try{await this.open(this.surface,'',false,degrees);}catch(restoreError){console.warn('SITECAM_LENS_RESTORE '+String(restoreError));}throw e as Error;
    }
  }
  async startRecording():Promise<void>{
    const generation=this.lifecycleGeneration;
    await this.enqueueLifecycle(async()=>{
      this.requireLifecycle(generation);if(!this.recorder||!this.video)throw new Error('录像尚未就绪');
      await this.video.start();this.requireLifecycle(generation);await this.recorder.start();this.recording=true;this.requireLifecycle(generation);
    });
  }
  async stopRecording():Promise<void>{await this.enqueueLifecycle(()=>this.stopRecordingNow());}
  private async stopRecordingNow():Promise<void>{
    if(!this.recording)return;this.recording=false;let failure:Error|null=null;
    try{await this.video?.stop();}catch(e){failure=e as Error;}
    try{await this.recorder?.stop();}catch(e){if(!failure)failure=e as Error;}
    if(failure)throw failure;
  }
  // Session-scoped lens exclusions are dropped here, on the real teardown.
  // closeNow() also runs as the reopen step inside open(), so clearing there
  // would erase a just-recorded failure before the recovery open.
  async close():Promise<void>{this.lifecycleGeneration++;this.invalidatePhoto(new Error('相机已关闭'));for(let i=0;i<this.lensSessionUnusable.length;i++)this.lensSessionUnusable[i]=false;await this.enqueueLifecycle(()=>this.closeNow());}
  private async closeNow():Promise<void>{
    if(this.session&&!this.videoMode&&this.sdkApiVersion>=24){try{(this.session as camera.PhotoSession).offIsoInfoChange();}catch(e){}}
    this.shortExposureActive=false;
    this.onFocusChanged('idle');
    this.photoSupported=false;this.videoSupported=false;
    const preserveFlash=this.requestedFlashMode;this.zoomSessionToken++;this.zoomRequestToken++;this.clearZoomReadback();this.previewRotation=-1;this.displayRotation=-1;this.invalidatePhoto(new Error('相机已关闭'));
    if(this.recording){try{await this.stopRecordingNow();}catch(e){this.recording=false;}}
    try {await this.session?.stop();}catch(e){}
    try {await this.session?.release();}catch(e){}this.session=null;
    try {await this.photo?.release();}catch(e){}this.photo=null;
    try {await this.video?.release();}catch(e){}this.video=null;
    try {await this.preview?.release();}catch(e){}this.preview=null;
    try {await this.input?.close();}catch(e){}this.input=null;
    try {await this.recorder?.release();}catch(e){}this.recorder=null;
    if(this.outputFile){const file=this.outputFile;this.outputFile=null;try{await fs.close(file);}catch(e){console.warn('SITECAM_RECORDING_FD_CLOSE '+String(e));}}for(const capability of this.lensCapabilities)capability.active=false;this.activeLensRole='UNKNOWN';this.activeLensEquivalentFocalLength=0;this.activeLensPhysicalConfirmed=false;this.zoomEquivalentFocalLength=0;this.flashMode='off';this.flash=false;this.flashSupported=false;this.flashOffSupported=false;this.flashAutoSupported=false;this.flashOnSupported=false;this.flashTorchSupported=false;this.flashDiagnostics='session=closed';this.requestedFlashMode=preserveFlash==='torch'?'off':preserveFlash;this.scheduleDiagnosticsWrite();
  }
}
