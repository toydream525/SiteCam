import { SITE_ACCENT, SITE_CARD, SITE_TEXT } from './UiPrimitives';
@CustomDialog
export struct OrientationDialog {
  controller: CustomDialogController;
  @Prop maxHeight: number=520;
  @Prop selected: string = 'AUTO';
  onSelect: (value: string) => void = (value: string) => {};
  build() {
    Scroll(){Column({ space: 8 }) {
      Text('拍摄方向').fontSize(20).fontWeight(FontWeight.Bold).fontColor(SITE_TEXT);
      ForEach(['AUTO','PORTRAIT','LEFT','RIGHT'],(value:string)=>{Button(value==='AUTO'?'自动方向':value==='PORTRAIT'?'竖屏':value==='LEFT'?'左横屏':'右横屏').width('100%').fontColor(this.selected===value?'#231A00':SITE_TEXT).backgroundColor(this.selected===value?SITE_ACCENT:'#303030').onClick(()=>{this.controller.close();this.onSelect(value);});},(value:string)=>value);
    }.padding(18).backgroundColor(SITE_CARD).borderRadius(14).width('100%');}.width('88%').constraintSize({maxWidth:480,maxHeight:this.maxHeight}).scrollBar(BarState.Off).align(Alignment.Top);
  }
}
