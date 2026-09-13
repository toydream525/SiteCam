import { SITE_ACCENT, SITE_CARD, SITE_MUTED, SITE_TEXT } from './UiPrimitives';

class CropRect {
  x: number = 0;
  y: number = 0;
  width: number = 0;
  height: number = 0;
}

@CustomDialog
export struct CropDialog {
  controller: CustomDialogController;
  @Prop previewPath: string = '';
  @Prop imageRatio: number = 1;
  @Prop initialLeft: number = 0;
  @Prop initialTop: number = 0;
  @Prop initialRight: number = 1;
  @Prop initialBottom: number = 1;
  @State leftValue: number = 0;
  @State topValue: number = 0;
  @State rightValue: number = 1;
  @State bottomValue: number = 1;
  @State previewWidth: number = 0;
  @State previewHeight: number = 170;
  onApply: (left:number,top:number,right:number,bottom:number)=>void = (left:number,top:number,right:number,bottom:number)=>{};
  aboutToAppear(): void {
    this.leftValue=this.initialLeft; this.topValue=this.initialTop;
    this.rightValue=this.initialRight; this.bottomValue=this.initialBottom;
  }
  private imageRect(): CropRect {
    const boxW = this.previewWidth > 0 ? this.previewWidth : 320;
    const boxH = this.previewHeight;
    const ratio = this.imageRatio > 0 ? this.imageRatio : 1;
    const width = Math.min(boxW, boxH * ratio);
    const height = Math.min(boxH, boxW / ratio);
    const rect = new CropRect();
    rect.x = (boxW-width)/2; rect.y = (boxH-height)/2; rect.width = width; rect.height = height;
    return rect;
  }
  private safeBounds(): void {
    this.leftValue = Math.max(0, Math.min(this.leftValue, this.rightValue - 0.01));
    this.topValue = Math.max(0, Math.min(this.topValue, this.bottomValue - 0.01));
    this.rightValue = Math.min(1, Math.max(this.rightValue, this.leftValue + 0.01));
    this.bottomValue = Math.min(1, Math.max(this.bottomValue, this.topValue + 0.01));
  }
  build() {
    Scroll() { Column({space:8}) {
      Text('裁剪照片').fontSize(20).fontWeight(FontWeight.Bold).fontColor(SITE_TEXT);
      Text('拖动范围由四个边界控制，预览框显示将保留的区域').fontSize(12).fontColor(SITE_MUTED);
      Stack() {
        if (this.previewPath) Image('file://'+this.previewPath).width('100%').height(170).objectFit(ImageFit.Contain);
        else Blank().width('100%').height(170).backgroundColor('#101010');
        Column(){Blank().width('100%').height('100%').border({width:2,color:SITE_ACCENT});}
          .width(Math.max(1,this.imageRect().width*(this.rightValue-this.leftValue)))
          .height(Math.max(1,this.imageRect().height*(this.bottomValue-this.topValue)))
          .position({x:this.imageRect().x+this.imageRect().width*this.leftValue,y:this.imageRect().y+this.imageRect().height*this.topValue});
        Text(`${Math.round(this.leftValue*100)}%  ${Math.round(this.topValue*100)}%  -  ${Math.round(this.rightValue*100)}%  ${Math.round(this.bottomValue*100)}%`)
          .fontSize(11).fontColor(SITE_ACCENT).backgroundColor('#99000000').padding(4).align(Alignment.Bottom);
      }.width('100%').height(170).backgroundColor('#10171D').borderRadius(8)
        .onAreaChange((oldArea, newArea)=>{this.previewWidth=Number(newArea.width);this.previewHeight=Number(newArea.height);});
      Text('左边界').fontColor(SITE_MUTED);
      Slider({value:this.leftValue,min:0,max:Math.max(0.01,this.rightValue-.01),step:.01}).selectedColor('#FFB300').trackColor('#505050').blockColor('#FFFFFF').onChange((v:number)=>{this.leftValue=v;this.safeBounds();});
      Text('上边界').fontColor(SITE_MUTED);
      Slider({value:this.topValue,min:0,max:Math.max(0.01,this.bottomValue-.01),step:.01}).selectedColor('#FFB300').trackColor('#505050').blockColor('#FFFFFF').onChange((v:number)=>{this.topValue=v;this.safeBounds();});
      Text('右边界').fontColor(SITE_MUTED);
      Slider({value:this.rightValue,min:Math.min(.99,this.leftValue+.01),max:1,step:.01}).selectedColor('#FFB300').trackColor('#505050').blockColor('#FFFFFF').onChange((v:number)=>{this.rightValue=v;this.safeBounds();});
      Text('下边界').fontColor(SITE_MUTED);
      Slider({value:this.bottomValue,min:Math.min(.99,this.topValue+.01),max:1,step:.01}).selectedColor('#FFB300').trackColor('#505050').blockColor('#FFFFFF').onChange((v:number)=>{this.bottomValue=v;this.safeBounds();});
      Row({space:10}) {
        Button('取消').layoutWeight(1).backgroundColor(SITE_CARD).fontColor(SITE_TEXT).onClick(()=>this.controller.close());
        Button('应用').layoutWeight(1).backgroundColor(SITE_ACCENT).fontColor('#231A00').onClick(()=>{this.safeBounds();this.controller.close();this.onApply(this.leftValue,this.topValue,this.rightValue,this.bottomValue);});
      }.width('100%');
    }.width('100%').padding(18).backgroundColor(SITE_CARD).borderRadius(14); }.width('90%').height('90%').scrollable(ScrollDirection.Vertical).scrollBar(BarState.Off);
  }
}
