import { Project } from '../../core/Models';
import { SITE_CARD, SITE_MUTED, SITE_TEXT } from './UiPrimitives';

@CustomDialog
export struct ProjectMoveDialog {
  controller: CustomDialogController;
  @Prop projects: Project[] = [];
  onSelect: (projectId: number) => void = (projectId: number) => {};
  onCancel: () => void = () => {};
  private projectLabel(project: Project): string {
    let label: string = project.name + ' · ' + (project.category || '未分类');
    if (project.locked) label += ' · 拍摄锁定';
    if (project.archived) label += ' · 已归档';
    return label;
  }
  build() {
    Scroll(){Column({ space: 8 }) {
      Text('移入工程（含锁定工程）').fontSize(20).fontWeight(FontWeight.Bold).fontColor(SITE_TEXT);
      Text('选择资料要归入的工程').fontSize(13).fontColor(SITE_MUTED);
      Scroll() {
        Column({ space: 4 }) {
          ForEach(this.projects, (project: Project) => {
            Button(this.projectLabel(project)).width('100%').height(44).fontSize(13).fontColor(SITE_TEXT).backgroundColor('#303030').onClick(() => { this.controller.close(); this.onSelect(project.id); });
          }, (project: Project) => project.id.toString());
        }.width('100%');
      }.width('100%').height(260);
      Button('取消').width('100%').fontColor(SITE_MUTED).backgroundColor(Color.Transparent).onClick(() => { this.controller.close(); this.onCancel(); });
    }.padding(18).width('92%').backgroundColor(SITE_CARD).borderRadius(14);
    }.width('100%').constraintSize({maxWidth:600,maxHeight:'90%'}).align(Alignment.Top);
  }
}
