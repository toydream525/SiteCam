import { camera } from '@kit.CameraKit';
import { media } from '@kit.MediaKit';
import { image } from '@kit.ImageKit';
import { common } from '@kit.AbilityKit';
import { fileIo as fs } from '@kit.CoreFileKit';
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
  private index: number=0;
  private surface: string='';
  private previewRotation: number=-1;
  private displayRotation: number=-1;
  private pending: ((bytes: ArrayBuffer)=>void)|null=null;
  private failed: ((error: Error)=>void)|null=null;
  private captureTimeout: number=-1;
  private captureToken: number=0;
  zoomMin: number=1;zoomMax: number=1;flash: boolean=false;
  /** Supported flash modes exposed to the camera page. */
  flashMode: string='off';
  flashSupported: boolean=false;
  flashAutoSupported: boolean=false;
  previewWidth: number=4;previewHeight: number=3;
  recording: boolean=false;
  photoSupported: boolean=false;
  videoSupported: boolean=false;
  get canSwitchLens(): boolean{return this.devices.length>1;}
  videoWidth: number=0;videoHeight: number=0;
  onError: (message: string)=>void=()=>{};
  constructor(context: common.UIAbilityContext) {this.manager=camera.getCameraManager(context);this.devices=this.manager.getSupportedCameras();}
  async open(surface: string,videoPath: string='',audio: boolean=false,rotation: number=0): Promise<void> {
    await this.close();this.surface=surface;if(!this.devices.length)throw new Error('设备没有可用摄像头');const device=this.devices[this.index];const mode=videoPath?camera.SceneMode.NORMAL_VIDEO:camera.SceneMode.NORMAL_PHOTO;
    const caps=this.manager.getSupportedOutputCapability(device,mode);this.videoSupported=false;try{this.videoSupported=this.manager.getSupportedOutputCapability(device,camera.SceneMode.NORMAL_VIDEO).videoProfiles.length>0;}catch(e){}this.input=this.manager.createCameraInput(device);if(!this.input)throw new Error('当前设备未提供可用相机输入');await this.input.open();this.input.on('error',device,()=>this.onError('相机被占用或已断开'));
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
      this.photoSupported=!!this.photo;this.photo?.on('photoAvailable',async(err,value)=>{if(err){this.reject(new Error(`照片返回失败：${err.code}`));return;}try {const component=await value.main.getComponent(image.ComponentType.JPEG);const bytes=component.byteBuffer.slice(0);this.pending?.(bytes);this.pending=null;this.failed=null;}catch(e){this.reject(e as Error);}finally{await value.main.release();}});
    }
    console.info('SITECAM_PROFILES photo='+caps.photoProfiles.map((v)=>v.size.width+'x'+v.size.height).join(',')+' preview='+caps.previewProfiles.map((v)=>v.size.width+'x'+v.size.height).join(','));const previews=caps.previewProfiles.slice().sort((a,b)=>{const da=Math.abs(a.size.width/a.size.height-ratio),db=Math.abs(b.size.width/b.size.height-ratio);return Math.abs(da-db)>.02?da-db:Math.abs(a.size.width-1280)-Math.abs(b.size.width-1280);});const pp=previews[0];if(!pp)throw new Error('没有可用预览配置');this.previewWidth=pp.size.width;this.previewHeight=pp.size.height;
    this.preview=this.manager.createPreviewOutput(pp,surface);if(!this.preview)throw new Error('当前设备未提供相机预览输出');this.session=videoPath?this.manager.createSession(camera.SceneMode.NORMAL_VIDEO) as camera.VideoSession:this.manager.createSession(camera.SceneMode.NORMAL_PHOTO) as camera.PhotoSession;if(!this.session)throw new Error('当前设备未提供原生相机会话，请在支持相机的设备上拍摄');this.session!.beginConfig();this.session!.addInput(this.input);this.session!.addOutput(this.preview);if(this.photo)this.session!.addOutput(this.photo);if(this.video)this.session!.addOutput(this.video);await this.session!.commitConfig();if(this.video&&this.recorder){const videoRotation=this.video.getVideoRotation(((rotation%360)+360)%360);await this.recorder.updateRotation(videoRotation);}try{const previewRotation=this.preview.getPreviewRotation(rotation);console.info(`SITECAM_PREVIEW_ROTATION_REQUEST display=${rotation} result=${String(previewRotation)}`);if(previewRotation===undefined)console.warn('SITECAM_PREVIEW_ROTATION_UNAVAILABLE');else{this.preview.setPreviewRotation(previewRotation,true);this.previewRotation=previewRotation;this.displayRotation=rotation;}}catch(e){console.warn('SITECAM_PREVIEW_ROTATION '+(e as Error).message);}console.info(`SITECAM_PREVIEW_SIZE ${this.previewWidth}x${this.previewHeight} surface=${surface}`);await this.session!.start();
    console.info('SITECAM_CAMERA rotation0='+this.photoRotation(0));this.zoomMin=1;this.zoomMax=1;this.flash=false;this.flashMode='off';this.flashSupported=false;this.flashAutoSupported=false;try{const zoom=this.session!.getZoomRatioRange();if(zoom&&zoom.length){this.zoomMin=zoom[0]||1;this.zoomMax=zoom[1]||1;}}catch(e){}try{this.flashSupported=!!this.session!.hasFlash();this.flashAutoSupported=this.session!.isFlashModeSupported(camera.FlashMode.FLASH_MODE_AUTO);this.flash=this.flashSupported;}catch(e){}
  }
  getActualPreviewRotation(): number { return this.previewRotation; }
  orientPreview(displayRotation: number): void {if(!this.preview||displayRotation===this.displayRotation)return;const rotation=this.preview.getPreviewRotation(displayRotation);console.info(`SITECAM_PREVIEW_ROTATION_UPDATE display=${displayRotation} result=${String(rotation)}`);if(rotation===undefined){console.warn('SITECAM_PREVIEW_ROTATION_UNAVAILABLE');return;}this.preview.setPreviewRotation(rotation,true);this.previewRotation=rotation;this.displayRotation=displayRotation;}
  photoRotation(degrees: number): number {const rotation=this.photo?.getPhotoRotation(degrees);return rotation===undefined?0:rotation;}
  async shoot(rotation: number=0): Promise<ArrayBuffer> {if(!this.photo||this.pending)throw new Error('相机尚未就绪');const token=++this.captureToken;return new Promise<ArrayBuffer>((resolve,reject)=>{this.pending=resolve;this.failed=reject;this.photo!.capture({quality:camera.QualityLevel.QUALITY_LEVEL_HIGH,rotation:rotation as camera.ImageRotation}).catch((e: Error)=>this.reject(e));this.captureTimeout=setTimeout(()=>{if(this.pending&&token===this.captureToken)this.reject(new Error('拍摄超时，请重试'));},15000);});}
  private reject(e: Error): void {if(this.captureTimeout>=0){clearTimeout(this.captureTimeout);this.captureTimeout=-1;}this.failed?.(e);this.pending=null;this.failed=null;}
  zoom(value: number): void {this.session?.setZoomRatio(Math.max(this.zoomMin,Math.min(this.zoomMax,value)));}
  focus(x: number,y: number): void {if(this.session?.isFocusModeSupported(camera.FocusMode.FOCUS_MODE_AUTO)){let fx=x;let fy=y;const r=((this.previewRotation%360)+360)%360;if(r===90){fx=1-y;fy=x;}else if(r===180){fx=1-x;fy=1-y;}else if(r===270){fx=y;fy=1-x;}this.session!.setFocusMode(camera.FocusMode.FOCUS_MODE_AUTO);this.session!.setFocusPoint({x:Math.max(0,Math.min(1,fx)),y:Math.max(0,Math.min(1,fy))});}}
  setFlash(enabled: boolean): void {this.setFlashMode(enabled?'on':'off');}
  setFlashMode(mode: string): void {let target=camera.FlashMode.FLASH_MODE_CLOSE;if(mode==='on')target=camera.FlashMode.FLASH_MODE_OPEN;else if(mode==='auto')target=camera.FlashMode.FLASH_MODE_AUTO;if(this.session?.isFlashModeSupported(target)){this.session!.setFlashMode(target);this.flashMode=mode;this.flash=mode!=='off';}}
  setTorch(enabled: boolean): void {const target=enabled?camera.FlashMode.FLASH_MODE_ALWAYS_OPEN:camera.FlashMode.FLASH_MODE_CLOSE;if(this.session?.isFlashModeSupported(target)){this.session!.setFlashMode(target);this.flashMode=enabled?'torch':'off';this.flash=enabled;}}
  cycleFlash(): string {if(!this.flashSupported)return 'off';const next=this.flashMode==='off'?(this.flashAutoSupported?'auto':'on'):this.flashMode==='auto'?'on':'off';this.setFlashMode(next);return this.flashMode;}
  async switchLens(): Promise<void> {if(this.recording)throw new Error('录像期间不能切换镜头');this.index=(this.index+1)%this.devices.length;const degrees=this.displayRotation>=0?this.displayRotation:0;await this.open(this.surface,'',false,degrees);}
  async startRecording(): Promise<void> {if(!this.recorder||!this.video)throw new Error('录像尚未就绪');await this.video.start();await this.recorder.start();this.recording=true;}
  async stopRecording(): Promise<void> {if(!this.recording)return;this.recording=false;await this.video?.stop();await this.recorder?.stop();}
  async close(): Promise<void> {
    this.previewRotation=-1;this.displayRotation=-1;this.captureToken++;this.reject(new Error('相机已关闭'));
    if(this.recording){try{await this.stopRecording();}catch(e){this.recording=false;}}
    try {await this.session?.stop();}catch(e){}
    try {await this.session?.release();}catch(e){}this.session=null;
    try {await this.photo?.release();}catch(e){}this.photo=null;
    try {await this.video?.release();}catch(e){}this.video=null;
    try {await this.preview?.release();}catch(e){}this.preview=null;
    try {await this.input?.close();}catch(e){}this.input=null;
    try {await this.recorder?.release();}catch(e){}this.recorder=null;
    if(this.outputFile){await fs.close(this.outputFile);this.outputFile=null;}
  }
}
