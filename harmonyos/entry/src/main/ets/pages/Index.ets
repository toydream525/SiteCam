import { FoldCoverControls } from './components/FoldCoverControls';
import { WindowEnvironment } from '../core/WindowEnvironment';
 import { CameraLayout, cameraLayout, captureDegrees, windowOrientationTarget } from '../core/CameraLayout';
import { util } from '@kit.ArkTS';
import { abilityAccessCtrl, common, Permissions } from '@kit.AbilityKit';
import { promptAction, display, window, curves } from '@kit.ArkUI';
import { fileIo as fs } from '@kit.CoreFileKit';
import { image } from '@kit.ImageKit';
import { deviceInfo } from '@kit.BasicServicesKit';
import { pasteboard } from '@kit.BasicServicesKit';
import { bundleManager } from '@kit.AbilityKit';
import { store } from '../core/Store';
import { Project, MediaItem, Settings, Snapshot, Watermark, WatermarkTemplate, Field, Style, Annotation, Issue, STYLES, QUALITY_NAMES, captureSnapshot, resetWatermarkFields, dateText, timeText } from '../core/Models';
import { CameraService } from '../core/CameraService';
import { ShutterSoundService } from '../core/ShutterSoundService';
import { flashModeLabel } from '../core/FlashMode';
import { isNearestZoomPreset, zoomPresetLabel } from '../core/CameraCapability';
import { MediaService, EditRecipe, EditStep, Mark } from '../core/MediaService';
import { LocationService } from '../core/LocationService';
import { ExportService } from '../core/ExportService';
import { VideoService } from '../core/VideoService';
import { WatermarkView } from '../components/WatermarkView';
import { SITE_BG, SITE_CARD, SITE_CARD_RAISED, SITE_ACCENT, SITE_TEXT, SITE_MUTED } from './components/UiPrimitives';
import { SitePage, PROJECT_SORT_LABELS, ISSUE_STATUS_LABELS, SEVERITY_LABELS } from './state/AppState';
import { ProjectCardView, EditorToolBar, WatermarkStyleGrid } from './components/FeaturePages';
import { ProjectsPageView } from './components/ProjectsPageView';
import { ProjectSwitchPanel } from './components/ProjectSwitchPanel';
import { ProjectUndoBar } from './components/ProjectUndoBar';
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
import { AddressRefreshRect, chooseAddressRefreshPlacement } from '../core/AddressRefreshPlacement';
import { projectPanelGeometry } from '../core/ProjectPanelGeometry';

class RefreshButtonGeometry {
  x: number = 0;
  y: number = 0;
  width: number = 0;
  height: number = 0;
  visible: boolean = false;
}

@Entry
@Component
struct Index {
  @StorageProp('windowEnvironment') @Watch('environmentChanged') environment: WindowEnvironment=new WindowEnvironment();
  @State shutterScale: number=1;
  @State thumbnailScale: number=1;
  @State shutterFlash: number=0;
  private captureInFlight: boolean=false;
  private videoStarting: boolean=false;
  private stoppingVideo: boolean=false;
  private lastCaptureDegrees: number=0;
  private animationTimers: number[]=[];
  @State ready: boolean=false;
  @State page: string='projects';
  @State pageOrigin: string='camera';
  @State galleryOrigin: string='camera';
  @State navDirection: string='forward';
  private readonly forwardTransition: TransitionEffect = TransitionEffect.asymmetric(
    TransitionEffect.OPACITY.combine(TransitionEffect.translate({ x: '20%' }))
      .animation({ curve: curves.springMotion(0.35, 0.9) }),
    TransitionEffect.OPACITY.combine(TransitionEffect.translate({ x: '-20%' }))
      .animation({ curve: curves.springMotion(0.35, 0.9) })
  );
  private readonly backwardTransition: TransitionEffect = TransitionEffect.asymmetric(
    TransitionEffect.OPACITY.combine(TransitionEffect.translate({ x: '-20%' }))
      .animation({ curve: curves.springMotion(0.35, 0.9) }),
    TransitionEffect.OPACITY.combine(TransitionEffect.translate({ x: '20%' }))
      .animation({ curve: curves.springMotion(0.35, 0.9) })
  );
  private readonly modalTransition: TransitionEffect = TransitionEffect.asymmetric(
    TransitionEffect.OPACITY.combine(TransitionEffect.translate({ y: '25%' }))
      .animation({ curve: curves.springMotion(0.35, 0.9) }),
    TransitionEffect.OPACITY.combine(TransitionEffect.translate({ y: '25%' }))
      .animation({ curve: curves.springMotion(0.35, 0.9) })
  );

  private getPageTransition(targetPage: string): TransitionEffect {
    if (targetPage === 'watermark' || targetPage === 'move' || targetPage === 'category' || this.navDirection === 'modal') {
      return this.modalTransition;
    }
    if (this.navDirection === 'backward') {
      return this.backwardTransition;
    }
    return this.forwardTransition;
  }
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
  @State locationRefreshState: string='idle';
  @State locationFailureReason: string='';
  @State flash: boolean=false;
  @State flashMode: string='off';
  @State flashAvailable: boolean=false;
  @State videoProgress: number=0;
  @State templates: WatermarkTemplate[]=[];
  @State projectCategories: string[]=[];
  @State projectSwitchOpen: boolean=false;
  @State projectSwitchingId: number=0;
  @State projectSwitchItems: Project[]=[];
  @State projectUndoMessage: string='';
  @State projectUndoId: number=0;
  @State projectUndoKind: string='';
  @State projectUndoApplied: boolean=false;
  @State refreshButtonMeasuredWidth: number=0;
  @State refreshButtonMeasuredHeight: number=0;
  @State refreshButtonMeasuredKey: string='';
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
  private shutterSound: ShutterSoundService=new ShutterSoundService();
  private video: VideoService=new VideoService();
  private exporter: ExportService|null=null;
  private exportMediaProjectIds: number[]=[];
  private controller: XComponentController=new XComponentController();
  private surface: string='';
  private openingCamera: boolean=false;
  private timer: number=-1;
  private recordingItem: MediaItem|null=null;
  /** Page the permission guide was opened from, so its back button matches the system back. */
  private permissionsReturn: string='camera';
  @State photoPermission: boolean=false;
  @State microphonePermission: boolean=false;
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
  private editTarget: MediaItem|null=null;
  private orientationDialog: CustomDialogController|null=null;
  private dateRangeDialog: CustomDialogController|null=null;
  private actionDialog: CustomDialogController|null=null;
  private lifecycleToken: number=0;
  private flashLongPressConsumed: boolean=false;
  private orientationQueue: Promise<void>=Promise.resolve();
  private orientationSequence: number=0;
  private projectUndoTimer: number=-1;
  private environmentChanged(): void {
    this.windowWidth=Math.max(1,this.environment.width-this.environment.left-this.environment.right);
    this.windowHeight=Math.max(1,this.environment.height-this.environment.top-this.environment.bottom);
    if(this.page==='camera'&&!this.captureInFlight&&!this.recording&&!this.videoStarting)this.updateFrameRatio();
  }
  private effectiveDegrees(): number {this.lastCaptureDegrees=captureDegrees(this.settings.orientation,this.environment.rotation,this.lastCaptureDegrees);return this.lastCaptureDegrees;}
  private cameraGeometry(): CameraLayout {return cameraLayout(this.windowWidth,this.windowHeight,this.frameRatio,this.environment.halfFolded,this.environment.creaseTop,this.environment.creaseHeight,this.environment.coverScreen,this.environment.density);}
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
    this.lifecycleToken++;
    this.surface='';this.cameraReady=false;
    this.syncFlashUi();
    if(this.recording)this.stopVideo().catch((e:Error)=>this.notify(e.message));
    else {void this.restoreWindowOrientation();this.camera?.close();}
  }

  async aboutToAppear(): Promise<void> {
    try {this.location.onChange=()=>this.locationChanged();await store.open(this.context);this.settings=store.settings;this.files=new MediaService(this.context);this.exporter=new ExportService(this.context,this.files);await this.refresh();await this.permissions(false);this.syncLocationUi();this.ready=true;this.message='';if(!this.settings.guideDone)this.page='guide';else if(!this.settings.permissionHandled)this.page='permissions';else this.page=this.settings.currentProject?'camera':'projects';
      this.environmentChanged();
      const bytes=await this.context.resourceManager.getRawFileContent('USER_GUIDE.md');this.help=String.fromCharCode();const decoder=new util.TextDecoder('utf-8');this.help=decoder.decodeToString(bytes);
    }catch(e){this.message=`打开失败：${(e as Error).message}`;}
  }
  aboutToDisappear(): void {for(const timer of this.animationTimers)clearTimeout(timer);if(this.timer>=0)clearInterval(this.timer);if(this.projectUndoTimer>=0)clearTimeout(this.projectUndoTimer);this.lifecycleToken++;this.projectSwitchOpen=false;this.cameraReady=false;this.syncFlashUi();this.location.onChange=null;this.location.stop();void this.restoreWindowOrientation();this.camera?.close();this.shutterSound.release().catch(()=>{});this.video.cancel();}
  onBackPress(): boolean {
    if(this.quickSheet){this.persistQuickWatermark();this.quickSheet=false;return true;}
    if(this.recording||this.busy){this.notify('请先结束当前操作');return true;}
    if(this.page==='gallery'&&this.galleryStack.size()>0&&!this.galleryStack.getAllPathName().includes('media-placeholder')){this.galleryStack.pop();return true;}
    if(this.page==='camera')return false;
    if(this.page==='editor'){AlertDialog.show({title:'退出编辑？',message:'当前标注和裁剪尚未保存，退出后将丢失。',primaryButton:{value:'继续编辑',action:()=>{}},secondaryButton:{value:'放弃修改',fontColor:'#F44336',action:()=>this.task(async()=>this.navigate('detail','backward'))}});return true;}
    if(this.page==='projects'||this.page==='settings'){this.task(async()=>this.navigate('camera','backward'));return true;}
    if(this.page==='gallery'){this.task(async()=>this.navigate(this.galleryOrigin||'camera','backward'));return true;}
    if(this.page==='permissions'){this.task(async()=>this.navigate(this.permissionsReturn||'camera','backward'));return true;}
    const back=this.page==='detail'||this.page==='move'?'gallery':this.page==='editor'?'detail':this.page==='watermark'?this.styleReturn:this.page==='help'?'settings':this.pageOrigin||'camera';
    this.task(async()=>{if(this.page==='watermark')await this.persistWatermarkBeforeLeave();await this.navigate(back,'backward');});return true;
  }
  private async refresh(): Promise<void> {this.projects=await store.projects();this.items=await store.media();this.templates=await store.templates();this.projectCategories=(await store.categories()).map(c=>c.name);if(this.settings.currentProject&&!this.projects.some((p:Project)=>p.id===this.settings.currentProject)){this.settings.currentProject=0;this.settings.projectSelectionCleared=true;await this.persist();}const activeDetailId=this.detail.id;if(activeDetailId){const latest=this.items.find((m:MediaItem)=>m.id===activeDetailId);if(latest)this.detail=JSON.parse(JSON.stringify(latest)) as MediaItem;else{this.detail=new MediaItem();if(this.page==='detail'){this.galleryStack.clear();await this.navigate('gallery','backward');}}}}
  private async task(action: ()=>Promise<void>): Promise<void> {if(this.busy)return;this.busy=true;try{await action();}catch(e){console.error('SITECAM_ACTION '+(e as Error).stack);this.notify((e as Error).message||'操作未完成');}finally{this.busy=false;}}
  private notify(text: string): void {promptAction.showToast({message:text,duration:3000});}
  private async copyDiagnostics(): Promise<void> {const cameraInfo=this.camera?`\n闪光：${this.camera.flashDiagnostics}\n倍率：${this.camera.zoomRatio}×（范围 ${this.camera.zoomMin}–${this.camera.zoomMax}，候选 ${this.camera.zoomPresets.join(',')}）`:'';const text=`设备：${deviceInfo.productModel||'未知'}\nSDK：${deviceInfo.sdkApiVersion}\n版本：v0.3.3${cameraInfo}`;const board=pasteboard.getSystemPasteboard();await board.setData(pasteboard.createData(pasteboard.MIMETYPE_TEXT_PLAIN,text));this.notify('诊断信息已复制');}
  private async openExternal(url: string): Promise<void> {await this.context.startAbility({uri:url,action:'ohos.want.action.viewData'});}
  private current(): Project {return this.projects.find(p=>p.id===this.settings.currentProject)||new Project();}
  private async persist(): Promise<void> {store.settings=this.settings;await store.saveSettings();}
  private async persistSettingsSnapshot(next: Settings): Promise<void> {const previous=store.settings;store.settings=next;try{await store.saveSettings();}catch(e){store.settings=previous;throw e as Error;}}
  private syncLocationUi(): void {this.locationRefreshState=this.location.refreshState;this.locationFailureReason=this.location.failureReason;if(this.page==='camera'&&!this.captureInFlight&&!this.recording&&!this.videoStarting)this.snapshot=captureSnapshot(this.current(),this.settings,this.location.fix);}
  private locationChanged(): void {this.syncLocationUi();}
  private locationAddressAvailable(): boolean {const address=this.location.fix?.address||'';return !!address.trim();}
  private shouldShowAddressRefresh(): boolean {if(this.locationRefreshState==='refreshing')return true;if(!this.locationPermission)return !this.locationAddressAvailable();return !this.locationAddressAvailable()||this.locationRefreshState==='failed';}
  private addressRefreshLabel(): string {if(this.locationRefreshState==='refreshing')return '刷新中…';if(!this.locationPermission)return '请开定位';if(this.locationFailureReason==='location-disabled')return '打开定位';return this.locationFailureReason?'重试地址':'刷新地址';}
  private addressRefreshFailureMessage(): string {if(this.locationFailureReason==='permission-denied')return '请先开启定位权限';if(this.locationFailureReason==='location-disabled')return '请打开系统定位后重试';if(this.locationFailureReason==='location-timeout')return '暂未获取到有效位置，请重试';return '地址暂不可用，请重试';}
  /** A missing permission is resolved where the user actually hit the wall. */
  private async resolveLocationPermission(): Promise<void> {if(this.environment.coverScreen){await this.permissions(true);this.syncLocationUi();return;}await this.navigate('permissions','forward');}
  private async requestAddressRefresh(): Promise<void> {if(!this.locationPermission){await this.resolveLocationPermission();return;}if(this.locationRefreshState==='refreshing')return;const lifecycle=this.lifecycleToken;try{const success=await this.location.refreshAddress();this.syncLocationUi();if(!this.foreground||this.page!=='camera'||this.lifecycleToken!==lifecycle)return;if(success){if(!this.captureInFlight&&!this.recording&&!this.videoStarting)this.snapshot=captureSnapshot(this.current(),this.settings,this.location.fix);this.notify('地址与海拔已更新');}else this.notify(this.addressRefreshFailureMessage());}catch(e){this.syncLocationUi();if(this.foreground&&this.page==='camera'&&this.lifecycleToken===lifecycle)this.notify(this.addressRefreshFailureMessage());}}
  private addressRefreshFontSize(): number {return Math.max(13,Math.min(22,14*this.fontScale));}
  private addressRefreshMeasureKey(): string {return this.fontScale.toString()+'|'+this.addressRefreshLabel();}
  private addressRefreshEstimatedWidth(): number {return Math.max(112,this.addressRefreshFontSize()*4+32);}
  private addressRefreshEstimatedHeight(): number {return Math.max(48,this.addressRefreshFontSize()*2+20);}
  private refreshButtonWidth(): number {const key=this.addressRefreshMeasureKey();return this.refreshButtonMeasuredKey===key&&this.refreshButtonMeasuredWidth>0?this.refreshButtonMeasuredWidth:this.addressRefreshEstimatedWidth();}
  private refreshButtonHeight(): number {const key=this.addressRefreshMeasureKey();return this.refreshButtonMeasuredKey===key&&this.refreshButtonMeasuredHeight>0?this.refreshButtonMeasuredHeight:this.addressRefreshEstimatedHeight();}
  private refreshButtonPosition(): RefreshButtonGeometry {const result=new RefreshButtonGeometry();const g=this.cameraGeometry();const width=this.refreshButtonWidth();const height=this.refreshButtonHeight();const watermarkWidth=this.watermarkBound(2);const watermarkHeight=this.watermarkBound(3);let watermarkRect:AddressRefreshRect|null=null;if(watermarkWidth>0&&watermarkHeight>0){watermarkRect={x:this.watermarkBound(0),y:this.watermarkBound(1),width:watermarkWidth,height:watermarkHeight};}const placement=chooseAddressRefreshPlacement({previewWidth:g.previewWidth,previewHeight:g.previewHeight,buttonWidth:width,buttonHeight:height,watermarkRect:watermarkRect,safeSpacing:8});if(placement===null)return result;result.x=g.previewX+placement.x;result.y=g.previewY+placement.y;result.width=placement.width;result.height=placement.height;result.visible=true;return result;}
  private coverProjectPosition(): RefreshButtonGeometry {const result=new RefreshButtonGeometry();if(!this.environment.coverScreen)return result;const g=this.cameraGeometry();const width=Math.max(96,Math.min(180,g.previewWidth-16));const height=Math.max(44,Math.min(56,g.previewHeight-16));const watermarkWidth=this.watermarkBound(2);const watermarkHeight=this.watermarkBound(3);let watermarkRect:AddressRefreshRect|null=null;if(watermarkWidth>0&&watermarkHeight>0){watermarkRect={x:this.watermarkBound(0),y:this.watermarkBound(1),width:watermarkWidth,height:watermarkHeight};}const placement=chooseAddressRefreshPlacement({previewWidth:g.previewWidth,previewHeight:g.previewHeight,buttonWidth:width,buttonHeight:height,watermarkRect:watermarkRect,safeSpacing:8});if(placement===null)return result;result.x=g.previewX+placement.x;result.y=g.previewY+placement.y;result.width=placement.width;result.height=placement.height;result.visible=true;return result;}
  private coverProjectEntryVisible(): boolean {return this.coverProjectPosition().visible;}
  private coverProjectEntryX(): number {return this.coverProjectPosition().x;}
  private coverProjectEntryY(): number {return this.coverProjectPosition().y;}
  private coverProjectEntryWidth(): number {return this.coverProjectPosition().width;}
  private coverProjectEntryHeight(): number {return this.coverProjectPosition().height;}
  private addressRefreshPlacementAvailable(): boolean {if(this.environment.coverScreen&&this.coverProjectEntryVisible())return false;return this.refreshButtonPosition().visible;}
  private addressRefreshFallbackVisible(): boolean {return this.shouldShowAddressRefresh()&&!this.addressRefreshPlacementAvailable();}
  private refreshButtonX(): number {return this.refreshButtonPosition().x;}
  private refreshButtonY(): number {return this.refreshButtonPosition().y;}
  private projectPanelBounds() {const geometry=this.cameraGeometry();return projectPanelGeometry({windowWidth:this.windowWidth,windowHeight:this.windowHeight,phonePortrait:!geometry.side&&!this.environment.coverScreen,wideSidebar:geometry.side&&!this.environment.coverScreen,smallOuterScreen:this.environment.coverScreen,sidebarWidth:geometry.side?geometry.toolbarWidth:0});}
  private projectSwitchPanelWidth(): number {return this.projectPanelBounds().width;}
  private projectSwitchPanelHeight(): number {return this.projectPanelBounds().height;}
  private projectSwitchPanelX(): number {return this.projectPanelBounds().x;}
  private projectSwitchPanelY(): number {return this.projectPanelBounds().y;}
  private openCoverToolsMenu(): void {const buttons: AlertDialogButtonOptions[]=[];if(this.addressRefreshFallbackVisible())buttons.push({value:'刷新地址',action:()=>this.requestAddressRefresh()});buttons.push({value:'切换工程',action:()=>this.openProjectSwitchPanel()});buttons.push({value:'取消',action:()=>{}});AlertDialog.show({title:'相机工具',message:'选择当前工程或刷新水印地址',buttons});}
  @Builder
  private coverProjectEntry() {if(this.environment.coverScreen&&this.coverProjectEntryVisible()){Button(){Text('当前工程\n'+(this.current().name||'请选择工程')).fontSize(Math.max(13,Math.min(20,14*this.fontScale))).maxLines(3).textAlign(TextAlign.Center).textOverflow({overflow:TextOverflow.Ellipsis}).lineHeight(Math.max(16,14*this.fontScale+3));}.width(this.coverProjectEntryWidth()).height(this.coverProjectEntryHeight()).padding(8).fontColor(SITE_TEXT).backgroundColor(SITE_CARD_RAISED).position({x:this.coverProjectEntryX(),y:this.coverProjectEntryY()}).id('camera-project-cover').accessibilityText('当前工程：'+(this.current().name||'请选择工程')).onClick(()=>this.openProjectSwitchPanel());}}
  @Builder
  private addressRefreshButton() {if(this.shouldShowAddressRefresh()&&this.addressRefreshPlacementAvailable()){Button(){Text(this.addressRefreshLabel()).fontSize(this.addressRefreshFontSize()).maxLines(2).textAlign(TextAlign.Center).lineHeight(this.addressRefreshFontSize()+4).textOverflow({overflow:TextOverflow.Ellipsis});}.width(this.refreshButtonWidth()).height(this.refreshButtonHeight()).fontColor(this.locationRefreshState==='refreshing'?SITE_MUTED:'#231A00').backgroundColor(this.locationRefreshState==='refreshing'?SITE_CARD_RAISED:SITE_ACCENT).enabled(this.locationRefreshState!=='refreshing').accessibilityText(this.locationRefreshState==='refreshing'?'地址刷新中':this.addressRefreshFailureMessage()).position({x:this.refreshButtonX(),y:this.refreshButtonY()}).id('camera-refresh-address').onAreaChange((oldValue,newValue)=>{const width=Number(newValue.width);const height=Number(newValue.height);const key=this.addressRefreshMeasureKey();if(width>0&&height>0&&(this.refreshButtonMeasuredKey!==key||this.refreshButtonMeasuredWidth!==width||this.refreshButtonMeasuredHeight!==height)){this.refreshButtonMeasuredKey=key;this.refreshButtonMeasuredWidth=width;this.refreshButtonMeasuredHeight=height;}}).onClick(()=>this.requestAddressRefresh());}}
  @Builder
  private projectSwitchOverlay() {if(this.projectSwitchOpen){Rect().width('100%').height('100%').fill('#99000000').onClick(()=>this.closeProjectSwitchPanel());ProjectSwitchPanel({projects:this.projectSwitchItems,currentProjectId:this.settings.currentProject,switchingId:this.projectSwitchingId,fontScale:this.fontScale,panelWidth:this.projectSwitchPanelWidth(),panelHeight:this.projectSwitchPanelHeight(),onSwitch:(p:Project)=>this.requestProjectSwitch(p),onManage:()=>{this.closeProjectSwitchPanel();this.task(async()=>this.navigate('projects'));},onCreate:()=>{this.closeProjectSwitchPanel();this.editProject(new Project());},onClose:()=>this.closeProjectSwitchPanel()}).position({x:this.projectSwitchPanelX(),y:this.projectSwitchPanelY()});}}
  private projectQuickRows(): Project[] {const currentId=this.settings.currentProject;const recent=this.settings.recentProjectIds||[];const rows=this.projects.filter((p:Project)=>p.id===currentId||(!p.archived&&recent.includes(p.id))).slice();rows.sort((a:Project,b:Project)=>{if(a.id===currentId&&b.id!==currentId)return -1;if(b.id===currentId&&a.id!==currentId)return 1;const ai=recent.indexOf(a.id),bi=recent.indexOf(b.id);if(ai<0&&bi<0)return a.id-b.id;if(ai<0)return 1;if(bi<0)return -1;return ai-bi;});return rows.slice(0,7);}
  private openProjectSwitchPanel(): void {if(this.projectOperationBlocked())return;this.projectSwitchItems=this.projectQuickRows();this.projectSwitchOpen=true;}
  private closeProjectSwitchPanel(): void {if(this.projectSwitchingId===0)this.projectSwitchOpen=false;}
  private projectOperationBlocked(): boolean {if(this.captureInFlight||this.recording||this.stoppingVideo||this.video.running){this.notify('正在拍摄或保存，完成后再操作工程');return true;}if(this.busy){this.notify('当前操作完成后再操作工程');return true;}return false;}
  private requestProjectSwitch(p: Project): void {if(this.projectSwitchingId!==0)return;if(!p.id){this.notify('工程不存在，请刷新列表');return;}if(this.projectOperationBlocked())return;this.projectSwitchingId=p.id;this.projectSwitchOpen=true;void this.task(async()=>{try{await this.selectProject(p);}finally{if(this.projectSwitchingId===p.id)this.projectSwitchingId=0;}});}
  private async selectProject(p: Project): Promise<void> {const live=(await store.projects('UPDATED',false,'ALL')).find((value:Project)=>value.id===p.id);if(!live)throw new Error('目标工程已删除，请刷新列表');const next=JSON.parse(JSON.stringify(this.settings)) as Settings;next.currentProject=live.id;next.projectSelectionCleared=false;next.recentProjectIds=[live.id].concat((next.recentProjectIds||[]).filter((id:number)=>id!==live.id)).slice(0,8);await this.persistSettingsSnapshot(next);this.settings=next;store.settings=next;await this.refresh();this.projectSwitchOpen=false;await this.navigate('camera');const status=live.archived?'（已归档，不能拍摄）':live.locked?'（已锁定拍摄）':'';this.notify(`已切换到${live.name}${status}`);}
  private requestProjectStatus(p: Project, kind: string): void {if(this.projectOperationBlocked())return;const desired=kind==='lock'?!p.locked:!p.archived;void this.task(async()=>{const live=(await store.projects('UPDATED',false,'ALL')).find((value:Project)=>value.id===p.id);if(!live)throw new Error('工程已删除，请刷新列表');const previous=kind==='lock'?live.locked:live.archived;if(previous===desired){await this.refresh();this.notify(kind==='lock'?(desired?'已经锁定拍摄':'已经解锁拍摄'):(desired?'工程已经归档':'工程已经恢复'));return;}if(kind==='lock')await store.updateProjectCaptureLock(live.id,desired);else await store.updateProjectArchive(live.id,desired);await this.refresh();this.notify(kind==='lock'?(desired?'已锁定拍摄':'已解锁拍摄'):(desired?'工程已归档':'工程已恢复'));this.offerProjectUndo(live.id,kind,previous);});}
  private clearProjectUndo(): void {this.projectUndoMessage='';this.projectUndoId=0;this.projectUndoKind='';this.projectUndoApplied=false;if(this.projectUndoTimer>=0){clearTimeout(this.projectUndoTimer);this.projectUndoTimer=-1;}}
  private offerProjectUndo(id: number, kind: string, previous: boolean): void {this.clearProjectUndo();this.projectUndoId=id;this.projectUndoKind=kind;this.projectUndoApplied=!previous;this.projectUndoMessage=kind==='lock'?(this.projectUndoApplied?'已锁定拍摄':'已解锁拍摄'):(this.projectUndoApplied?'工程已归档':'工程已恢复');this.projectUndoTimer=setTimeout(()=>this.clearProjectUndo(),6000);}
  private undoProjectStatus(): void {if(!this.projectUndoId)return;if(this.projectOperationBlocked())return;const id=this.projectUndoId,kind=this.projectUndoKind,applied=this.projectUndoApplied;this.clearProjectUndo();void this.task(async()=>{const live=(await store.projects('UPDATED',false,'ALL')).find((value:Project)=>value.id===id);if(!live)throw new Error('工程已删除，无法撤销');const actual=kind==='lock'?live.locked:live.archived;if(actual!==applied)throw new Error('工程状态已变化，未执行撤销');if(kind==='lock')await store.updateProjectCaptureLock(id,!applied);else await store.updateProjectArchive(id,!applied);await this.refresh();this.notify('工程状态已撤销');});}
  private async saveAlbumCopy(item: MediaItem, sourcePath: string=''): Promise<string> {
    const mediaId=item.id;
    const frozen=new MediaItem();
    frozen.id=mediaId;frozen.name=item.name;frozen.type=item.type;
    const path=sourcePath||item.edited||item.path;
    const uri=await this.files!.albumCopy(frozen,path);
    if(uri){
      await store.updateMediaAlbumUri(mediaId,uri);
      if(this.detail.id===mediaId)this.detail.albumUri=uri;
    }
    return uri;
  }
  private async navigate(page: string, forcedDirection?: string): Promise<void> {
    if(this.recording){this.notify('请先结束录像');return;}
    if(this.page===page)return;
    if(page==='permissions')this.permissionsReturn=this.page;
    if(page==='gallery'){this.galleryOrigin=this.page==='projects'?'projects':this.page==='camera'?'camera':this.galleryOrigin;}
    // Remember the source page so system back on the pages without an explicit
    // target returns to where the user actually came from. (The previous
    // expression compared this.page with page after the early return, so it
    // always evaluated to itself and pageOrigin never changed.)
    this.pageOrigin=this.page;
    if(this.page==='editor'&&page!=='editor'){for(const path of this.previewFiles)await this.files!.removePath(path);this.previewFiles=[];this.editTarget=null;}

    let dir: string;
    if(forcedDirection){
      dir=forcedDirection;
    }else if(page==='watermark'||page==='move'||page==='category'||this.page==='watermark'||this.page==='move'||this.page==='category'){
      dir='modal';
    }else{
      const depth: Record<string, number>={
        'camera':0,
        'projects':1,
        'gallery':1,
        'settings':1,
        'permissions':2,
        'guide':2,
        'export':2,
        'help':2,
        'projectEdit':2,
        'detail':2,
        'editor':3
      };
      const fromDepth=depth[this.page]!==undefined?depth[this.page]:1;
      const toDepth=depth[page]!==undefined?depth[page]:1;
      dir=toDepth>=fromDepth?'forward':'backward';
    }
    this.navDirection=dir;

    if(this.page==='camera'&&page!=='camera'){
      this.lifecycleToken++;
      this.cameraReady=false;
      this.syncFlashUi();
      await this.restoreWindowOrientation();
      await this.camera?.close();
      this.syncFlashUi();
      this.location.stop();
      if(this.timer>=0){clearInterval(this.timer);this.timer=-1;}
    }
    this.getUIContext().animateTo({ curve: curves.springMotion(0.35, 0.9) }, () => {
      this.page=page;
    });
    this.updateCameraSystemBars();
    this.message='';
    if(page==='camera'){
      if(!this.current().id){
        this.getUIContext().animateTo({ curve: curves.springMotion(0.35, 0.9) }, () => {
          this.page='projects';
        });
        this.notify('请先新建或选择一个工程');
      }else{
        void this.ensureCamera();
      }
    }
  }
  private async ensureCamera(): Promise<void> {
    if(this.openingCamera)return;
    if(!this.foreground||this.page!=='camera')return;
    if(!this.surface){
      try{
        const sid=this.controller.getXComponentSurfaceId();
        if(sid)this.surface=sid;
      }catch(e){}
    }
    if(this.surface){
      await this.openCamera();
    }
  }
  private async foregroundChanged(): Promise<void> {
    this.lifecycleToken++;
    if(!this.foreground){
      await this.shutterSound.suspend();
      await this.restoreWindowOrientation();
      if(this.recording){try{await this.stopVideo();}catch(e){this.notify((e as Error).message||'录像已安全收尾');}}
      this.cameraReady=false;
      this.syncFlashUi();
      await this.camera?.close();
      this.syncFlashUi();
      this.location.stop();
      // The 1 Hz watermark clock only matters while the camera page is visible.
      if(this.timer>=0){clearInterval(this.timer);this.timer=-1;}
      this.video.cancel();
    }else{
      this.shutterSound.resume();
      if(this.page==='permissions'){
        await this.permissions(false);
      }else if(this.page==='camera'){
        void this.ensureCamera();
      }
    }
  }
  private async permissions(request: boolean): Promise<void> {
    const manager=abilityAccessCtrl.createAtManager();const token=this.context.applicationInfo.accessTokenId;
    const list: Permissions[]=['ohos.permission.CAMERA','ohos.permission.APPROXIMATELY_LOCATION','ohos.permission.LOCATION','ohos.permission.MICROPHONE'];
    if(request){const missing: Permissions[]=[];for(const p of list)if(await manager.checkAccessToken(token,p)!==abilityAccessCtrl.GrantStatus.PERMISSION_GRANTED)missing.push(p);if(missing.length)await manager.requestPermissionsFromUser(this.context,missing);this.settings.permissionHandled=true;this.settings.systemPermissionsRequested=true;await this.persist();}
    this.photoPermission=await manager.checkAccessToken(token,'ohos.permission.CAMERA')===abilityAccessCtrl.GrantStatus.PERMISSION_GRANTED;
    this.microphonePermission=await manager.checkAccessToken(token,'ohos.permission.MICROPHONE')===abilityAccessCtrl.GrantStatus.PERMISSION_GRANTED;
    this.locationPermission=(await manager.checkAccessToken(token,'ohos.permission.LOCATION')===abilityAccessCtrl.GrantStatus.PERMISSION_GRANTED)||(await manager.checkAccessToken(token,'ohos.permission.APPROXIMATELY_LOCATION')===abilityAccessCtrl.GrantStatus.PERMISSION_GRANTED);
    this.locationNeedsSettings=!this.locationPermission&&this.settings.systemPermissionsRequested;
  }
  /** The only place allowed to open the system app-details page. */
  private async openAppSettings(): Promise<void> {
    // A permission must have been asked for at least once, or the system
    // settings page has no switch to show at all (Huawei review 3.1).
    if(!this.settings.systemPermissionsRequested){await this.permissions(true);}
    await this.context.startAbility({bundleName:'com.huawei.hmos.settings',abilityName:'com.huawei.hmos.settings.MainAbility',uri:'application_info_entry',parameters:{bundleName:'com.sitecam.app'}});
  }
  private permissionRows(): PermissionStatus[] {const rows:PermissionStatus[]=[];rows.push({id:'camera',title:'相机',description:'用于拍照和录像。不开启也能浏览、管理已有工程资料。',granted:this.photoPermission,needsSettings:!this.photoPermission&&this.settings.systemPermissionsRequested,optional:false,detail:this.photoPermission?'已允许':'未开启'} as PermissionStatus);rows.push({id:'location',title:'位置 · 可选',description:'用于水印中的现场地点。允许大致位置也可以。',granted:this.locationPermission,needsSettings:this.locationNeedsSettings,optional:true,detail:this.locationPermission?'已允许':'未开启'} as PermissionStatus);rows.push({id:'microphone',title:'麦克风 · 可选',description:'用于录制视频声音。不开启时可以录制无声视频。',granted:this.microphonePermission,needsSettings:!this.microphonePermission&&this.settings.systemPermissionsRequested,optional:true,detail:this.microphonePermission?'已允许':'未开启'} as PermissionStatus);return rows;}
  private updateCameraSystemBars():void {window.getLastWindow(this.context).then((win:window.Window)=>win.setWindowSystemBarEnable(this.page==='camera'?[]:['status','navigation'])).catch((e:Error)=>console.warn('SITECAM_SYSTEM_BARS '+e.message));}
  private syncFlashUi(): void {
    if(!this.camera||!this.cameraReady){this.flashMode='off';this.flash=false;this.flashAvailable=false;return;}
    this.flashMode=this.camera.flashMode;
    this.flash=this.flashMode!=='off';
    this.flashAvailable=this.camera.flashSupported===true&&(this.camera.flashOffSupported||this.camera.flashAutoSupported||this.camera.flashOnSupported||this.camera.flashTorchSupported);
  }
  private zoomOptions(): number[] {return this.camera&&this.camera.zoomPresets.length?this.camera.zoomPresets:[1];}
  private zoomPresetSelected(preset: number): boolean {return isNearestZoomPreset(this.zoom,preset,this.zoomOptions());}
  private chooseZoom(preset: number): void {if(!this.camera||!this.cameraReady)return;this.zoom=this.camera.zoom(preset);}
  private syncZoomUi(): void {if(this.camera)this.zoom=this.camera.syncZoom();}
  private flashShortPress(): void {if(this.flashLongPressConsumed){this.flashLongPressConsumed=false;return;}if(!this.camera||!this.flashAvailable)return;const before=this.camera.flashMode;const actual=this.camera.cycleFlash();this.syncFlashUi();if(actual===before)this.notify(this.camera.flashDiagnostics||'当前闪光模式暂不可用');}
  private flashTouchDown(): void {this.flashLongPressConsumed=false;}
  private flashLongPress(): void {if(!this.camera||!this.flashAvailable)return;this.flashLongPressConsumed=true;const before=this.camera.flashMode;const actual=this.camera.toggleTorch();this.syncFlashUi();if(actual===before)this.notify(this.camera.flashDiagnostics||'常亮模式暂不可用');}
  private async switchCameraLens(videoMode: boolean): Promise<void> {
    if(!this.camera)return;
    const token=this.lifecycleToken;
    this.cameraReady=false;
    this.syncFlashUi();
    try {
      await this.camera.switchLens(videoMode);
      if(!this.foreground||this.page!=='camera'||this.lifecycleToken!==token){this.syncFlashUi();return;}
      this.cameraReady=true;
      this.syncFlashUi();
      this.syncZoomUi();
      this.updateFrameRatio();
    } catch(e) {
      if(this.foreground&&this.page==='camera'&&this.lifecycleToken===token){
        this.cameraReady=this.camera.photoSupported||this.camera.videoSupported;
        this.syncFlashUi();
      }else this.syncFlashUi();
      throw e as Error;
    }
  }
  private async openCamera(): Promise<void> {
    if(this.openingCamera)return;
    if(!this.foreground||this.page!=='camera'||!this.surface)return;
    this.openingCamera=true;
    try {
      this.cameraReady=false;
      this.syncFlashUi();
      this.updateCameraSystemBars();
      await this.permissions(false);
      if(!this.photoPermission){
        this.cameraReady=false;
        this.syncFlashUi();
        this.message='请开启相机权限后拍摄';
        return;
      }
      await this.applyWindowOrientation(this.settings.orientation);
      await this.camera?.close();
      if(!this.camera)this.camera=new CameraService(this.context);
      const cameraLifecycle=this.lifecycleToken;
      this.camera.onZoomChanged=(value:number)=>{
        if(this.foreground&&this.page==='camera'&&this.lifecycleToken===cameraLifecycle&&this.cameraReady)this.zoom=value;
      };
      this.camera.onError=(msg: string)=>{
        if(!this.foreground||this.page!=='camera'||this.lifecycleToken!==cameraLifecycle)return;
        this.cameraReady=false;
        this.syncFlashUi();
        if(this.recording)this.stopVideo().catch((e: Error)=>this.notify(e.message));
        this.notify(msg);
      };
      const displayDegrees=this.effectiveDegrees();
      try{
        this.controller.setXComponentSurfaceRotation({lock:true});
      }catch(e){}
      await this.camera.open(this.surface,'',false,displayDegrees);
      try{
        console.info(`SITECAM_XCOMPONENT_CONFIG ${this.camera.previewWidth}x${this.camera.previewHeight} rotation=${JSON.stringify(this.controller.getXComponentSurfaceRotation())}`);
      }catch(e){
        console.warn('SITECAM_XCOMPONENT_CONFIG_FAILED '+(e as Error).message);
      }
      if(!this.foreground||this.page!=='camera'||this.lifecycleToken!==cameraLifecycle){
        await this.camera.close();
        this.cameraReady=false;
        this.syncFlashUi();
        return;
      }
      this.cameraReady=true;
      this.syncFlashUi();
      this.syncZoomUi();
      if(this.settings.shutterSoundEnabled)void this.shutterSound.warmUp(this.context);
      this.updateFrameRatio();
      this.message=this.camera.photoSupported?'':'当前设备仅支持预览，拍照能力不可用';
      this.location.start();
      this.snapshot=captureSnapshot(this.current(),this.settings,this.location.fix);
      if(this.timer>=0)clearInterval(this.timer);
      this.timer=setInterval(()=>{
        if(!this.recording&&!this.captureInFlight&&!this.videoStarting)this.snapshot=captureSnapshot(this.current(),this.settings,this.location.fix);
      },1000);
    } catch(e) {
      this.cameraReady=false;
      this.syncFlashUi();
      this.message='相机启动失败，请重试';
      console.warn('SITECAM_OPEN_CAMERA_FAILED '+(e as Error).message);
    } finally {
      this.openingCamera=false;
    }
  }
  private updateFrameRatio(): void {if(!this.camera||this.captureInFlight||this.recording||this.videoStarting)return;const displayDegrees=this.effectiveDegrees();try{this.camera.orientPreview(displayDegrees);}catch(e){}const ratio=this.camera.previewWidth/this.camera.previewHeight;const actual=this.camera.getActualPreviewRotation();this.sourcePreviewRatio=actual>=0&&actual%180!==0?1/ratio:ratio;this.frameRatio=this.captureMode==='photo'?(displayDegrees%180===0?3/4:4/3):this.sourcePreviewRatio;console.info(`SITECAM_FRAME_RATIO source=${this.camera.previewWidth}x${this.camera.previewHeight} displayDegrees=${displayDegrees} actualPreviewRotation=${actual} effective=${this.frameRatio}`);}
  private setPreviewSurfaceArea(width:number,height:number): void {if(width<=0||height<=0)return;try{const density=display.getDefaultDisplaySync().densityPixels||1;const pxWidth=width*density,pxHeight=height*density;this.controller.setXComponentSurfaceRect({offsetX:0,offsetY:0,surfaceWidth:pxWidth,surfaceHeight:pxHeight});console.info(`SITECAM_XCOMPONENT_RECT vp=${width}x${height} px=${pxWidth}x${pxHeight} density=${density}`);}catch(e){console.warn('SITECAM_XCOMPONENT_RECT_FAILED '+(e as Error).message);}}
  private frameWidth(): number {return this.cameraGeometry().previewWidth;}
  private surfaceWidth():number{return Math.max(this.frameWidth(),this.frameWidth()/this.frameRatio*this.sourcePreviewRatio);}
  private surfaceHeight():number{return this.surfaceWidth()/this.sourcePreviewRatio;}
  private watermarkBound(index: number): number {const parts=this.watermarkBounds.split(',');if(index<0||index>=parts.length)return 0;const value=Number(parts[index]);return Number.isFinite(value)?value:0;}
  private rotation(): number {if(this.settings.orientation==='PORTRAIT')return 90;if(this.settings.orientation==='LEFT')return 0;if(this.settings.orientation==='RIGHT')return 180;const rotation=display.getDefaultDisplaySync().rotation;return rotation===1?0:rotation===3?180:90;}
  private async captureProject(): Promise<Project> {const selectedId=this.settings.currentProject;if(!selectedId)throw new Error('请先选择工程包');const live=(await store.projects('UPDATED',false,'ALL')).find((value:Project)=>value.id===selectedId);if(!live){await this.refresh();throw new Error('当前工程已删除，请重新选择工程包');}if(this.settings.currentProject!==selectedId)throw new Error('工程选择已变化，请重试');if(live.locked||live.archived){await this.refresh();throw new Error('当前工程已锁定或归档，不能继续拍摄');}return live;}
  private async shoot(): Promise<void> {
    const p=await this.captureProject();
    if(!this.cameraReady||!this.camera?.photoSupported)throw new Error('相机尚未就绪');
    const s=captureSnapshot(p,this.settings,this.location.fix),quality=this.settings.photoQuality,degrees=this.effectiveDegrees();
    const soundToken=this.lifecycleToken;const orientationMode=this.settings.orientation;const captureSurface=this.surface;s.captureRatio=degrees%180===0?3/4:4/3;this.snapshot=s;this.captureInFlight=true;
    try {
      if(!await this.applyWindowOrientation(orientationMode,true))throw new Error('无法锁定拍摄方向');
      if(!this.captureContextValid(soundToken,captureSurface))return;
      if(!this.cameraReady||!this.camera?.photoSupported)throw new Error('相机尚未就绪');
      this.animateShutter(true);
      const soundEnabled=this.settings.shutterSoundEnabled;
      const bytes=await this.camera!.shoot(this.camera!.photoRotation(degrees),()=>{if(soundEnabled&&this.foreground&&this.page==='camera'&&this.lifecycleToken===soundToken)void this.shutterSound.play(this.context);});
      const item=await this.files!.capture(bytes,s,quality,0);item.projectId=p.id;
      try{await store.putMedia(item);}catch(e){await this.files!.remove(item);throw e as Error;}
      await this.refresh();this.animatePhotoSaved();this.notify('照片已保存到工程相册');
      if(this.settings.albumCopy){try{const uri=await this.saveAlbumCopy(item);if(uri)item.albumUri=uri;}catch(e){this.notify('照片已保存，另存系统相册未完成：'+(e as Error).message);}}
      if(this.quickIssueEnabled)this.openIssue(item);
    } finally {if(this.captureContextValid(soundToken,captureSurface))await this.restoreWindowOrientation(this.settings.orientation===orientationMode?orientationMode:this.settings.orientation);this.captureInFlight=false;if(this.captureContextValid(soundToken,captureSurface))this.updateFrameRatio();}
  }
  private openIssue(item: MediaItem): void {this.issueTarget=item;this.issueDialog=new CustomDialogController({customStyle:true,alignment:this.cameraGeometry().hover?DialogAlignment.Bottom:DialogAlignment.Center,offset:{dx:0,dy:-this.environment.bottom},builder:QuickIssueDialog({maxHeight:this.cameraGeometry().hover?this.cameraGeometry().controlsHeight:Math.max(120,this.windowHeight-40),existingTitle:item.issueTitle,existingSeverity:item.severity,existingStatus:item.issueStatus,existingNotes:item.issueNotes,onConfirm:(title:string,severity:string,status:string,notes:string)=>this.task(async()=>{item.issueTitle=title;item.severity=severity;item.issueStatus=status;item.issueNotes=notes;const issue=new Issue();issue.mediaId=item.id;issue.title=title;issue.description=notes;issue.severity=severity;issue.status=status;await store.putIssue(issue);await this.refresh();this.notify('问题记录已保存');}),onRemove:()=>this.task(async()=>{item.issueTitle='';item.issueNotes='';item.severity='NORMAL';item.issueStatus='PENDING';await store.clearIssue(item.id);await this.refresh();this.notify('问题已移除');})})});this.issueDialog!.open();}
  private async applyWindowOrientation(value: string, lockAuto: boolean=false): Promise<boolean> {const target=windowOrientationTarget(value,lockAuto);let orientation: window.Orientation;if(target==='PORTRAIT')orientation=window.Orientation.PORTRAIT;else if(target==='LANDSCAPE')orientation=window.Orientation.LANDSCAPE;else if(target==='LANDSCAPE_INVERTED')orientation=window.Orientation.LANDSCAPE_INVERTED;else if(target==='LOCKED')orientation=window.Orientation.LOCKED;else orientation=window.Orientation.AUTO_ROTATION_RESTRICTED;const sequence=++this.orientationSequence;const operation=this.orientationQueue.catch(()=>{}).then(async(): Promise<boolean>=>{if(sequence!==this.orientationSequence)return false;const win=await window.getLastWindow(this.context);if(sequence!==this.orientationSequence)return false;await win.setPreferredOrientation(orientation);return true;});this.orientationQueue=operation.then(()=>{},()=>{});try{return await operation;}catch(e){this.notify('无法切换屏幕方向');return false;}}
  private async restoreWindowOrientation(mode: string=this.settings.orientation): Promise<boolean> {return this.applyWindowOrientation(mode);}
  private captureContextValid(token: number, surface: string): boolean {return this.foreground&&this.page==='camera'&&this.lifecycleToken===token&&surface.length>0&&this.surface===surface;}
  private openOrientation(): void {this.orientationDialog=new CustomDialogController({customStyle:true,alignment:this.cameraGeometry().hover?DialogAlignment.Bottom:DialogAlignment.Center,offset:{dx:0,dy:-this.environment.bottom},builder:OrientationDialog({maxHeight:this.cameraGeometry().hover?this.cameraGeometry().controlsHeight:Math.max(120,this.windowHeight-40),selected:this.settings.orientation,onSelect:(value:string)=>{void this.task(async()=>{this.settings.orientation=value;await this.applyWindowOrientation(value);this.updateFrameRatio();await this.persist();});}})});this.orientationDialog!.open();}
  private async toggleVideo(): Promise<void> {
    if(this.recording){await this.stopVideo();return;}
    try {
      this.videoStarting=true;
      const p=await this.captureProject();
      if(!this.cameraReady||!this.camera?.videoSupported)throw new Error('相机尚未就绪');
      const s=captureSnapshot(p,this.settings,this.location.fix),orientationMode=this.settings.orientation,operationToken=this.lifecycleToken,captureSurface=this.surface;
      const item=new MediaItem();item.type='VIDEO';item.projectId=p.id;item.timestamp=s.timestamp;item.snapshot=JSON.stringify(s);item.status='PROCESSING';item.name=`${s.timestamp}_source.mp4`;item.source=this.files!.path(item.name);item.path=item.source;this.snapshot=s;
      let mediaStored=false;
      try {
        this.cameraReady=false;this.syncFlashUi();
        if(!await this.applyWindowOrientation(orientationMode,true))throw new Error('无法锁定录像方向');
        if(!this.captureContextValid(operationToken,captureSurface))return;
        if(!this.camera?.videoSupported)throw new Error('相机尚未就绪');
        await this.camera!.open(captureSurface,item.source,this.microphonePermission,this.effectiveDegrees());
        this.cameraReady=true;this.syncFlashUi();this.syncZoomUi();this.updateFrameRatio();
        if(!this.captureContextValid(operationToken,captureSurface))throw new Error('录像已取消');
        await store.putMedia(item);mediaStored=true;
        if(!this.captureContextValid(operationToken,captureSurface))throw new Error('录像已取消');
        await this.camera!.startRecording();
        if(!this.captureContextValid(operationToken,captureSurface))throw new Error('录像已取消');
      } catch(e) {
        if(mediaStored){item.status='FAILED';try{await store.putMedia(item);}catch(storeError){console.warn('SITECAM_VIDEO_FAILURE_RECORD '+String(storeError));}}
        try{await this.camera?.close();}catch(closeError){console.warn('SITECAM_VIDEO_CLOSE '+String(closeError));}
        this.cameraReady=false;this.syncFlashUi();this.recording=false;this.recordingItem=null;
        if(this.captureContextValid(operationToken,captureSurface)){await this.restoreWindowOrientation(this.settings.orientation===orientationMode?orientationMode:this.settings.orientation);try{await this.openCamera();}catch(re){this.message='相机恢复失败，请重新进入相机页';}}
        if(this.captureContextValid(operationToken,captureSurface))throw e as Error;
        return;
      }
      this.recordingItem=item;this.recording=true;this.notify(this.microphonePermission?'正在录像':'正在录制无声视频');
    } finally {
      this.videoStarting=false;
    }
  }
  private async stopVideo(): Promise<void> {const item=this.recordingItem;if(!item||this.stoppingVideo)return;this.stoppingVideo=true;try{let recordingError='';try{await this.camera!.stopRecording();}catch(e){recordingError=(e as Error).message;}await this.camera!.close();this.cameraReady=false;this.syncFlashUi();await this.applyWindowOrientation(this.settings.orientation);this.recording=false;this.recordingItem=null;item.duration=Date.now()-item.timestamp;item.status='FAILED';await store.putMedia(item);await this.refresh();if(recordingError){if(this.foreground){try{await this.openCamera();}catch(e){this.notify('相机恢复失败，请重新进入相机页');}}throw new Error('录像已中断，保留已有原片：'+recordingError);}if(this.foreground){this.message='正在写入视频水印';try{await this.video.process(item,this.files!,v=>this.videoProgress=v);await this.refresh();if(this.settings.albumCopy){try{const uri=await this.saveAlbumCopy(item);if(uri)item.albumUri=uri;}catch(e){this.notify('视频已保存，但系统相册副本失败：'+(e as Error).message);}}}finally{try{await this.openCamera();}catch(e){this.notify('相机恢复失败，请重新进入相机页');}}this.notify('视频已保存');}else{this.notify('原片已保存，可在相册重试水印处理');}}finally{this.stoppingVideo=false;}}
  private projectRows(): Project[] {let rows=this.projects.filter(p=>(this.projectScope===2||p.archived=== (this.projectScope===1))&&(!this.query||(p.name+' '+p.route).toLowerCase().includes(this.query.toLowerCase())));rows=rows.sort((a,b)=>{let value=this.sort===0?a.last-b.last:this.sort===1?a.created-b.created:this.sort===2?a.updated-b.updated:this.sort===3?a.name.localeCompare(b.name,'zh'):a.route.localeCompare(b.route,'zh');return this.ascending?value:-value;});return rows;}
  private years(): number[] {const values: number[]=[];for(const m of this.items){const year=new Date(m.timestamp).getFullYear();if(!values.includes(year))values.push(year);}return values.sort((a,b)=>b-a);}
  private pickDate(start: boolean): void {DatePickerDialog.show({selected:new Date((start?this.fromDate:this.toDate)||Date.now()),onAccept:(value: DatePickerResult)=>{const text=dateText(new Date(value.year!,value.month!,value.day!).getTime());if(start)this.fromDate=text;else this.toDate=text;}});}
  private pickDateRange(): void { this.dateRangeDialog=new CustomDialogController({customStyle:true,builder:DateRangeDialog({initialStart:this.fromDate,initialEnd:this.toDate,onApply:(start:string,end:string)=>{this.mediaSelection=[];this.fromDate=start;this.toDate=end;},onCancel:()=>{}})});this.dateRangeDialog!.open(); }
  private batchIssueDialog(): void { const chosen=this.items.filter((m:MediaItem)=>this.mediaSelection.includes(m.id)); if(!chosen.length)return; AlertDialog.show({title:'批量记录问题',message:`将更新 ${chosen.length} 项`,primaryButton:{value:'取消',action:()=>{}},buttons:[{value:'待处理',action:()=>this.task(async()=>{for(const m of chosen){const issue=new Issue();issue.mediaId=m.id;issue.title=m.issueTitle||'现场问题';issue.description=m.issueNotes;issue.severity=m.severity;issue.status='PENDING';await store.putIssue(issue);}await this.refresh();})},{value:'处理中',action:()=>this.task(async()=>{for(const m of chosen){const issue=new Issue();issue.mediaId=m.id;issue.title=m.issueTitle||'现场问题';issue.description=m.issueNotes;issue.severity=m.severity;issue.status='IN_PROGRESS';await store.putIssue(issue);}await this.refresh();})},{value:'已完成',action:()=>this.task(async()=>{for(const m of chosen){const issue=new Issue();issue.mediaId=m.id;issue.title=m.issueTitle||'现场问题';issue.description=m.issueNotes;issue.severity=m.severity;issue.status='COMPLETED';await store.putIssue(issue);}await this.refresh();})}]}); }
  private batchMediaMoreDialog(): void { AlertDialog.show({title:'更多操作',message:`已选 ${this.mediaSelection.length} 项`,primaryButton:{value:'取消',action:()=>{}},buttons:[{value:'移入工程',action:()=>this.openMoveDialog()},{value:'分享',action:()=>this.task(async()=>this.files!.share(this.items.filter((m:MediaItem)=>this.mediaSelection.includes(m.id)).map((m:MediaItem)=>m.edited||m.path),false,'gallery-share-anchor'))},{value:'删除',fontColor:'#F44336',action:()=>this.confirmDelete(false)}]}); }
  private galleryRows(): MediaItem[] {return this.items.filter(m=>(!this.filterProject||m.projectId===this.filterProject)&&(!this.filterYear||new Date(m.timestamp).getFullYear()===this.filterYear)&&(!this.filterMonth||new Date(m.timestamp).getMonth()+1===this.filterMonth)&&(!this.fromDate||dateText(m.timestamp)>=this.fromDate)&&(!this.toDate||dateText(m.timestamp)<=this.toDate)&&(!this.category||this.projects.find(p=>p.id===m.projectId)?.category===this.category)&&(!this.issueFilter||!!m.issueTitle&&(this.issueFilter===1||m.issueStatus===['','','PENDING','IN_PROGRESS','COMPLETED'][this.issueFilter]))&&(!this.severityFilter||!!m.issueTitle&&m.severity===['','NORMAL','IMPORTANT','CRITICAL'][this.severityFilter]));}
  private galleryDates(): string[] {const dates: string[]=[];for(const item of this.galleryRows()){const day=dateText(item.timestamp);if(!dates.includes(day))dates.push(day);}return dates.sort((a,b)=>b.localeCompare(a));}
  private galleryRowsForDate(day: string): MediaItem[] {return this.galleryRows().filter(item=>dateText(item.timestamp)===day);}
  private toggleProject(id: number): void {if(!this.batchMode)this.batchMode=true;this.selected=this.selected.includes(id)?this.selected.filter(v=>v!==id):this.selected.concat(id);}
  private setProjectSelected(id: number,selected: boolean): void {if(!this.batchMode)this.batchMode=true;const current=this.selected.includes(id);if(current===selected)return;this.selected=selected?this.selected.concat(id):this.selected.filter(v=>v!==id);}
  private async batchMoreDialog(): Promise<void> { this.actionDialog=new CustomDialogController({customStyle:true,builder:ProjectBatchActionDialog({selectedCount:this.selected.length,onAction:(action:string)=>{if(action==='category')this.openCategoryDialog();else if(action==='delete')this.confirmDelete(true);else this.task(async()=>this.projectBatch(action));},onCancel:()=>{}})});this.actionDialog!.open(); }
  private projectSortDialog(): void {AlertDialog.show({title:'排序方式',message:'选择工程列表排序',primaryButton:{value:'取消',action:()=>{}},buttons:[{value:'最近拍摄',action:()=>{this.sort=0;}},{value:'创建时间',action:()=>{this.sort=1;}},{value:'最近修改',action:()=>{this.sort=2;}},{value:'名称',action:()=>{this.sort=3;}},{value:'线路',action:()=>{this.sort=4;}}]});}
  private toggleMedia(id: number): void {this.mediaSelection=this.mediaSelection.includes(id)?this.mediaSelection.filter(v=>v!==id):this.mediaSelection.concat(id);}
  private deleteDetail(): void {AlertDialog.show({title:'删除资料？',message:'将删除工程相册中的原件和编辑成品，系统相册副本不受影响。',primaryButton:{value:'取消',action:()=>{}},secondaryButton:{value:'删除',fontColor:'#F44336',action:()=>{this.task(async()=>{await this.files!.remove(this.detail);await store.deleteMedia(this.detail.id);this.detail=new MediaItem();this.galleryStack.clear();await this.refresh();await this.navigate('gallery','backward');this.notify('资料已删除');});}}});}
  private editProject(p: Project): void {this.draft=JSON.parse(JSON.stringify(p)) as Project;this.editorDialog=new CustomDialogController({customStyle:true,builder:ProjectEditorDialog({project:this.draft,categories:this.projectCategories.length?this.projectCategories:['其他'],onConfirm:(value: Project)=>this.task(async()=>{this.draft=value;await this.saveProject();}),onCancel:()=>{}})});this.editorDialog!.open();}
  private async saveProject(): Promise<void> {if(!this.draft.name.trim())throw new Error('请填写工程名称');const creating=!this.draft.id;this.draft.name=this.draft.name.trim();await store.putProject(this.draft);await this.refresh();if(creating){await this.selectProject(this.draft);return;}await this.navigate('projects','backward');}
  private async projectBatch(action: string): Promise<void> {if((action==='lock'||action==='unlock'||action==='archive'||action==='restore')&&(this.captureInFlight||this.recording||this.stoppingVideo||this.video.running))throw new Error('正在拍摄或保存，完成后再操作工程');const liveProjects=await store.projects('UPDATED',false,'ALL');for(const id of this.selected){const p=liveProjects.find((value:Project)=>value.id===id);if(!p)continue;if(action==='lock')await store.updateProjectCaptureLock(id,true);else if(action==='unlock')await store.updateProjectCaptureLock(id,false);else if(action==='archive')await store.updateProjectArchive(id,true);else if(action==='restore')await store.updateProjectArchive(id,false);else if(action==='category'){p.category=this.batchProjectCategory||'未分类';await store.putProject(p);}}this.selected=[];this.batchMode=false;await this.refresh();}
  private openProjectMenu(p: Project): void {AlertDialog.show({title:p.name,message:'选择一项工程操作',buttons:[{value:'取消',action:()=>{}},{value:p.id===this.settings.currentProject?'返回当前工程':'切换到此工程',action:()=>this.requestProjectSwitch(p)},{value:'查看资料',action:()=>this.task(async()=>{this.filterProject=p.id;await this.navigate('gallery');})},{value:p.locked?'解锁拍摄':'锁定拍摄',action:()=>this.requestProjectStatus(p,'lock')},{value:p.archived?'恢复工程':'归档工程',action:()=>this.requestProjectStatus(p,'archive')},{value:'删除工程',fontColor:'#F44336',action:()=>{this.selected=[p.id];this.confirmDelete(true);}}]});}
  private openCategoryDialog(): void {this.actionDialog=new CustomDialogController({customStyle:true,builder:ProjectCategoryDialog({categories:this.projectCategories.length?this.projectCategories:['其他'],onSelect:(name:string)=>{this.batchProjectCategory=name;this.task(async()=>this.projectBatch('category'));},onCancel:()=>{}})});this.actionDialog!.open();}
  private projectPickerDialog(): void {const buttons: AlertDialogButtonOptions[]=[{value:'全部工程',action:()=>{this.filterProject=0;this.task(async()=>this.navigate('gallery','forward'));}}];for(const p of this.projects)buttons.push({value:p.name,action:()=>{this.mediaSelection=[];this.filterProject=p.id;this.task(async()=>this.navigate('gallery','forward'));}});AlertDialog.show({title:'选择工程相册',message:'选择要查看的工程',primaryButton:{value:'取消',action:()=>{}},buttons});}
  private openMoveDialog(): void {this.actionDialog=new CustomDialogController({customStyle:true,builder:ProjectMoveDialog({projects:this.projects,onSelect:(projectId:number)=>this.task(async()=>{await store.moveMedia(this.mediaSelection,projectId);this.mediaSelection=[];await this.refresh();await this.navigate('gallery','backward');}),onCancel:()=>{}})});this.actionDialog!.open();}
  private confirmDelete(projects: boolean): void {AlertDialog.show({title:projects?'删除所选工程？':'删除所选资料？',message:'将删除应用内原件和编辑成品，系统相册副本不受影响。此操作无法撤销。',primaryButton:{value:'取消',action:()=>{}},secondaryButton:{value:'删除',fontColor:'#CF3A31',action:()=>{this.task(async()=>{if(projects){for(const id of this.selected){for(const m of this.items.filter(v=>v.projectId===id))await this.files!.remove(m);await store.deleteProject(id);}this.selected=[];}else{for(const id of this.mediaSelection){const m=this.items.find(v=>v.id===id);if(m){await this.files!.remove(m);await store.deleteMedia(id);}}this.mediaSelection=[];}await this.refresh();});}}});}
  private exportStart(ids: number[],mediaIds: number[]=[]): void {this.exportIds=ids.slice();this.exportMediaIds=mediaIds.slice();this.exportMediaProjectIds=this.exportMediaIds.map(id=>this.items.find((m:MediaItem)=>m.id===id)?.projectId||0);this.actionDialog=new CustomDialogController({customStyle:true,builder:ExportChoiceDialog({onZip:(quality:number)=>{this.exportFolder=false;this.exportQuality=quality;this.task(async()=>this.exportNow());},onFolder:(quality:number)=>{this.exportFolder=true;this.exportQuality=quality;this.task(async()=>this.exportNow());},onCancel:()=>{}})});this.actionDialog!.open();}
  private async exportNow(): Promise<void> {const selectedMediaIds=this.exportMediaIds.slice();const selectedProjectIds=this.exportIds.slice();const liveItems=await store.media();const items=liveItems.filter(m=>selectedMediaIds.length?selectedMediaIds.includes(m.id):selectedProjectIds.includes(m.projectId));if(selectedMediaIds.length!==0){if(items.length!==selectedMediaIds.length)throw new Error('选中的资料已变化，请刷新后重试');for(let i=0;i<selectedMediaIds.length;i++){const live=items.find((m:MediaItem)=>m.id===selectedMediaIds[i]);if(!live||this.exportMediaProjectIds[i]!==live.projectId)throw new Error('选中的资料已移动，请刷新后重试');}}const projectIds=selectedMediaIds.length?Array.from(new Set(items.map(m=>m.projectId))):selectedProjectIds;const liveProjects=await store.projects('UPDATED',false,'ALL');const projects=liveProjects.filter(p=>projectIds.includes(p.id));if(!projects.length)throw new Error('请先选择工程');const path=await this.exporter!.run(projects,items,this.exportFolder,this.exportQuality,v=>this.message=v);this.notify(this.exporter!.failures?`导出完成，${this.exporter!.failures} 项未成功，请查看报告`:'导出完成：'+path);}
  private openDetail(m: MediaItem): void {if(this.detail.id!==m.id){this.detail=JSON.parse(JSON.stringify(m)) as MediaItem;this.showEdited=true;this.viewScale=1;this.viewX=0;this.viewY=0;}if(!this.galleryStack.getAllPathName().includes('media')){this.galleryStack.clear();this.galleryStack.pushPath({name:'media'});}}
  private openStyles(from: string): void {this.styleDraft=JSON.parse(JSON.stringify(this.settings.watermark)) as Watermark;this.styleReturn=from;this.draftTemplateId=this.settings.templateId||0;this.templateName=this.templates.find(t=>t.id===this.settings.templateId)?.name||'我的模板';this.task(async()=>this.navigate('watermark','modal'));}
  private styleSnapshot(id: string): Snapshot {const s=captureSnapshot(this.current().id?this.current():this.example(),this.settings,this.location.fix);s.watermark=JSON.parse(JSON.stringify(this.styleDraft)) as Watermark;s.watermark.style=id;return s;}
  private example(): Project {const p=new Project();p.name='滨江路雨污分流改造';p.address='北段雨水管施工现场';return p;}
  private async saveWatermark(): Promise<void> {this.settings.watermark=JSON.parse(JSON.stringify(this.styleDraft)) as Watermark;await this.persist();const t=new WatermarkTemplate();t.id=this.draftTemplateId;t.name=this.templateName||'我的模板';t.config=JSON.stringify(this.settings.watermark);await store.putTemplate(t);this.settings.templateId=t.id;await this.persist();await this.refresh();await this.navigate(this.styleReturn,'modal');}
  private persistQuickWatermark(): void {this.settings.watermark=JSON.parse(JSON.stringify(this.styleDraft)) as Watermark;store.settings=this.settings;store.saveCurrentWatermark().catch((e:Error)=>this.notify(e.message));}
  private async persistWatermarkBeforeLeave(): Promise<void> {this.settings.watermark=JSON.parse(JSON.stringify(this.styleDraft)) as Watermark;store.settings=this.settings;await store.saveCurrentWatermark();}
  private async beginEdit(): Promise<void> {const target=JSON.parse(JSON.stringify(this.detail)) as MediaItem;const targetId=target.id;if(!targetId)return;this.editTarget=target;this.recipe=new EditRecipe();const annotations=await store.annotations(targetId);if(this.detail.id!==targetId){this.editTarget=null;return;}if(annotations.length&&annotations[0].vectorDataJson){try{this.recipe=JSON.parse(annotations[0].vectorDataJson) as EditRecipe;}catch(e){this.recipe=new EditRecipe();}}this.history=[];this.redoHistory=[];this.previewFiles=[];this.editPreview=target.path;this.editRatio=target.width/Math.max(1,target.height);this.cropMode=false;await this.navigate('editor','forward');await this.renderEdit();}
  private openCropDialog(): void {this.cropDialog=new CustomDialogController({customStyle:true,builder:CropDialog({previewPath:this.editPreview,imageRatio:this.editRatio,initialLeft:this.cropLeft,initialTop:this.cropTop,initialRight:this.cropRight,initialBottom:this.cropBottom,onApply:(left:number,top:number,right:number,bottom:number)=>{this.cropLeft=left;this.cropTop=top;this.cropRight=right;this.cropBottom=bottom;this.pushEdit();const step=new EditStep('crop');step.mark.x=left;step.mark.y=top;step.mark.endX=right;step.mark.endY=bottom;this.recipe.steps=this.recipe.steps.concat(step);this.task(async()=>this.renderEdit());}})});this.cropDialog!.open();}
  private pushEdit(): void {this.history=this.history.concat(JSON.stringify(this.recipe));this.redoHistory=[];}
  private async renderEdit(): Promise<void> {const target=this.editTarget;if(!target||!target.id||this.detail.id!==target.id)return;const targetId=target.id;const revision=++this.editRevision;const path=await this.files!.edit(JSON.parse(JSON.stringify(target)) as MediaItem,JSON.parse(JSON.stringify(this.recipe)) as EditRecipe,0);if(revision!==this.editRevision||this.detail.id!==targetId||!this.editTarget||this.editTarget.id!==targetId){await this.files!.removePath(path);return;}this.previewFiles.push(path);const source=image.createImageSource(path);try{const info=await source.getImageInfo();if(revision===this.editRevision&&this.detail.id===targetId&&this.editTarget?.id===targetId){this.editPreview=path;this.editRatio=info.size.width/info.size.height;}}finally{await source.release();}}
  private async undo(redo: boolean): Promise<void> {if(redo){if(!this.redoHistory.length)return;this.history=this.history.concat(JSON.stringify(this.recipe));this.recipe=JSON.parse(this.redoHistory[this.redoHistory.length-1]) as EditRecipe;this.redoHistory=this.redoHistory.slice(0,-1);}else{if(!this.history.length)return;this.redoHistory=this.redoHistory.concat(JSON.stringify(this.recipe));this.recipe=JSON.parse(this.history[this.history.length-1]) as EditRecipe;this.history=this.history.slice(0,-1);}await this.renderEdit();}
  private async saveEdit(): Promise<void> {const editTarget=this.editTarget;if(!editTarget||!editTarget.id||this.detail.id!==editTarget.id)throw new Error('资料已切换，请重新进入编辑');const target=JSON.parse(JSON.stringify(editTarget)) as MediaItem;const targetId=target.id;const recipe=JSON.parse(JSON.stringify(this.recipe)) as EditRecipe;const old=target.edited;let next='';let committed=false;try{next=await this.files!.edit(target,recipe,this.settings.photoQuality);const annotation=new Annotation();annotation.mediaId=targetId;annotation.annotatedContentUri=next;annotation.vectorDataJson=JSON.stringify(recipe);await store.putAnnotation(annotation);committed=true;if(this.detail.id===targetId)this.detail.edited=next;if(this.settings.albumCopy){try{const uri=await this.saveAlbumCopy(target,next);if(uri&&this.detail.id===targetId)this.detail.albumUri=uri;}catch(e){this.notify('成品已保存，但系统相册副本失败：'+(e as Error).message);}}if(old){try{await this.files!.removePath(old);}catch(e){console.warn('SITECAM_OLD_EDIT_CLEANUP '+(e as Error).message);}}for(const path of this.previewFiles)await this.files!.removePath(path);this.previewFiles=[];await this.refresh();this.editTarget=null;if(this.page==='editor')await this.navigate('detail','backward');}catch(e){if(!committed&&next)await this.files!.removePath(next);throw e as Error;}}
  private async editorTouch(e: TouchEvent): Promise<void> {
    if(this.busy)return;
    if(e.type===TouchType.Down&&e.touches.length){this.touchX=Math.max(0,Math.min(1,e.touches[0].x/this.editorWidth));this.touchY=Math.max(0,Math.min(1,e.touches[0].y/this.editorHeight));this.strokePoints=[this.touchX,this.touchY];}
    else if(e.type===TouchType.Move&&e.touches.length&&(this.markTool==='freehand'||this.markTool==='mosaic')){const x=Math.max(0,Math.min(1,e.touches[0].x/this.editorWidth));const y=Math.max(0,Math.min(1,e.touches[0].y/this.editorHeight));this.strokePoints=this.strokePoints.concat([x,y]);}
    else if(e.type===TouchType.Up&&e.changedTouches.length){const x=Math.max(0,Math.min(1,e.changedTouches[0].x/this.editorWidth)),y=Math.max(0,Math.min(1,e.changedTouches[0].y/this.editorHeight));if(this.cropMode){const w=Math.abs(x-this.touchX),h=Math.abs(y-this.touchY);if(w<.03||h<.03)return;this.pushEdit();const step=new EditStep('crop');step.mark.x=this.touchX;step.mark.y=this.touchY;step.mark.endX=x;step.mark.endY=y;this.recipe.steps=this.recipe.steps.concat(step);this.cropMode=false;}else{this.pushEdit();const mark=new Mark();mark.kind=this.markTool;mark.color=this.markColor;mark.x=this.touchX;mark.y=this.touchY;mark.endX=x;mark.endY=y;mark.text=this.markText;mark.points=(this.markTool==='freehand'||this.markTool==='mosaic')?this.strokePoints.concat([x,y]):[];if(mark.kind==='text'&&!mark.text){this.notify('请先填写标注文字');return;}const step=new EditStep(mark.kind);step.mark=mark;this.recipe.steps=this.recipe.steps.concat(step);}this.strokePoints=[];await this.task(async()=>this.renderEdit());}
  }

  @Builder
  header(title: string,back: string='') {Row({space:12}) {if(back){Button(){Row({space:4}){Image($r('app.media.ic_arrow_back')).width(22).height(22);Text('返回').fontSize(15).fontColor(SITE_TEXT);}}.backgroundColor(SITE_CARD_RAISED).onClick(()=>this.task(async()=>this.navigate(back)));}
Column({space:2}){Text(title).fontSize(22).fontWeight(FontWeight.Bold).fontColor(SITE_TEXT);Text('工程水印相机 · SiteCam').fontSize(11).fontColor(SITE_MUTED);}.alignItems(HorizontalAlign.Start);Blank();if(this.busy){Text('处理中…').fontSize(12).fontColor(SITE_MUTED);}}.width('100%').padding({left:16,right:16,top:14,bottom:12}).backgroundColor(SITE_BG);}
  @Builder
  field(label: string,value: string,change: (value: string)=>void) {Column({space:5}){Text(label).fontSize(13).fontColor($r('app.color.text_secondary')).width('100%');TextInput({text:value,placeholder:label}).fontColor('#FFFFFF').placeholderColor('#B0BEC5').backgroundColor('#303030').caretColor('#FFB300').borderRadius(10).onChange(change).backgroundColor('#EAF0F3').fontColor('#173044');}.width('100%');}
  @Builder
  projectCardNew(p: Project) {ProjectCardView({project:p,current:p.id===this.settings.currentProject,checked:this.selected.includes(p.id),batchMode:this.batchMode,onOpen:()=>this.batchMode?this.toggleProject(p.id):this.task(async()=>{this.filterProject=p.id;await this.navigate('gallery');}),onEdit:()=>this.editProject(p),onExport:()=>this.exportStart([p.id]),onChecked:()=>this.toggleProject(p.id),onMenu:()=>this.openProjectMenu(p)});}
  @Builder
  projectCard(p: Project) {Column({space:7}){Row({space:8}){Checkbox({name:p.name}).accessibilityText('选择工程 '+p.name).select(this.selected.includes(p.id)).onChange(()=>this.toggleProject(p.id)).width(20).height(20);Text(p.name).fontWeight(FontWeight.Bold).fontSize(18).fontColor(SITE_TEXT).flexShrink(1).maxLines(this.fontScale>1.2?100:1).textOverflow({overflow:TextOverflow.Ellipsis});if(p.id===this.settings.currentProject){Text('当前').fontSize(10).fontColor('#231A00').backgroundColor(SITE_ACCENT).padding(4).borderRadius(4);}Blank();Text(p.locked?'已锁定':'').fontSize(11).fontColor(SITE_ACCENT);}.width('100%');
      Row(){Column({space:5}){Text(p.route||'未填写线路').fontSize(13).fontColor(SITE_MUTED).maxLines(this.fontScale>1.2?100:1);Text(p.count?`${dateText(p.first)} 至 ${dateText(p.last)}`:'尚无拍摄资料').fontSize(11).fontColor(SITE_MUTED);Row({space:10}){Text(`照片 ${p.photoCount} · 视频 ${p.videoCount} · 问题 ${p.issueCount}`).fontSize(11).fontColor(SITE_TEXT).fontWeight(FontWeight.Bold);Text(p.category).fontColor(SITE_MUTED);};}.alignItems(HorizontalAlign.Start).layoutWeight(1).onClick(()=>this.task(async()=>{this.filterProject=p.id;await this.navigate('gallery');}));Column({space:5}){Button('编辑').height(this.fontScale>1.2?40:26).fontSize(12).backgroundColor(SITE_CARD_RAISED).fontColor(SITE_TEXT).onClick(()=>this.editProject(p));Button('导出').height(this.fontScale>1.2?40:26).fontSize(12).backgroundColor(SITE_ACCENT).fontColor('#231A00').onClick(()=>this.exportStart([p.id]));}}.width('100%');}.padding(10).width('100%').constraintSize({minHeight:112}).backgroundColor(SITE_CARD).borderRadius(12).border({width:p.id===this.settings.currentProject?2:0,color:SITE_ACCENT}).gesture(LongPressGesture({repeat:false}).onAction(()=>this.toggleProject(p.id)));}
  @Builder
  projectsRootPage() {ProjectsPageView({projects:this.projectRows(),windowWidth:this.windowWidth,windowHeight:this.windowHeight,fontScale:this.fontScale,search:this.query,archiveFilter:this.projectScope===1?'ARCHIVED':this.projectScope===2?'ALL':'ACTIVE',sortLabel:PROJECT_SORT_LABELS[this.sort],ascending:this.ascending,batchMode:this.batchMode,selectedIds:this.selected,currentProjectId:this.settings.currentProject,switchingId:this.projectSwitchingId,onBack:()=>this.task(async()=>this.navigate('camera')),onCreate:()=>this.editProject(new Project()),onSearch:(v:string)=>this.query=v,onArchive:(v:string)=>{this.projectScope=v==='ARCHIVED'?1:v==='ALL'?2:0;},onSort:()=>this.projectSortDialog(),onAscending:()=>this.ascending=!this.ascending,onBatch:()=>this.batchMode=!this.batchMode,onOpen:(p:Project)=>this.task(async()=>{this.filterProject=p.id;await this.navigate('gallery');}),onView:(p:Project)=>this.task(async()=>{this.filterProject=p.id;await this.navigate('gallery');}),onSwitch:(p:Project)=>this.requestProjectSwitch(p),onEdit:(p:Project)=>this.editProject(p),onExport:(p:Project)=>this.exportStart([p.id]),onToggleLock:(p:Project)=>this.requestProjectStatus(p,'lock'),onToggleArchive:(p:Project)=>this.requestProjectStatus(p,'archive'),onMore:(p:Project)=>this.openProjectMenu(p),onToggle:(p:Project,selected:boolean)=>this.setProjectSelected(p.id,selected),onBatchSelectAll:()=>{this.selected=this.projectRows().map((p:Project)=>p.id);},onBatchClear:()=>{this.selected=[];},onBatchExport:()=>this.exportStart(this.selected),onBatchMore:()=>this.batchMoreDialog()});}

  @Builder
  projectEditPage() {this.header(this.draft.id?'编辑工程':'新建工程','projects');Scroll(){Column({space:14}){this.field('工程名称',this.draft.name,v=>this.draft.name=v);this.field('线路（可选）',this.draft.route,v=>this.draft.route=v);this.field('类别',this.draft.category,v=>this.draft.category=v);this.field('地点',this.draft.address,v=>this.draft.address=v);this.field('备注',this.draft.notes,v=>this.draft.notes=v);Row(){Text('锁定工程，禁止继续拍摄').fontColor($r('app.color.text_primary')).layoutWeight(1);Toggle({type:ToggleType.Switch,isOn:this.draft.locked}).selectedColor('#FFB300').onChange(v=>this.draft.locked=v);}
Row(){Text('归档').fontColor($r('app.color.text_primary')).layoutWeight(1);Toggle({type:ToggleType.Switch,isOn:this.draft.archived}).selectedColor('#FFB300').onChange(v=>this.draft.archived=v);}
Button('保存工程').width('100%').backgroundColor('#FFCA55').fontColor('#352500').onClick(()=>this.task(async()=>this.saveProject()));}.padding(18);}.scrollBar(BarState.Off).align(Alignment.Top).layoutWeight(1);}
  @Builder
  cameraToolbar() {
    Flex({direction:this.cameraGeometry().side?FlexDirection.Column:FlexDirection.Row,justifyContent:this.cameraGeometry().side?FlexAlign.SpaceEvenly:FlexAlign.Start,alignItems:ItemAlign.Center}) {
      Button(){Text(this.current().name||'选择工程').fontSize(this.cameraGeometry().side?13:14).maxLines(this.cameraGeometry().side?2:1).textOverflow({overflow:TextOverflow.Ellipsis}).fontColor(SITE_TEXT);}.layoutWeight(this.cameraGeometry().side?0:1).width(this.cameraGeometry().side?Math.min(180,Math.max(72,this.cameraGeometry().toolbarWidth-12)):'auto').height(44).accessibilityText('工程项目：'+(this.current().name||'请选择工程'))
        .constraintSize({maxWidth:360}).fontColor(SITE_TEXT).backgroundColor(SITE_CARD_RAISED).id('camera-project')
        .onClick(()=>this.openProjectSwitchPanel());
      if(this.addressRefreshFallbackVisible()) Button(){Text(this.addressRefreshLabel()).fontSize(Math.max(13,Math.min(18,14*this.fontScale))).maxLines(2).textAlign(TextAlign.Center).lineHeight(Math.max(16,14*this.fontScale+2)).textOverflow({overflow:TextOverflow.Ellipsis});}.width(this.cameraGeometry().side?72:58).height(44)
        .fontColor(this.locationRefreshState==='refreshing'?SITE_MUTED:'#231A00')
        .backgroundColor(this.locationRefreshState==='refreshing'?SITE_CARD_RAISED:SITE_ACCENT)
        .enabled(this.locationRefreshState!=='refreshing').accessibilityText(this.locationRefreshState==='refreshing'?'地址刷新中':this.addressRefreshFailureMessage())
        .id('camera-refresh-address-fallback').onClick(()=>this.requestAddressRefresh());
      if(!this.cameraGeometry().side&&this.windowWidth>=600)Blank().layoutWeight(1);
      Button(){Column({space:2}){if(this.flashMode==='off')Image($r('app.media.ic_flash_off')).width(20).height(20);else if(this.flashMode==='auto')Image($r('app.media.ic_flash_auto')).width(20).height(20);else if(this.flashMode==='torch')Image($r('app.media.ic_flash_torch')).width(20).height(20);else Image($r('app.media.ic_flash_on')).width(20).height(20);Text(flashModeLabel(this.flashMode)).fontSize(10).fontColor(this.flashMode==='off'?SITE_TEXT:SITE_ACCENT);}.width('100%');}.width(48).height(44).fontSize(12).padding(0).fontColor(this.flashMode==='off'?SITE_TEXT:SITE_ACCENT)
        .backgroundColor(Color.Transparent).enabled(!this.recording&&!this.videoStarting&&this.flashAvailable)
        .onTouch(e=>{if(e.type===TouchType.Down)this.flashTouchDown();}).onClick(()=>this.flashShortPress()).gesture(LongPressGesture({repeat:false}).onAction(()=>this.flashLongPress()));
      Button(){Column({space:2}){Image(this.quickIssueEnabled?$r('app.media.ic_issue_selected'):$r('app.media.ic_issue')).width(20).height(20);Text('问题').fontSize(10).fontColor(this.quickIssueEnabled?SITE_ACCENT:SITE_TEXT);}.width('100%');}.width(48).height(44).fontSize(12).padding(0).fontColor(this.quickIssueEnabled?SITE_ACCENT:SITE_TEXT)
        .backgroundColor(Color.Transparent).enabled(!this.recording).accessibilityText(this.quickIssueEnabled?'问题标记已开启':'问题标记已关闭').onClick(()=>this.quickIssueEnabled=!this.quickIssueEnabled);
      Button(){Column({space:2}){Image($r('app.media.ic_orientation')).width(20).height(20);Text('方向').fontSize(10).fontColor(SITE_TEXT);}.width('100%');}.width(48).height(44).fontSize(12).padding(0).fontColor(SITE_TEXT).backgroundColor(Color.Transparent)
        .enabled(!this.busy&&!this.recording).id('camera-orientation').onClick(()=>this.openOrientation());
      Button(){Column({space:2}){Image($r('app.media.ic_settings')).width(20).height(20);Text('设置').fontSize(10).fontColor(SITE_TEXT);}.width('100%');}.width(48).height(44).fontSize(12).padding(0).fontColor(SITE_TEXT).backgroundColor(Color.Transparent)
        .id('camera-settings').onClick(()=>this.task(async()=>this.navigate('settings')));
    }.width(this.cameraGeometry().toolbarWidth).height(this.cameraGeometry().toolbarHeight).padding(this.cameraGeometry().side?{top:8,bottom:8,left:0,right:0}:{left:8,right:8,top:0,bottom:0}).backgroundColor(Color.Black).id('camera-toolbar');
  }
  @Builder
  cameraControls() {
    if(this.environment.coverScreen){this.coverCameraControls();}else if(this.cameraGeometry().side){this.landscapeCameraControls();}else{Scroll() {
      Column({space:4}) {
        if(this.environment.compactPhonePortrait){
          Scroll(){Row({space:2}){
            ForEach(this.zoomOptions(),(preset:number)=>{
              Button(zoomPresetLabel(preset)).width(44).height(44).padding(0).fontSize(13)
                .fontColor(this.zoomPresetSelected(preset)?SITE_ACCENT:SITE_TEXT).backgroundColor(Color.Transparent)
                .enabled(this.cameraReady&&preset>=(this.camera?.zoomMin||1)&&preset<=(this.camera?.zoomMax||1))
                .onClick(()=>this.chooseZoom(preset));
            },(preset:number)=>preset.toString());
          }.justifyContent(FlexAlign.Center)}.scrollable(ScrollDirection.Horizontal).scrollBar(BarState.Off).width('100%').height(44);
        }else{
          Row({space:2}) {
            ForEach(this.zoomOptions(),(preset:number)=>{
              Button(zoomPresetLabel(preset)).layoutWeight(1).height(36).padding(0).fontSize(13)
                .fontColor(this.zoomPresetSelected(preset)?SITE_ACCENT:SITE_TEXT).backgroundColor(Color.Transparent)
                .enabled(this.cameraReady&&preset>=(this.camera?.zoomMin||1)&&preset<=(this.camera?.zoomMax||1))
                .onClick(()=>this.chooseZoom(preset));
            },(preset:number)=>preset.toString());
          }.width('100%');
        }
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
              if(this.recording){Rect().width(26).height(26).radius(6).fill('#E53935');}
              else{Circle().width(48).height(48).fill(this.captureMode==='video'?'#E53935':Color.White);}
            }
          }.width(74).height(74).padding(0).backgroundColor(Color.Transparent)
            .scale({x:this.shutterScale,y:this.shutterScale}).id('camera-shutter').accessibilityText(this.recording?'停止录像':'拍摄')
            .enabled(!this.busy&&(this.recording||(!this.current().locked&&!this.current().archived&&this.cameraReady&&!!(this.captureMode==='photo'?this.camera?.photoSupported:this.camera?.videoSupported))))
            .onClick(()=>this.task(async()=>{if(this.captureMode==='photo')await this.shoot();else{this.animateShutter(false);await this.toggleVideo();}}));
          Button() {Image($r('app.media.ic_switch_lens')).width(26).height(26);}
            .width(52).height(52).padding(0).backgroundColor(SITE_CARD_RAISED).borderRadius(26).id('camera-switch-lens')
            .enabled(!this.busy&&!this.recording&&!!this.camera?.canSwitchLensForMode(this.captureMode==='video'))
            .onClick(()=>this.task(async()=>this.switchCameraLens(this.captureMode==='video')));
        }.width('100%').height(this.cameraGeometry().side?198:82);
        if(this.cameraGeometry().hover) {
          Button('修改水印').fontColor(SITE_ACCENT).backgroundColor(Color.Transparent)
            .enabled(!this.recording).onClick(()=>{this.styleDraft=JSON.parse(JSON.stringify(this.settings.watermark)) as Watermark;this.quickSheet=true;});
        }
      }.width('100%').padding({left:8,right:8,bottom:8});
    }.width(this.cameraGeometry().controlsWidth).height(this.cameraGeometry().controlsHeight)
      .align(Alignment.Top).scrollable(ScrollDirection.Vertical).scrollBar(BarState.Off)
      .position({x:this.cameraGeometry().controlsX,y:this.cameraGeometry().controlsY}).id('camera-controls');}
  }
  @Builder
  coverCameraControls() {
    FoldCoverControls({panelWidth:this.cameraGeometry().controlsWidth,panelHeight:this.cameraGeometry().controlsHeight,recording:this.recording,
      canCapture:!this.busy&&(this.recording||(!this.current().locked&&!this.current().archived&&this.cameraReady&&!!(this.captureMode==='photo'?this.camera?.photoSupported:this.camera?.videoSupported))),
      canSwitch:!this.busy&&!this.recording&&!!this.camera?.canSwitchLensForMode(this.captureMode==='video'),
      onGallery:()=>this.task(async()=>{this.filterProject=this.current().id;await this.navigate('gallery');}),
      onCapture:()=>this.task(async()=>{if(this.captureMode==='photo')await this.shoot();else await this.toggleVideo();}),
      onSwitch:()=>this.task(async()=>this.switchCameraLens(this.captureMode==='video')),
      onLongPress:()=>this.openCoverToolsMenu()})
      .position({x:this.cameraGeometry().controlsX,y:this.cameraGeometry().controlsY});
  }
  @Builder
  coverCompanionPage() {
    Column({space:4}) {
      if(this.page==='gallery'||this.page==='detail') {
        if((this.detail.id?this.detail:this.items[0])?.type==='PHOTO') {
          Image('file://'+(this.detail.id?(this.showEdited&&this.detail.edited?this.detail.edited:this.detail.path):(this.items[0].edited||this.items[0].path))).width('100%').layoutWeight(1).objectFit(ImageFit.Contain);
        }else{Text('展开查看资料').fontSize(12).fontColor(SITE_TEXT).layoutWeight(1);}
        Button('返回拍摄').height(36).fontSize(12).fontColor(SITE_ACCENT).backgroundColor(Color.Transparent).onClick(()=>this.task(async()=>this.navigate('camera')));
      }else{Text('展开手机继续操作').fontSize(14).fontColor(SITE_TEXT).textAlign(TextAlign.Center);}
    }.width('100%').height('100%').justifyContent(FlexAlign.Center).backgroundColor(SITE_BG).id('cover-companion');
  }
  private compactLandscape():boolean{return this.cameraGeometry().controlsWidth<235||this.cameraGeometry().controlsHeight<360||this.fontScale>=1.25;}
  @Builder
  landscapeCameraControls() {
    Row({space:4}) {
      Column({space:4}){ForEach(this.zoomOptions(),(preset:number)=>{
        Button(zoomPresetLabel(preset)).width(36).height(32).padding(0).fontSize(12)
          .fontColor(this.zoomPresetSelected(preset)?SITE_ACCENT:SITE_TEXT).backgroundColor(Color.Transparent)
          .enabled(this.cameraReady&&preset>=(this.camera?.zoomMin||1)&&preset<=(this.camera?.zoomMax||1))
          .onClick(()=>this.chooseZoom(preset));
      },(preset:number)=>preset.toString());}.width(36).id('camera-zoom-rail');
      Column({space:16}){
        Text('拍照').fontSize(13).fontColor(this.captureMode==='photo'?SITE_ACCENT:SITE_MUTED).enabled(!this.busy&&!this.recording).onClick(()=>{this.captureMode='photo';this.updateFrameRatio();});
        Text('录像').fontSize(13).fontColor(this.captureMode==='video'?SITE_ACCENT:SITE_MUTED).enabled(!this.busy&&!this.recording).onClick(()=>{this.captureMode='video';this.updateFrameRatio();});
      }.width(32).id('camera-mode-rail');
      Blank().layoutWeight(1);
      Column(){
        Button(){if(this.items.length&&this.items[0].type==='PHOTO'){Image('file://'+(this.items[0].edited||this.items[0].path)).width('100%').height('100%').borderRadius(8).objectFit(ImageFit.Cover);}else{Image($r('app.media.icon_folder')).width(26).height(26);}}
          .width(this.compactLandscape()?40:46).height(this.compactLandscape()?40:46).padding(0).backgroundColor(SITE_CARD_RAISED).borderRadius(8).id('camera-gallery-dock')
          .onClick(()=>this.task(async()=>{this.filterProject=this.current().id;await this.navigate('gallery');}));
        Button(){Stack(){Circle().width(this.compactLandscape()?56:66).height(this.compactLandscape()?56:66).fill(Color.White);Circle().width(this.compactLandscape()?46:56).height(this.compactLandscape()?46:56).fill(Color.Black);if(this.recording){Rect().width(this.compactLandscape()?20:24).height(this.compactLandscape()?20:24).radius(6).fill('#E53935');}else{Circle().width(this.compactLandscape()?38:46).height(this.compactLandscape()?38:46).fill(this.captureMode==='video'?'#E53935':Color.White);}}}
          .width(this.compactLandscape()?58:68).height(this.compactLandscape()?58:68).padding(0).backgroundColor(Color.Transparent).scale({x:this.shutterScale,y:this.shutterScale}).id('camera-shutter-dock').accessibilityText(this.recording?'停止录像':'拍摄')
          .enabled(!this.busy&&(this.recording||(!this.current().locked&&!this.current().archived&&this.cameraReady&&!!(this.captureMode==='photo'?this.camera?.photoSupported:this.camera?.videoSupported))))
          .onClick(()=>this.task(async()=>{if(this.captureMode==='photo')await this.shoot();else{this.animateShutter(false);await this.toggleVideo();}}));
        Button(){Image($r('app.media.ic_switch_lens')).width(24).height(24);}.width(this.compactLandscape()?40:46).height(this.compactLandscape()?40:46).padding(0).backgroundColor(SITE_CARD_RAISED).borderRadius(23).id('camera-switch-lens-dock')
            .enabled(!this.busy&&!this.recording&&!!this.camera?.canSwitchLensForMode(this.captureMode==='video')).onClick(()=>this.task(async()=>this.switchCameraLens(this.captureMode==='video')));
      }.width(this.compactLandscape()?58:68).height(this.environment.largeScreenScheme?Math.min(320,this.cameraGeometry().controlsHeight):this.cameraGeometry().controlsHeight).padding({top:22,bottom:22}).justifyContent(FlexAlign.SpaceBetween).id('camera-shutter-rail');
    }.width(this.cameraGeometry().controlsWidth).height(this.cameraGeometry().controlsHeight)
      .padding({left:4,right:Math.min(this.compactLandscape()?58:68,Math.max(4,this.cameraGeometry().controlsWidth-(this.compactLandscape()?58:68)-84))})
      .backgroundColor(Color.Black).position({x:this.cameraGeometry().controlsX,y:this.cameraGeometry().controlsY}).id('camera-controls-dock');
  }
  @Builder
  cameraPage() {
    Stack({alignContent:Alignment.TopStart}) {
      if(!this.environment.coverScreen)this.cameraToolbar();
      Stack() {
        XComponent({id:'sitecam-preview',type:XComponentType.SURFACE,controller:this.controller})
          .width(this.surfaceWidth()).height(this.surfaceHeight()).onAreaChange((a,b)=>this.setPreviewSurfaceArea(Number(b.width),Number(b.height))).onLoad(()=>{this.surface=this.controller.getXComponentSurfaceId();void this.ensureCamera();})
          .onDestroy(()=>this.cameraSurfaceLost())
          .onTouch(e=>{if(e.type===TouchType.Down&&this.cameraReady&&e.touches.length)this.camera?.focus(e.touches[0].x/this.surfaceWidth(),e.touches[0].y/this.surfaceHeight());})
          .gesture(PinchGesture().onActionStart(()=>this.pinchStartZoom=this.zoom).onActionUpdate(e=>{if(this.cameraReady&&this.camera)this.zoom=this.camera.zoom(this.pinchStartZoom*e.scale);}));
        WatermarkView({snapshot:this.snapshot,reportBounds:true}).width('100%').height('100%').hitTestBehavior(HitTestMode.None);
        if(!this.environment.coverScreen&&this.watermarkBound(2)>0&&this.watermarkBound(3)>0) {
          Button('').width(this.watermarkBound(2)).height(this.watermarkBound(3))
            .position({x:this.watermarkBound(0),y:this.watermarkBound(1)}).backgroundColor(Color.Transparent)
            .enabled(!this.busy&&!this.recording).id('camera-watermark')
            .onClick(()=>{this.styleDraft=JSON.parse(JSON.stringify(this.settings.watermark)) as Watermark;this.quickSheet=true;});
        }
        Rect().width('100%').height('100%').fill(Color.White).opacity(this.shutterFlash).hitTestBehavior(HitTestMode.None);
        if(!this.cameraReady) {Column({space:8}){Text(this.message||'正在打开相机').fontColor(SITE_TEXT).maxFontScale(1.6);if(!this.photoPermission)Button(this.environment.coverScreen?'开启相机权限':'打开权限设置').fontSize(14).constraintSize({minHeight:40}).maxFontScale(1.6).onClick(()=>this.task(async()=>{if(this.environment.coverScreen){await this.permissions(true);await this.ensureCamera();}else{await this.navigate('permissions','forward');}}));}.padding(12);}
      }.width(this.frameWidth()).height(this.frameWidth()/this.frameRatio).backgroundColor(Color.Black).clip(true)
        .position({x:this.cameraGeometry().previewX,y:this.cameraGeometry().previewY})
        .id('camera-preview');
      this.coverProjectEntry();
      this.cameraControls();
      this.addressRefreshButton();
      this.projectSwitchOverlay();
      if(this.quickSheet&&!this.environment.coverScreen) {
        if(this.cameraGeometry().side){Rect().width('100%').height('100%').fill('#99000000').onClick(()=>{this.persistQuickWatermark();this.quickSheet=false;});}
        QuickWatermarkPanel({panelWidth:this.cameraGeometry().side?Math.min(640,this.windowWidth):this.windowWidth,snapshot:this.snapshot,fields:this.styleDraft.fields,selectedStyle:this.styleDraft.style,fontScale:this.styleDraft.fontScale,wmOpacity:this.styleDraft.opacity,wmPosition:this.styleDraft.position,
          onDismiss:()=>{this.persistQuickWatermark();this.quickSheet=false;},onFieldChange:(i:number,v:string)=>{this.styleDraft.fields[i].value=v;this.persistQuickWatermark();},onFieldToggle:(i:number,v:boolean)=>{this.styleDraft.fields[i].enabled=v;this.persistQuickWatermark();},onStyle:(v:string)=>{this.styleDraft.style=v;this.persistQuickWatermark();},onScale:(v:number)=>{this.styleDraft.fontScale=v;this.persistQuickWatermark();},onOpacity:(v:number)=>{this.styleDraft.opacity=v;this.persistQuickWatermark();},onPosition:(v:string)=>{this.styleDraft.position=v;this.persistQuickWatermark();},onAddField:(label:string)=>this.styleDraft.fields=this.styleDraft.fields.concat(new Field('custom_'+Date.now(),label,'')),onAddFieldValue:(label:string,value:string)=>{this.styleDraft.fields=this.styleDraft.fields.concat(new Field('custom_'+Date.now(),label,value));this.persistQuickWatermark();}})
          .width(this.cameraGeometry().side?Math.min(640,this.windowWidth):this.windowWidth)
          .height(this.cameraGeometry().side||this.cameraGeometry().hover?this.cameraGeometry().controlsHeight:Math.max(1,this.windowHeight-56))
          .position({x:this.cameraGeometry().side?(this.windowWidth-Math.min(640,this.windowWidth))/2:0,y:this.cameraGeometry().side?0:this.cameraGeometry().hover?this.cameraGeometry().controlsY:56});
      }
    }.width('100%').height('100%').backgroundColor(Color.Black);
  }
  @Builder
  galleryRootPage() {GalleryPageView({pathStack:this.galleryStack,detailContent:()=>{this.galleryDetailContent();},items:this.galleryRows(),projects:this.projects,currentProjectName:this.filterProject?(this.projects.find((p:Project)=>p.id===this.filterProject)?.name||'工程相册'):'全部工程相册',previewItem:this.detail,windowWidth:this.windowWidth,parallelEnabled:this.environment.largeScreenScheme&&this.windowWidth>=600,categories:Array.from(new Set(this.projects.map((p:Project)=>p.category).filter((v:string)=>!!v))),selectedFilter:this.fromDate===dateText(Date.now())?'TODAY':this.issueFilter===1?'ISSUES_ONLY':'ALL',selectedCategory:this.category,selectedStatus:this.issueFilter>1?['','','PENDING','IN_PROGRESS','COMPLETED'][this.issueFilter]:'',selectedSeverity:this.severityFilter?['','NORMAL','IMPORTANT','CRITICAL'][this.severityFilter]:'',dateRangeText:this.fromDate||this.toDate?`${this.fromDate||'起始'} 至 ${this.toDate||'结束'}`:'',selectedIds:this.mediaSelection,onBack:()=>this.task(async()=>this.navigate(this.galleryOrigin||'camera','backward')),onSwitchProject:()=>this.projectPickerDialog(),onFilter:(v:string)=>{this.mediaSelection=[];if(v==='TODAY'){this.fromDate=dateText(Date.now());this.toDate=dateText(Date.now());this.issueFilter=0;}else if(v==='ISSUES_ONLY'){this.fromDate='';this.toDate='';this.issueFilter=1;}else{this.fromDate='';this.toDate='';this.issueFilter=0;}},onDateRange:()=>this.pickDateRange(),onClearDate:()=>{this.mediaSelection=[];this.fromDate='';this.toDate='';},onCategory:(v:string)=>{this.mediaSelection=[];this.category=v;},onStatus:(v:string)=>{this.mediaSelection=[];this.issueFilter=v?['','','PENDING','IN_PROGRESS','COMPLETED'].indexOf(v):0;},onSeverity:(v:string)=>{this.mediaSelection=[];this.severityFilter=v?['','NORMAL','IMPORTANT','CRITICAL'].indexOf(v):0;},onSelectAll:()=>this.mediaSelection=this.galleryRows().map((m:MediaItem)=>m.id),onClearSelection:()=>this.mediaSelection=[],onOpen:(m:MediaItem)=>this.openDetail(m),onOpenFull:()=>this.task(async()=>this.navigate('detail','forward')),onToggle:(m:MediaItem)=>this.toggleMedia(m.id),onIssue:(m:MediaItem)=>this.openIssue(m),onLongPress:(m:MediaItem)=>this.toggleMedia(m.id),onBatchAction:(a:string)=>a==='export'?this.exportStart(this.projects.map((p:Project)=>p.id),this.mediaSelection):this.batchMediaMoreDialog(),onBatchMove:()=>this.openMoveDialog(),onBatchShare:()=>this.task(async()=>this.files!.share(this.items.filter((m:MediaItem)=>this.mediaSelection.includes(m.id)).map((m:MediaItem)=>m.edited||m.path),false,'gallery-share-anchor')),onBatchIssue:()=>{}});}

  @Builder
  galleryDetailContent() {if(this.detail.id&&!this.galleryStack.getAllPathName().includes('media-placeholder')){this.detailRootPage();}else{Column({space:12}){Image($r('app.media.ic_camera')).width(48).height(48);Text('选择照片或视频').fontSize(20).fontWeight(FontWeight.Bold).fontColor(SITE_TEXT);Text('在左侧查看工程资料，在这里预览、编辑和分享').fontSize(14).fontColor(SITE_MUTED).textAlign(TextAlign.Center);}.width('100%').height('100%').justifyContent(FlexAlign.Center).padding(24).backgroundColor(SITE_BG);}}
  @Builder
  detailRootPage() {DetailPageView({item:this.detail,showEdited:this.showEdited,issueTitle:this.detail.issueTitle,issueNotes:this.detail.issueNotes,issueSeverity:this.detail.severity,issueStatus:this.detail.issueStatus,retrying:this.busy,onSaveAlbum:()=>this.task(async()=>{const uri=await this.saveAlbumCopy(this.detail,this.showEdited&&this.detail.edited?this.detail.edited:this.detail.path);if(uri){await this.refresh();this.notify('已另存到系统相册，工程原件不受影响');}else this.notify('已取消另存，工程原件不受影响');}),onBack:()=>{if(this.page==='gallery')this.galleryStack.pop();else{this.galleryStack.clear();this.task(async()=>this.navigate('gallery','backward'));}},onEdit:()=>this.task(async()=>this.beginEdit()),onShare:()=>this.task(async()=>this.files!.share([this.showEdited&&this.detail.edited?this.detail.edited:this.detail.path],this.detail.type==='VIDEO')),onDelete:()=>this.deleteDetail(),onRetryVideo:()=>this.task(async()=>{const item=new MediaItem();item.id=this.detail.id;item.path=this.detail.path;item.edited=this.detail.edited;item.name=this.detail.name;item.type=this.detail.type;await this.video.process(item,this.files!,v=>this.videoProgress=v);await this.refresh();}),onToggleEdited:()=>this.showEdited=!this.showEdited,onIssue:()=>this.openIssue(this.detail)});}
  private editorImageWidth(): number {const w=this.windowWidth>=840?this.windowWidth-344:this.windowWidth-24;const h=this.windowWidth>=840?this.windowHeight-76:Math.max(80,this.windowHeight-304);return Math.max(1,Math.min(w,h*this.editRatio));}
  @Builder
  editorToolsScroll() {
    Scroll() {
      Column(){Row({space:8}){Button('水平翻转').layoutWeight(1).padding(0).fontSize(12).fontColor(SITE_ACCENT).backgroundColor(Color.Transparent).onClick(()=>this.task(async()=>{this.pushEdit();this.recipe.steps=this.recipe.steps.concat(new EditStep('flipX'));await this.renderEdit();}));Button('垂直翻转').layoutWeight(1).padding(0).fontSize(12).fontColor(SITE_ACCENT).backgroundColor(Color.Transparent).onClick(()=>this.task(async()=>{this.pushEdit();this.recipe.steps=this.recipe.steps.concat(new EditStep('flipY'));await this.renderEdit();}));Button('旋转90°').layoutWeight(1).padding(0).fontSize(12).fontColor(SITE_ACCENT).backgroundColor(Color.Transparent).onClick(()=>this.task(async()=>{this.pushEdit();this.recipe.steps=this.recipe.steps.concat(new EditStep('rotate'));await this.renderEdit();}));Button('裁剪').layoutWeight(1).padding(0).fontSize(12).fontColor(SITE_ACCENT).backgroundColor(Color.Transparent).onClick(()=>this.openCropDialog());}.width('100%').justifyContent(FlexAlign.Center);Text(this.cropMode?'在照片上拖出裁剪范围':'选择工具，在照片上拖动添加标注').fontSize(13).fontColor(SITE_MUTED);Column({space:10}){GridRow({columns:this.windowWidth>=840||this.windowWidth<380?3:6,gutter:4}){ForEach([['箭头','arrow'],['矩形','rect'],['圆形','circle'],['画笔','freehand'],['文字','text'],['马赛克','mosaic']],(tool: string[])=>{GridCol(){Button(){Column(){Image(tool[1]==='arrow'?$r('app.media.ic_tool_arrow'):tool[1]==='rect'?$r('app.media.ic_tool_rect'):tool[1]==='circle'?$r('app.media.ic_tool_circle'):tool[1]==='freehand'?$r('app.media.ic_tool_brush'):tool[1]==='text'?$r('app.media.ic_tool_text'):$r('app.media.ic_tool_mosaic')).width(24).height(24);Text(tool[0]).fontSize(10).fontColor(this.markTool===tool[1]?'#231A00':SITE_TEXT);}.width(52).height(48);}.padding(0).backgroundColor(this.markTool===tool[1]?SITE_ACCENT:SITE_CARD_RAISED).onClick(()=>{this.markTool=tool[1];this.cropMode=false;});}},(tool: string[])=>tool[1]);}
Row({space:8}){ForEach(['#F44336','#FFB300','#FFC107','#4CAF50','#FFFFFF'],(color: string)=>{Button().width(28).height(28).backgroundColor(color).border({width:this.markColor===color?3:1,color:this.markColor===color?SITE_TEXT:SITE_MUTED}).borderRadius(14).onClick(()=>this.markColor=color);},(color: string)=>color);}.justifyContent(FlexAlign.Center);TextInput({text:this.markText,placeholder:'文字标注内容（选择文字工具）'}).fontColor('#FFFFFF').placeholderColor('#B0BEC5').backgroundColor('#303030').caretColor('#FFB300').borderRadius(10).onChange(v=>this.markText=v);}.padding(10);}
    }
    .width(this.windowWidth>=840?320:'100%')
    .height(this.windowWidth>=840?'100%':228)
    .scrollable(ScrollDirection.Vertical)
    .scrollBar(BarState.Off)
    .align(Alignment.Top);
  }
  @Builder
  editorPage() {Column(){Row({space:8}){Image($r('app.media.ic_arrow_back')).width(24).height(24).onClick(()=>this.onBackPress());Text('现场隐患照片标注').fontSize(19).fontWeight(FontWeight.Bold).fontColor(SITE_TEXT).layoutWeight(1);Button(){Image($r('app.media.ic_undo')).width(24).height(24)}.width(48).height(48).padding(0).backgroundColor(Color.Transparent).enabled(this.history.length>0).onClick(()=>this.task(async()=>this.undo(false)));Button(){Image($r('app.media.ic_redo')).width(24).height(24)}.width(48).height(48).padding(0).backgroundColor(Color.Transparent).enabled(this.redoHistory.length>0).onClick(()=>this.task(async()=>this.undo(true)));Button('✓').width(48).height(48).padding(0).fontSize(22).fontColor(SITE_ACCENT).backgroundColor(Color.Transparent).onClick(()=>this.task(async()=>this.saveEdit()));}.padding({left:12,right:12,bottom:6});Flex({direction:this.windowWidth>=840?FlexDirection.Row:FlexDirection.Column}){Column(){Image('file://'+this.editPreview).width(this.editorImageWidth()).height(this.editorImageWidth()/this.editRatio).objectFit(ImageFit.Fill).onAreaChange((a,b)=>{this.editorWidth=Number(b.width);this.editorHeight=Number(b.height);}).onTouch(e=>this.editorTouch(e));}.width(this.windowWidth>=840?this.windowWidth-320:this.windowWidth).layoutWeight(1).justifyContent(FlexAlign.Center).alignItems(HorizontalAlign.Center);
this.editorToolsScroll();
}.layoutWeight(1).width('100%');}.width('100%').height('100%');}
  @Builder
  watermarkPanelPage() {WatermarkCustomizationPanel({snapshot:this.snapshot,fields:this.styleDraft.fields,selectedStyle:this.styleDraft.style,fontScale:this.styleDraft.fontScale,wmOpacity:this.styleDraft.opacity,wmPosition:this.styleDraft.position,onReset:()=>{this.styleDraft=resetWatermarkFields(this.styleDraft);},onBack:()=>this.task(async()=>{await this.persistWatermarkBeforeLeave();await this.navigate(this.styleReturn,'modal');}),onFontScale:(v:number)=>this.styleDraft.fontScale=v,onOpacity:(v:number)=>this.styleDraft.opacity=v,onPosition:(v:string)=>this.styleDraft.position=v,onStyle:(v:string)=>this.styleDraft.style=v,onFieldChange:(i:number,v:string)=>this.styleDraft.fields[i].value=v,onFieldLabelChange:(i:number,v:string)=>this.styleDraft.fields[i].label=v,onFieldToggle:(i:number,v:boolean)=>this.styleDraft.fields[i].enabled=v,onMove:(i:number,d:number)=>{const a=this.styleDraft.fields.slice();const j=i+d;if(j>=0&&j<a.length){const t=a[i];a[i]=a[j];a[j]=t;this.styleDraft.fields=a;}},onDelete:(i:number)=>{this.styleDraft.fields=this.styleDraft.fields.filter((f:Field,index:number)=>index!==i);},onAddField:(label:string,value:string)=>{this.styleDraft.fields=this.styleDraft.fields.concat(new Field('custom_'+Date.now(),label,value));}});}
  @Builder
  permissionRootPage() {PermissionGuidePageView({permissions:this.permissionRows(),hasRequested:this.settings.systemPermissionsRequested,requesting:this.permissionRequesting,fontScale:this.fontScale,viewportHeight:this.windowHeight,onBack:()=>this.task(async()=>this.navigate(this.permissionsReturn||'camera','backward')),onRequest:()=>this.task(async()=>{this.permissionRequesting=true;try{await this.permissions(true);}finally{this.permissionRequesting=false;}}),onContinue:()=>this.task(async()=>{this.settings.permissionHandled=true;await this.persist();await this.navigate(this.settings.currentProject?'camera':'projects','forward');}),onOpenSettings:()=>this.task(async()=>this.openAppSettings())});}
  @Builder
  settingsRootPage() {SettingsPageView({quality:this.settings.photoQuality,albumCopy:this.settings.albumCopy,shutterSoundEnabled:this.settings.shutterSoundEnabled,icon:this.settings.icon||'D',apiVersion:deviceInfo.sdkApiVersion,diagnostics:(this.camera?.flashDiagnostics||'')+(this.message?'\n'+this.message:''),templateId:this.settings.templateId,onBack:()=>this.task(async()=>this.navigate('camera','backward')),onHelp:()=>this.task(async()=>this.navigate('help','forward')),onWatermark:()=>this.openStyles('settings'),onQuality:(v:number)=>{this.settings.photoQuality=v;this.settings.editQuality=v;this.persist();},onAlbumCopy:(v:boolean)=>{this.settings.albumCopy=v;this.persist();},onShutterSound:(v:boolean)=>{this.settings.shutterSoundEnabled=v;this.persist();},onIcon:(v:string)=>this.task(async()=>{await bundleManager.setAlternateIcon(v==='D'?'':v);this.settings.icon=v;await this.persist();}),onCopyDiagnostics:()=>this.task(async()=>this.copyDiagnostics()),onOpenLink:(url:string)=>this.task(async()=>this.openExternal(url)),onPermissions:()=>this.task(async()=>this.navigate('permissions','forward'))});}
  @Builder
  helpRootPage() {HelpPageView({onBack:()=>this.task(async()=>this.navigate('settings','backward')),onReplayGuide:()=>this.task(async()=>{this.guideStep=0;await this.navigate('guide','forward');})});}
  @Builder
  onboardingRootPage() {OnboardingPageView({step:this.guideStep,viewportHeight:this.windowHeight,onNext:()=>this.task(async()=>{if(this.guideStep<3){this.guideStep++;}else{this.settings.guideDone=true;await this.persist();await this.navigate(this.settings.permissionHandled?(this.settings.currentProject?'camera':'projects'):'permissions','forward');}}),onSkip:()=>this.task(async()=>{this.settings.guideDone=true;await this.persist();await this.navigate(this.settings.permissionHandled?(this.settings.currentProject?'camera':'projects'):'permissions','forward');})});}
  @Builder
  exportPage() {this.header('工程资料导出','projects');Column({space:18}){Text(`已选 ${this.exportIds.length} 个工程`).fontSize(22).fontColor($r('app.color.text_accent'));Select([{value:'ZIP 压缩包'},{value:'总文件夹'}]).selected(this.exportFolder?1:0).value(this.exportFolder?'总文件夹':'ZIP 压缩包').onSelect(i=>this.exportFolder=i===1);Text('总文件夹通过系统授权保存到下载目录。').fontColor($r('app.color.text_secondary'));
Text('照片导出质量').fontColor($r('app.color.text_primary'));Select([{value:'保持原文件'},...QUALITY_NAMES.map((value: string): SelectOption=>({value:value}))]).selected(this.exportQuality+1).value(this.exportQuality<0?'保持原文件':QUALITY_NAMES[this.exportQuality]).onSelect(i=>this.exportQuality=i-1);Text('包含工程信息、照片、视频、编辑成品、清单和报告。缺失或处理失败的资料会在报告中说明。').fontColor($r('app.color.text_secondary'));if(this.busy&&this.message)Text(this.message).fontSize(13).fontColor($r('app.color.text_accent'));Button('选择位置并导出').width('100%').enabled(!this.busy).onClick(()=>this.task(async()=>this.exportNow()));if(this.busy)Button('取消导出').onClick(()=>{this.exporter!.cancelled=true;});}.padding(20).layoutWeight(1);}
  build() {Stack(){
    Stack(){
      if(!this.ready){
        Column(){
          this.header('工程水印相机');
          Text(this.message).fontColor($r('app.color.text_primary')).padding(24);
        }.width('100%').height('100%');
      }else{
        if(this.page==='projects'){
          Stack(){this.projectsRootPage();}.width('100%').height('100%').transition(this.getPageTransition('projects'));
        }else if(this.page==='projectEdit'){
          Stack(){this.projectEditPage();}.width('100%').height('100%').transition(this.getPageTransition('projectEdit'));
        }else if(this.page==='camera'){
          Stack(){this.cameraPage();}.width('100%').height('100%').transition(this.getPageTransition('camera'));
        }else if(this.page==='gallery'){
          Stack(){this.galleryRootPage();}.width('100%').height('100%').transition(this.getPageTransition('gallery'));
        }else if(this.page==='detail'){
          Stack(){this.detailRootPage();}.width('100%').height('100%').transition(this.getPageTransition('detail'));
        }else if(this.page==='editor'){
          Stack(){this.editorPage();}.width('100%').height('100%').transition(this.getPageTransition('editor'));
        }else if(this.page==='watermark'){
          Stack(){this.watermarkPanelPage();}.width('100%').height('100%').transition(this.getPageTransition('watermark'));
        }else if(this.page==='settings'){
          Stack(){this.settingsRootPage();}.width('100%').height('100%').transition(this.getPageTransition('settings'));
        }else if(this.page==='guide'){
          Stack(){this.onboardingRootPage();}.width('100%').height('100%').transition(this.getPageTransition('guide'));
        }else if(this.page==='permissions'){
          Stack(){this.permissionRootPage();}.width('100%').height('100%').transition(this.getPageTransition('permissions'));
        }else if(this.page==='export'){
          Stack(){this.exportPage();}.width('100%').height('100%').transition(this.getPageTransition('export'));
        }else if(this.page==='help'){
          Stack(){this.helpRootPage();}.width('100%').height('100%').transition(this.getPageTransition('help'));
        }else if(this.page==='category'){
          Stack(){Column(){this.header('批量分类','projects');this.field('类别',this.batchProjectCategory,v=>this.batchProjectCategory=v);Button('应用分类').onClick(()=>this.task(async()=>{await this.projectBatch('category');await this.navigate('projects','backward');}));Blank();}.width('100%').height('100%');}.width('100%').height('100%').transition(this.getPageTransition('category'));
        }else if(this.page==='move'){
          Stack(){Column(){this.header('移动至工程','gallery');Scroll(){Column({space:10}){ForEach(this.projects,(p: Project)=>{Button(p.name).width('100%').onClick(()=>this.task(async()=>{await store.moveMedia(this.mediaSelection,p.id);this.mediaSelection=[];await this.refresh();await this.navigate('gallery','backward');}));});}.padding(16);}.scrollBar(BarState.Off).align(Alignment.Top).layoutWeight(1);}.width('100%').height('100%');}.width('100%').height('100%').transition(this.getPageTransition('move'));
        }
      }
      if(this.busy&&this.video.running){
        Column({space:4}){
          Text(`视频处理中 ${Math.round(this.videoProgress*100)}%`).fontSize(13).fontColor(SITE_ACCENT).width('95%');
          Button('取消视频处理').onClick(()=>this.video.cancel());
        }.position({x:0,y:'88%'}).width('100%').alignItems(HorizontalAlign.Center);
      }
    // Camera pages keep every margin black so the letterbox around the preview
    // never shows a lighter strip than the preview/toolbar. On landscape
    // tablets the cutout inset (environment.left) exposed exactly that strip.
    }.width('100%').height('100%').backgroundColor(this.page==='camera'?Color.Black:$r('app.color.page_bg')).padding({top:this.environment.top,bottom:this.environment.bottom,left:this.environment.left,right:this.environment.right}).visibility(this.environment.coverScreen&&this.page!=='camera'?Visibility.Hidden:Visibility.Visible);
    if(this.environment.coverScreen&&this.page!=='camera'){Column(){this.coverCompanionPage();}.width('100%').height('100%').padding({top:this.environment.top,bottom:this.environment.bottom,left:this.environment.left,right:this.environment.right});}
    if(this.projectUndoMessage){ProjectUndoBar({message:this.projectUndoMessage,barWidth:Math.min(420,Math.max(280,this.windowWidth-24)),onUndo:()=>this.undoProjectStatus(),onDismiss:()=>this.clearProjectUndo()}).position({x:Math.max(8,(this.windowWidth-Math.min(420,Math.max(280,this.windowWidth-24)))/2),y:Math.max(8,this.windowHeight-68)});}
  }.width('100%').height('100%');}
}
