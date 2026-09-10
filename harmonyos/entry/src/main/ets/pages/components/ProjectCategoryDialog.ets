import { SITE_ACCENT, SITE_CARD, SITE_MUTED, SITE_TEXT } from './UiPrimitives';

@CustomDialog
export struct ProjectCategoryDialog {
  controller: CustomDialogController;
  @Prop categories: string[] = [];
  onSelect: (category: string) => void = (category: string) => {};
  onCancel: () => void = () => {};
  build() {
    Scroll(){Column({ space: 8 }) {
      Text('批量分类').fontSize(20).fontWeight(FontWeight.Bold).fontColor(SITE_TEXT);
      Text('给选中的工程统一设置类别').fontSize(13).fontColor(SITE_MUTED);
      Scroll() {
        Column({ space: 5 }) {
          ForEach(this.categories, (category: string) => {
            Button(category).width('100%').height(40).fontSize(13).fontColor(SITE_TEXT).backgroundColor('#303030').onClick(() => { this.controller.close(); this.onSelect(category); });
          }, (category: string) => category);
        }.width('100%');
      }.width('100%').constraintSize({ maxHeight: 260 });
      Button('取消').width('100%').fontColor(SITE_ACCENT).backgroundColor(Color.Transparent).onClick(() => { this.controller.close(); this.onCancel(); });
    }.padding(18).width('90%').backgroundColor(SITE_CARD).borderRadius(14);
    }.width('100%').constraintSize({maxWidth:600,maxHeight:'90%'}).align(Alignment.Top);
  }
}
