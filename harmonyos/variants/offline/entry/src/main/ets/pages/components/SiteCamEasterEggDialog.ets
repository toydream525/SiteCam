import { SITE_ACCENT, SITE_CARD, SITE_MUTED, SITE_TEXT } from './UiPrimitives';

@CustomDialog
export struct SiteCamEasterEggDialog {
  controller: CustomDialogController;
  onOpenStar: () => void = () => {};
  onOpenIssue: () => void = () => {};
  onCopyProject: () => void = () => {};
  onOpenWebsite: () => void = () => {};
  @State sparkOpacity: number = 0.65;

  aboutToAppear(): void {
    this.getUIContext().animateTo({ duration: 650, curve: Curve.EaseInOut }, () => this.sparkOpacity = 1);
  }

  build() {
    Scroll() {
      Column({ space: 14 }) {
        Stack({ alignContent: Alignment.Center }) {
          Circle().width(66).height(66).fill('#22FFB300').opacity(this.sparkOpacity);
          Rect().width(34).height(34).fill(Color.Transparent).border({ width: 2, color: SITE_ACCENT }).borderRadius(8);
          Text('✦').fontSize(18).fontColor(SITE_ACCENT).opacity(this.sparkOpacity).position({ x: 42, y: 3 });
          Text('✧').fontSize(14).fontColor(SITE_TEXT).position({ x: 4, y: 11 });
        }.width(72).height(72);
        Text('咔嚓，发现彩蛋！').fontSize(21).fontWeight(FontWeight.Bold).fontColor(SITE_TEXT).textAlign(TextAlign.Center);
        Text('作者：ninjaaqua · @toydream525').fontSize(14).fontColor(SITE_ACCENT).textAlign(TextAlign.Center).width('100%');
        Text('项目地址：https://github.com/toydream525/SiteCam。离线版不打开外部网页，你可以复制链接后在其他联网环境查看。')
          .fontSize(14).lineHeight(21).fontColor(SITE_MUTED).textAlign(TextAlign.Center).width('100%');
        Button('复制项目链接').width('100%').height(46).fontColor('#231A00').backgroundColor(SITE_ACCENT).onClick(this.onCopyProject);
        Button('关闭').width('100%').height(44).fontColor(SITE_MUTED).backgroundColor(Color.Transparent).onClick(() => this.controller.close());
      }.width('100%').padding(20).backgroundColor(SITE_CARD).borderRadius(18).alignItems(HorizontalAlign.Center);
    }.width('92%').constraintSize({ maxWidth: 460, maxHeight: '90%' }).scrollBar(BarState.Off).align(Alignment.Center);
  }
}
