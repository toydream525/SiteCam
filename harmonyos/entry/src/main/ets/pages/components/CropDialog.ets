import { SITE_ACCENT, SITE_CARD, SITE_TEXT } from './UiPrimitives';
@CustomDialog
export struct CropDialog {
  controller:CustomDialogController;
  @Prop previewPath:string='';@Prop imageRatio:number=1;@Prop maxHeight:number=620;
  @Prop initialLeft:number=0;@Prop initialTop:number=0;@Prop initialRight:number=1;@Prop initialBottom:number=1;
  @State leftValue:number=0;@State topValue:number=0;@State rightValue:number=1;@State bottomValue:number=1;
  @State previewWidth:number=320;@State previewHeight:number=360;
  private pointer:number=-1;private edgeX:string='';private edgeY:string='';private before:number[]=[];
  onApply:(left:number,top:number,right:number,bottom:number)=>void=()=>{};
  aboutToAppear():void{this.leftValue=this.initialLeft;this.topValue=this.initialTop;this.rightValue=this.initialRight;this.bottomValue=this.initialBottom;}
  private imageWidth():number{return Math.min(this.previewWidth,this.previewHeight*Math.max(.01,this.imageRatio));}
  private imageHeight():number{return this.imageWidth()/Math.max(.01,this.imageRatio);}
  private imageX():number{return (this.previewWidth-this.imageWidth())/2;}
  private imageY():number{return (this.previewHeight-this.imageHeight())/2;}
  private cropX():number{return this.imageX()+this.imageWidth()*this.leftValue;}
  private cropY():number{return this.imageY()+this.imageHeight()*this.topValue;}
  private cropW():number{return this.imageWidth()*(this.rightValue-this.leftValue);}
  private cropH():number{return this.imageHeight()*(this.bottomValue-this.topValue);}
  private resetPointer(restore:boolean):void{if(restore&&this.before.length===4){this.leftValue=this.before[0];this.topValue=this.before[1];this.rightValue=this.before[2];this.bottomValue=this.before[3];}this.pointer=-1;this.edgeX='';this.edgeY='';this.before=[];}
  private touch(e:TouchEvent):void{
    if(e.type===TouchType.Cancel||e.touches.length>1){this.resetPointer(true);return;}
    if(e.type===TouchType.Down&&e.touches.length===1){const p=e.touches[0];this.pointer=p.id;this.before=[this.leftValue,this.topValue,this.rightValue,this.bottomValue];
      const left=Math.abs(p.x-this.cropX()),right=Math.abs(p.x-this.cropX()-this.cropW()),top=Math.abs(p.y-this.cropY()),bottom=Math.abs(p.y-this.cropY()-this.cropH());
      this.edgeX=Math.min(left,right)<=28?(left<=right?'left':'right'):'';this.edgeY=Math.min(top,bottom)<=28?(top<=bottom?'top':'bottom'):'';
      if(!this.edgeX&&!this.edgeY)this.resetPointer(false);return;}
    if(this.pointer<0)return;const point=(e.type===TouchType.Up?e.changedTouches:e.touches).find((p:TouchObject)=>p.id===this.pointer);if(!point){this.resetPointer(true);return;}
    const x=Math.max(0,Math.min(1,(point.x-this.imageX())/this.imageWidth())),y=Math.max(0,Math.min(1,(point.y-this.imageY())/this.imageHeight()));
    if(this.edgeX==='left')this.leftValue=Math.min(x,this.rightValue-.01);else if(this.edgeX==='right')this.rightValue=Math.max(x,this.leftValue+.01);
    if(this.edgeY==='top')this.topValue=Math.min(y,this.bottomValue-.01);else if(this.edgeY==='bottom')this.bottomValue=Math.max(y,this.topValue+.01);
    if(e.type===TouchType.Up)this.resetPointer(false);
  }
  @Builder handle(x:number,y:number){Rect().width(12).height(12).fill(SITE_ACCENT).position({x:x-6,y:y-6}).hitTestBehavior(HitTestMode.None);}
  build(){Column({space:12}){
    Text('裁剪照片').fontSize(20).fontWeight(FontWeight.Bold).fontColor(SITE_TEXT);
    Stack({alignContent:Alignment.TopStart}){
      Image('file://'+this.previewPath).width('100%').height('100%').objectFit(ImageFit.Contain).hitTestBehavior(HitTestMode.None);
      Rect().width(this.cropW()).height(this.cropH()).fill(Color.Transparent).stroke(SITE_ACCENT).strokeWidth(2).position({x:this.cropX(),y:this.cropY()}).hitTestBehavior(HitTestMode.None);
      this.handle(this.cropX(),this.cropY());this.handle(this.cropX()+this.cropW(),this.cropY());this.handle(this.cropX(),this.cropY()+this.cropH());this.handle(this.cropX()+this.cropW(),this.cropY()+this.cropH());
      this.handle(this.cropX()+this.cropW()/2,this.cropY());this.handle(this.cropX()+this.cropW()/2,this.cropY()+this.cropH());this.handle(this.cropX(),this.cropY()+this.cropH()/2);this.handle(this.cropX()+this.cropW(),this.cropY()+this.cropH()/2);
    }.width('100%').layoutWeight(1).backgroundColor(Color.Black).onAreaChange((a,b)=>{this.previewWidth=Number(b.width);this.previewHeight=Number(b.height);}).onTouch((e:TouchEvent)=>this.touch(e));
    Row({space:12}){Button('取消').layoutWeight(1).height(48).fontColor(SITE_TEXT).backgroundColor(SITE_CARD).onClick(()=>this.controller.close());Button('应用').layoutWeight(1).height(48).fontColor('#231A00').backgroundColor(SITE_ACCENT).onClick(()=>{this.onApply(this.leftValue,this.topValue,this.rightValue,this.bottomValue);this.controller.close();});}.width('100%');
  }.width('94%').constraintSize({maxWidth:840}).height(Math.max(180,this.maxHeight)).padding(18).backgroundColor(SITE_CARD).borderRadius(16);}
}
