import { SITE_ACCENT, SITE_CARD_RAISED, SITE_MUTED, SITE_TEXT } from './UiPrimitives';

/** Non-modal, short-lived undo affordance for a project status change. */
@Component
export struct ProjectUndoBar {
  @Prop message: string = '';
  @Prop barWidth: number = 320;
  onUndo: () => void = () => {};
  onDismiss: () => void = () => {};
  build() {
    Row({ space: 8 }) {
      Text(this.message).fontSize(13).fontColor(SITE_TEXT).layoutWeight(1).maxLines(2).textOverflow({ overflow: TextOverflow.Ellipsis });
      Button('撤销').height(36).fontSize(13).fontColor('#231A00').backgroundColor(SITE_ACCENT).onClick(() => this.onUndo());
      Button('关闭').height(36).fontSize(12).fontColor(SITE_MUTED).backgroundColor(Color.Transparent).onClick(() => this.onDismiss());
    }.width(this.barWidth).padding({ left: 12, right: 8, top: 8, bottom: 8 }).backgroundColor(SITE_CARD_RAISED).borderRadius(10)
      .border({ width: 1, color: SITE_ACCENT });
  }
}
