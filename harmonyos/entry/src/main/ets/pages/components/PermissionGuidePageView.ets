const BG='#121212';const CARD='#282828';const TEXT='#F5F5F5';const MUTED='#B0BEC5';const YELLOW='#FFB300';const GREEN='#66BB6A';
export interface PermissionStatus {id:string;title:string;description:string;granted:boolean;needsSettings:boolean;optional:boolean;detail?:string;}
export type PermissionGuideAction='request'|'settings'|'enter';
/**
 * Single decision point for the permission guide's primary action.
 *
 * HarmonyOS only lists a permission in the system settings page once the app
 * has asked for it at least once. `settings` therefore must never be reachable
 * before a request happened: an earlier AppGallery build sent the user to a
 * settings page with no switches at all, which is exactly how review item 3.1
 * was triggered. Keep this rule in one testable place.
 */
export function permissionGuideAction(rows:PermissionStatus[],hasRequested:boolean,requesting:boolean):PermissionGuideAction{
  if(requesting)return 'request';
  const camera=rows.find((v:PermissionStatus)=>v.id==='camera');
  if(camera!==undefined&&camera.granted)return 'enter';
  if(rows.some((v:PermissionStatus)=>!v.granted&&!v.needsSettings))return 'request';
  return hasRequested?'settings':'request';
}
export function permissionGuideLabel(action:PermissionGuideAction,requesting:boolean):string{
  if(requesting)return '请完成系统权限询问…';
  if(action==='enter')return '进入相机';
  if(action==='settings')return '去系统设置开启';
  return '开启所需权限';
}
@Component export struct PermissionGuidePageView {
 @Prop permissions:PermissionStatus[]=[
  {id:'camera',title:'相机',description:'用于拍照和录像。不开启也能浏览、管理已有工程资料。',granted:false,needsSettings:false,optional:false},
  {id:'location',title:'位置 · 可选',description:'用于水印中的现场地点。允许大致位置也可以；不开启时照片不含定位。',granted:false,needsSettings:false,optional:true},
  {id:'microphone',title:'麦克风 · 可选',description:'用于录制视频声音。不开启时可以录制无声视频。',granted:false,needsSettings:false,optional:true}
 ];
 @Prop hasRequested:boolean=false;@Prop requesting:boolean=false;@Prop fontScale:number=1;
 /** Usable app-window height in vp, so short screens keep the actions on screen. */
 @Prop viewportHeight:number=720;
 /**
  * Short windows (phone landscape, half-folded posture, small fold inner
  * screen) get tighter spacing, otherwise the fixed action area would push the
  * permission cards out of view.
  */
 private compact():boolean{return this.viewportHeight>0&&this.viewportHeight<560;}
 private action():PermissionGuideAction{return permissionGuideAction(this.permissions,this.hasRequested,this.requesting);}
 private primaryTap():void{const action=this.action();if(action==='enter')this.onContinue();else if(action==='request')this.onRequest();else this.onOpenSettings();}
 onBack:()=>void=()=>{};onRequest:()=>void=()=>{};onContinue:()=>void=()=>{};onOpenSettings:()=>void=()=>{};
 @Builder permissionCard(item:PermissionStatus){Row({space:14}){Stack(){Image(item.id==='camera'?$r('app.media.ic_camera'):item.id==='location'?$r('app.media.ic_location'):$r('app.media.ic_microphone')).width(this.compact()?22:28).height(this.compact()?22:28);}.width(this.compact()?40:48).height(this.compact()?40:48).backgroundColor('#FFB30022').borderRadius(14);Column({space:5}){Text(item.title).fontSize(this.compact()?16:18).fontWeight(FontWeight.Bold).fontColor(TEXT);Text(item.description).fontSize(this.compact()?13:14).lineHeight(this.compact()?19:21).fontColor(MUTED).width('100%');Text(item.granted?(item.detail||'已允许'):item.needsSettings?'未开启 · 需前往系统设置':'未开启').fontSize(13).fontColor(item.granted?GREEN:YELLOW);}.alignItems(HorizontalAlign.Start).layoutWeight(1);}.padding(this.compact()?12:16).width('100%').backgroundColor(CARD).borderRadius(14);}
 build(){
  Column(){
   Row(){Image($r('app.media.ic_arrow_back')).width(24).height(24).margin({left:20,right:20}).onClick(()=>this.onBack());Text('开始前，一次设置好权限').fontSize(this.compact()?16:19).fontWeight(FontWeight.Bold).fontColor(TEXT).layoutWeight(1);}.height(this.compact()?52:64).width('100%');
   // Only the explanatory content scrolls; the actions below always stay visible.
   Scroll(){Column({space:this.compact()?10:16}){Column({space:8}){Text('系统会依次询问权限').fontSize(this.compact()?20:25).fontWeight(FontWeight.Bold).fontColor(TEXT);Text('相机是拍摄必需权限。定位和麦克风都可不允许，之后仍能拍照、整理和导出资料。').fontSize(14).lineHeight(22).fontColor(MUTED);}.width('100%').alignItems(HorizontalAlign.Start);ForEach(this.permissions,(item:PermissionStatus)=>this.permissionCard(item),(item:PermissionStatus)=>`${item.id}_${item.granted}_${item.needsSettings}`);if(this.hasRequested)Text(this.permissions.every(v=>v.granted||v.optional)?'所需权限已就绪，可以开始使用。':'授权结果已更新。未开启的项目可稍后补充，不会再次自动弹出申请。').fontSize(14).fontColor(YELLOW).width('100%');}.width('100%').constraintSize({maxWidth:720}).padding({left:20,right:20,top:this.compact()?8:16,bottom:12}).alignItems(HorizontalAlign.Start);}.layoutWeight(1).align(Alignment.Top).scrollBar(BarState.Auto);
   Column({space:this.compact()?8:10}){
    // Buttons carry a minimum height instead of a fixed one, so a large system
    // font scale grows the button rather than clipping its label.
    Button(){Text(permissionGuideLabel(this.action(),this.requesting)).fontSize(16).fontColor('#231A00');}.width('100%').constraintSize({minHeight:this.compact()?44:50}).padding({top:6,bottom:6}).backgroundColor(YELLOW).enabled(!this.requesting).onClick(()=>this.primaryTap());
    if(this.hasRequested&&this.permissions.some(v=>v.needsSettings))Button(){Text('去系统设置补充权限').fontSize(15).fontColor(YELLOW);}.width('100%').constraintSize({minHeight:this.compact()?40:46}).padding({top:6,bottom:6}).backgroundColor(Color.Transparent).borderColor('#555555').borderWidth(1).onClick(()=>this.onOpenSettings());
    Button(){Text(this.hasRequested?'稍后补充，先管理资料':'暂时跳过').fontSize(14).fontColor(MUTED);}.width('100%').constraintSize({minHeight:this.compact()?40:44}).padding({top:6,bottom:6}).backgroundColor(Color.Transparent).enabled(!this.requesting).onClick(()=>this.onContinue());
    Text('不会申请后台定位、通知或存储权限。系统相册默认关闭，需要时再到设置开启。').fontSize(12).lineHeight(17).fontColor(MUTED).width('100%');
   }.width('100%').constraintSize({maxWidth:720}).padding({left:20,right:20,top:8,bottom:this.compact()?10:16}).alignItems(HorizontalAlign.Start);
  }.backgroundColor(BG).height('100%').width('100%');
 }
}
