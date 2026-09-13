import { Project, MediaItem, Style, STYLES } from '../../core/Models';
import { SITE_ACCENT, SITE_CARD, SITE_CARD_RAISED, SITE_MUTED, SITE_TEXT, SiteChip } from './UiPrimitives';

/** Project card used by the phone and large-window project layouts. */
@Component
export struct ProjectCardView {
  @Prop project: Project = new Project();
  @Prop current: boolean = false;
  @Prop checked: boolean = false;
  @Prop batchMode: boolean = false;
  onOpen: () => void = () => {};
  onEdit: () => void = () => {};
  onExport: () => void = () => {};
  onChecked: () => void = () => {};
  onMenu: () => void = () => {};
  build() {
    Column({ space: 8 }) {
      Row({ space: 8 }) {
        if (this.batchMode) Checkbox().select(this.checked).onChange(() => this.onChecked());
        Text(this.project.name).fontSize(17).fontWeight(FontWeight.Bold).fontColor(SITE_TEXT).flexShrink(1).maxLines(1);
        if (this.current) Text('✓ 当前').fontSize(11).fontColor('#4CAF50').backgroundColor('#264D2C').padding({left:5,right:5,top:3,bottom:3}).borderRadius(5).border({width:1,color:'#4CAF50'});
      }
      Row() {
        Column({ space: 5 }) {
          Text(this.project.category + '  ·  ' + (this.project.route || '未填写线路')).fontSize(13).fontColor(SITE_MUTED);
          Text(this.project.count ? `${this.project.photoCount} 照片 · ${this.project.videoCount} 视频 · ${this.project.issueCount} 问题` : '尚无拍摄资料').fontSize(12).fontColor(SITE_MUTED);
        }.layoutWeight(1).alignItems(HorizontalAlign.Start).onClick(() => this.onOpen());
        Column({ space: 4 }) {
          Button('编辑').fontColor(SITE_TEXT).backgroundColor(SITE_CARD_RAISED).onClick(() => this.onEdit());
          Button('导出').fontColor('#231A00').backgroundColor(SITE_ACCENT).onClick(() => this.onExport());
        }
        if (!this.batchMode) Image($r('app.media.ic_more')).width(44).height(48).objectFit(ImageFit.Contain).onClick(() => this.onMenu());
      }
    }.padding(14).backgroundColor(SITE_CARD).borderRadius(12)
      .border({ width: this.current ? 2 : 0, color: SITE_ACCENT })
      .gesture(LongPressGesture({ repeat: false }).onAction(() => this.onChecked()));
  }
}

/** Shared horizontal filter row. Keep all filtering in the parent state. */
@Component
export struct GalleryFilterBar {
  @Prop active: string = '全部';
  @Prop labels: string[] = ['全部', '今天', '问题', '日期 / 范围'];
  onPick: (value: string) => void = (value: string) => {};
  build() {
    Scroll() {
      Row({ space: 8 }) {
        ForEach(this.labels, (label: string) => {
          SiteChip({ label: label, selected: this.active === label, onSelect: () => this.onPick(label) });
        }, (label: string) => label);
      }
    }.scrollable(ScrollDirection.Horizontal).scrollBar(BarState.Off).width('100%');
  }
}

@Component
export struct EditorToolBar {
  @Prop selected: string = 'arrow';
  @Prop color: string = '#FFB300';
  onTool: (tool: string) => void = (tool: string) => {};
  onColor: (color: string) => void = (value: string) => {};
  build() {
    Column({ space: 12 }) {
      Row({ space: 6 }) {
        ForEach(['arrow', 'rect', 'circle', 'brush', 'text', 'mosaic'], (tool: string) => {
          Button(tool === 'arrow' ? '↗\n箭头' : tool === 'rect' ? '□\n矩形' : tool === 'circle' ? '○\n圆圈' : tool === 'brush' ? '✎\n画笔' : tool === 'text' ? 'A\n文字' : '•••\n脱敏')
            .layoutWeight(1).height(58).fontSize(12).fontColor(this.selected === tool ? '#231A00' : SITE_TEXT)
            .backgroundColor(this.selected === tool ? SITE_ACCENT : SITE_CARD_RAISED).onClick(() => this.onTool(tool));
        }, (tool: string) => tool);
      }
      Row({ space: 12 }) {
        ForEach(['#F44336', '#FF9800', '#FFC107', '#4CAF50', '#FFFFFF'], (value: string) => {
          Button('●').fontSize(24).fontColor(value).backgroundColor(SITE_CARD_RAISED).border({ width: this.color === value ? 2 : 0, color: SITE_TEXT }).onClick(() => this.onColor(value));
        }, (value: string) => value);
      }
    }.width('100%').padding(12).backgroundColor(SITE_CARD);
  }
}

@Component
export struct WatermarkStyleGrid {
  @Prop selected: string = 'CLASSIC';
  onPick: (style: Style) => void = (style: Style) => {};
  build() {
    GridRow({ columns: 2, gutter: 10 }) {
      ForEach(STYLES, (style: Style) => {
        GridCol() {
          Column({ space: 5 }) {
            Text(style.name).fontSize(14).fontColor(SITE_TEXT);
            Text(style.id).fontSize(11).fontColor(SITE_MUTED);
          }.padding(12).width('100%').backgroundColor(style.background).borderRadius(8)
            .border({ width: this.selected === style.id ? 2 : 0, color: SITE_ACCENT })
            .onClick(() => this.onPick(style));
        }
      }, (style: Style) => style.id);
    }
  }
}
