const BG='#121212';const CARD='#282828';const TEXT='#F5F5F5';const MUTED='#B0BEC5';const YELLOW='#FFB300';
interface OnboardingStep {title:string;body:string;detail:string;icon:string;}
const STEPS:OnboardingStep[]=[{title:'先选工程，照片不混放',body:'拍摄前先选好工程和线路。照片、视频、问题记录会跟着当前工程保存。',detail:'进入工程项目，可以新建工程，填写名称、类别、地点和备注。',icon:'icon_folder'},{title:'横拍竖拍，自己选择',body:'按现场需要握持手机。竖屏、左横屏、右横屏或自动，都可以记住你的选择。',detail:'取景画面会跟着方向调整，水印和成片也会保持一致。',icon:'ic_orientation'},{title:'按日期找照片，随手记问题',body:'相册可以按照片拍摄年月日区间快速查找，也能只看问题记录。',detail:'拍完发现问题，直接标记一般、重要或严重，并填写处理状态。',icon:'ic_calendar'},{title:'资料统一导出，完工及时锁定',body:'导出时可选一个 ZIP 或总文件夹，每个工程都有独立目录和清单。',detail:'完工后锁定工程，暂时停用该工程快门，仍可编辑、移动、删除和导出。',icon:'ic_export'}];
@Component export struct OnboardingPageView {
 @Prop step:number=0;
 /** Usable app-window height in vp; short windows get a denser layout that still scrolls. */
 @Prop viewportHeight:number=720;
 private compact():boolean{return this.viewportHeight>0&&this.viewportHeight<560;}
 onNext:()=>void=()=>{};onSkip:()=>void=()=>{};
 private iconSize():number{return this.compact()?64:96;}
 @Builder icon(name:string){if(name==='icon_folder')Image($r('app.media.icon_folder')).width(this.iconSize()).height(this.iconSize());else if(name==='ic_orientation')Image($r('app.media.ic_orientation')).width(this.iconSize()).height(this.iconSize());else if(name==='ic_calendar')Image($r('app.media.ic_calendar')).width(this.iconSize()).height(this.iconSize());else Image($r('app.media.ic_export')).width(this.iconSize()).height(this.iconSize());}
 currentIndex():number{return Math.max(0,Math.min(3,this.step));}
 build(){
  Column(){
   Text('工程水印相机').fontSize(18).fontWeight(FontWeight.Bold).fontColor(MUTED).margin({top:this.compact()?8:18});
   // Scroll instead of clipping: phone landscape, half-folded postures and the
   // small fold inner screen are all shorter than the full guide.
   Scroll(){
    Column({space:this.compact()?12:18}){
     this.icon(STEPS[this.currentIndex()].icon);
     Text(STEPS[this.currentIndex()].title).fontSize(this.compact()?22:28).fontWeight(FontWeight.Bold).fontColor(YELLOW).textAlign(TextAlign.Center);
     Text(STEPS[this.currentIndex()].body).fontSize(this.compact()?16:18).lineHeight(this.compact()?25:29).fontColor(TEXT).textAlign(TextAlign.Center);
     Text(STEPS[this.currentIndex()].detail).fontSize(14).lineHeight(22).fontColor(MUTED).textAlign(TextAlign.Center);
     Text(`${this.currentIndex()+1} / 4`).fontSize(13).fontColor(MUTED);
     Row({space:10}){Button(){Text(this.currentIndex()===3?'开始使用':'下一步').fontSize(16).fontWeight(FontWeight.Bold).fontColor('#231A00');}.layoutWeight(1).constraintSize({minHeight:this.compact()?44:50}).padding({top:6,bottom:6}).backgroundColor(YELLOW).onClick(()=>this.onNext());Button(){Text('跳过').fontSize(14).fontColor(MUTED);}.constraintSize({minHeight:this.compact()?44:50}).padding({top:6,bottom:6,left:18,right:18}).backgroundColor(CARD).onClick(()=>this.onSkip());}.width('100%');
    }.width('100%').constraintSize({maxWidth:560}).padding(this.compact()?16:24).justifyContent(FlexAlign.Center);
   }.layoutWeight(1).align(Alignment.Center).scrollBar(BarState.Off);
  }.backgroundColor(BG).height('100%').width('100%');
 }
}
