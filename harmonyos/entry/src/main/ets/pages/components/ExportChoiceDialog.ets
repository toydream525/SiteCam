import { QUALITY_NAMES } from '../../core/Models';
import { SITE_ACCENT, SITE_CARD, SITE_MUTED, SITE_TEXT } from './UiPrimitives';

@CustomDialog
export struct ExportChoiceDialog {
  controller: CustomDialogController;
  @State quality: number = -1;
  onZip: (quality: number) => void = (quality: number) => {};
  onFolder: (quality: number) => void = (quality: number) => {};
  onCancel: () => void = () => {};
  private labels(): string[] { return ['保持原文件（默认）'].concat(QUALITY_NAMES); }
  private qualityForLabel(label: string): number { return this.labels().indexOf(label) - 1; }
  private chooseZip(): void { this.controller.close(); this.onZip(this.quality); }
  private chooseFolder(): void { this.controller.close(); this.onFolder(this.quality); }
  build() {
    Scroll(){Column({ space: 10 }) {
      Text('导出工程档案').fontSize(20).fontWeight(FontWeight.Bold).fontColor(SITE_TEXT);
      Text('导出副本不会修改原始照片，视频保持原文件。').fontSize(13).fontColor(SITE_MUTED);
      Scroll() {
        Column({ space: 6 }) {
          ForEach(this.labels(), (label: string) => {
            Button(label).width('100%').height(36).fontSize(12).fontColor(this.quality === this.qualityForLabel(label) ? '#231A00' : SITE_TEXT).backgroundColor(this.quality === this.qualityForLabel(label) ? SITE_ACCENT : '#303030').onClick(() => { this.quality = this.qualityForLabel(label); });
          }, (label: string) => label);
        }.width('100%');
      }.width('100%').constraintSize({ maxHeight: 260 }).scrollBar(BarState.Off);
      Row({ space: 6 }) {
        Button('一个 ZIP').layoutWeight(1).fontColor('#231A00').backgroundColor(SITE_ACCENT).onClick(() => this.chooseZip());
        Button('一个总文件夹').layoutWeight(1).fontColor(SITE_TEXT).backgroundColor('#303030').onClick(() => this.chooseFolder());
      }.width('100%');
      Button('取消').width('100%').fontColor(SITE_MUTED).backgroundColor(Color.Transparent).onClick(() => { this.controller.close(); this.onCancel(); });
    }.padding(18).width('92%').backgroundColor(SITE_CARD).borderRadius(14);
    }.width('100%').constraintSize({maxWidth:600,maxHeight:'90%'}).align(Alignment.Top).scrollBar(BarState.Off);
  }
}
