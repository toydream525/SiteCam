import { MediaItem, Snapshot } from '../../core/Models';
import { SITE_ACCENT, SITE_BG, SITE_CARD, SITE_CARD_RAISED, SITE_MUTED, SITE_TEXT } from './UiPrimitives';

function snapshotOf(item: MediaItem): Snapshot|null { if(!item.snapshot)return null;try{return JSON.parse(item.snapshot) as Snapshot;}catch(e){return null;} }
function severityLabel(v: string): string { return v==='CRITICAL'?'严重':v==='IMPORTANT'?'重要':'一般'; }
function statusLabel(v: string): string { return v==='COMPLETED'?'已完成':v==='IN_PROGRESS'?'处理中':'待处理'; }
function fileDate(t: number): string { const d=new Date(t),p=(n:number)=>n.toString().padStart(2,'0');return `${d.getFullYear()}-${p(d.getMonth()+1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`; }

@Component
export struct DetailPageView {
  @Prop item: MediaItem = new MediaItem();
  @Prop showEdited: boolean = true;
  @Prop issueTitle: string = '';
  @Prop issueNotes: string = '';
  @Prop issueSeverity: string = 'NORMAL';
  @Prop issueStatus: string = 'PENDING';
  @Prop retrying: boolean = false;
  @State infoVisible: boolean = false;
  @State viewScale: number = 1;
  @State offsetX: number = 0;
  @State offsetY: number = 0;
  private startScale: number = 1;
  private startX: number = 0;
  private startY: number = 0;
  onBack: () => void = () => {};
  onEdit: () => void = () => {};
  onSaveAlbum: () => void = () => {};
  onShare: () => void = () => {};
  onDelete: () => void = () => {};
  onRetryVideo: () => void = () => {};
  onToggleEdited: () => void = () => {};
  onIssue: () => void = () => {};
  onPlayVideo: () => void = () => {};
  build() {
    Column(){
      Row({space:2}){Button(){Image($r('app.media.ic_arrow_back')).width(24).height(24)}.width(44).height(44).backgroundColor(Color.Transparent).accessibilityText('返回').onClick(()=>this.onBack());Text(this.item.name||'媒体详情').fontSize(16).fontWeight(FontWeight.Bold).fontColor(SITE_TEXT).maxLines(1).textOverflow({overflow:TextOverflow.Ellipsis}).layoutWeight(1);if(this.item.type==='PHOTO')Button(){Image($r('app.media.ic_edit')).width(24).height(24)}.width(44).height(44).backgroundColor(Color.Transparent).accessibilityText('编辑照片').onClick(()=>this.onEdit());Button(){Image($r('app.media.ic_info')).width(24).height(24)}.width(44).height(44).backgroundColor(Color.Transparent).accessibilityText('详细信息').onClick(()=>this.infoVisible=!this.infoVisible);Button(){Image($r('app.media.ic_export')).width(24).height(24)}.width(44).height(44).backgroundColor(Color.Transparent).id('sitecam-share-anchor').accessibilityText('分享').onClick(()=>this.onShare());Button(){Image($r('app.media.ic_delete')).width(24).height(24)}.width(44).height(44).backgroundColor(Color.Transparent).accessibilityText('删除').onClick(()=>this.onDelete());}.width('100%').padding({left:4,right:2,top:4,bottom:4});
      Stack(){Column(){if(this.item.type==='VIDEO'){Video({src:'file://'+(this.showEdited&&this.item.edited?this.item.edited:this.item.path)}).width('100%').layoutWeight(1).autoPlay(false).controls(true);if(this.item.status!=='READY')Button(this.retrying?'正在转码…':'重试视频水印').enabled(!this.retrying).fontColor('#231A00').backgroundColor(SITE_ACCENT).onClick(()=>this.onRetryVideo()).margin({top:12});}else{Image('file://'+(this.showEdited&&this.item.edited?this.item.edited:this.item.path)).width('100%').height('100%').objectFit(ImageFit.Contain).scale({x:this.viewScale,y:this.viewScale}).translate({x:this.offsetX,y:this.offsetY}).gesture(GestureGroup(GestureMode.Parallel,PinchGesture().onActionStart(()=>this.startScale=this.viewScale).onActionUpdate((e)=>this.viewScale=Math.max(1,Math.min(8,this.startScale*e.scale))).onActionEnd(()=>{if(this.viewScale===1){this.offsetX=0;this.offsetY=0;}}),PanGesture({fingers:2}).onActionStart(()=>{this.startX=this.offsetX;this.startY=this.offsetY;}).onActionUpdate((e)=>{if(this.viewScale>1){this.offsetX=this.startX+e.offsetX;this.offsetY=this.startY+e.offsetY;}}),TapGesture({count:2}).onAction(()=>{this.viewScale=this.viewScale===1?2:1;this.offsetX=0;this.offsetY=0;})));}}.width('100%').height('100%').justifyContent(FlexAlign.Center);if(this.infoVisible)this.infoCard();}.layoutWeight(1).width('100%').backgroundColor('#10171D');
      Flex({wrap:FlexWrap.Wrap,justifyContent:FlexAlign.Center}){Button('另存到系统相册').id('detail-save-album').fontSize(12).fontColor(SITE_ACCENT).backgroundColor(Color.Transparent).onClick(()=>this.onSaveAlbum());if(this.item.edited)Button(this.showEdited?'查看原图':'查看编辑成品').fontSize(13).fontColor(SITE_ACCENT).backgroundColor(Color.Transparent).onClick(()=>this.onToggleEdited());Button(this.issueTitle?'编辑问题':'标记问题').fontSize(13).fontColor(this.issueTitle?'#231A00':SITE_ACCENT).backgroundColor(this.issueTitle?SITE_ACCENT:Color.Transparent).onClick(()=>this.onIssue());}.width('100%').padding({top:8,bottom:4});
      if(this.issueTitle)Column({space:5}){Row(){Text('问题记录').fontSize(15).fontWeight(FontWeight.Bold).fontColor(SITE_TEXT).layoutWeight(1);Text(severityLabel(this.issueSeverity)).fontSize(12).fontColor(this.issueSeverity==='CRITICAL'?'#FFFFFF':'#231A00').backgroundColor(this.issueSeverity==='CRITICAL'?'#D32F2F':this.issueSeverity==='IMPORTANT'?'#F6C344':SITE_ACCENT).padding({left:7,right:7,top:3,bottom:3}).borderRadius(5);Text(statusLabel(this.issueStatus)).fontSize(12).fontColor(SITE_MUTED);}.width('100%');Text(this.issueTitle).fontSize(14).fontWeight(FontWeight.Bold).fontColor(SITE_TEXT);Text(this.issueNotes||'未填写整改说明').fontSize(13).fontColor(SITE_MUTED).maxLines(4);}.width('100%').padding(12).backgroundColor(SITE_CARD).borderRadius(10).margin({left:12,right:12,bottom:12});
    }.width('100%').height('100%').backgroundColor(SITE_BG);
  }
  @Builder infoCard(){Column({space:7}){Text(this.item.type==='VIDEO'?'视频详细信息':'照片详细信息').fontSize(16).fontWeight(FontWeight.Bold).fontColor(SITE_TEXT);Text('拍摄时间: '+fileDate(this.item.timestamp)).fontSize(13).fontColor(SITE_MUTED);if(this.item.addressText)Text('地点: '+this.item.addressText).fontSize(13).fontColor(SITE_MUTED);if(this.item.latitude!==null&&this.item.longitude!==null)Text(`GPS: ${this.item.latitude.toFixed(6)}, ${this.item.longitude.toFixed(6)}`).fontSize(13).fontColor(SITE_MUTED);if(snapshotOf(this.item)&&snapshotOf(this.item)!.locationStatus)Text('定位状态: '+snapshotOf(this.item)!.locationStatus).fontSize(13).fontColor(SITE_MUTED);Text(`分辨率: ${this.item.width} × ${this.item.height}`).fontSize(13).fontColor(SITE_MUTED);if(this.item.type==='VIDEO')Text(`时长: ${Math.round(this.item.duration/1000)} 秒 · ${this.item.status==='READY'?'已烧录工程水印':'原片已保留，水印待重试'}`).fontSize(13).fontColor(SITE_MUTED);Text('文件名: '+(this.item.name||'未命名文件')).fontSize(13).fontColor(SITE_MUTED);}.width('92%').padding(16).backgroundColor('#E61E2A33').borderRadius(10).align(Alignment.Bottom);}
}
