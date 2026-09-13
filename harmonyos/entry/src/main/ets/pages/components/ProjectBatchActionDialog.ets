import { SITE_ACCENT, SITE_CARD, SITE_MUTED, SITE_TEXT } from './UiPrimitives';

@CustomDialog
export struct ProjectBatchActionDialog {
  controller: CustomDialogController;
  @Prop selectedCount: number = 0;
  onAction: (action: string) => void = (action: string) => {};
  onCancel: () => void = () => {};
  private actionTitle(action: string): string {
    if (action === 'category') return '批量分类';
    if (action === 'archive') return '归档';
    if (action === 'restore') return '恢复';
    if (action === 'lock') return '锁定拍摄';
    if (action === 'unlock') return '解锁拍摄';
    return '删除工程';
  }
  private actionDescription(action: string): string {
    if (action === 'category') return '给选中的工程统一设置类别';
    if (action === 'archive') return '把选中的工程移入归档';
    if (action === 'restore') return '把选中的工程放回进行中';
    if (action === 'lock') return '暂停选中工程继续拍摄录像';
    if (action === 'unlock') return '允许选中工程继续拍摄录像';
    return '删除工程及其照片、视频和编辑成品';
  }
  private choose(action: string): void { this.controller.close(); this.onAction(action); }
  build() {
    Scroll(){Column({ space: 6 }) {
      Text('批量管理').fontSize(20).fontWeight(FontWeight.Bold).fontColor(SITE_TEXT);
      Text(`已选 ${this.selectedCount} 项`).fontSize(13).fontColor(SITE_MUTED);
      Scroll() {
        Column({ space: 2 }) {
          ForEach(['category','archive','restore','lock','unlock','delete'], (action: string) => {
            Column({ space: 2 }) {
              Text(this.actionTitle(action)).fontSize(15).fontColor(action === 'delete' ? '#F44336' : SITE_TEXT).width('100%');
              Text(this.actionDescription(action)).fontSize(12).fontColor(SITE_MUTED).width('100%');
            }.width('100%').padding({ top: 8, bottom: 8 }).onClick(() => this.choose(action));
          }, (action: string) => action);
        }.width('100%');
      }.width('100%').constraintSize({ maxHeight: 360 }).scrollBar(BarState.Off);
      Button('取消').width('100%').fontColor(SITE_MUTED).backgroundColor(Color.Transparent).onClick(() => { this.controller.close(); this.onCancel(); });
    }.padding(18).width('90%').backgroundColor(SITE_CARD).borderRadius(14);
    }.width('100%').constraintSize({maxWidth:600,maxHeight:'90%'}).align(Alignment.Top).scrollBar(BarState.Off);
  }
}
