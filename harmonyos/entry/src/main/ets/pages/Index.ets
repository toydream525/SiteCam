import { WindowEnvironment } from '../core/WindowEnvironment';
import { CameraLayout, cameraLayout, captureDegrees } from '../core/CameraLayout';
import { util } from '@kit.ArkTS';
import { abilityAccessCtrl, common, Permissions } from '@kit.AbilityKit';
import { promptAction, display, window } from '@kit.ArkUI';
import { fileIo as fs } from '@kit.CoreFileKit';
import { image } from '@kit.ImageKit';
import { deviceInfo } from '@kit.BasicServicesKit';
import { pasteboard } from '@kit.BasicServicesKit';
import { bundleManager } from '@kit.AbilityKit';
import { store } from '../core/Store';
import { Project, MediaItem, Settings, Snapshot, Watermark, WatermarkTemplate, Field, Style, Annotation, STYLES, QUALITY_NAMES, captureSnapshot, dateText, timeText } from '../core/Models';
import { CameraService } from '../core/CameraService';
import { MediaService, EditRecipe, EditStep, Mark } from '../core/MediaService';
import { LocationService } from '../core/LocationService';
import { ExportService } from '../core/ExportService';
import { VideoService } from '../core/VideoService';
import { WatermarkView } from '../components/WatermarkView';
import { SITE_BG, SITE_CARD, SITE_CARD_RAISED, SITE_ACCENT, SITE_TEXT, SITE_MUTED } from './components/UiPrimitives';
import { SitePage, PROJECT_SORT_LABELS, ISSUE_STATUS_LABELS, SEVERITY_LABELS } from './state/AppState';
import { ProjectCardView, EditorToolBar, WatermarkStyleGrid } from './components/FeaturePages';
import { ProjectsPageView } from './components/ProjectsPageView';
import { ProjectEditorDialog } from './components/ProjectDialogs';
import { ProjectExportDialog } from './components/ExportDialog';
import { ProjectBatchActionDialog } from './components/ProjectBatchActionDialog';
import { ProjectMoveDialog } from './components/ProjectMoveDialog';
import { ProjectCategoryDialog } from './components/ProjectCategoryDialog';
import { ExportChoiceDialog } from './components/ExportChoiceDialog';
import { QuickIssueDialog } from './components/QuickIssueDialog';
import { OrientationDialog } from './components/OrientationDialog';
import { WatermarkCustomizationPanel, QuickWatermarkPanel } from './components/WatermarkPanels';
import { SettingsPageView } from './components/SettingsPageView';
import { HelpPageView } from './components/HelpPageView';
import { OnboardingPageView } from './components/OnboardingPageView';
import { GalleryPageView } from './components/GalleryPageView';
import { DetailPageView } from './components/DetailPageView';
import { CropDialog } from './components/CropDialog';
import { DateRangeDialog } from './components/DateRangeDialog';
import { PermissionGuidePageView, PermissionStatus } from './components/PermissionGuidePageView';

@Entry
@Component
struct Index {
  @StorageProp('windowEnvironment') @Watch('environmentChanged') environment: WindowEnvironment=new WindowEnvironment();
  @State shutterScale: number=1;
  @State thumbnailScale: number=1;
  @State shutterFlash: number=0;
  private captureInFlight: boolean=false;
  private stoppingVideo: boolean=false;
  private lastCaptureDegrees: number=0;
  private animationTimers: number[]=[];
  @State ready: boolean=false;
  @State page: string='projects';
  @State pageOrigin: string='camera';
  @State galleryOrigin: string='camera';
  @State projects: Project[]=[];
  @State items: MediaItem[]=[];
  @State settings: Settings=new Settings();
  @State message: string='正在打开工程资料';
  @State busy: boolean=false;
  @StorageProp('fontSizeScale') fontScale: number=1;
  @State windowWidth: number=390;
  @State windowHeight: number=720;
  @State query: string='';
  @State archived: boolean=false;
  @State projectScope: number=0;
  @State batchMode: boolean=false;
  @State selectedProjectId: number=0;
  @State sort: number=0;
  @State ascending: boolean=false;
  @State selected: number[]=[];
  @State mediaSelection: number[]=[];
  @State draft: Project=new Project();
  @State detail: MediaItem=new MediaItem();
  @State filterYear: number=0;
  @State filterMonth: number=0;
  @State fromDate: string='';
  @State toDate: string='';
  @State category: string='';
  @State batchProjectCategory: string='';
  @State filterProject: number=0;
  @State issueFilter: number=0;
  @State severityFilter: number=0;
  @State snapshot: Snapshot=new Snapshot();
  @State cameraReady: boolean=false;
  @State sourcePreviewRatio: number=3/4;
  @State frameRatio: number=3/4;
  @State recording: boolean=false;
  @State zoom: number=1;
  @State captureMode: string='photo';
  @State quickIssueEnabled: boolean=false;
  @State quickSheet: boolean=false;
  @StorageLink('watermarkBounds') watermarkBounds: string='';
  @State permissionRequesting: boolean=false;
  @State locationPermission: boolean=false;
  @State locationNeedsSettings: boolean=false;
  @State flash: boolean=false;
  @State videoProgress: number=0;
  @State templates: WatermarkTemplate[]=[];
  @State projectCategories: string[]=[];
  @State templateName: string='';
  @State draftTemplateId: number=0;
  @State styleDraft: Watermark=new Watermark();
  @State styleReturn: string='settings';
  @State guideStep: number=0;
  @State help: string='';
  @State recipe: EditRecipe=new EditRecipe();
  @State history: string[]=[];
  @State redoHistory: string[]=[];
  @State editPreview: string='';
  @State editRatio: number=1;
  @State showEdited: boolean=true;
  @State markTool: string='arrow';
  @State markColor: string='#FFB300';
  @State cropLeft: number=0;
  @State cropTop: number=0;
  @State cropRight: number=1;
  @State cropBottom: number=1;
  @State markText: string='';
  @State cropMode: boolean=false;
  @State cropPanel: boolean=false;
  @State exportQuality: number=-1;
  @State exportFolder: boolean=false;
  @State exportIds: number[]=[];
  @State exportMediaIds: number[]=[];
  @StorageLink('foreground') @Watch('foregroundChanged') foreground: boolean=true;
  private context: common.UIAbilityContext=getContext(this) as common.UIAbilityContext;
  private galleryStack: NavPathStack=new NavPathStack();
  private files: MediaService|null=null;
  private camera: CameraService|null=null;
  private location: LocationService=new LocationService();
  private video: VideoService=new VideoService();
  private exporter: ExportService|null=null;
  private controller: XComponentController=new XComponentController();
  private surface: string='';
  private timer: number=-1;
  private recordingItem: MediaItem|null=null;
  private photoPermission: boolean=false;
  private microphonePermission: boolean=false;
  private touchX: number=0;
  private touchY: number=0;
  private strokePoints: number[]=[];
  private editorWidth: number=1;
  private editorHeight: number=1;
  private editRevision: number=0;
  private previewFiles: string[]=[];
  private imageScale: number=1;
  @State viewScale: number=1;
  @State viewX: number=0;
  @State viewY: number=0;
  private dragX: number=0;
  private dragY: number=0;
  private pinchStartZoom: number=1;
  private editorDialog: CustomDialogController|null=null;
  private cropDialog: CustomDialogController|null=null;
  private exportDialog: CustomDialogController|null=null;
  private issueDialog: CustomDialogController|null=null;
  private issueTarget: MediaItem|null=null;
  private orientationDialog: CustomDialogController|null=null;
  private dateRangeDialog: CustomDialogController|null=null;
  private actionDialog: CustomDialogController|null=null;
  private environmentChanged(): void {
    this.windowWidth=Math.max(1,this.environment.width-this.environment.left-this.environment.right);
    this.windowHeight=Math.max(1,this.environment.height-this.environment.top-this.environment.bottom);
    if(this.page==='camera'&&!this.captureInFlight&&!this.recording)this.updateFrameRatio();
  }
  private effectiveDegrees(): number {this.lastCaptureDegrees=captureDegrees(this.settings.orientation,this.environment.rotation,this.lastCaptureDegrees);return this.lastCaptureDegrees;}
  private cameraGeometry(): CameraLayout {return cameraLayout(this.windowWidth,this.windowHeight,this.frameRatio,this.environment.halfFolded,this.environment.creaseTop,this.environment.creaseHeight);}
  private animateShutter(photo:boolean): void {
    this.getUIContext().animateTo({duration:45},()=>this.shutterScale=.90);
    this.animationTimers.push(setTimeout(()=>this.getUIContext().animateTo({duration:70},()=>this.shutterScale=1),45));
    if(photo){this.shutterFlash=.28;this.getUIContext().animateTo({duration:100},()=>this.shutterFlash=0);}
  }
  private animatePhotoSaved(): void {
    this.thumbnailScale=.86;this.getUIContext().animateTo({duration:110},()=>this.thumbnailScale=1.10);
    this.animationTimers.push(setTimeout(()=>this.getUIContext().animateTo({duration:90},()=>this.thumbnailScale=1),110));
  }
  private cameraSurfaceLost(): void {
    this.surface='';this.cameraReady=false;
    if(this.recording)this.stopVideo().catch((e:Error)=>this.notify(e.message));
    else this.camera?.close();
  }

  async aboutToAppear(): Promise<void> {
    try {await store.open(this.context);this.settings=store.settings;this.files=new MediaService(this.context);this.exporter=new ExportService(this.context,this.files);await this.refresh();await this.permissions(false);this.ready=true;this.message='';if(!this.settings.guideDone)this.page='guide';else if(!this.settings.permissionHandled)this.page='permissions';else this.page=this.settings.currentProject?'camera':'projects';
      this.environmentChanged();
      const bytes=await this.context.resourceManager.getRawFileContent('USER_GUIDE.md');this.help=String.fromCharCode();const decoder=new util.TextDecoder('utf-8');this.help=decoder.decodeToString(bytes);
    }catch(e){this.message=`打开失败：${(e as Error).message}`;}
  }
  aboutToDisappear(): void {for(const timer of this.animationTimers)clearTimeout(timer);if(this.timer>=0)clearInterval(this.timer);this.location.stop();this.camera?.close();this.video.cancel();}
  onBackPress(): boolean {
    if(this.quickSheet){this.persistQuickWatermark();this.quickSheet=false;return true;}
    if(this.recording||this.busy){this.notify('请先结束当前操作');return true;}
    if(this.page==='gallery'&&this.galleryStack.size()>0&&!this.galleryStack.getAllPathName().includes('media-placeholder')){this.galleryStack.pop();return true;}
    if(this.page==='camera')return false;
    if(this.page==='editor'){AlertDialog.show({title:'退出编辑？',message:'当前标注和裁剪尚未保存，退出后将丢失。',primaryButton:{value:'继续编辑',action:()=>{}},secondaryButton:{value:'放弃修改',fontColor:'#F44336',action:()=>this.task(async()=>this.navigate('detail'))}});return true;}
    if(this.page==='projects'||this.page==='settings'){this.task(async()=>this.navigate('camera'));return true;}
    if(this.page==='gallery'){this.task(async()=>this.navigate(this.galleryOrigin||'camera'));return true;}
    const back=this.page==='detail'||this.page==='move'?'gallery':this.page==='editor'?'detail':this.page==='watermark'?this.styleReturn:this.page==='help'?'settings':this.pageOrigin||'camera';
    this.task(async()=>{if(this.page==='watermark')await this.persistWatermarkBeforeLeave();await this.navigate(back);});return true;
  }
  private async refresh(): Promise<void> {this.projects=await store.projects();this.items=await store.media();this.templates=await store.templates();this.projectCategories=(await store.categories()).map(c=>c.name);}
  private async task(action: ()=>Promise<void>): Promise<void> {if(this.busy)return;this.busy=true;try{await action();}catch(e){console.error('SITECAM_ACTION '+(e as Error).stack);this.notify((e as Error).message||'操作未完成');}finally{this.busy=false;}}
  private notify(text: string): void {this.message=text;promptAction.showToast({message:text,duration:3000});}
  private async copyDiagnostics(): Promise<void> {const text=`设备：${deviceInfo.productModel||'未知'}\nSDK：${deviceInfo.sdkApiVersion}\n版本：v0.2.8`;const board=pasteboard.getSystemPasteboard();await board.setData(pasteboard.createData(pasteboard.MIMETYPE_TEXT_PLAIN,text));this.notify('诊断信息已复制');}
  private async openExternal(url: string): Promise<void> {await this.context.startAbility({uri:url,action:'ohos.want.action.viewData'});}
  private current(): Project {return this.projects.find(p=>p.id===this.settings.currentProject)||new Project();}
  private async persist(): Promise<void> {store.settings=this.settings;await store.saveSettings();}
  private async navigate(page: string): Promise<void> {if(this.recording){this.notify('请先结束录像');return;}if(page==='gallery'){this.galleryOrigin=this.page==='projects'?'projects':this.page==='camera'?'camera':this.galleryOrigin;}if(page==='projects'||page==='settings')this.pageOrigin=this.page==='camera'?'camera':this.pageOrigin;
if(this.page==='editor'&&page!=='editor'){for(const path of this.previewFiles)await this.files!.removePath(path);this.previewFiles=[];}if(this.page==='camera'&&page!=='camera'){this.restoreWindowOrientation();await this.camera?.close();this.cameraReady=false;this.location.stop();if(this.timer>=0){clearInterval(this.timer);this.timer=-1;}}this.page=page;this.message='';if(page==='camera'&&!this.current().id){this.page='projects';this.notify('请先新建或选择一个工程');}}
  private async foregroundChanged(): Promise<void> {if(!this.foreground){if(this.recording){try{await this.stopVideo();}catch(e){this.notify((e as Error).message||'录像已安全收尾');}}await this.camera?.close();this.cameraReady=false;this.location.stop();this.video.cancel();}else if(this.page==='permissions'){await this.permissions(false);}else if(this.page==='camera'&&this.surface){await this.task(async()=>this.openCamera());}}
  private async permissions(request: boolean): Promise<void> {
    const manager=abilityAccessCtrl.createAtManager();const token=this.context.applicationInfo.accessTokenId;
    const list: Permissions[]=['ohos.permission.CAMERA','ohos.permission.APPROXIMATELY_LOCATION','ohos.permission.LOCATION','ohos.permission.MICROPHONE'];
    if(request){const missing: Permissions[]=[];for(const p of list)if(await manager.checkAccessToken(token,p)!==abilityAccessCtrl.GrantStatus.PERMISSION_GRANTED)missing.push(p);if(missing.length)await manager.requestPermissionsFromUser(this.context,missing);this.settings.permissionHandled=true;await this.persist();}
    this.photoPermission=await manager.checkAccessToken(token,'ohos.permission.CAMERA')===abilityAccessCtrl.GrantStatus.PERMISSION_GRANTED;
    this.microphonePermission=await manager.checkAccessToken(token,'ohos.permission.MICROPHONE')===abilityAccessCtrl.GrantStatus.PERMISSION_GRANTED;
    this.locationPermission=(await manager.checkAccessToken(token,'ohos.permission.LOCATION')===abilityAccessCtrl.GrantStatus.PERMISSION_GRANTED)||(await manager.checkAccessToken(token,'ohos.permission.APPROXIMATELY_LOCATION')===abilityAccessCtrl.GrantStatus.PERMISSION_GRANTED);
    this.locationNeedsSettings=!this.locationPermission;
  }
  private permissionRows(): PermissionStatus[] {const rows:PermissionStatus[]=[];rows.push({id:'camera',title:'相机',description:'用于拍照和录像。不开启也能浏览、管理已有工程资料。',granted:this.photoPermission,needsSettings:!this.photoPermission&&this.settings.permissionHandled,optional:false,detail:this.photoPermission?'已允许':'未开启'} as PermissionStatus);rows.push({id:'location',title:'位置 · 可选',description:'用于水印中的现场地点。允许大致位置也可以。',granted:this.locationPermission,needsSettings:this.locationNeedsSettings,optional:true,detail:this.locationPermission?'已允许':'未开启'} as PermissionStatus);rows.push({id:'microphone',title:'麦克风 · 可选',description:'用于录制视频声音。不开启时可以录制无声视频。',granted:this.microphonePermission,needsSettings:!this.microphonePermission&&this.settings.permissionHandled,optional:true,detail:this.microphonePermission?'已允许':'未开启'} as PermissionStatus);return rows;}
  private async openCamera(): Promise<void> {if(!this.foreground||this.page!=='camera'||!this.surface)return;await this.permissions(false);if(!this.photoPermission){this.cameraReady=false;this.message='请开启相机权限后拍摄';return;}this.applyWindowOrientation(this.settings.orientation);await this.camera?.close();this.camera=new CameraService(this.context);this.camera.onError=(msg: string)=>{this.cameraReady=false;if(this.recording)this.stopVideo().catch((e: Error)=>this.notify(e.message));this.notify(msg);};const displayDegrees=this.effectiveDegrees();try{this.controller.setXComponentSurfaceRotation({lock:true});}catch(e){}await this.camera.open(this.surface,'',false,displayDegrees);try{console.info(`SITECAM_XCOMPONENT_CONFIG ${this.camera.previewWidth}x${this.camera.previewHeight} rotation=${JSON.stringify(this.controller.getXComponentSurfaceRotation())}`);}catch(e){console.warn('SITECAM_XCOMPONENT_CONFIG_FAILED '+(e as Error).message);}this.cameraReady=true;this.updateFrameRatio();this.message=this.camera.photoSupported?'':'当前设备仅支持预览，拍照能力不可用';this.zoom=1;this.flash=false;this.location.start();this.snapshot=captureSnapshot(this.current(),this.settings,this.location.fix);if(this.timer>=0)clearInterval(this.timer);this.timer=setInterval(()=>{if(!this.recording&&!this.captureInFlight)this.snapshot=captureSnapshot(this.current(),this.settings,this.location.fix);},1000);}
  private updateFrameRatio(): void {if(!this.camera||this.captureInFlight||this.recording)return;const displayDegrees=this.effectiveDegrees();try{this.camera.orientPreview(displayDegrees);}catch(e){}const ratio=this.camera.previewWidth/this.camera.previewHeight;const actual=this.camera.getActualPreviewRotation();this.sourcePreviewRatio=actual>=0&&actual%180!==0?1/ratio:ratio;this.frameRatio=this.captureMode==='photo'?(displayDegrees%180===0?3/4:4/3):this.sourcePreviewRatio;console.info(`SITECAM_FRAME_RATIO source=${this.camera.previewWidth}x${this.camera.previewHeight} displayDegrees=${displayDegrees} actualPreviewRotation=${actual} effective=${this.frameRatio}`);}
  private setPreviewSurfaceArea(width:number,height:number): void {if(width<=0||height<=0)return;try{const density=display.getDefaultDisplaySync().densityPixels||1;const pxWidth=width*density,pxHeight=height*density;this.controller.setXComponentSurfaceRect({offsetX:0,offsetY:0,surfaceWidth:pxWidth,surfaceHeight:pxHeight});console.info(`SITECAM_XCOMPONENT_RECT vp=${width}x${height} px=${pxWidth}x${pxHeight} density=${density}`);}catch(e){console.warn('SITECAM_XCOMPONENT_RECT_FAILED '+(e as Error).message);}}
  private frameWidth(): number {return this.cameraGeometry().previewWidth;}
  private surfaceWidth():number{return Math.max(this.frameWidth(),this.frameWidth()/this.frameRatio*this.sourcePreviewRatio);}
  private surfaceHeight():number{return this.surfaceWidth()/this.sourcePreviewRatio;}
  private watermarkBound(index: number): number {const parts=this.watermarkBounds.split(',');if(index<0||index>=parts.length)return 0;const value=Number(parts[index]);return Number.isFinite(value)?value:0;}
  private rotation(): number {if(this.settings.orientation==='PORTRAIT')return 90;if(this.settings.orientation==='LEFT')return 0;if(this.settings.orientation==='RIGHT')return 180;const rotation=display.getDefaultDisplaySync().rotation;return rotation===1?0:rotation===3?180:90;}
  private async shoot(): Promise<void> {
    const p=this.current();if(!p.id||p.locked||p.archived)throw new Error('当前工程已锁定或归档，不能继续拍摄');
    if(!this.cameraReady||!this.camera?.photoSupported)throw new Error('相机尚未就绪');
    const s=captureSnapshot(p,this.settings,this.location.fix),quality=this.settings.photoQuality,degrees=this.effectiveDegrees();
    s.captureRatio=degrees%180===0?3/4:4/3;this.snapshot=s;this.captureInFlight=true;this.applyWindowOrientation(this.settings.orientation,true);this.animateShutter(true);
    try {
      const bytes=await this.camera!.shoot(this.camera!.photoRotation(degrees));
      const item=await this.files!.capture(bytes,s,quality,0);item.projectId=p.id;
      try{await store.putMedia(item);}catch(e){await this.files!.remove(item);throw e as Error;}
      await this.refresh();this.animatePhotoSaved();this.notify('照片已保存到工程相册');
      if(this.settings.albumCopy){try{const uri=await this.files!.albumCopy(item);if(uri){item.albumUri=uri;await store.putMedia(item);}}catch(e){this.notify('照片已保存，另存系统相册未完成：'+(e as Error).message);}}
      if(this.quickIssueEnabled)this.openIssue(item);
    } finally {this.captureInFlight=false;this.applyWindowOrientation(this.settings.orientation);this.updateFrameRatio();}
  }
  private openIssue(item: MediaItem): void {this.issueTarget=item;this.issueDialog=new CustomDialogController({customStyle:true,alignment:this.cameraGeometry().hover?DialogAlignment.Bottom:DialogAlignment.Center,offset:{dx:0,dy:-this.environment.bottom},builder:QuickIssueDialog({maxHeight:this.cameraGeometry().hover?this.cameraGeometry().controlsHeight:Math.max(120,this.windowHeight-40),existingTitle:item.issueTitle,existingSeverity:item.severity,existingStatus:item.issueStatus,existingNotes:item.issueNotes,onConfirm:(title:string,severity:string,status:string,notes:string)=>this.task(async()=>{item.issueTitle=title;item.severity=severity;item.issueStatus=status;item.issueNotes=notes;await store.putMedia(item);await this.refresh();this.notify('问题记录已保存');}),onRemove:()=>this.task(async()=>{item.issueTitle='';item.issueNotes='';item.severity='NORMAL';item.issueStatus='PENDING';await store.putMedia(item);await this.refresh();this.notify('问题已移除');})})});this.issueDialog!.open();}
  private applyWindowOrientation(value: string, lockAuto: boolean=false): void { let orientation: window.Orientation; if(value==='PORTRAIT')orientation=window.Orientation.PORTRAIT;else if(value==='LEFT')orientation=window.Orientation.LANDSCAPE;else if(value==='RIGHT')orientation=window.Orientation.LANDSCAPE_INVERTED;else if(lockAuto){const r=this.effectiveDegrees();orientation=r===90?window.Orientation.LANDSCAPE:r===270?window.Orientation.LANDSCAPE_INVERTED:window.Orientation.PORTRAIT;}else orientation=window.Orientation.AUTO_ROTATION_RESTRICTED;window.getLastWindow(this.context).then((win: window.Window)=>win.setPreferredOrientation(orientation)).catch(()=>this.notify('无法切换屏幕方向'));}
  private restoreWindowOrientation(): void { window.getLastWindow(this.context).then((win: window.Window)=>win.setPreferredOrientation(window.Orientation.AUTO_ROTATION)).catch(()=>{}); }
  private openOrientation(): void {this.orientationDialog=new CustomDialogController({customStyle:true,alignment:this.cameraGeometry().hover?DialogAlignment.Bottom:DialogAlignment.Center,offset:{dx:0,dy:-this.environment.bottom},builder:OrientationDialog({maxHeight:this.cameraGeometry().hover?this.cameraGeometry().controlsHeight:Math.max(120,this.windowHeight-40),selected:this.settings.orientation,onSelect:(value:string)=>{this.settings.orientation=value;this.applyWindowOrientation(value);this.updateFrameRatio();this.persist();}})});this.orientationDialog!.open();}
  private async toggleVideo(): Promise<void> {if(this.recording){await this.stopVideo();return;}const p=this.current();if(!p.id||p.locked||p.archived)throw new Error('当前工程已锁定或归档，不能录像');if(!this.cameraReady)throw new Error('相机尚未就绪');const s=captureSnapshot(p,this.settings,this.location.fix);const item=new MediaItem();item.type='VIDEO';item.projectId=p.id;item.timestamp=s.timestamp;item.snapshot=JSON.stringify(s);item.status='PROCESSING';item.name=`${s.timestamp}_source.mp4`;item.source=this.files!.path(item.name);item.path=item.source;this.snapshot=s;this.applyWindowOrientation(this.settings.orientation,true);try{await this.camera!.open(this.surface,item.source,this.microphonePermission,this.effectiveDegrees());this.updateFrameRatio();await store.putMedia(item);await this.camera!.startRecording();}catch(e){item.status='FAILED';await store.putMedia(item);await this.camera!.close();this.cameraReady=false;this.recording=false;this.recordingItem=null;this.applyWindowOrientation(this.settings.orientation);if(this.foreground){try{await this.openCamera();}catch(re){this.message='相机恢复失败，请重新进入相机页';}}throw e as Error;}this.recordingItem=item;this.recording=true;this.notify(this.microphonePermission?'正在录像':'正在录制无声视频');}
  private async stopVideo(): Promise<void> {const item=this.recordingItem;if(!item||this.stoppingVideo)return;this.stoppingVideo=true;try{let recordingError='';try{await this.camera!.stopRecording();}catch(e){recordingError=(e as Error).message;}await this.camera!.close();this.cameraReady=false;this.applyWindowOrientation(this.settings.orientation);this.recording=false;this.recordingItem=null;item.duration=Date.now()-item.timestamp;item.status='FAILED';await store.putMedia(item);await this.refresh();if(recordingError){if(this.foreground){try{await this.openCamera();}catch(e){this.notify('相机恢复失败，请重新进入相机页');}}throw new Error('录像已中断，保留已有原片：'+recordingError);}if(this.foreground){this.message='正在写入视频水印';try{await this.video.process(item,this.files!,v=>this.videoProgress=v);await this.refresh();if(this.settings.albumCopy){try{item.albumUri=await this.files!.albumCopy(item);await store.putMedia(item);}catch(e){this.notify('视频已保存，但系统相册副本失败：'+(e as Error).message);}}}finally{try{await this.openCamera();}catch(e){this.notify('相机恢复失败，请重新进入相机页');}}this.notify('视频已保存');}else{this.notify('原片已保存，可在相册重试水印处理');}}finally{this.stoppingVideo=false;}}
  private projectRows(): Project[] {let rows=this.projects.filter(p=>(this.projectScope===2||p.archived=== (this.projectScope===1))&&(!this.query||(p.name+' '+p.route).toLowerCase().includes(this.query.toLowerCase())));rows=rows.sort((a,b)=>{let value=this.sort===0?a.last-b.last:this.sort===1?a.created-b.created:this.sort===2?a.updated-b.updated:this.sort===3?a.name.localeCompare(b.name,'zh'):a.route.localeCompare(b.route,'zh');return this.ascending?value:-value;});return rows;}
  private years(): number[] {const values: number[]=[];for(const m of this.items){const year=new Date(m.timestamp).getFullYear();if(!values.includes(year))values.push(year);}return values.sort((a,b)=>b-a);}
  private pickDate(start: boolean): void {DatePickerDialog.show({selected:new Date((start?this.fromDate:this.toDate)||Date.now()),onAccept:(value: DatePickerResult)=>{const text=dateText(new Date(value.year!,value.month!,value.day!).getTime());if(start)this.fromDate=text;else this.toDate=text;}});}
  private pickDateRange(): void { this.dateRangeDialog=new CustomDialogController({customStyle:true,builder:DateRangeDialog({initialStart:this.fromDate,initialEnd:this.toDate,onApply:(start:string,end:string)=>{this.mediaSelection=[];this.fromDate=start;this.toDate=end;},onCancel:()=>{}})});this.dateRangeDialog!.open(); }
  private batchIssueDialog(): void { const chosen=this.items.filter((m:MediaItem)=>this.mediaSelection.includes(m.id)); if(!chosen.length)return; AlertDialog.show({title:'批量记录问题',message:`将更新 ${chosen.length} 项`,primaryButton:{value:'取消',action:()=>{}},buttons:[{value:'待处理',action:()=>this.task(async()=>{for(const m of chosen){m.issueTitle=m.issueTitle||'现场问题';m.issueStatus='PENDING';await store.putMedia(m);}await this.refresh();})},{value:'处理中',action:()=>this.task(async()=>{for(const m of chosen){m.issueTitle=m.issueTitle||'现场问题';m.issueStatus='IN_PROGRESS';await store.putMedia(m);}await this.refresh();})},{value:'已完成',action:()=>this.task(async()=>{for(const m of chosen){m.issueTitle=m.issueTitle||'现场问题';m.issueStatus='COMPLETED';await store.putMedia(m);}await this.refresh();})}]}); }
  private batchMediaMoreDialog(): void { AlertDialog.show({title:'更多操作',message:`已选 ${this.mediaSelection.length} 项`,primaryButton:{value:'取消',action:()=>{}},buttons:[{value:'移入工程',action:()=>this.openMoveDialog()},{value:'分享',action:()=>this.task(async()=>this.files!.share(this.items.filter((m:MediaItem)=>this.mediaSelection.includes(m.id)).map((m:MediaItem)=>m.edited||m.path)))},{value:'删除',fontColor:'#F44336',action:()=>this.confirmDelete(false)}]}); }
  private galleryRows(): MediaItem[] {return this.items.filter(m=>(!this.filterProject||m.projectId===this.filterProject)&&(!this.filterYear||new Date(m.timestamp).getFullYear()===this.filterYear)&&(!this.filterMonth||new Date(m.timestamp).getMonth()+1===this.filterMonth)&&(!this.fromDate||dateText(m.timestamp)>=this.fromDate)&&(!this.toDate||dateText(m.timestamp)<=this.toDate)&&(!this.category||this.projects.find(p=>p.id===m.projectId)?.category===this.category)&&(!this.issueFilter||!!m.issueTitle&&(this.issueFilter===1||m.issueStatus===['','','PENDING','IN_PROGRESS','COMPLETED'][this.issueFilter]))&&(!this.severityFilter||!!m.issueTitle&&m.severity===['','NORMAL','IMPORTANT','CRITICAL'][this.severityFilter]));}
  private galleryDates(): string[] {const dates: string[]=[];for(const item of this.galleryRows()){const day=dateText(item.timestamp);if(!dates.includes(day))dates.push(day);}return dates.sort((a,b)=>b.localeCompare(a));}
  private galleryRowsForDate(day: string): MediaItem[] {return this.galleryRows().filter(item=>dateText(item.timestamp)===day);}
  private toggleProject(id: number): void {if(!this.batchMode)this.batchMode=true;this.selected=this.selected.includes(id)?this.selected.filter(v=>v!==id):this.selected.concat(id);}
  private async batchMoreDialog(): Promise<void> { this.actionDialog=new CustomDialogController({customStyle:true,builder:ProjectBatchActionDialog({selectedCount:this.selected.length,onAction:(action:string)=>{if(action==='category')this.openCategoryDialog();else if(action==='delete')this.confirmDelete(true);else this.task(async()=>this.projectBatch(action));},onCancel:()=>{}})});this.actionDialog!.open(); }
  private projectSortDialog(): void {AlertDialog.show({title:'排序方式',message:'选择工程列表排序',primaryButton:{value:'取消',action:()=>{}},buttons:[{value:'最近拍摄',action:()=>{this.sort=0;}},{value:'创建时间',action:()=>{this.sort=1;}},{value:'最近修改',action:()=>{this.sort=2;}},{value:'名称',action:()=>{this.sort=3;}},{value:'线路',action:()=>{this.sort=4;}}]});}
  private toggleMedia(id: number): void {this.mediaSelection=this.mediaSelection.includes(id)?this.mediaSelection.filter(v=>v!==id):this.mediaSelection.concat(id);}
  private deleteDetail(): void {AlertDialog.show({title:'删除资料？',message:'将删除工程相册中的原件和编辑成品，系统相册副本不受影响。',primaryButton:{value:'取消',action:()=>{}},secondaryButton:{value:'删除',fontColor:'#F44336',action:()=>{this.task(async()=>{await this.files!.remove(this.detail);await store.deleteMedia(this.detail.id);await this.refresh();this.page='gallery';this.notify('资料已删除');});}}});}
  private async selectProject(p: Project): Promise<void> {this.settings.currentProject=p.id;await this.persist();this.settings=JSON.parse(JSON.stringify(this.settings)) as Settings;this.message='';promptAction.showToast({message:`当前工程：${p.name}`});}
  private editProject(p: Project): void {this.draft=JSON.parse(JSON.stringify(p)) as Project;this.editorDialog=new CustomDialogController({customStyle:true,builder:ProjectEditorDialog({project:this.draft,categories:this.projectCategories.length?this.projectCategories:['其他'],onConfirm:(value: Project)=>this.task(async()=>{this.draft=value;await this.saveProject();}),onCancel:()=>{}})});this.editorDialog!.open();}
  private async saveProject(): Promise<void> {if(!this.draft.name.trim())throw new Error('请填写工程名称');this.draft.name=this.draft.name.trim();await store.putProject(this.draft);if(!this.settings.currentProject){this.settings.currentProject=this.draft.id;await this.persist();}await this.refresh();this.page='projects';}
  private async projectBatch(action: string): Promise<void> {for(const id of this.selected){const p=this.projects.find(v=>v.id===id);if(!p)continue;if(action==='lock')p.locked=true;else if(action==='unlock')p.locked=false;else if(action==='archive')p.archived=true;else if(action==='restore')p.archived=false;else if(action==='category')p.category=this.batchProjectCategory||'未分类';await store.putProject(p);}this.selected=[];this.batchMode=false;await this.refresh();}
  private openProjectMenu(p: Project): void {AlertDialog.show({title:p.name,message:'工程操作',primaryButton:{value:'取消',action:()=>{}},secondaryButton:{value:p.id===this.settings.currentProject?'当前工程':'设为拍摄工程',action:()=>{this.task(async()=>{if(!p.archived&&!p.locked){await this.selectProject(p);}else this.notify('已归档或锁定工程不能设为拍摄工程');});}},buttons:[{value:p.locked?'解锁拍摄':'锁定拍摄',action:()=>{this.selected=[p.id];this.task(async()=>this.projectBatch(p.locked?'unlock':'lock'));}},{value:p.archived?'恢复工程':'归档工程',action:()=>{this.selected=[p.id];this.task(async()=>this.projectBatch(p.archived?'restore':'archive'));}},{value:'删除工程',fontColor:'#F44336',action:()=>{this.selected=[p.id];this.confirmDelete(true);}}]});}
  private openCategoryDialog(): void {this.actionDialog=new CustomDialogController({customStyle:true,builder:ProjectCategoryDialog({categories:this.projectCategories.length?this.projectCategories:['其他'],onSelect:(name:string)=>{this.batchProjectCategory=name;this.task(async()=>this.projectBatch('category'));},onCancel:()=>{}})});this.actionDialog!.open();}
  private projectPickerDialog(): void {const buttons: AlertDialogButtonOptions[]=[{value:'全部工程',action:()=>{this.filterProject=0;this.page='gallery';}}];for(const p of this.projects)buttons.push({value:p.name,action:()=>{this.mediaSelection=[];this.filterProject=p.id;this.page='gallery';}});AlertDialog.show({title:'选择工程相册',message:'选择要查看的工程',primaryButton:{value:'取消',action:()=>{}},buttons});}
  private openMoveDialog(): void {this.actionDialog=new CustomDialogController({customStyle:true,builder:ProjectMoveDialog({projects:this.projects,onSelect:(projectId:number)=>this.task(async()=>{await store.moveMedia(this.mediaSelection,projectId);this.mediaSelection=[];await this.refresh();this.page='gallery';}),onCancel:()=>{}})});this.actionDialog!.open();}
  private confirmDelete(projects: boolean): void {AlertDialog.show({title:projects?'删除所选工程？':'删除所选资料？',message:'将删除应用内原件和编辑成品，系统相册副本不受影响。此操作无法撤销。',primaryButton:{value:'取消',action:()=>{}},secondaryButton:{value:'删除',fontColor:'#CF3A31',action:()=>{this.task(async()=>{if(projects){for(const id of this.selected){for(const m of this.items.filter(v=>v.projectId===id))await this.files!.remove(m);await store.deleteProject(id);}this.selected=[];}else{for(const id of this.mediaSelection){const m=this.items.find(v=>v.id===id);if(m){await this.files!.remove(m);await store.deleteMedia(id);}}this.mediaSelection=[];}await this.refresh();});}}});}
  private exportStart(ids: number[],mediaIds: number[]=[]): void {this.exportIds=ids;this.exportMediaIds=mediaIds;this.actionDialog=new CustomDialogController({customStyle:true,builder:ExportChoiceDialog({onZip:(quality:number)=>{this.exportFolder=false;this.exportQuality=quality;this.task(async()=>this.exportNow());},onFolder:(quality:number)=>{this.exportFolder=true;this.exportQuality=quality;this.task(async()=>this.exportNow());},onCancel:()=>{}})});this.actionDialog!.open();}
  private async exportNow(): Promise<void> {const projects=this.projects.filter(p=>this.exportIds.includes(p.id));const items=this.items.filter(m=>this.exportIds.includes(m.projectId)&&(!this.exportMediaIds.length||this.exportMediaIds.includes(m.id)));if(!projects.length)throw new Error('请先选择工程');const path=await this.exporter!.run(projects,items,this.exportFolder,this.exportQuality,v=>this.message=v);this.notify(this.exporter!.failures?`导出完成，${this.exporter!.failures} 项未成功，请查看报告`:'导出完成：'+path);}
  private openDetail(m: MediaItem): void {this.detail=JSON.parse(JSON.stringify(m)) as MediaItem;this.showEdited=true;this.viewScale=1;this.viewX=0;this.viewY=0;this.galleryStack.clear();this.galleryStack.pushPath({name:'media'});}
  private openStyles(from: string): void {this.styleDraft=JSON.parse(JSON.stringify(this.settings.watermark)) as Watermark;this.styleReturn=from;this.draftTemplateId=this.settings.templateId||0;this.templateName=this.templates.find(t=>t.id===this.settings.templateId)?.name||'我的模板';this.page='watermark';}
  private styleSnapshot(id: string): Snapshot {const s=captureSnapshot(this.current().id?this.current():this.example(),this.settings,this.location.fix);s.watermark=JSON.parse(JSON.stringify(this.styleDraft)) as Watermark;s.watermark.style=id;return s;}
  private example(): Project {const p=new Project();p.name='滨江路雨污分流改造';p.address='北段雨水管施工现场';return p;}
  private async saveWatermark(): Promise<void> {this.settings.watermark=JSON.parse(JSON.stringify(this.styleDraft)) as Watermark;await this.persist();const t=new WatermarkTemplate();t.id=this.draftTemplateId;t.name=this.templateName||'我的模板';t.config=JSON.stringify(this.settings.watermark);await store.putTemplate(t);this.settings.templateId=t.id;await this.persist();await this.refresh();this.page=this.styleReturn;}
  private persistQuickWatermark(): void {this.settings.watermark=JSON.parse(JSON.stringify(this.styleDraft)) as Watermark;this.persist().catch((e:Error)=>this.notify(e.message));}
  private async persistWatermarkBeforeLeave(): Promise<void> {this.settings.watermark=JSON.parse(JSON.stringify(this.styleDraft)) as Watermark;await this.persist();}
  private async beginEdit(): Promise<void> {this.recipe=new EditRecipe();const annotations=await store.annotations(this.detail.id);if(annotations.length&&annotations[0].vectorDataJson){try{this.recipe=JSON.parse(annotations[0].vectorDataJson) as EditRecipe;}catch(e){this.recipe=new EditRecipe();}}this.history=[];this.redoHistory=[];this.editPreview=this.detail.path;this.editRatio=this.detail.width/this.detail.height;this.cropMode=false;this.page='editor';}
  private openCropDialog(): void {this.cropDialog=new CustomDialogController({customStyle:true,builder:CropDialog({previewPath:this.editPreview,imageRatio:this.editRatio,initialLeft:this.cropLeft,initialTop:this.cropTop,initialRight:this.cropRight,initialBottom:this.cropBottom,onApply:(left:number,top:number,right:number,bottom:number)=>{this.cropLeft=left;this.cropTop=top;this.cropRight=right;this.cropBottom=bottom;this.pushEdit();const step=new EditStep('crop');step.mark.x=left;step.mark.y=top;step.mark.endX=right;step.mark.endY=bottom;this.recipe.steps=this.recipe.steps.concat(step);this.task(async()=>this.renderEdit());}})});this.cropDialog!.open();}
  private pushEdit(): void {this.history=this.history.concat(JSON.stringify(this.recipe));this.redoHistory=[];}
  private async renderEdit(): Promise<void> {const revision=++this.editRevision;const path=await this.files!.edit(this.detail,JSON.parse(JSON.stringify(this.recipe)) as EditRecipe,0);this.previewFiles.push(path);if(revision===this.editRevision){this.editPreview=path;const source=image.createImageSource(path);try{const info=await source.getImageInfo();this.editRatio=info.size.width/info.size.height;}finally{await source.release();}}}
  private async undo(redo: boolean): Promise<void> {if(redo){if(!this.redoHistory.length)return;this.history=this.history.concat(JSON.stringify(this.recipe));this.recipe=JSON.parse(this.redoHistory[this.redoHistory.length-1]) as EditRecipe;this.redoHistory=this.redoHistory.slice(0,-1);}else{if(!this.history.length)return;this.redoHistory=this.redoHistory.concat(JSON.stringify(this.recipe));this.recipe=JSON.parse(this.history[this.history.length-1]) as EditRecipe;this.history=this.history.slice(0,-1);}await this.renderEdit();}
  private async saveEdit(): Promise<void> {const old=this.detail.edited;let next='';let committed=false;try{next=await this.files!.edit(this.detail,this.recipe,this.settings.photoQuality);const annotation=new Annotation();annotation.mediaId=this.detail.id;annotation.annotatedContentUri=next;annotation.vectorDataJson=JSON.stringify(this.recipe);await store.putAnnotation(annotation);committed=true;this.detail.edited=next;if(this.settings.albumCopy){try{this.detail.albumUri=await this.files!.albumCopy(this.detail,next);await store.putMedia(this.detail);}catch(e){this.notify('成品已保存，但系统相册副本失败：'+(e as Error).message);}}if(old){try{await this.files!.removePath(old);}catch(e){console.warn('SITECAM_OLD_EDIT_CLEANUP '+(e as Error).message);}}for(const path of this.previewFiles)await this.files!.removePath(path);this.previewFiles=[];await this.refresh();this.page='detail';}catch(e){if(!committed&&next)await this.files!.removePath(next);throw e as Error;}}
  private async editorTouch(e: TouchEvent): Promise<void> {
    if(this.busy)return;
    if(e.type===TouchType.Down&&e.touches.length){this.touchX=Math.max(0,Math.min(1,e.touches[0].x/this.editorWidth));this.touchY=Math.max(0,Math.min(1,e.touches[0].y/this.editorHeight));this.strokePoints=[this.touchX,this.touchY];}
    else if(e.type===TouchType.Move&&e.touches.length&&(this.markTool==='freehand'||this.markTool==='mosaic')){const x=Math.max(0,Math.min(1,e.touches[0].x/this.editorWidth));const y=Math.max(0,Math.min(1,e.touches[0].y/this.editorHeight));this.strokePoints=this.strokePoints.concat([x,y]);}
    else if(e.type===TouchType.Up&&e.changedTouches.length){const x=Math.max(0,Math.min(1,e.changedTouches[0].x/this.editorWidth)),y=Math.max(0,Math.min(1,e.changedTouches[0].y/this.editorHeight));if(this.cropMode){const w=Math.abs(x-this.touchX),h=Math.abs(y-this.touchY);if(w<.03||h<.03)return;this.pushEdit();const step=new EditStep('crop');step.mark.x=this.touchX;step.mark.y=this.touchY;step.mark.endX=x;step.mark.endY=y;this.recipe.steps=this.recipe.steps.concat(step);this.cropMode=false;}else{this.pushEdit();const mark=new Mark();mark.kind=this.markTool;mark.color=this.markColor;mark.x=this.touchX;mark.y=this.touchY;mark.endX=x;mark.endY=y;mark.text=this.markText;mark.points=(this.markTool==='freehand'||this.markTool==='mosaic')?this.strokePoints.concat([x,y]):[];if(mark.kind==='text'&&!mark.text){this.notify('请先填写标注文字');return;}const step=new EditStep(mark.kind);step.mark=mark;this.recipe.steps=this.recipe.steps.concat(step);}this.strokePoints=[];await this.task(async()=>this.renderEdit());}
  }

  @Builder
  header(title: string,back: string='') {Row({space:12}) {if(back){Button(){Row({space:4}){Image($r('app.media.ic_arrow_back')).width(22).height(22);Text('返回').fontSize(15).fontColor(SITE_TEXT);}}.backgroundColor(SITE_CARD_RAISED).onClick(()=>this.task(async()=>this.navigate(back)));}
Column({space:2}){Text(title).fontSize(22).fontWeight(FontWeight.Bold).fontColor(SITE_TEXT);Text('工程水印相机 · SiteCam').fontSize(11).fontColor(SITE_MUTED);}.alignItems(HorizontalAlign.Start);Blank();if(this.busy){LoadingProgress().width(26).height(26);}}.width('100%').padding({left:16,right:16,top:14,bottom:12}).backgroundColor(SITE_BG);}
  @Builder
  field(label: string,value: string,change: (value: string)=>void) {Column({space:5}){Text(label).fontSize(13).fontColor($r('app.color.text_secondary')).width('100%');TextInput({text:value,placeholder:label}).fontColor('#FFFFFF').placeholderColor('#B0BEC5').backgroundColor('#303030').caretColor('#FFB300').borderRadius(10).onChange(change).backgroundColor('#EAF0F3').fontColor('#173044');}.width('100%');}
  @Builder
  projectCardNew(p: Project) {ProjectCardView({project:p,current:p.id===this.settings.currentProject,checked:this.selected.includes(p.id),batchMode:this.batchMode,onOpen:()=>this.batchMode?this.toggleProject(p.id):this.task(async()=>{this.filterProject=p.id;await this.navigate('gallery');}),onEdit:()=>this.editProject(p),onExport:()=>this.exportStart([p.id]),onChecked:()=>this.toggleProject(p.id),onMenu:()=>this.openProjectMenu(p)});}
  @Builder
  projectCard(p: Project) {Column({space:7}){Row({space:8}){Checkbox({name:p.name}).accessibilityText('选择工程 '+p.name).select(this.selected.includes(p.id)).onChange(()=>this.toggleProject(p.id)).width(20).height(20);Text(p.name).fontWeight(FontWeight.Bold).fontSize(18).fontColor(SITE_TEXT).flexShrink(1).maxLines(this.fontScale>1.2?100:1).textOverflow({overflow:TextOverflow.Ellipsis});if(p.id===this.settings.currentProject){Text('当前').fontSize(10).fontColor('#231A00').backgroundColor(SITE_ACCENT).padding(4).borderRadius(4);}Blank();Text(p.locked?'已锁定':'').fontSize(11).fontColor(SITE_ACCENT);}.width('100%');
      Row(){Column({space:5}){Text(p.route||'未填写线路').fontSize(13).fontColor(SITE_MUTED).maxLines(this.fontScale>1.2?100:1);Text(p.count?`${dateText(p.first)} 至 ${dateText(p.last)}`:'尚无拍摄资料').fontSize(11).fontColor(SITE_MUTED);Row({space:10}){Text(`照片 ${p.photoCount} · 视频 ${p.videoCount} · 问题 ${p.issueCount}`).fontSize(11).fontColor(SITE_TEXT).fontWeight(FontWeight.Bold);Text(p.category).fontColor(SITE_MUTED);};}.alignItems(HorizontalAlign.Start).layoutWeight(1).onClick(()=>this.task(async()=>this.selectProject(p)));Column({space:5}){Button('编辑').height(this.fontScale>1.2?40:26).fontSize(12).backgroundColor(SITE_CARD_RAISED).fontColor(SITE_TEXT).onClick(()=>this.editProject(p));Button('导出').height(this.fontScale>1.2?40:26).fontSize(12).backgroundColor(SITE_ACCENT).fontColor('#231A00').onClick(()=>this.exportStart([p.id]));}}.width('100%');}.padding(10).width('100%').constraintSize({minHeight:112}).backgroundColor(SITE_CARD).borderRadius(12).border({width:p.id===this.settings.currentProject?2:0,color:SITE_ACCENT}).gesture(LongPressGesture({repeat:false}).onAction(()=>this.toggleProject(p.id)));}
  @Builder
  projectsRootPage() {ProjectsPageView({projects:this.projectRows(),windowWidth:this.windowWidth,windowHeight:this.windowHeight,fontScale:this.fontScale,search:this.query,archiveFilter:this.projectScope===1?'ARCHIVED':this.projectScope===2?'ALL':'ACTIVE',sortLabel:PROJECT_SORT_LABELS[this.sort],ascending:this.ascending,batchMode:this.batchMode,selectedIds:this.selected,currentProjectId:this.settings.currentProject,onBack:()=>this.task(async()=>this.navigate('camera')),onCreate:()=>this.editProject(new Project()),onSearch:(v:string)=>this.query=v,onArchive:(v:string)=>{this.projectScope=v==='ARCHIVED'?1:v==='ALL'?2:0;},onSort:()=>this.projectSortDialog(),onAscending:()=>this.ascending=!this.ascending,onBatch:()=>this.batchMode=!this.batchMode,onOpen:(p:Project)=>this.task(async()=>{this.filterProject=p.id;await this.navigate('gallery');}),onEdit:(p:Project)=>this.editProject(p),onExport:(p:Project)=>this.exportStart([p.id]),onMore:(p:Project)=>this.openProjectMenu(p),onToggle:(p:Project)=>this.toggleProject(p.id),onBatchSelectAll:()=>{this.selected=this.projectRows().map((p:Project)=>p.id);},onBatchClear:()=>{this.selected=[];},onBatchExport:()=>this.exportStart(this.selected),onBatchMore:()=>this.batchMoreDialog()});}
  @Builder
  projectsPage() {this.header('工程包');Column({space:8}){Row({space:8}){Search({value:this.query,placeholder:'搜索工程 / 线路'}).onChange(v=>this.query=v).layoutWeight(1);Button('＋ 新建').backgroundColor(SITE_ACCENT).fontColor('#231A00').onClick(()=>this.editProject(new Project()));}
Row({space:8}){ForEach(['进行中','已归档','全部'],(label: string,index: number)=>{Button(label).layoutWeight(1).fontSize(13).fontWeight(this.projectScope===index?FontWeight.Bold:FontWeight.Normal).fontColor(this.projectScope===index?'#231A00':SITE_TEXT).backgroundColor(this.projectScope===index?SITE_ACCENT:SITE_CARD).borderRadius(14).onClick(()=>{this.projectScope=index;this.archived=index===1;this.selected=[];});});}
Row({space:8}){if(!this.batchMode){Select(PROJECT_SORT_LABELS.map((value: string): SelectOption=>({value:value}))).selected(this.sort).value(PROJECT_SORT_LABELS[this.sort]).onSelect(i=>this.sort=i).layoutWeight(1);Button(this.ascending?'↑':'↓').fontSize(18).onClick(()=>this.ascending=!this.ascending);Button('批量管理').fontColor(SITE_ACCENT).backgroundColor(Color.Transparent).onClick(()=>{this.batchMode=true;this.selected=[];});}else{Text('批量管理').fontColor(SITE_TEXT).fontWeight(FontWeight.Bold).layoutWeight(1);Text(`已选 ${this.selected.length} 项`).fontSize(12).fontColor(SITE_MUTED);Button('全选').onClick(()=>{this.selected=this.projectRows().map(p=>p.id);});Button('清空').onClick(()=>this.selected=[]);Button('取消').onClick(()=>{this.batchMode=false;this.selected=[];});}}
if(this.batchMode){Row({space:4}){Button('锁定').onClick(()=>this.task(async()=>this.projectBatch('lock')));Button('解锁').onClick(()=>this.task(async()=>this.projectBatch('unlock')));Button('归档/恢复').onClick(()=>this.task(async()=>this.projectBatch('archive')));}
Row({space:4}){Button('分类').onClick(()=>this.openCategoryDialog());Button('导出').onClick(()=>this.exportStart(this.selected));Button('删除').onClick(()=>this.confirmDelete(true));}}}.padding({left:12,right:12,bottom:8});
    Row({space:12}) {Scroll(){GridRow({columns:this.windowWidth>=600&&this.windowWidth<840?2:1,gutter:8}){ForEach(this.projectRows(),(p: Project)=>{GridCol(){this.projectCardNew(p);}},(p: Project)=>p.id.toString());}.padding(12);}.align(Alignment.Top).layoutWeight(1).height('100%');
      if(this.windowWidth>=840){Column({space:16}){Text(this.current().id?this.current().name:'选择工程查看详情').fontSize(25).fontWeight(FontWeight.Bold).fontColor($r('app.color.text_primary'));if(this.current().id){Text(this.current().route||'未填写线路').fontColor($r('app.color.text_secondary'));Text(this.current().address).fontColor($r('app.color.text_secondary'));Text(this.current().notes).fontColor($r('app.color.text_secondary'));Text(`照片 ${this.current().photoCount} · 视频 ${this.current().videoCount} · 问题 ${this.current().issueCount}`).fontColor($r('app.color.text_accent'));Button('查看工程资料').onClick(()=>{this.filterProject=this.current().id;this.page='gallery';});Button('编辑工程').onClick(()=>this.editProject(this.current()));Button('导出工程').onClick(()=>this.exportStart([this.current().id]));}Blank();}.width('42%').height('100%').alignItems(HorizontalAlign.Start).padding(24);}
    }.layoutWeight(1).width('100%');}

  @Builder
  projectEditPage() {this.header(this.draft.id?'编辑工程':'新建工程','projects');Scroll(){Column({space:14}){this.field('工程名称',this.draft.name,v=>this.draft.name=v);this.field('线路（可选）',this.draft.route,v=>this.draft.route=v);this.field('类别',this.draft.category,v=>this.draft.category=v);this.field('地点',this.draft.address,v=>this.draft.address=v);this.field('备注',this.draft.notes,v=>this.draft.notes=v);Row(){Text('锁定工程，禁止继续拍摄').fontColor($r('app.color.text_primary')).layoutWeight(1);Toggle({type:ToggleType.Switch,isOn:this.draft.locked}).selectedColor('#FFB300').onChange(v=>this.draft.locked=v);}
Row(){Text('归档').fontColor($r('app.color.text_primary')).layoutWeight(1);Toggle({type:ToggleType.Switch,isOn:this.draft.archived}).selectedColor('#FFB300').onChange(v=>this.draft.archived=v);}
Button('保存工程').width('100%').backgroundColor('#FFCA55').fontColor('#352500').onClick(()=>this.task(async()=>this.saveProject()));}.padding(18);}.align(Alignment.Top).layoutWeight(1);}
  @Builder
  cameraToolbar() {
    Row({space:4}) {
      Button(){Text(this.current().name||'选择工程').fontSize(14).maxLines(1).fontColor(SITE_TEXT);}.layoutWeight(1).height(44)
        .constraintSize({maxWidth:360}).fontColor(SITE_TEXT).backgroundColor(SITE_CARD_RAISED).id('camera-project')
        .onClick(()=>this.task(async()=>this.navigate('projects')));
      if(this.windowWidth>=600)Blank().layoutWeight(1);
      Button(){Column({space:2}){Image($r('app.media.ic_flash')).width(20).height(20);Text('闪光').fontSize(10).fontColor(this.flash?SITE_ACCENT:SITE_TEXT);}.width('100%');}.width(48).height(44).fontSize(12).padding(0).fontColor(this.flash?SITE_ACCENT:SITE_TEXT)
        .backgroundColor(Color.Transparent).enabled(!this.recording&&!!this.camera?.flashSupported)
        .onClick(()=>{try{this.flash=this.camera!.cycleFlash()!=='off';}catch(e){this.notify('闪光灯暂不可用');}})
        .gesture(LongPressGesture({repeat:false}).onAction(()=>this.camera?.setTorch(true)).onActionEnd(()=>this.camera?.setTorch(false)));
      Button(){Column({space:2}){Image($r('app.media.ic_issue')).width(20).height(20);Text('问题').fontSize(10).fontColor(this.quickIssueEnabled?SITE_ACCENT:SITE_TEXT);}.width('100%');}.width(48).height(44).fontSize(12).padding(0).fontColor(this.quickIssueEnabled?SITE_ACCENT:SITE_TEXT)
        .backgroundColor(Color.Transparent).enabled(!this.recording).onClick(()=>this.quickIssueEnabled=!this.quickIssueEnabled);
      Button(){Column({space:2}){Image($r('app.media.ic_orientation')).width(20).height(20);Text('方向').fontSize(10).fontColor(SITE_TEXT);}.width('100%');}.width(48).height(44).fontSize(12).padding(0).fontColor(SITE_TEXT).backgroundColor(Color.Transparent)
        .enabled(!this.busy&&!this.recording).id('camera-orientation').onClick(()=>this.openOrientation());
      Button(){Column({space:2}){Image($r('app.media.ic_settings')).width(20).height(20);Text('设置').fontSize(10).fontColor(SITE_TEXT);}.width('100%');}.width(48).height(44).fontSize(12).padding(0).fontColor(SITE_TEXT).backgroundColor(Color.Transparent)
        .id('camera-settings').onClick(()=>this.task(async()=>this.navigate('settings')));
    }.width('100%').height(56).padding({left:8,right:8});
  }
  @Builder
  cameraControls() {
    Scroll() {
      Column({space:4}) {
        Row({space:2}) {
          ForEach([1,2,3,5],(preset:number)=>{
            Button(`${preset}×`).layoutWeight(1).height(36).padding(0).fontSize(13)
              .fontColor(Math.abs(this.zoom-preset)<.05?SITE_ACCENT:SITE_TEXT).backgroundColor(Color.Transparent)
              .enabled(this.cameraReady&&preset<=(this.camera?.zoomMax||1))
              .onClick(()=>{this.zoom=Math.max(this.camera?.zoomMin||1,Math.min(this.camera?.zoomMax||1,preset));this.camera?.zoom(this.zoom);});
          },(preset:number)=>preset.toString());
        }.width('100%');
        Row({space:18}) {
          Text('拍照').fontSize(15).fontColor(this.captureMode==='photo'?SITE_ACCENT:SITE_MUTED)
            .enabled(!this.busy&&!this.recording).onClick(()=>{this.captureMode='photo';this.updateFrameRatio();});
          Text('录像').fontSize(15).fontColor(this.captureMode==='video'?SITE_ACCENT:SITE_MUTED)
            .enabled(!this.busy&&!this.recording).onClick(()=>{this.captureMode='video';this.updateFrameRatio();});
        }.height(32).justifyContent(FlexAlign.Center);
        Flex({direction:this.cameraGeometry().side?FlexDirection.Column:FlexDirection.Row,
          justifyContent:FlexAlign.SpaceEvenly,alignItems:ItemAlign.Center}) {
          Button() {
            if(this.items.length&&this.items[0].type==='PHOTO') {
              Image('file://'+(this.items[0].edited||this.items[0].path)).width(48).height(48).borderRadius(10).objectFit(ImageFit.Cover);
            } else {Image($r('app.media.icon_folder')).width(32).height(32);}
          }.width(52).height(52).padding(0).backgroundColor(SITE_CARD_RAISED).borderRadius(10)
            .scale({x:this.thumbnailScale,y:this.thumbnailScale}).id('camera-gallery')
            .onClick(()=>this.task(async()=>{this.filterProject=this.current().id;await this.navigate('gallery');}));
          Button() {
            Stack() {
              Circle().width(70).height(70).fill(Color.White);
              Circle().width(58).height(58).fill(Color.Black);
              Circle().width(48).height(48).fill(this.recording?'#E53935':Color.White);
            }
          }.width(74).height(74).padding(0).backgroundColor(Color.Transparent)
            .scale({x:this.shutterScale,y:this.shutterScale}).id('camera-shutter').accessibilityText(this.recording?'停止录像':'拍摄')
            .enabled(!this.busy&&(this.recording||(!this.current().locked&&!this.current().archived&&this.cameraReady&&!!(this.captureMode==='photo'?this.camera?.photoSupported:this.camera?.videoSupported))))
            .onClick(()=>this.task(async()=>{if(this.captureMode==='photo')await this.shoot();else{this.animateShutter(false);await this.toggleVideo();}}));
          Button() {Image($r('app.media.ic_switch_lens')).width(26).height(26);}
            .width(52).height(52).padding(0).backgroundColor(SITE_CARD_RAISED).borderRadius(26).id('camera-switch-lens')
            .enabled(!this.busy&&!this.recording&&!!this.camera?.canSwitchLens)
            .onClick(()=>this.task(async()=>{await this.camera?.switchLens();this.cameraReady=true;this.updateFrameRatio();}));
        }.width('100%').height(this.cameraGeometry().side?198:82);
        if(this.cameraGeometry().hover) {
          Button('修改水印').fontColor(SITE_ACCENT).backgroundColor(Color.Transparent)
            .enabled(!this.recording).onClick(()=>{this.styleDraft=JSON.parse(JSON.stringify(this.settings.watermark)) as Watermark;this.quickSheet=true;});
        }
      }.width('100%').padding({left:8,right:8,bottom:8});
    }.width(this.cameraGeometry().controlsWidth).height(this.cameraGeometry().controlsHeight)
      .align(Alignment.Top).scrollable(ScrollDirection.Vertical)
      .position({x:this.cameraGeometry().controlsX,y:this.cameraGeometry().controlsY}).id('camera-controls');
  }
  @Builder
  cameraPage() {
    Stack({alignContent:Alignment.TopStart}) {
      this.cameraToolbar();
      Stack() {
        XComponent({id:'sitecam-preview',type:XComponentType.SURFACE,controller:this.controller})
          .width(this.surfaceWidth()).height(this.surfaceHeight()).onAreaChange((a,b)=>this.setPreviewSurfaceArea(Number(b.width),Number(b.height))).onLoad(()=>{this.surface=this.controller.getXComponentSurfaceId();this.task(async()=>this.openCamera());})
          .onDestroy(()=>this.cameraSurfaceLost())
          .onTouch(e=>{if(e.type===TouchType.Down&&this.cameraReady&&e.touches.length)this.camera?.focus(e.touches[0].x/this.surfaceWidth(),e.touches[0].y/this.surfaceHeight());})
          .gesture(PinchGesture().onActionStart(()=>this.pinchStartZoom=this.zoom).onActionUpdate(e=>{if(this.cameraReady&&this.camera){this.zoom=Math.max(this.camera.zoomMin,Math.min(this.camera.zoomMax,this.pinchStartZoom*e.scale));this.camera.zoom(this.zoom);}}));
        WatermarkView({snapshot:this.snapshot,reportBounds:true}).width('100%').height('100%').hitTestBehavior(HitTestMode.None);
        if(this.watermarkBound(2)>0&&this.watermarkBound(3)>0) {
          Button('').width(this.watermarkBound(2)).height(this.watermarkBound(3))
            .position({x:this.watermarkBound(0),y:this.watermarkBound(1)}).backgroundColor(Color.Transparent)
            .enabled(!this.busy&&!this.recording).id('camera-watermark')
            .onClick(()=>{this.styleDraft=JSON.parse(JSON.stringify(this.settings.watermark)) as Watermark;this.quickSheet=true;});
        }
        Rect().width('100%').height('100%').fill(Color.White).opacity(this.shutterFlash).hitTestBehavior(HitTestMode.None);
        if(!this.cameraReady) {Column(){Text(this.message||'正在打开相机').fontColor(SITE_TEXT);if(!this.photoPermission)Button('打开权限设置').onClick(()=>this.page='permissions');}.padding(12);}
      }.width(this.frameWidth()).height(this.frameWidth()/this.frameRatio).backgroundColor(Color.Black).clip(true)
        .position({x:this.cameraGeometry().previewX,y:this.cameraGeometry().previewY})
        .id('camera-preview');
      this.cameraControls();
      if(this.quickSheet) {
        QuickWatermarkPanel({panelWidth:this.cameraGeometry().side?this.cameraGeometry().controlsWidth:this.windowWidth,snapshot:this.snapshot,fields:this.styleDraft.fields,selectedStyle:this.styleDraft.style,fontScale:this.styleDraft.fontScale,wmOpacity:this.styleDraft.opacity,wmPosition:this.styleDraft.position,
          onDismiss:()=>{this.persistQuickWatermark();this.quickSheet=false;},onFieldChange:(i:number,v:string)=>{this.styleDraft.fields[i].value=v;this.persistQuickWatermark();},onFieldToggle:(i:number,v:boolean)=>{this.styleDraft.fields[i].enabled=v;this.persistQuickWatermark();},onStyle:(v:string)=>{this.styleDraft.style=v;this.persistQuickWatermark();},onScale:(v:number)=>{this.styleDraft.fontScale=v;this.persistQuickWatermark();},onOpacity:(v:number)=>{this.styleDraft.opacity=v;this.persistQuickWatermark();},onPosition:(v:string)=>{this.styleDraft.position=v;this.persistQuickWatermark();},onAddField:(label:string)=>this.styleDraft.fields=this.styleDraft.fields.concat(new Field('custom_'+Date.now(),label,'')),onAddFieldValue:(label:string,value:string)=>{this.styleDraft.fields=this.styleDraft.fields.concat(new Field('custom_'+Date.now(),label,value));this.persistQuickWatermark();}})
          .width(this.cameraGeometry().side||this.cameraGeometry().hover?this.cameraGeometry().controlsWidth:this.windowWidth)
          .height(this.cameraGeometry().side||this.cameraGeometry().hover?this.cameraGeometry().controlsHeight:Math.max(1,this.windowHeight-56))
          .position({x:this.cameraGeometry().side?this.cameraGeometry().controlsX:0,y:this.cameraGeometry().hover?this.cameraGeometry().controlsY:56});
      }
    }.width('100%').height('100%').backgroundColor(Color.Black);
  }
  @Builder
  galleryRootPage() {GalleryPageView({pathStack:this.galleryStack,detailContent:()=>{this.galleryDetailContent();},items:this.galleryRows(),projects:this.projects,currentProjectName:this.filterProject?(this.projects.find((p:Project)=>p.id===this.filterProject)?.name||'工程相册'):'全部工程相册',previewItem:this.detail,windowWidth:this.windowWidth,categories:Array.from(new Set(this.projects.map((p:Project)=>p.category).filter((v:string)=>!!v))),selectedFilter:this.fromDate===dateText(Date.now())?'TODAY':this.issueFilter===1?'ISSUES_ONLY':'ALL',selectedCategory:this.category,selectedStatus:this.issueFilter>1?['','','PENDING','IN_PROGRESS','COMPLETED'][this.issueFilter]:'',selectedSeverity:this.severityFilter?['','NORMAL','IMPORTANT','CRITICAL'][this.severityFilter]:'',dateRangeText:this.fromDate||this.toDate?`${this.fromDate||'起始'} 至 ${this.toDate||'结束'}`:'',selectedIds:this.mediaSelection,onBack:()=>this.task(async()=>this.navigate(this.galleryOrigin||'camera')),onSwitchProject:()=>this.projectPickerDialog(),onFilter:(v:string)=>{this.mediaSelection=[];if(v==='TODAY'){this.fromDate=dateText(Date.now());this.toDate=dateText(Date.now());this.issueFilter=0;}else if(v==='ISSUES_ONLY'){this.fromDate='';this.toDate='';this.issueFilter=1;}else{this.fromDate='';this.toDate='';this.issueFilter=0;}},onDateRange:()=>this.pickDateRange(),onClearDate:()=>{this.mediaSelection=[];this.fromDate='';this.toDate='';},onCategory:(v:string)=>{this.mediaSelection=[];this.category=v;},onStatus:(v:string)=>{this.mediaSelection=[];this.issueFilter=v?['','','PENDING','IN_PROGRESS','COMPLETED'].indexOf(v):0;},onSeverity:(v:string)=>{this.mediaSelection=[];this.severityFilter=v?['','NORMAL','IMPORTANT','CRITICAL'].indexOf(v):0;},onSelectAll:()=>this.mediaSelection=this.galleryRows().map((m:MediaItem)=>m.id),onClearSelection:()=>this.mediaSelection=[],onOpen:(m:MediaItem)=>this.openDetail(m),onOpenFull:()=>this.page='detail',onToggle:(m:MediaItem)=>this.toggleMedia(m.id),onIssue:(m:MediaItem)=>this.openIssue(m),onLongPress:(m:MediaItem)=>this.toggleMedia(m.id),onBatchAction:(a:string)=>a==='export'?this.exportStart(this.projects.map((p:Project)=>p.id),this.mediaSelection):this.batchMediaMoreDialog(),onBatchMove:()=>this.openMoveDialog(),onBatchShare:()=>this.task(async()=>this.files!.share(this.items.filter((m:MediaItem)=>this.mediaSelection.includes(m.id)).map((m:MediaItem)=>m.edited||m.path))),onBatchIssue:()=>{}});}
  @Builder
  galleryPage() {this.header('工程相册');Column({space:8}){Row({space:6}){ForEach(['全部','今天','问题'],(label: string,i: number)=>{Button(label).fontSize(13).backgroundColor(i===2&&this.issueFilter===1?SITE_ACCENT:i===1&&this.fromDate===dateText(Date.now())?SITE_ACCENT:i===0&&!this.fromDate&&!this.issueFilter?SITE_ACCENT:SITE_CARD_RAISED).onClick(()=>{if(i===0){this.fromDate='';this.toDate='';this.issueFilter=0;}else if(i===1){this.fromDate=dateText(Date.now());this.toDate=dateText(Date.now());this.issueFilter=0;}else{this.fromDate='';this.toDate='';this.issueFilter=1;}});},(label: string)=>label);}.width('100%');Select([{value:'全部工程'},...this.projects.map((p: Project): SelectOption=>({value:p.name}))]).selected(this.filterProject?this.projects.findIndex(p=>p.id===this.filterProject)+1:0).value(this.projects.find(p=>p.id===this.filterProject)?.name||'全部工程').onSelect(i=>this.filterProject=i?this.projects[i-1].id:0).width('100%');Row({space:6}){Button(this.fromDate||'开始日期').onClick(()=>this.pickDate(true)).layoutWeight(1);Button(this.toDate||'结束日期').onClick(()=>this.pickDate(false)).layoutWeight(1);}
Row({space:6}){Select([{value:'全部问题状态'},{value:'全部问题'},{value:'待处理'},{value:'处理中'},{value:'已完成'}]).selected(this.issueFilter).value(['全部问题状态','全部问题','待处理','处理中','已完成'][this.issueFilter]).onSelect(i=>this.issueFilter=i);Select([{value:'全部等级'},{value:'一般'},{value:'重要'},{value:'严重'}]).selected(this.severityFilter).value(['全部等级','一般','重要','严重'][this.severityFilter]).onSelect(i=>this.severityFilter=i);Button('清空').onClick(()=>{this.fromDate='';this.toDate='';this.filterYear=0;this.filterMonth=0;this.category='';this.issueFilter=0;this.severityFilter=0;});}
Row({space:6}){Select([{value:'全部年份'},...this.years().map((year: number): SelectOption=>({value:year.toString()}))]).selected(this.filterYear?this.years().indexOf(this.filterYear)+1:0).value(this.filterYear?this.filterYear.toString():'全部年份').onSelect(i=>this.filterYear=i?this.years()[i-1]:0);Select([{value:'全部月份'},...[1,2,3,4,5,6,7,8,9,10,11,12].map((v: number): SelectOption=>({value:`${v}月`}))]).selected(this.filterMonth).value(this.filterMonth?`${this.filterMonth}月`:'全部月份').onSelect(i=>this.filterMonth=i);}
TextInput({text:this.category,placeholder:'按工程类别筛选（留空为全部）'}).fontColor('#FFFFFF').placeholderColor('#B0BEC5').backgroundColor('#303030').caretColor('#FFB300').borderRadius(10).onChange(v=>this.category=v);if(this.mediaSelection.length){Row({space:6}){Text(`已选 ${this.mediaSelection.length}`).fontColor($r('app.color.text_accent'));Button('移动').onClick(()=>this.openMoveDialog());Button('导出').onClick(()=>this.exportStart(this.projects.map(p=>p.id),this.mediaSelection));Button('分享').onClick(()=>this.task(async()=>this.files!.share(this.items.filter(m=>this.mediaSelection.includes(m.id)).map(m=>m.edited||m.path))));Button('删除').onClick(()=>this.confirmDelete(false));}}}.padding(10);
    Row({space:12}){Scroll(){Column({space:10}){ForEach(this.galleryDates(),(day: string)=>{Column({space:6}){Text(`${day} · ${this.galleryRowsForDate(day).length} 项`).fontSize(16).fontWeight(FontWeight.Bold).fontColor(SITE_TEXT).width('100%');GridRow({columns:this.windowWidth>=840?3:this.windowWidth>=600?4:3,gutter:6}){ForEach(this.galleryRowsForDate(day),(m: MediaItem)=>{GridCol(){Column({space:4}){Stack({alignContent:Alignment.TopEnd}){if(m.type==='PHOTO'){Image('file://'+(m.edited||m.path)).width('100%').aspectRatio(1).objectFit(ImageFit.Cover).onClick(()=>this.batchMode?this.toggleMedia(m.id):this.openDetail(m));}else{Column({space:8}){Text('▶').fontSize(34).fontColor(Color.White);Text(m.status==='READY'?'视频':'待处理视频').fontSize(11);}.width('100%').aspectRatio(1).justifyContent(FlexAlign.Center).backgroundColor(SITE_CARD_RAISED).onClick(()=>this.openDetail(m));}Checkbox().width(18).height(18).select(this.mediaSelection.includes(m.id)).onChange(()=>this.toggleMedia(m.id));}
Text(`${m.type==='VIDEO'?'视频':'照片'} · ${this.projects.find(p=>p.id===m.projectId)?.name||'未分类'}`).fontSize(10).fontColor(SITE_MUTED);Text(day).fontSize(11).fontColor(SITE_MUTED);if(m.issueTitle)Text(`${m.issueTitle} · ${m.issueStatus==='PENDING'?'待处理':m.issueStatus==='IN_PROGRESS'?'处理中':'已完成'}`).maxLines(1).fontSize(11).fontColor(SITE_ACCENT);}.padding(3).gesture(LongPressGesture({repeat:false}).onAction(()=>this.toggleMedia(m.id)));}},(m: MediaItem)=>m.id.toString());};}.width('100%');},(day: string)=>day);}.padding(8);}.align(Alignment.Top).layoutWeight(1).height('100%');
      if(this.windowWidth>=840){Column({space:14}){Text(this.detail.id?'资料预览':'选择资料查看').fontSize(24).fontColor($r('app.color.text_primary'));if(this.detail.id){if(this.detail.type==='PHOTO'){Image('file://'+(this.detail.edited||this.detail.path)).width('100%').height(300).objectFit(ImageFit.Contain);}else{Video({src:'file://'+this.detail.path}).width('100%').height(300).controls(true);}Text(timeText(this.detail.timestamp)).fontColor($r('app.color.text_secondary'));Text(this.detail.issueTitle).fontColor($r('app.color.text_accent'));Button('详情、编辑与问题记录').onClick(()=>this.page='detail');}Blank();}.padding(18).width('42%').height('100%');}
    }.layoutWeight(1).width('100%');}

  @Builder
  galleryDetailContent() {if(this.detail.id&&!this.galleryStack.getAllPathName().includes('media-placeholder')){this.detailRootPage();}else{Column({space:12}){Image($r('app.media.ic_camera')).width(48).height(48);Text('选择照片或视频').fontSize(20).fontWeight(FontWeight.Bold).fontColor(SITE_TEXT);Text('在左侧查看工程资料，在这里预览、编辑和分享').fontSize(14).fontColor(SITE_MUTED).textAlign(TextAlign.Center);}.width('100%').height('100%').justifyContent(FlexAlign.Center).padding(24).backgroundColor(SITE_BG);}}
  @Builder
  detailRootPage() {DetailPageView({item:this.detail,showEdited:this.showEdited,issueTitle:this.detail.issueTitle,issueNotes:this.detail.issueNotes,issueSeverity:this.detail.severity,issueStatus:this.detail.issueStatus,retrying:this.busy,onSaveAlbum:()=>this.task(async()=>{const uri=await this.files!.albumCopy(this.detail,this.showEdited&&this.detail.edited?this.detail.edited:this.detail.path);if(uri){this.detail.albumUri=uri;await store.putMedia(this.detail);this.notify('已另存到系统相册，工程原件不受影响');}else this.notify('已取消另存，工程原件不受影响');}),onBack:()=>{if(this.page==='gallery')this.galleryStack.pop();else{this.galleryStack.clear();this.task(async()=>this.navigate('gallery'));}},onEdit:()=>this.beginEdit(),onShare:()=>this.task(async()=>this.files!.share([this.showEdited&&this.detail.edited?this.detail.edited:this.detail.path],this.detail.type==='VIDEO')),onDelete:()=>this.deleteDetail(),onRetryVideo:()=>this.task(async()=>{await this.video.process(this.detail,this.files!,v=>this.videoProgress=v);await this.refresh();}),onToggleEdited:()=>this.showEdited=!this.showEdited,onIssue:()=>this.openIssue(this.detail)});}
  @Builder
  detailPage() {this.header('资料详情','gallery');Row({space:8}){Button('分享').onClick(()=>this.task(async()=>this.files!.share([this.showEdited&&this.detail.edited?this.detail.edited:this.detail.path],this.detail.type==='VIDEO')));Button('编辑').onClick(()=>this.beginEdit());Button('删除').fontColor('#F44336').onClick(()=>this.deleteDetail());}.padding({left:14,right:14,bottom:6});Scroll(){Column({space:14}){if(this.detail.type==='VIDEO'){Video({src:'file://'+this.detail.path}).width('100%').height(300).controls(true).autoPlay(false);}else{Image('file://'+(this.showEdited&&this.detail.edited?this.detail.edited:this.detail.path)).width('100%').height(350).objectFit(ImageFit.Contain).scale({x:this.viewScale,y:this.viewScale}).translate({x:this.viewX,y:this.viewY}).gesture(GestureGroup(GestureMode.Parallel,PinchGesture().onActionStart(()=>this.imageScale=this.viewScale).onActionUpdate(e=>this.viewScale=Math.max(1,Math.min(8,this.imageScale*e.scale))),PanGesture({fingers:2}).onActionStart(()=>{this.dragX=this.viewX;this.dragY=this.viewY;}).onActionUpdate(e=>{this.viewX=this.dragX+e.offsetX;this.viewY=this.dragY+e.offsetY;}),TapGesture({count:2}).onAction(()=>{this.viewScale=this.viewScale===1?2:1;this.viewX=0;this.viewY=0;})));Row({space:10}){Button(this.showEdited?'查看原图':'查看成品').enabled(!!this.detail.edited).onClick(()=>this.showEdited=!this.showEdited);Button('编辑 / 标注').onClick(()=>this.beginEdit());Button('问题对话框').onClick(()=>this.openIssue(this.detail));}}
      Text(timeText(this.detail.timestamp)).fontColor($r('app.color.text_secondary'));Text(`${this.detail.width} × ${this.detail.height} · ${this.detail.status==='READY'?'已完成':'原片已保留，水印未完成'}`).fontColor($r('app.color.text_secondary'));if(this.detail.type==='VIDEO'&&this.detail.status!=='READY'){Button('重试视频水印').onClick(()=>this.task(async()=>{await this.video.process(this.detail,this.files!,v=>this.videoProgress=v);await this.refresh();}));}
      Row({space:10}){Button('分享').onClick(()=>this.task(async()=>this.files!.share([this.showEdited&&this.detail.edited?this.detail.edited:this.detail.path],this.detail.type==='VIDEO')));Button('保存副本到系统相册').onClick(()=>this.task(async()=>{this.detail.albumUri=await this.files!.albumCopy(this.detail,this.showEdited&&this.detail.edited?this.detail.edited:this.detail.path);await store.putMedia(this.detail);}));}
      this.field('问题标题（留空表示无问题）',this.detail.issueTitle,v=>this.detail.issueTitle=v);this.field('问题说明',this.detail.issueNotes,v=>this.detail.issueNotes=v);Row({space:10}){Select([{value:'一般'},{value:'重要'},{value:'严重'}]).selected(['NORMAL','IMPORTANT','CRITICAL'].indexOf(this.detail.severity)).value(['一般','重要','严重'][['NORMAL','IMPORTANT','CRITICAL'].indexOf(this.detail.severity)]).onSelect(i=>this.detail.severity=['NORMAL','IMPORTANT','CRITICAL'][i]);Select([{value:'待处理'},{value:'处理中'},{value:'已完成'}]).selected(['PENDING','IN_PROGRESS','COMPLETED'].indexOf(this.detail.issueStatus)).value(['待处理','处理中','已完成'][['PENDING','IN_PROGRESS','COMPLETED'].indexOf(this.detail.issueStatus)]).onSelect(i=>this.detail.issueStatus=['PENDING','IN_PROGRESS','COMPLETED'][i]);Button('保存问题').onClick(()=>this.task(async()=>{await store.putMedia(this.detail);await this.refresh();this.notify('问题记录已保存');}));}
    }.padding(14);}.align(Alignment.Top).layoutWeight(1);}
  private editorImageWidth(): number {const w=this.windowWidth>=840?this.windowWidth-344:this.windowWidth-24;const h=this.windowWidth>=840?this.windowHeight-76:Math.max(80,this.windowHeight-304);return Math.max(1,Math.min(w,h*this.editRatio));}
  @Builder
  editorPage() {Column(){Row({space:8}){Image($r('app.media.ic_arrow_back')).width(24).height(24).onClick(()=>this.onBackPress());Text('现场隐患照片标注').fontSize(19).fontWeight(FontWeight.Bold).fontColor(SITE_TEXT).layoutWeight(1);Button(){Image($r('app.media.ic_undo')).width(24).height(24)}.width(48).height(48).padding(0).backgroundColor(Color.Transparent).enabled(this.history.length>0).onClick(()=>this.task(async()=>this.undo(false)));Button(){Image($r('app.media.ic_redo')).width(24).height(24)}.width(48).height(48).padding(0).backgroundColor(Color.Transparent).enabled(this.redoHistory.length>0).onClick(()=>this.task(async()=>this.undo(true)));Button('✓').width(48).height(48).padding(0).fontSize(22).fontColor(SITE_ACCENT).backgroundColor(Color.Transparent).onClick(()=>this.task(async()=>this.saveEdit()));}.padding({left:12,right:12,bottom:6});Flex({direction:this.windowWidth>=840?FlexDirection.Row:FlexDirection.Column}){Column(){Image('file://'+this.editPreview).width(this.editorImageWidth()).height(this.editorImageWidth()/this.editRatio).objectFit(ImageFit.Fill).onAreaChange((a,b)=>{this.editorWidth=Number(b.width);this.editorHeight=Number(b.height);}).onTouch(e=>this.editorTouch(e));}.width(this.windowWidth>=840?this.windowWidth-320:this.windowWidth).layoutWeight(1).justifyContent(FlexAlign.Center).alignItems(HorizontalAlign.Center);
Scroll(){Column(){Row({space:8}){Button('水平翻转').layoutWeight(1).padding(0).fontSize(12).fontColor(SITE_ACCENT).backgroundColor(Color.Transparent).onClick(()=>this.task(async()=>{this.pushEdit();this.recipe.steps=this.recipe.steps.concat(new EditStep('flipX'));await this.renderEdit();}));Button('垂直翻转').layoutWeight(1).padding(0).fontSize(12).fontColor(SITE_ACCENT).backgroundColor(Color.Transparent).onClick(()=>this.task(async()=>{this.pushEdit();this.recipe.steps=this.recipe.steps.concat(new EditStep('flipY'));await this.renderEdit();}));Button('旋转90°').layoutWeight(1).padding(0).fontSize(12).fontColor(SITE_ACCENT).backgroundColor(Color.Transparent).onClick(()=>this.task(async()=>{this.pushEdit();this.recipe.steps=this.recipe.steps.concat(new EditStep('rotate'));await this.renderEdit();}));Button('裁剪').layoutWeight(1).padding(0).fontSize(12).fontColor(SITE_ACCENT).backgroundColor(Color.Transparent).onClick(()=>this.openCropDialog());}.width('100%').justifyContent(FlexAlign.Center);Text(this.cropMode?'在照片上拖出裁剪范围':'选择工具，在照片上拖动添加标注').fontSize(13).fontColor(SITE_MUTED);Column({space:10}){GridRow({columns:this.windowWidth>=840||this.windowWidth<380?3:6,gutter:4}){ForEach([['箭头','arrow'],['矩形','rect'],['圆形','circle'],['画笔','freehand'],['文字','text'],['马赛克','mosaic']],(tool: string[])=>{GridCol(){Button(){Column(){Image(tool[1]==='arrow'?$r('app.media.ic_tool_arrow'):tool[1]==='rect'?$r('app.media.ic_tool_rect'):tool[1]==='circle'?$r('app.media.ic_tool_circle'):tool[1]==='freehand'?$r('app.media.ic_tool_brush'):tool[1]==='text'?$r('app.media.ic_tool_text'):$r('app.media.ic_tool_mosaic')).width(24).height(24);Text(tool[0]).fontSize(10).fontColor(this.markTool===tool[1]?'#231A00':SITE_TEXT);}.width(52).height(48);}.padding(0).backgroundColor(this.markTool===tool[1]?SITE_ACCENT:SITE_CARD_RAISED).onClick(()=>{this.markTool=tool[1];this.cropMode=false;});}},(tool: string[])=>tool[1]);}
Row({space:8}){ForEach(['#F44336','#FFB300','#FFC107','#4CAF50','#FFFFFF'],(color: string)=>{Button().width(28).height(28).backgroundColor(color).border({width:this.markColor===color?3:1,color:this.markColor===color?SITE_TEXT:SITE_MUTED}).borderRadius(14).onClick(()=>this.markColor=color);},(color: string)=>color);}.justifyContent(FlexAlign.Center);TextInput({text:this.markText,placeholder:'文字标注内容（选择文字工具）'}).fontColor('#FFFFFF').placeholderColor('#B0BEC5').backgroundColor('#303030').caretColor('#FFB300').borderRadius(10).onChange(v=>this.markText=v);}.padding(10);} .width('100%');}.width(this.windowWidth>=840?320:'100%').height(this.windowWidth>=840?'100%':228).align(Alignment.Top);
}.layoutWeight(1).width('100%');}.width('100%').height('100%');}
  @Builder
  watermarkPanelPage() {WatermarkCustomizationPanel({snapshot:this.snapshot,fields:this.styleDraft.fields,selectedStyle:this.styleDraft.style,fontScale:this.styleDraft.fontScale,wmOpacity:this.styleDraft.opacity,wmPosition:this.styleDraft.position,onBack:()=>{this.persistQuickWatermark();this.page=this.styleReturn;},onFontScale:(v:number)=>this.styleDraft.fontScale=v,onOpacity:(v:number)=>this.styleDraft.opacity=v,onPosition:(v:string)=>this.styleDraft.position=v,onStyle:(v:string)=>this.styleDraft.style=v,onFieldChange:(i:number,v:string)=>this.styleDraft.fields[i].value=v,onFieldLabelChange:(i:number,v:string)=>this.styleDraft.fields[i].label=v,onFieldToggle:(i:number,v:boolean)=>this.styleDraft.fields[i].enabled=v,onMove:(i:number,d:number)=>{const a=this.styleDraft.fields.slice();const j=i+d;if(j>=0&&j<a.length){const t=a[i];a[i]=a[j];a[j]=t;this.styleDraft.fields=a;}},onDelete:(i:number)=>{this.styleDraft.fields=this.styleDraft.fields.filter((f:Field,index:number)=>index!==i);},onAddField:(label:string,value:string)=>{this.styleDraft.fields=this.styleDraft.fields.concat(new Field('custom_'+Date.now(),label,value));}});}
  @Builder
  watermarkPage() {this.header('自定义水印',this.styleReturn);Scroll(){Column({space:12}){Select(this.templates.map((t: WatermarkTemplate): SelectOption=>({value:t.name}))).selected(this.templates.findIndex(t=>t.id===this.draftTemplateId)).value(this.templates.find(t=>t.id===this.draftTemplateId)?.name||'选择模板').onSelect(i=>{this.draftTemplateId=this.templates[i].id;this.styleDraft=JSON.parse(this.templates[i].config) as Watermark;this.templateName=this.templates[i].name;});this.field('模板名称',this.templateName,v=>this.templateName=v);Button('另存为新模板').onClick(()=>this.task(async()=>{const t=new WatermarkTemplate();t.name=this.templateName||'我的模板';t.config=JSON.stringify(this.styleDraft);await store.putTemplate(t);await this.refresh();this.notify('模板已另存，当前拍摄设置未改变');}));Text('当前样式 · 更换样式').fontColor($r('app.color.text_accent')).fontSize(18);GridRow({columns:2,gutter:10}){ForEach(STYLES,(style: Style)=>{GridCol(){Column({space:4}){Stack(){WatermarkView({snapshot:this.styleSnapshot(style.id)});}.width('100%').aspectRatio(4/3).backgroundColor('#576E7A');Text(style.name).fontColor($r('app.color.text_primary')).fontSize(13);}.padding(5).border({width:2,color:this.styleDraft.style===style.id?'#FFCA55':'#304D60'}).borderRadius(8).onClick(()=>{const copy=JSON.parse(JSON.stringify(this.styleDraft)) as Watermark;copy.style=style.id;this.styleDraft=copy;});}});}
Text('点击样式预览，保存后才应用').fontColor($r('app.color.text_secondary')).fontSize(12);this.field('拍摄人员',this.styleDraft.user,v=>this.styleDraft.user=v);Text('字号比例').fontColor($r('app.color.text_primary'));Slider({value:this.styleDraft.fontScale,min:.5,max:2.2,step:.1}).selectedColor('#FFB300').trackColor('#505050').blockColor('#FFFFFF').onChange(v=>this.styleDraft.fontScale=v);Text('底板透明度').fontColor($r('app.color.text_primary'));Slider({value:this.styleDraft.opacity,min:0,max:1,step:.05}).selectedColor('#FFB300').trackColor('#505050').blockColor('#FFFFFF').onChange(v=>this.styleDraft.opacity=v);Select([{value:'左下'},{value:'右下'},{value:'左上'},{value:'右上'}]).selected(['BOTTOM_LEFT','BOTTOM_RIGHT','TOP_LEFT','TOP_RIGHT'].indexOf(this.styleDraft.position)).value(['左下','右下','左上','右上'][['BOTTOM_LEFT','BOTTOM_RIGHT','TOP_LEFT','TOP_RIGHT'].indexOf(this.styleDraft.position)]).onSelect(i=>this.styleDraft.position=['BOTTOM_LEFT','BOTTOM_RIGHT','TOP_LEFT','TOP_RIGHT'][i]);ForEach(this.styleDraft.fields,(f: Field,i: number)=>{Column(){Row(){Text(f.label).fontColor($r('app.color.text_primary')).layoutWeight(1);Toggle({type:ToggleType.Switch,isOn:f.enabled}).selectedColor('#FFB300').onChange(v=>this.styleDraft.fields[i].enabled=v);}
TextInput({text:f.value,placeholder:'留空使用拍摄时信息'}).fontColor('#FFFFFF').placeholderColor('#B0BEC5').backgroundColor('#303030').caretColor('#FFB300').borderRadius(10).onChange(v=>this.styleDraft.fields[i].value=v);}});Button('添加自定义字段').onClick(()=>{this.styleDraft.fields=this.styleDraft.fields.concat(new Field('custom_'+Date.now(),'自定义字段'));this.styleDraft=JSON.parse(JSON.stringify(this.styleDraft)) as Watermark;});ForEach(this.styleDraft.fields.filter(f=>f.key.startsWith('custom_')),(f: Field)=>{this.field('自定义字段名称',f.label,v=>f.label=v);});Button('确认应用').width('100%').backgroundColor('#FFCA55').fontColor('#352500').onClick(()=>this.task(async()=>this.saveWatermark()));Button('取消').width('100%').onClick(()=>this.page=this.styleReturn);}.padding(14);}.align(Alignment.Top).layoutWeight(1);}
  @Builder
  permissionRootPage() {PermissionGuidePageView({permissions:this.permissionRows(),hasRequested:this.settings.permissionHandled,requesting:this.permissionRequesting,fontScale:this.fontScale,onBack:()=>this.task(async()=>this.navigate('projects')),onRequest:()=>this.task(async()=>{this.permissionRequesting=true;try{await this.permissions(true);}finally{this.permissionRequesting=false;}}),onContinue:()=>this.task(async()=>{this.settings.permissionHandled=true;await this.persist();this.page=this.settings.currentProject?'camera':'projects';}),onOpenSettings:()=>this.task(async()=>{await this.context.startAbility({bundleName:'com.huawei.hmos.settings',abilityName:'com.huawei.hmos.settings.MainAbility',uri:'application_info_entry',parameters:{bundleName:'com.sitecam.app'}});})});}
  @Builder
  settingsRootPage() {SettingsPageView({quality:this.settings.photoQuality,albumCopy:this.settings.albumCopy,icon:this.settings.icon||'D',apiVersion:deviceInfo.sdkApiVersion,diagnostics:this.message,templateId:this.settings.templateId,onBack:()=>this.task(async()=>this.navigate('camera')),onHelp:()=>this.page='help',onWatermark:()=>this.openStyles('settings'),onQuality:(v:number)=>{this.settings.photoQuality=v;this.settings.editQuality=v;this.persist();},onAlbumCopy:(v:boolean)=>{this.settings.albumCopy=v;this.persist();},onIcon:(v:string)=>this.task(async()=>{await bundleManager.setAlternateIcon(v==='D'?'':v);this.settings.icon=v;await this.persist();}),onCopyDiagnostics:()=>this.task(async()=>this.copyDiagnostics()),onOpenLink:(url:string)=>this.task(async()=>this.openExternal(url))});}
  @Builder
  helpRootPage() {HelpPageView({onBack:()=>this.page='settings',onReplayGuide:()=>{this.guideStep=0;this.page='guide';}});}
  @Builder
  onboardingRootPage() {OnboardingPageView({step:this.guideStep,onNext:()=>this.task(async()=>{if(this.guideStep<3){this.guideStep++;}else{this.settings.guideDone=true;await this.persist();this.page=this.settings.permissionHandled?(this.settings.currentProject?'camera':'projects'):'permissions';}}),onSkip:()=>this.task(async()=>{this.settings.guideDone=true;await this.persist();this.page=this.settings.permissionHandled?(this.settings.currentProject?'camera':'projects'):'permissions';})});}
  @Builder
  settingsPage() {this.header('设置与帮助');Scroll(){Column({space:16}){Button('离线使用教程').width('100%').onClick(()=>this.page='help');Button('重新查看首次指引').width('100%').onClick(()=>{this.guideStep=0;this.page='guide';});Button('当前水印样式 · 自定义').width('100%').onClick(()=>this.openStyles('settings'));Text('照片与编辑保存质量').fontColor($r('app.color.text_primary'));Select(QUALITY_NAMES.map((value: string): SelectOption=>({value:value}))).selected(this.settings.photoQuality).value(QUALITY_NAMES[this.settings.photoQuality]).onSelect(i=>{this.settings.photoQuality=i;this.settings.editQuality=i;this.persist();});Row(){Text('拍摄后保存副本到系统相册').fontColor($r('app.color.text_primary')).layoutWeight(1);Toggle({type:ToggleType.Switch,isOn:this.settings.albumCopy}).selectedColor('#FFB300').onChange(v=>{this.settings.albumCopy=v;this.persist();});}
Text('原件始终保留在工程相册。系统会请求保存授权，两处资料独立删除。').fontColor($r('app.color.text_secondary')).fontSize(13);Button('权限设置').width('100%').onClick(()=>this.page='permissions');Text('桌面图标 · '+(this.settings.icon||'D')).fontColor($r('app.color.text_accent'));if(deviceInfo.sdkApiVersion>=26){Row({space:6}){ForEach(['A','B','C','D'],(id: string)=>{Button(id).onClick(()=>this.task(async()=>{await bundleManager.setAlternateIcon(id==='D'?'':id);this.settings.icon=id;await this.persist();this.notify('桌面图标已切换');}));});}}else{Text('当前系统使用默认 D 图标').fontColor($r('app.color.text_secondary'));}
Text('v0.2.8 · 鸿蒙原生版\n永久免费 · 开源 · 无广告\n作者 ninjaaqua').fontSize(13).fontColor($r('app.color.text_secondary'));}.padding(20);}.align(Alignment.Top).layoutWeight(1);}
  @Builder
  guidePage() {this.header('欢迎使用');Column({space:24}){Image($r('app.media.app_icon')).width(120).height(120);Text(['工程照片，分开保存','横竖拍摄，按现场选择','按日期找照片，随手记问题','完工导出，及时锁定'][this.guideStep]).fontSize(28).fontWeight(FontWeight.Bold).fontColor($r('app.color.text_accent'));Text(['先建立工程与线路，再拍照、录像。资料自动归入当前工程。','预览和照片使用同一水印布局，拍摄前可修改字段和方向。','在工程相册按日期和问题状态查找，并保留独立编辑成品。','导出 ZIP 或工程文件夹。锁定后禁止新增拍摄，仍可整理旧资料。'][this.guideStep]).fontSize(19).fontColor($r('app.color.text_primary'));Text(`${this.guideStep+1} / 4`).fontColor($r('app.color.text_secondary'));Button(this.guideStep===3?'开始使用':'下一步').onClick(()=>this.task(async()=>{if(this.guideStep<3){this.guideStep++;return;}this.settings.guideDone=true;await this.persist();this.page=this.settings.permissionHandled?(this.settings.currentProject?'camera':'projects'):'permissions';}));Button('暂时跳过').onClick(()=>this.task(async()=>{this.settings.guideDone=true;await this.persist();this.page=this.settings.permissionHandled?(this.settings.currentProject?'camera':'projects'):'permissions';}));}.padding(30).layoutWeight(1).justifyContent(FlexAlign.Center);}
  @Builder
  permissionsPage() {this.header('使用权限','projects');Column({space:20}){Text('相机：用于拍照录像\n定位：可选，用于地点水印\n麦克风：可选，拒绝后仍可录制无声视频').fontSize(20).fontColor($r('app.color.text_primary')).lineHeight(34);Text('定位或地址不可用时会明确标注，不影响保存工程资料。').fontColor($r('app.color.text_secondary'));Button('开启所需权限').onClick(()=>this.task(async()=>{await this.permissions(true);this.page=this.settings.currentProject?'camera':'projects';this.notify(this.photoPermission?'权限设置已完成':'未开启相机，可先管理工程资料');}));Button('稍后设置').onClick(()=>this.task(async()=>{this.settings.permissionHandled=true;await this.persist();this.page=this.settings.currentProject?'camera':'projects';}));Button('打开系统应用设置').onClick(()=>this.task(async()=>{await this.context.startAbility({bundleName:'com.huawei.hmos.settings',abilityName:'com.huawei.hmos.settings.MainAbility',uri:'application_info_entry',parameters:{bundleName:'com.sitecam.app'}});}));}.padding(24).layoutWeight(1).justifyContent(FlexAlign.Center);}
  @Builder
  exportPage() {this.header('工程资料导出','projects');Column({space:18}){Text(`已选 ${this.exportIds.length} 个工程`).fontSize(22).fontColor($r('app.color.text_accent'));Select([{value:'ZIP 压缩包'},{value:'总文件夹'}]).selected(this.exportFolder?1:0).value(this.exportFolder?'总文件夹':'ZIP 压缩包').onSelect(i=>this.exportFolder=i===1);Text('总文件夹通过系统授权保存到下载目录。').fontColor($r('app.color.text_secondary'));
Text('照片导出质量').fontColor($r('app.color.text_primary'));Select([{value:'保持原文件'},...QUALITY_NAMES.map((value: string): SelectOption=>({value:value}))]).selected(this.exportQuality+1).value(this.exportQuality<0?'保持原文件':QUALITY_NAMES[this.exportQuality]).onSelect(i=>this.exportQuality=i-1);Text('包含工程信息、照片、视频、编辑成品、清单和报告。缺失或处理失败的资料会在报告中说明。').fontColor($r('app.color.text_secondary'));Button('选择位置并导出').width('100%').enabled(!this.busy).onClick(()=>this.task(async()=>this.exportNow()));if(this.busy)Button('取消导出').onClick(()=>{this.exporter!.cancelled=true;});}.padding(20).layoutWeight(1);}
  build() {Column(){if(!this.ready){this.header('工程水印相机');Text(this.message).fontColor($r('app.color.text_primary')).padding(24);}else{if(this.page==='projects'){this.projectsRootPage();}else if(this.page==='projectEdit'){this.projectEditPage();}else if(this.page==='camera'){this.cameraPage();}else if(this.page==='gallery'){this.galleryRootPage();}else if(this.page==='detail'){this.detailRootPage();}else if(this.page==='editor'){this.editorPage();}else if(this.page==='watermark'){this.watermarkPanelPage();}else if(this.page==='settings'){this.settingsRootPage();}else if(this.page==='guide'){this.onboardingRootPage();}else if(this.page==='permissions'){this.permissionRootPage();}else if(this.page==='export'){this.exportPage();}else if(this.page==='help'){this.helpRootPage();}else if(this.page==='category'){this.header('批量分类','projects');this.field('类别',this.batchProjectCategory,v=>this.batchProjectCategory=v);Button('应用分类').onClick(()=>this.task(async()=>{await this.projectBatch('category');this.page='projects';}));Blank();}else if(this.page==='move'){this.header('移动至工程','gallery');Scroll(){Column({space:10}){ForEach(this.projects,(p: Project)=>{Button(p.name).width('100%').onClick(()=>this.task(async()=>{await store.moveMedia(this.mediaSelection,p.id);this.mediaSelection=[];await this.refresh();this.page='gallery';}));});}.padding(16);}.align(Alignment.Top).layoutWeight(1);}
    if(this.busy&&this.video.running){Progress({value:this.videoProgress*100,total:100,type:ProgressType.Linear}).width('95%');Button('取消视频处理').onClick(()=>this.video.cancel());}
    if(this.message&&!['guide','permissions','camera'].includes(this.page)){Text(this.message).fontSize(12).fontColor($r('app.color.text_accent')).maxLines(3).padding(8).width('100%');}
    }
  }.width('100%').height('100%').backgroundColor($r('app.color.page_bg')).padding({top:this.environment.top,bottom:this.environment.bottom,left:this.environment.left,right:this.environment.right});}
}
