import { QUALITY_NAMES } from '../../core/Models';
import { SITE_ACCENT, PAGE_CARD as SITE_CARD, PAGE_MUTED as SITE_MUTED, PAGE_TEXT as SITE_TEXT } from './UiPrimitives';

@CustomDialog
export struct ProjectExportDialog {
  controller: CustomDialogController;
  @State quality: number = -1;
  @State folder: boolean = false;
  onConfirm: (folder: boolean, quality: number) => void = (folder: boolean, quality: number) => {};
  build() {
    Scroll(){Column({ space: 12 }) {
      Text('工程资料导出').fontSize(20).fontWeight(FontWeight.Bold).fontColor(SITE_TEXT);
      Text('导出质量').fontSize(14).fontColor(SITE_TEXT);
      Flex({wrap:FlexWrap.Wrap}) { ForEach(['原文件',...QUALITY_NAMES],(label: string,index: number)=>{Button(label).fontSize(11).fontColor(this.quality===index-1?'#231A00':SITE_TEXT).backgroundColor(this.quality===index-1?SITE_ACCENT:'#303030').margin(4).constraintSize({minHeight:44}).onClick(()=>this.quality=index-1);},(label: string)=>label); }.width('100%');
      Row({ space: 8 }) { Button('ZIP 压缩包').layoutWeight(1).fontColor(!this.folder?'#231A00':SITE_TEXT).backgroundColor(!this.folder?SITE_ACCENT:'#303030').onClick(()=>this.folder=false);Button('总文件夹').layoutWeight(1).fontColor(this.folder?'#231A00':SITE_TEXT).backgroundColor(this.folder?SITE_ACCENT:'#303030').onClick(()=>this.folder=true); }.width('100%');
      Flex({wrap:FlexWrap.Wrap}) { Button('取消').fontColor(SITE_MUTED).backgroundColor(Color.Transparent).onClick(()=>this.controller.close());Button('选择位置并导出').fontColor('#231A00').backgroundColor(SITE_ACCENT).onClick(()=>{this.controller.close();this.onConfirm(this.folder,this.quality);}); }.width('100%');
    }.padding(18).backgroundColor(SITE_CARD).borderRadius(14).width('92%');
    }.edgeEffect(EdgeEffect.Spring).width('100%').constraintSize({maxWidth:600,maxHeight:'90%'}).align(Alignment.Top).scrollBar(BarState.Off);
  }
}
