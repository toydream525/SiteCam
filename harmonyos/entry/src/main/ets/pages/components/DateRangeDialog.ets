import { SITE_ACCENT, SITE_CARD, SITE_MUTED, SITE_TEXT } from './UiPrimitives';

/** Expanded inclusive range used by the gallery date filter. */
export class DateRangeValue {
  start: string;
  end: string;
  constructor(start: string, end: string) { this.start = start; this.end = end; }
}

function leap(year: number): boolean {
  return year % 4 === 0 && (year % 100 !== 0 || year % 400 === 0);
}

function daysInMonth(year: number, month: number): number {
  if (month === 2) return leap(year) ? 29 : 28;
  return month === 4 || month === 6 || month === 9 || month === 11 ? 30 : 31;
}

function parts(value: string): number[] {
  const fields: string[] = value.split('-');
  const result: number[] = [];
  for (let i = 0; i < fields.length; i++) result.push(Number(fields[i]));
  return result;
}

function validDate(value: string): boolean {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(value)) return false;
  const p: number[] = parts(value);
  return p[0] >= 1 && p[0] <= 9999 && p[1] >= 1 && p[1] <= 12 && p[2] >= 1 && p[2] <= daysInMonth(p[0], p[1]);
}

function ordinal(value: string): number {
  const p: number[] = parts(value);
  let total: number = 0;
  for (let y = 1; y < p[0]; y++) total += leap(y) ? 366 : 365;
  for (let m = 1; m < p[1]; m++) total += daysInMonth(p[0], m);
  return total + p[2];
}

function first(value: string): string {
  if (/^\d{4}$/.test(value)) return value + '-01-01';
  if (/^\d{4}-\d{2}$/.test(value)) return value + '-01';
  return value;
}

function last(value: string): string {
  if (/^\d{4}$/.test(value)) return value + '-12-31';
  if (/^\d{4}-\d{2}$/.test(value)) {
    const p: number[] = parts(value);
    const day: number = daysInMonth(p[0], p[1]);
    return value + '-' + (day < 10 ? '0' : '') + day;
  }
  return value;
}

/** Same grammar as Android CaptureDateRange.parse: YYYY, YYYY-MM or YYYY-MM-DD. */
export function parseDateRangeInput(startText: string, endText: string): DateRangeValue | null {
  const start: string = startText.trim();
  const end: string = endText.trim();
  if (start.length === 0 && end.length === 0) return new DateRangeValue('', '');
  const startPattern: boolean = start.length === 0 || /^\d{4}$/.test(start) || /^\d{4}-\d{2}$/.test(start) || /^\d{4}-\d{2}-\d{2}$/.test(start);
  const endValue: string = end.length === 0 ? start : end;
  const endPattern: boolean = /^\d{4}$/.test(endValue) || /^\d{4}-\d{2}$/.test(endValue) || /^\d{4}-\d{2}-\d{2}$/.test(endValue);
  if (!startPattern || !endPattern) return null;
  const startDate: string = start.length === 0 ? '' : first(start);
  const endDate: string = last(endValue);
  if ((startDate.length > 0 && !validDate(startDate)) || !validDate(endDate) || (startDate.length > 0 && ordinal(startDate) > ordinal(endDate))) return null;
  return new DateRangeValue(startDate, endDate);
}

@CustomDialog
export struct DateRangeDialog {
  controller: CustomDialogController;
  @Prop initialStart: string = '';
  @Prop initialEnd: string = '';
  onApply: (start: string, end: string) => void = (start: string, end: string) => {};
  onCancel: () => void = () => {};
  @State mode: string = 'MONTH';
  @State year: number = 2026;
  @State month: number = 1;
  @State start: string = '';
  @State end: string = '';
  @State error: string = '';

  aboutToAppear(): void {
    const now: Date = new Date();
    this.year = now.getFullYear();
    this.month = now.getMonth() + 1;
    this.start = this.initialStart;
    this.end = this.initialEnd;
  }

  private apply(): void {
    let result: DateRangeValue | null = null;
    if (this.mode === 'YEAR') result = parseDateRangeInput(String(this.year), '');
    else if (this.mode === 'MONTH') result = parseDateRangeInput(String(this.year) + '-' + (this.month < 10 ? '0' : '') + this.month, '');
    else {
      if (this.mode === 'DAY' && this.start.trim().length === 0) { this.error = '请输入日期'; return; }
      result = parseDateRangeInput(this.start, this.mode === 'DAY' ? this.start : this.end);
    }
    if (result === null) { this.error = '日期格式无效或结束日期早于开始日期'; return; }
    this.controller.close();
    this.onApply(result.start, result.end);
  }

  private cancel(): void { this.controller.close(); this.onCancel(); }

  private modeLabel(value: string): string {
    if (value === 'YEAR') return '年份';
    if (value === 'MONTH') return '月份';
    if (value === 'DAY') return '单日';
    if (value === 'RANGE') return '范围';
    return '输入';
  }

  build() {
    Scroll(){Column({ space: 10 }) {
      Text('拍摄日期筛选').fontSize(20).fontWeight(FontWeight.Bold).fontColor(SITE_TEXT);
      Row({ space: 5 }) {
        ForEach(['YEAR','MONTH','DAY','RANGE','TEXT'], (value: string) => {
          Button(this.modeLabel(value)).fontSize(12).height(32).layoutWeight(1)
            .fontColor(this.mode === value ? '#231A00' : SITE_TEXT)
            .backgroundColor(this.mode === value ? SITE_ACCENT : '#303030')
            .onClick(() => { this.mode = value; this.error = ''; });
        }, (value: string) => value);
      }.width('100%');
      if (this.mode === 'YEAR' || this.mode === 'MONTH') {
        Row({ space: 8 }) {
          Button('上一年').fontSize(12).layoutWeight(1).backgroundColor('#303030').fontColor(SITE_TEXT).onClick(() => { this.year--; });
          Text(String(this.year) + '年').fontSize(17).fontWeight(FontWeight.Bold).fontColor(SITE_TEXT).layoutWeight(1).textAlign(TextAlign.Center);
          Button('下一年').fontSize(12).layoutWeight(1).backgroundColor('#303030').fontColor(SITE_TEXT).onClick(() => { this.year++; });
        }.width('100%');
        if (this.mode === 'MONTH') {
          GridRow({ columns: 4, gutter: 6 }) {
            ForEach([1,2,3,4,5,6,7,8,9,10,11,12], (value: number) => {
              GridCol() { Button(String(value) + '月').width('100%').height(32).fontSize(12).fontColor(this.month === value ? '#231A00' : SITE_TEXT).backgroundColor(this.month === value ? SITE_ACCENT : '#303030').onClick(() => { this.month = value; }); }
            }, (value: number) => String(value));
          }.width('100%');
        }
      } else {
        Text(this.mode === 'RANGE' ? '输入起止日期，结束日期包含当天' : '输入年份 2026、月份 2026-09 或日期 2026-09-08。结束留空表示同一周期')
          .fontSize(12).fontColor(SITE_MUTED);
        TextInput({ text: this.start, placeholder: this.mode === 'DAY' ? '日期 YYYY-MM-DD' : '开始：YYYY / YYYY-MM / YYYY-MM-DD' }).fontColor('#FFFFFF').placeholderColor('#B0BEC5').backgroundColor('#303030').caretColor('#FFB300').borderRadius(10)
          .fontColor(SITE_TEXT).backgroundColor('#303030').onChange((value: string) => { this.start = value; this.error = ''; });
        if (this.mode !== 'DAY') TextInput({ text: this.end, placeholder: '结束（可留空）' }).fontColor('#FFFFFF').placeholderColor('#B0BEC5').backgroundColor('#303030').caretColor('#FFB300').borderRadius(10).fontColor(SITE_TEXT).backgroundColor('#303030').onChange((value: string) => { this.end = value; this.error = ''; });
        if (this.error.length > 0) Text(this.error).fontSize(12).fontColor('#F44336');
      }
      Row({ space: 12 }) {
        Button('取消').fontColor(SITE_MUTED).backgroundColor(Color.Transparent).onClick(() => this.cancel());
        Button('应用').fontColor('#231A00').backgroundColor(SITE_ACCENT).onClick(() => this.apply());
      }.width('100%').justifyContent(FlexAlign.End);
    }.padding(18).backgroundColor(SITE_CARD).borderRadius(14).width('94%');
    }.width('100%').constraintSize({maxWidth:600,maxHeight:'90%'}).align(Alignment.Top).scrollBar(BarState.Off);
  }
}
