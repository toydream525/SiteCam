/** Small reusable native controls shared by every SiteCam page. */
export const SITE_BG: string = '#121212';
export const SITE_CARD: string = '#1E1E1E';
export const SITE_CARD_RAISED: string = '#282828';
export const SITE_ACCENT: string = '#FFB300';
export const SITE_TEXT: string = '#FFFFFF';
export const SITE_MUTED: string = '#B0BEC5';

@Component
export struct SiteTopBar {
  @Prop title: string = '';
  @Prop subtitle: string = '';
  @Prop showBack: boolean = false;
  onBack: () => void = () => {};
  build() {
    Row({ space: 12 }) {
      if (this.showBack) {
        Button('‹')
          .fontSize(28).fontColor(SITE_TEXT).backgroundColor(Color.Transparent)
          .width(40).height(40).onClick(() => this.onBack());
      }
      Column({ space: 2 }) {
        Text(this.title).fontSize(21).fontWeight(FontWeight.Bold).fontColor(SITE_TEXT);
        if (this.subtitle) Text(this.subtitle).fontSize(11).fontColor(SITE_MUTED);
      }.alignItems(HorizontalAlign.Start);
      Blank();
    }.width('100%').padding({ left: 12, right: 12, top: 10, bottom: 10 }).backgroundColor(SITE_BG);
  }
}

@Component
export struct SiteCard {
  @Prop title: string = '';
  @Prop subtitle: string = '';
  @Prop selected: boolean = false;
  onPressed: () => void = () => {};
  build() {
    Column({ space: 6 }) {
      if (this.title) Text(this.title).fontSize(16).fontWeight(FontWeight.Bold).fontColor(SITE_TEXT);
      if (this.subtitle) Text(this.subtitle).fontSize(12).fontColor(SITE_MUTED);
      Row() { }.height(0);
    }.width('100%').padding(14).backgroundColor(SITE_CARD)
      .borderRadius(10).border({ width: this.selected ? 2 : 0, color: SITE_ACCENT })
      .onClick(() => this.onPressed());
  }
}

@Component
export struct SiteChip {
  @Prop label: string = '';
  @Prop selected: boolean = false;
  onSelect: () => void = () => {};
  build() {
    Button(this.label).fontSize(12).height(32)
      .fontColor(this.selected ? '#231A00' : SITE_TEXT)
      .backgroundColor(this.selected ? SITE_ACCENT : SITE_CARD_RAISED)
      .borderRadius(16).onClick(() => this.onSelect());
  }
}

@Component
export struct SiteSection {
  @Prop title: string = '';
  build() {
    Column({ space: 8 }) {
      if (this.title) Text(this.title).fontSize(14).fontWeight(FontWeight.Bold).fontColor(SITE_ACCENT);
      Row() { }.height(0);
    }.width('100%').padding(14).backgroundColor(SITE_CARD).borderRadius(10);
  }
}
