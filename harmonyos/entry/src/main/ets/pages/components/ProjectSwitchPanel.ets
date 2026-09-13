import { Project } from '../../core/Models';
import { SITE_ACCENT, SITE_CARD, SITE_CARD_RAISED, SITE_MUTED, SITE_TEXT } from './UiPrimitives';

/**
 * The camera's small project chooser.  Viewing a project and making it the
 * capture project are separate actions so opening a card never changes where
 * the next capture is stored.
 */
@Component
export struct ProjectSwitchPanel {
  @Prop projects: Project[] = [];
  @Prop currentProjectId: number = 0;
  @Prop switchingId: number = 0;
  @Prop fontScale: number = 1;
  @Prop panelWidth: number = 320;
  @Prop panelHeight: number = 480;
  onSwitch: (project: Project) => void = (project: Project) => {};
  onManage: () => void = () => {};
  onCreate: () => void = () => {};
  onClose: () => void = () => {};

  private projectStatus(project: Project): string {
    const labels: string[] = [];
    if (project.id === this.currentProjectId) labels.push('当前工程');
    if (project.locked) labels.push('已锁定拍摄');
    if (project.archived) labels.push('已归档');
    return labels.length ? labels.join(' · ') : '可拍摄';
  }

  private actionLabel(project: Project): string {
    return project.id === this.currentProjectId ? '返回当前工程' : '切换到此工程';
  }

  private compactPanel(): boolean {
    return this.panelWidth < 280 || this.panelHeight < 300;
  }

  build() {
    if (this.compactPanel()) {
      Scroll() {
        Column({ space: 10 }) {
          Column({ space: 2 }) {
            Text('切换工程包').fontSize(this.fontScale > 1.2 ? 20 : 18).fontWeight(FontWeight.Bold).fontColor(SITE_TEXT)
              .width('100%').maxLines(2).textOverflow({ overflow: TextOverflow.Ellipsis });
            Text('选择后，新拍照片和录像归入该工程').fontSize(12).fontColor(SITE_MUTED)
              .width('100%').maxLines(2).textOverflow({ overflow: TextOverflow.Ellipsis });
          }.width('100%').alignItems(HorizontalAlign.Start);
          Button('关闭').width('100%').height(40).fontSize(13).fontColor(SITE_MUTED).backgroundColor(Color.Transparent)
            .onClick(() => this.onClose());
          if (this.projects.length === 0) {
            Text('暂无可选工程，请先新建工程包。').fontSize(14).fontColor(SITE_MUTED).width('100%');
          } else {
            ForEach(this.projects, (project: Project) => {
              Column({ space: 6 }) {
                Text(project.name || '未命名工程').fontSize(this.fontScale > 1.2 ? 17 : 15).fontWeight(FontWeight.Bold)
                  .fontColor(SITE_TEXT).width('100%').maxLines(2).textOverflow({ overflow: TextOverflow.Ellipsis });
                Text((project.route || '未填写线路') + ' · ' + this.projectStatus(project)).fontSize(12)
                  .fontColor(project.id === this.currentProjectId ? SITE_ACCENT : SITE_MUTED).width('100%').maxLines(2)
                  .textOverflow({ overflow: TextOverflow.Ellipsis });
                Button(this.actionLabel(project)).width('100%').height(this.fontScale > 1.2 ? 44 : 40).fontSize(12)
                  .fontColor(project.id === this.currentProjectId ? '#231A00' : SITE_TEXT)
                  .backgroundColor(project.id === this.currentProjectId ? SITE_ACCENT : SITE_CARD_RAISED)
                  .enabled(this.switchingId === 0).onClick(() => this.onSwitch(project));
              }.width('100%').padding(8).backgroundColor(SITE_CARD).borderRadius(10)
                .border({ width: project.id === this.currentProjectId ? 1 : 0, color: SITE_ACCENT });
            }, (project: Project) => project.id.toString());
          }
          if (this.switchingId !== 0) {
            Text('切换中，请稍候…').fontSize(13).fontColor(SITE_ACCENT).width('100%');
          }
          Button('全部工程包').width('100%').height(42).fontSize(13).fontColor(SITE_TEXT).backgroundColor(SITE_CARD_RAISED)
            .enabled(this.switchingId === 0).onClick(() => this.onManage());
          Button('新建工程').width('100%').height(42).fontSize(13).fontColor('#231A00').backgroundColor(SITE_ACCENT)
            .enabled(this.switchingId === 0).onClick(() => this.onCreate());
        }.width('100%').padding(12);
      }.width(this.panelWidth).height(this.panelHeight).scrollBar(BarState.Off)
        .constraintSize({ maxWidth: this.panelWidth, maxHeight: this.panelHeight }).backgroundColor('#171717').borderRadius(16);
    } else {
      Column({ space: 10 }) {
        Row({ space: 8 }) {
          Column({ space: 2 }) {
            Text('切换工程包').fontSize(this.fontScale > 1.2 ? 21 : 19).fontWeight(FontWeight.Bold).fontColor(SITE_TEXT);
            Text('选择后，新拍照片和录像归入该工程').fontSize(12).fontColor(SITE_MUTED);
          }.layoutWeight(1).alignItems(HorizontalAlign.Start);
          Button('关闭').fontSize(13).fontColor(SITE_MUTED).backgroundColor(Color.Transparent)
            .onClick(() => this.onClose());
        }.width('100%');
        Scroll() {
          Column({ space: 6 }) {
            if (this.projects.length === 0) {
              Text('暂无可选工程，请先新建工程包。').fontSize(14).fontColor(SITE_MUTED).padding(16);
            } else {
              ForEach(this.projects, (project: Project) => {
                Row({ space: 8 }) {
                  Column({ space: 3 }) {
                    Text(project.name || '未命名工程').fontSize(this.fontScale > 1.2 ? 17 : 15).fontWeight(FontWeight.Bold)
                      .fontColor(SITE_TEXT).maxLines(this.fontScale > 1.2 ? 2 : 1).textOverflow({ overflow: TextOverflow.Ellipsis });
                    Text((project.route || '未填写线路') + ' · ' + this.projectStatus(project)).fontSize(12).fontColor(project.id === this.currentProjectId ? SITE_ACCENT : SITE_MUTED)
                      .maxLines(this.fontScale > 1.2 ? 2 : 1).textOverflow({ overflow: TextOverflow.Ellipsis });
                  }.layoutWeight(1).alignItems(HorizontalAlign.Start);
                  Button(this.actionLabel(project)).height(this.fontScale > 1.2 ? 44 : 38).fontSize(12)
                    .fontColor(project.id === this.currentProjectId ? '#231A00' : SITE_TEXT)
                    .backgroundColor(project.id === this.currentProjectId ? SITE_ACCENT : SITE_CARD_RAISED)
                    .enabled(this.switchingId === 0)
                    .onClick(() => this.onSwitch(project));
                }.width('100%').padding({ left: 10, right: 8, top: 8, bottom: 8 }).backgroundColor(SITE_CARD).borderRadius(10)
                  .border({ width: project.id === this.currentProjectId ? 1 : 0, color: SITE_ACCENT });
              }, (project: Project) => project.id.toString());
            }
          }.width('100%');
        }.width('100%').layoutWeight(1).constraintSize({ minHeight: 56 }).align(Alignment.Top).scrollBar(BarState.Off);
        if (this.switchingId !== 0) {
          Text('切换中，请稍候…').fontSize(13).fontColor(SITE_ACCENT).width('100%');
        }
        Row({ space: 8 }) {
          Button('全部工程包').layoutWeight(1).height(42).fontSize(13).fontColor(SITE_TEXT).backgroundColor(SITE_CARD_RAISED)
            .enabled(this.switchingId === 0).onClick(() => this.onManage());
          Button('新建工程').layoutWeight(1).height(42).fontSize(13).fontColor('#231A00').backgroundColor(SITE_ACCENT)
            .enabled(this.switchingId === 0).onClick(() => this.onCreate());
        }.width('100%');
      }.padding(16).width(this.panelWidth).height(this.panelHeight).constraintSize({ maxWidth: this.panelWidth, maxHeight: this.panelHeight }).backgroundColor('#171717').borderRadius(16);
    }
  }
}
