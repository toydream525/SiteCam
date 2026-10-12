import { image } from '@kit.ImageKit';

const BG:Resource=$r('app.color.page_bg');
const TEXT:Resource=$r('app.color.text_primary');
const MUTED:Resource=$r('app.color.text_secondary');
const YELLOW='#FFB300';
const TITLES:string[]=['先选工程，\n照片不混放','横拍竖拍，\n自己选择','按日期找照片，\n随手记问题','资料统一导出，\n完工及时锁定'];
const ILLUSTRATION_LEFT:number[]=[12,422,832,1242];

@Component export struct OnboardingPageView {
 @Prop @Watch('stepChanged') step:number=0;
 @Prop viewportHeight:number=720;
 @State viewportWidth:number=390;
 @StorageProp('fontSizeScale') systemFontScale:number=1;
 @State illustration:image.PixelMap|undefined=undefined;
 private illustrationToken:number=0;
 onNext:()=>void=()=>{};
 onSkip:()=>void=()=>{};
 currentIndex():number{return Math.max(0,Math.min(3,this.step));}
 private tabletPortrait():boolean{return this.viewportWidth>=600&&this.viewportHeight>this.viewportWidth;}
 private stepTitle():string {const title=TITLES[this.currentIndex()];const singleLine=title.replace('\n','');return this.viewportWidth>=600&&singleLine.length*30*this.systemFontScale<=this.viewportWidth-48?singleLine:title;}
 private guideContentHeight():number{return this.viewportHeight-64;}
 private illustrationWidth():number{return Math.max(100,Math.min(this.viewportWidth-32,Math.max(160,this.viewportHeight-260)*386/445));}
 /** Wide layout measures the single line against the right column, not the window. */
 private wideStepTitle():string {const title=TITLES[this.currentIndex()];const singleLine=title.replace('\n','');return singleLine.length*30*this.systemFontScale<=this.guideRightWidth()-8?singleLine:title;}
 /** Guide geometry shared by both layouts: 64vp header, 24vp page padding, 32vp column gap. */
 private guideBlockWidth():number{return Math.min(this.viewportWidth,1100);}
 private guideRightWidth():number{return Math.max(320,Math.min(380,this.guideBlockWidth()*.34));}
 private guideLeftWidth():number{return this.guideBlockWidth()-80-this.guideRightWidth();}
 private guideBodyHeight():number{return this.guideContentHeight()-48;}
 private guideWideArtWidth():number{return Math.max(100,Math.min(this.guideLeftWidth(),this.guideBodyHeight()*386/445));}
 /** Two columns only when they keep the artwork at least as large as the single column. */
 private wideLayout():boolean{return this.viewportWidth>=600&&this.guideContentHeight()<this.viewportWidth*1.35&&this.guideWideArtWidth()>=this.illustrationWidth();}
 aboutToAppear():void {void this.loadIllustration();}
 aboutToDisappear():void {this.illustrationToken++;const previous=this.illustration;this.illustration=undefined;void previous?.release();}
 private stepChanged():void {void this.loadIllustration();}
 private async loadIllustration():Promise<void>{
  const token=++this.illustrationToken;
  const step=this.currentIndex();
  let source:image.ImageSource|undefined=undefined;
  try{
   const bytes=await this.getUIContext().getHostContext()!.resourceManager.getRawFileContent('first-launch-steps-v2.png');
   source=image.createImageSource(bytes.buffer.slice(bytes.byteOffset,bytes.byteOffset+bytes.byteLength));
   const cropped=await source.createPixelMap({desiredRegion:{x:ILLUSTRATION_LEFT[step],y:116,size:{width:386,height:445}}});
   if(token!==this.illustrationToken){await cropped.release();return;}
   const previous=this.illustration;this.illustration=cropped;await previous?.release();
  }catch(e){console.warn('SITECAM_GUIDE_ILLUSTRATION '+String(e));}
  finally{await source?.release();}
 }
 @Builder private stepContent(){
  Column({space:20}){
   if(this.illustration){Image(this.illustration).width(this.illustrationWidth()).height(this.illustrationWidth()*445/386).objectFit(ImageFit.Contain).accessibilityLevel('no');}
   Text(this.stepTitle()).fontSize(this.viewportWidth>=600?30:28).fontWeight(FontWeight.Bold).fontColor(TEXT).textAlign(TextAlign.Center).width('100%');
  }.width('100%').padding({top:8,bottom:20,left:16,right:16});
 }
 @Builder private guideDots(){
  Row({space:12}){ForEach([0,1,2,3],(index:number)=>{Circle().width(9).height(9).fill(index===this.currentIndex()?YELLOW:'#D5D9DF');});}.accessibilityText(`第 ${this.currentIndex()+1} 步，共 4 步`);
 }
 @Builder private stepActions(){
  Column({space:20}){
   this.guideDots();
   Button(this.currentIndex()===3?'开始使用':'下一步').width('100%').constraintSize({maxWidth:this.viewportWidth>=600?300:560,minHeight:50}).fontSize(18).fontWeight(FontWeight.Medium).fontColor('#231A00').backgroundColor(YELLOW).borderRadius(this.viewportWidth>=600?28:14).onClick(()=>this.onNext());
  }.width('100%').padding({left:24,right:24,top:12,bottom:20});
 }
 @Builder private wideBody(){
  Row({space:32}){
   Column(){
    if(this.illustration){Image(this.illustration).width(this.guideWideArtWidth()).height(this.guideWideArtWidth()*445/386).objectFit(ImageFit.Contain).accessibilityLevel('no');}
   }.layoutWeight(1).height('100%').justifyContent(FlexAlign.Center).alignItems(HorizontalAlign.Center);
   Column({space:24}){
    Text(this.wideStepTitle()).fontSize(30).fontWeight(FontWeight.Bold).fontColor(TEXT).textAlign(TextAlign.Center).width('100%');
    this.guideDots();
    Button(this.currentIndex()===3?'开始使用':'下一步').width('100%').constraintSize({minHeight:52}).fontSize(18).fontWeight(FontWeight.Medium).fontColor('#231A00').backgroundColor(YELLOW).borderRadius(28).onClick(()=>this.onNext());
   }.width(this.guideRightWidth()).height('100%').justifyContent(FlexAlign.Center);
  }.layoutWeight(1).width('100%').padding({left:24,right:24,top:24,bottom:24}).justifyContent(FlexAlign.Center);
 }
 build(){
  Column(){
   if(this.tabletPortrait()||this.wideLayout()){Row(){Blank().width(72);Text('工程水印相机').layoutWeight(1).textAlign(TextAlign.Center).fontSize(20).fontWeight(FontWeight.Bold).fontColor(TEXT);Button('跳过').width(72).height(48).fontSize(15).fontColor(MUTED).backgroundColor(Color.Transparent).onClick(()=>this.onSkip());}.width('100%').padding({left:20,right:20}).constraintSize({minHeight:64});}
   else{Row(){Text('工程水印相机').fontSize(20).fontWeight(FontWeight.Bold).fontColor(TEXT);Blank();Button('跳过').height(48).fontSize(15).fontColor(MUTED).backgroundColor(Color.Transparent).onClick(()=>this.onSkip());}.width('100%').padding({left:20,right:12}).constraintSize({minHeight:64});}
   if(this.wideLayout()){
    this.wideBody();
   }else if(this.tabletPortrait()){
    Scroll(){Column(){this.stepContent();this.stepActions();}.width('100%');}.edgeEffect(EdgeEffect.Spring).layoutWeight(1).width('100%').scrollBar(BarState.Off).align(Alignment.Center);
   }else{
    Scroll(){this.stepContent();}.edgeEffect(EdgeEffect.Spring).layoutWeight(1).width('100%').scrollBar(BarState.Off).align(Alignment.Center);
    this.stepActions();
   }
  }.backgroundColor(BG).height('100%').width('100%').onAreaChange((oldArea,newArea)=>this.viewportWidth=Number(newArea.width));
 }
}
