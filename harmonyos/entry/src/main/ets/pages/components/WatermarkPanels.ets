import { Snapshot, Field, Style, STYLES } from '../../core/Models';
import { WatermarkView } from '../../components/WatermarkView';
import { SITE_ACCENT, SITE_BG, SITE_CARD, SITE_CARD_RAISED, SITE_MUTED, SITE_TEXT } from './UiPrimitives';

const TAGS: string[] = ['隐蔽验收','主体结构','基础工程','钢筋绑扎','混凝土浇筑','防水工程','安全巡检','设备安装','回填土方','现场测绘','砌体工程','抹灰工程','消防工程','管线敷设','材料进场','质量整改'];
const DESC: Map<string,string> = new Map<string,string>([
  ['CLASSIC','深色信息卡，黄色工程标题与侧边线'],['MINIMAL','紧凑轻底，简洁纵向文字'],['INFO_BOARD','琥珀标题栏，完整工程字段'],
  ['ENGINEERING_BLUE','蓝色工程与时间标题，浅底深色标签和横线'],['CONSTRUCTION_TABLE','蓝灰表头，标签与内容分列表格'],
  ['INSPECTION','青绿巡检标题，逐项分隔的记录清单'],['ACCEPTANCE','深蓝分区表头与规整表格'],['TIME_LOCATION','突出拍摄时间，地点与工程信息依次排列']
]);
function snapshotFor(source: Snapshot,style: string): Snapshot { const out=JSON.parse(JSON.stringify(source)) as Snapshot;out.watermark.style=style;return out; }
function styleName(id: string): string { const found=STYLES.find((v: Style)=>v.id===id);return found?found.name:'经典工程水印'; }
function fieldDisplayLabel(field: Field): string {
  const defaults: Map<string,string> = new Map<string,string>([['project','工程名称'],['category','工程类型'],['time','拍摄时间'],['address','拍摄地点'],['gps','经纬度'],['user','拍摄人']]);
  const old: Map<string,string> = new Map<string,string>([['工程','工程名称'],['类别','工程类型'],['时间','拍摄时间'],['地点','拍摄地点'],['坐标','经纬度'],['人员','拍摄人']]);
  const expected=defaults.get(field.key);const legacy=old.get(field.label);return legacy||((expected&&(!field.label||field.label===field.key))?expected:field.label);
}

@Component
export struct WatermarkStylePickerPanel {
  @Prop snapshot: Snapshot = new Snapshot();
  @Prop selected: string = 'CLASSIC';
  @State availableWidth:number=390;
  @State draft: string = 'CLASSIC';
  onConfirm: (style: string) => void = (style: string) => {};
  onCancel: () => void = () => {};
  aboutToAppear(): void { this.draft=this.selected; }
  build() {
    Column({space:12}) {
      Row(){Text('选择水印样式').fontSize(this.availableWidth<320?18:24).layoutWeight(1).fontWeight(FontWeight.Bold).fontColor(SITE_TEXT);Blank();Button('×').fontSize(24).fontColor(SITE_MUTED).backgroundColor(Color.Transparent).onClick(()=>this.onCancel());}.width('100%');
      Scroll(){Column({space:12}) {
        Stack(){WatermarkView({snapshot:snapshotFor(this.snapshot,this.draft)});}.width('100%').height(180).backgroundColor('#202A31').borderRadius(12);
        Text(styleName(this.draft)).fontSize(22).fontWeight(FontWeight.Bold).fontColor(SITE_ACCENT);
        Text(DESC.get(this.draft)||'').fontSize(13).fontColor(SITE_MUTED);
        GridRow({columns:this.availableWidth>=840?4:this.availableWidth>=600?3:this.availableWidth>=320?2:1,gutter:10}){ForEach(STYLES,(style: Style)=>{
          GridCol(){Column({space:5}){Stack(){WatermarkView({snapshot:snapshotFor(this.snapshot,style.id)});}.width('100%').height(82).backgroundColor('#202A31');Text(style.name).fontSize(14).fontWeight(this.draft===style.id?FontWeight.Bold:FontWeight.Medium).fontColor(this.draft===style.id?SITE_ACCENT:SITE_TEXT).maxLines(1);Text(DESC.get(style.id)||'').fontSize(10).fontColor(SITE_MUTED).maxLines(2);}.padding(8).backgroundColor(SITE_CARD_RAISED).borderRadius(9).border({width:this.draft===style.id?2:1,color:this.draft===style.id?SITE_ACCENT:'#44515A'}).id('watermark-style-'+style.id).onClick(()=>this.draft=style.id);}
        },(style: Style)=>style.id);}
      }.width('100%').padding({left:10,right:10,bottom:12});}.layoutWeight(1).scrollable(ScrollDirection.Vertical);
      Row({space:12}){Button('取消').id('watermark-style-cancel').layoutWeight(1).backgroundColor(SITE_CARD_RAISED).fontColor(SITE_TEXT).onClick(()=>this.onCancel());Button('使用此样式').layoutWeight(1).backgroundColor(SITE_ACCENT).fontColor('#231A00').onClick(()=>this.onConfirm(this.draft));}.width('100%');
    }.width('100%').height('100%').padding(16).backgroundColor(SITE_CARD).onAreaChange((oldArea,newArea)=>this.availableWidth=Number(newArea.width));
  }
}

@Component
export struct WatermarkCustomizationPanel {
  @Prop snapshot: Snapshot = new Snapshot();
  @Prop fields: Field[] = [];
  @Prop selectedStyle: string = 'CLASSIC';
  @Prop fontScale: number = 1;
  @Prop wmOpacity: number = .85;
  @Prop wmPosition: string = 'BOTTOM_LEFT';
  @State pickerVisible: boolean = false;
  @State editorVisible: boolean = false;
  @State editingIndex: number = -1;
  @State draftLabel: string = '';
  @State draftValue: string = '';
  onBack: () => void = () => {};
  onFontScale: (value: number) => void = (value: number) => {};
  onOpacity: (value: number) => void = (value: number) => {};
  onPosition: (value: string) => void = (value: string) => {};
  onStyle: (value: string) => void = (value: string) => {};
  onFieldChange: (index: number,value: string) => void = (index: number,value: string) => {};
  onFieldLabelChange: (index: number,value: string) => void = (index: number,value: string) => {};
  onFieldToggle: (index: number,value: boolean) => void = (index: number,value: boolean) => {};
  onMove: (index: number,direction: number) => void = (index: number,direction: number) => {};
  onDelete: (index: number) => void = (index: number) => {};
  onAddField: (label: string,value: string) => void = (label: string,value: string) => {};
  build() {
    Stack(){Column(){Row(){Button('‹').fontSize(30).fontColor(SITE_TEXT).backgroundColor(Color.Transparent).onClick(()=>this.onBack());Text('水印样式与字段定制').fontSize(21).fontWeight(FontWeight.Bold).fontColor(SITE_TEXT);}.width('100%').padding({bottom:8});
      Scroll(){Column({space:14}){Text('实时效果预览').fontSize(14).fontWeight(FontWeight.Bold).fontColor(SITE_MUTED);Stack(){WatermarkView({snapshot:this.preview()});}.width('100%').height(200).backgroundColor('#1E2830').borderRadius(12);
        Row(){Column({space:3}){Text('当前样式').fontSize(12).fontColor(SITE_MUTED);Text(styleName(this.selectedStyle)).fontSize(16).fontWeight(FontWeight.Bold).fontColor(SITE_ACCENT);}.layoutWeight(1);Button('更换样式').fontSize(14).fontColor(SITE_ACCENT).backgroundColor(Color.Transparent).onClick(()=>this.pickerVisible=true);}.width('100%').padding({left:4,right:4});
        Column({space:10}){Text('样式调整').fontSize(17).fontWeight(FontWeight.Bold).fontColor(SITE_TEXT);this.sliderRow('字号缩放比例',this.fontScale.toFixed(1)+'×',this.fontScale,.6,2,this.onFontScale);this.sliderRow('背景卡片不透明度',Math.round(this.wmOpacity*100)+'%',this.wmOpacity,.3,1,this.onOpacity);Text('水印位置').fontSize(13).fontColor(SITE_MUTED);Row({space:7}){ForEach([['BOTTOM_LEFT','左下角'],['BOTTOM_RIGHT','右下角'],['TOP_LEFT','左上角'],['TOP_RIGHT','右上角']],(p: string[])=>Button(p[1]).fontSize(12).layoutWeight(1).height(36).fontColor(this.wmPosition===p[0]?'#231A00':SITE_TEXT).backgroundColor(this.wmPosition===p[0]?SITE_ACCENT:SITE_BG).borderRadius(8).onClick(()=>this.onPosition(p[0])),(p: string[])=>p[0]);}}.width('100%').padding(16).backgroundColor(SITE_CARD_RAISED).borderRadius(12);
        Row(){Text('水印字段管理与排序').fontSize(14).fontWeight(FontWeight.Bold).fontColor(SITE_MUTED).layoutWeight(1);Button('+ 新增字段').fontSize(13).fontColor('#231A00').backgroundColor(SITE_ACCENT).onClick(()=>{this.editingIndex=-1;this.draftLabel='现场备注';this.draftValue='';this.editorVisible=true;});}.width('100%');
        ForEach(this.fields,(field: Field,index: number)=>{Row({space:6}){Column({space:3}){Text(fieldDisplayLabel(field)).fontSize(15).fontWeight(FontWeight.Bold).fontColor(field.enabled?SITE_TEXT:SITE_MUTED);Text(field.value?'默认: '+field.value:'').fontSize(12).fontColor(SITE_MUTED);}.layoutWeight(1).onClick(()=>{this.editingIndex=index;this.draftLabel=fieldDisplayLabel(field);this.draftValue=field.value;this.editorVisible=true;});Button('↑').fontSize(14).fontColor(SITE_TEXT).backgroundColor(Color.Transparent).enabled(index>0).onClick(()=>this.onMove(index,-1));Button('↓').fontSize(14).fontColor(SITE_TEXT).backgroundColor(Color.Transparent).enabled(index<this.fields.length-1).onClick(()=>this.onMove(index,1));Toggle({type:ToggleType.Switch,isOn:field.enabled}).selectedColor('#FFB300').onChange((v:boolean)=>this.onFieldToggle(index,v));Button('×').fontColor('#E57373').backgroundColor(Color.Transparent).onClick(()=>this.onDelete(index));}.padding(10).backgroundColor(field.enabled?SITE_CARD:SITE_CARD_RAISED).borderRadius(10);},(field: Field,index: number)=>field.key+'_'+index);
      }.padding({left:12,right:12,bottom:20});}.layoutWeight(1).scrollable(ScrollDirection.Vertical);
    }.width('100%').height('100%').backgroundColor(SITE_BG);if(this.pickerVisible){WatermarkStylePickerPanel({snapshot:this.snapshot,selected:this.selectedStyle,onConfirm:(v:string)=>{this.pickerVisible=false;this.onStyle(v);},onCancel:()=>this.pickerVisible=false}).width('100%').height('92%').align(Alignment.Bottom);}if(this.editorVisible){Column({space:12}){Text(this.editingIndex<0?'新增水印字段':'编辑水印字段').fontSize(19).fontWeight(FontWeight.Bold).fontColor(SITE_TEXT);TextInput({text:this.draftLabel,placeholder:'字段名称'}).fontColor('#FFFFFF').placeholderColor('#B0BEC5').backgroundColor('#303030').caretColor('#FFB300').borderRadius(10).onChange((v:string)=>this.draftLabel=v);TextInput({text:this.draftValue,placeholder:'默认值（可留空）'}).fontColor('#FFFFFF').placeholderColor('#B0BEC5').backgroundColor('#303030').caretColor('#FFB300').borderRadius(10).onChange((v:string)=>this.draftValue=v);Row({space:10}){Button('取消').layoutWeight(1).onClick(()=>this.editorVisible=false);Button('保存').layoutWeight(1).backgroundColor(SITE_ACCENT).fontColor('#231A00').onClick(()=>{if(this.editingIndex<0)this.onAddField(this.draftLabel,this.draftValue);else{this.onFieldLabelChange(this.editingIndex,this.draftLabel);this.onFieldChange(this.editingIndex,this.draftValue);}this.editorVisible=false;});}.width('100%');}.width('90%').padding(18).backgroundColor(SITE_CARD).borderRadius(14).align(Alignment.Center);}}
  }
  preview(): Snapshot { const s=snapshotFor(this.snapshot,this.selectedStyle);s.watermark.fontScale=this.fontScale;s.watermark.opacity=this.wmOpacity;s.watermark.position=this.wmPosition;s.watermark.fields=this.fields;return s; }
  @Builder sliderRow(label: string,value: string,current: number,min: number,max: number,change: (v:number)=>void){Row(){Text(label).fontSize(13).fontColor(SITE_MUTED).layoutWeight(1);Text(value).fontSize(13).fontColor(SITE_ACCENT);}.width('100%');Slider({value:current,min:min,max:max,step:.1}).selectedColor('#FFB300').trackColor('#505050').blockColor('#FFFFFF').onChange((v:number)=>change(v));}
}

@Component
export struct QuickWatermarkPanel {
  @Prop panelWidth:number=390;
  @Prop snapshot: Snapshot = new Snapshot();
  @Prop fields: Field[] = [];
  @Prop selectedStyle: string = 'CLASSIC';
  @Prop fontScale: number = 1;
  @Prop wmOpacity: number = .85;
  @Prop wmPosition: string = 'BOTTOM_LEFT';
  @State pickerVisible: boolean = false;
  @State focused: number = 0;
  @State addVisible: boolean = false;
  @State addLabel: string = '';
  @State addValue: string = '';
  onDismiss: () => void = () => {};
  onFieldChange: (index: number,value: string) => void = (index: number,value: string) => {};
  onFieldToggle: (index: number,value: boolean) => void = (index: number,value: boolean) => {};
  onStyle: (value: string) => void = (value: string) => {};
  onScale: (value: number) => void = (value: number) => {};
  onOpacity: (value: number) => void = (value: number) => {};
  onPosition: (value: string) => void = (value: string) => {};
  onAddField: (label: string) => void = (label: string) => {};
  onAddFieldValue: (label: string,value: string) => void = (label: string,value: string) => { this.onAddField(label); };
  build() {
    Stack(){Column({space:10}){Row(){Text('现场快速修改水印').fontSize(this.panelWidth<320?14:18).fontWeight(FontWeight.Bold).fontColor(SITE_TEXT).layoutWeight(1);Button('完成').fontColor('#231A00').backgroundColor(SITE_ACCENT).onClick(()=>this.onDismiss());}.width('100%');
      Scroll(){Column({space:10}){Row(){Text('当前水印样式').fontSize(13).fontColor(SITE_MUTED).layoutWeight(1);Button(styleName(this.selectedStyle)).fontColor(SITE_ACCENT).backgroundColor(Color.Transparent).onClick(()=>this.pickerVisible=true);}.width('100%').padding(10).backgroundColor(SITE_CARD).borderRadius(10);
      Text('⚡ 常用工程部位 / 施工标签').fontSize(12).fontColor(SITE_MUTED);
      Scroll(){Row({space:6}){ForEach(TAGS,(tag:string)=>Button('+ '+tag).fontSize(12).fontColor(SITE_ACCENT).backgroundColor(SITE_CARD_RAISED).borderRadius(14).onClick(()=>{const i=Math.min(this.focused,this.fields.length-1);if(i>=0)this.onFieldChange(i,this.fields[i].value?this.fields[i].value+' '+tag:tag);}), (tag:string)=>tag);}}.scrollable(ScrollDirection.Horizontal).width('100%');
      Row(){Text('字号缩放比例').fontSize(12).fontColor(SITE_MUTED).layoutWeight(1);Text(this.fontScale.toFixed(1)+'×').fontColor(SITE_ACCENT);}.width('100%');Slider({value:this.fontScale,min:.6,max:2,step:.1}).selectedColor('#FFB300').trackColor('#505050').blockColor('#FFFFFF').onChange((v:number)=>this.onScale(v));
      Row(){Text('背景卡片不透明度').fontSize(12).fontColor(SITE_MUTED).layoutWeight(1);Text(Math.round(this.wmOpacity*100)+'%').fontColor(SITE_ACCENT);}.width('100%');Slider({value:this.wmOpacity,min:.3,max:1,step:.1}).selectedColor('#FFB300').trackColor('#505050').blockColor('#FFFFFF').onChange((v:number)=>this.onOpacity(v));
      Row({space:5}){ForEach([['BOTTOM_LEFT','左下'],['BOTTOM_RIGHT','右下'],['TOP_LEFT','左上'],['TOP_RIGHT','右上']],(p:string[])=>Button(p[1]).layoutWeight(1).fontSize(11).fontColor(this.wmPosition===p[0]?'#231A00':SITE_TEXT).backgroundColor(this.wmPosition===p[0]?SITE_ACCENT:SITE_CARD_RAISED).onClick(()=>this.onPosition(p[0])),(p:string[])=>p[0]);}.width('100%');
      Column({space:8}){ForEach(this.fields,(field:Field,index:number)=>Flex({direction:this.panelWidth<320?FlexDirection.Column:FlexDirection.Row,alignItems:ItemAlign.Center}){Row({space:6}){Toggle({type:ToggleType.Switch,isOn:field.enabled}).selectedColor('#FFB300').onChange((v:boolean)=>this.onFieldToggle(index,v));Text(fieldDisplayLabel(field)).width(68).fontSize(13).fontColor(SITE_TEXT);}.margin({right:6,bottom:this.panelWidth<320?6:0});TextInput({text:field.value,placeholder:'留空使用拍摄时信息'}).fontSize(13).fontColor('#FFFFFF').placeholderColor('#B0BEC5').backgroundColor('#303030').caretColor('#FFB300').borderRadius(10).enabled(field.enabled).width(this.panelWidth<320?'100%':Math.max(80,this.panelWidth-162)).onFocus(()=>this.focused=index).onChange((v:string)=>this.onFieldChange(index,v));},(field:Field,index:number)=>field.key+'_'+index);Button('+ 新增现场自定义字段').fontColor(SITE_ACCENT).backgroundColor(SITE_CARD_RAISED).onClick(()=>{this.addLabel='现场备注';this.addValue='';this.addVisible=true;});}.width('100%');}.width('100%').padding({bottom:12});}.layoutWeight(1).width('100%').scrollable(ScrollDirection.Vertical);
    }.width('100%').height('100%').padding(16).backgroundColor(SITE_BG);if(this.pickerVisible){WatermarkStylePickerPanel({snapshot:this.snapshot,selected:this.selectedStyle,onConfirm:(v:string)=>{this.pickerVisible=false;this.onStyle(v);},onCancel:()=>this.pickerVisible=false}).width('100%').height('92%').align(Alignment.Bottom);}if(this.addVisible){Column({space:12}){Text('新增现场自定义字段').fontSize(19).fontWeight(FontWeight.Bold).fontColor(SITE_TEXT);TextInput({text:this.addLabel,placeholder:'字段名称'}).fontColor('#FFFFFF').placeholderColor('#B0BEC5').backgroundColor('#303030').caretColor('#FFB300').borderRadius(10).onChange((v:string)=>this.addLabel=v);TextInput({text:this.addValue,placeholder:'默认值（可留空）'}).fontColor('#FFFFFF').placeholderColor('#B0BEC5').backgroundColor('#303030').caretColor('#FFB300').borderRadius(10).onChange((v:string)=>this.addValue=v);Row({space:10}){Button('取消').layoutWeight(1).onClick(()=>this.addVisible=false);Button('添加').layoutWeight(1).backgroundColor(SITE_ACCENT).fontColor('#231A00').onClick(()=>{if(!this.addLabel.trim())return;this.onAddFieldValue(this.addLabel.trim(),this.addValue);this.addVisible=false;});}.width('100%');}.width('90%').padding(18).backgroundColor(SITE_CARD).borderRadius(14).align(Alignment.Center);}}
  }
}
