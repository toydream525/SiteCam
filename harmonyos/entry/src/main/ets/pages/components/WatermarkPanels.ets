import { curves } from '@kit.ArkUI';
import { Snapshot, Field, Style, STYLES } from '../../core/Models';
import { OFFLINE_BUILD } from '../../core/BuildVariant';
import { WatermarkEditingPreview } from './WatermarkEditingPreview';
import { WatermarkView } from '../../components/WatermarkView';
import { SITE_ACCENT, SITE_BG, SITE_CARD, SITE_CARD_RAISED, SITE_MUTED, SITE_TEXT, PAGE_BG, PAGE_CARD, PAGE_RAISED, PAGE_TEXT, PAGE_MUTED } from './UiPrimitives';

const TAGS: string[] = ['隐蔽验收','主体结构','基础工程','钢筋绑扎','混凝土浇筑','防水工程','安全巡检','设备安装','回填土方','现场测绘','砌体工程','抹灰工程','消防工程','管线敷设','材料进场','质量整改'];
const DESC: Map<string,string> = new Map<string,string>([
  ['CLASSIC','深色信息卡，黄色工程标题与侧边线'],['MINIMAL','紧凑轻底，简洁纵向文字'],['INFO_BOARD','琥珀标题栏，完整工程字段'],
  ['ENGINEERING_BLUE','蓝色工程与时间标题，浅底深色标签和横线'],['CONSTRUCTION_TABLE','蓝灰表头，标签与内容分列表格'],
  ['INSPECTION','青绿巡检标题，逐项分隔的记录清单'],['ACCEPTANCE','深蓝分区表头与规整表格'],['TIME_LOCATION','突出拍摄时间，地点与工程信息依次排列']
]);
function snapshotFor(source: Snapshot,style: string): Snapshot { const out=JSON.parse(JSON.stringify(source)) as Snapshot;out.watermark.style=style;return out; }
function styleName(id: string): string { const found=STYLES.find((v: Style)=>v.id===id);return found?found.name:'经典工程水印'; }
function fieldDisplayLabel(field: Field): string {
  if (OFFLINE_BUILD && field.key === 'address' && field.label === '现场位置') return '具体地址';
  return field.label || field.key;
}

@Component
export struct WatermarkStylePickerPanel {
  @Prop snapshot: Snapshot = new Snapshot();
  @Prop selected: string = 'CLASSIC';
  @State availableWidth:number=390;
  @State availableHeight:number=640;
  @StorageProp('fontSizeScale') systemFontScale:number=1;
  @State draft: string = 'CLASSIC';
  onConfirm: (style: string) => void = (style: string) => {};
  onCancel: () => void = () => {};
  aboutToAppear(): void { this.draft=this.selected; }
  private buttonHeight(stacked:boolean,characters:number):number {const width=stacked?this.availableWidth-32:(this.availableWidth-44)/2;const perLine=Math.max(1,Math.floor(Math.max(16,width-32)/(16*this.systemFontScale)));return Math.max(52,Math.ceil(characters/perLine)*24*this.systemFontScale+24);}
  private footerStacked():boolean {const narrow=this.availableWidth-32<2*(80*this.systemFontScale+32)+12;const stackedHeight=this.buttonHeight(true,2)+this.buttonHeight(true,5)+12;return narrow&&(stackedHeight<=this.availableHeight-80||stackedHeight<=this.buttonHeight(false,5));}
  private footerButtonHeight(characters:number=5):number{return this.buttonHeight(this.footerStacked(),characters);}
  private footerHeight():number{return this.footerStacked()?this.footerButtonHeight(2)+this.footerButtonHeight(5)+12:this.footerButtonHeight();}
  private previewHeight():number{return Math.max(96,Math.min(190,(this.availableHeight-this.footerHeight()-100)*.38));}
  @Builder previewPane(){Column({space:8}){Stack(){WatermarkEditingPreview({snapshot:snapshotFor(this.snapshot,this.draft)});}.width('100%').layoutWeight(1).backgroundColor('#202A31').borderRadius(12);Text(styleName(this.draft)).fontSize(18).fontWeight(FontWeight.Bold).fontColor($r('app.color.text_accent'));}.width('100%').height('100%');}
  @Builder choices(){GridRow({columns:this.availableWidth>=320?2:1,gutter:10}){ForEach(STYLES,(style:Style)=>{GridCol(){Column({space:8}){Stack(){WatermarkEditingPreview({snapshot:snapshotFor(this.snapshot,style.id)});}.width('100%').height(88).backgroundColor('#202A31');Text(style.name).fontSize(14).fontWeight(this.draft===style.id?FontWeight.Bold:FontWeight.Medium).fontColor(this.draft===style.id?$r('app.color.text_accent'):PAGE_TEXT).maxLines(2);}.padding(8).backgroundColor(PAGE_RAISED).borderRadius(10).border({width:this.draft===style.id?2:1,color:this.draft===style.id?SITE_ACCENT:$r('app.color.divider')}).id('watermark-style-'+style.id).onClick(()=>this.draft=style.id);}},(style:Style)=>style.id);}.width('100%').padding({bottom:12});}
  build(){Column({space:12}){
    Row(){Text('选择水印样式').fontSize(20).fontWeight(FontWeight.Bold).fontColor(PAGE_TEXT).layoutWeight(1);Button('×').width(44).height(44).fontSize(24).fontColor(PAGE_TEXT).backgroundColor(Color.Transparent).onClick(()=>this.onCancel());}.width('100%').flexShrink(0);
    if(this.availableWidth>=840){Row({space:20}){Column(){this.previewPane();}.layoutWeight(1).height('100%');Scroll(){this.choices();}.edgeEffect(EdgeEffect.Spring).layoutWeight(1).height('100%').scrollBar(BarState.Off).align(Alignment.Top);}.layoutWeight(1).width('100%').constraintSize({minHeight:0});}
    else{Scroll(){Column({space:12}){Column(){this.previewPane();}.height(this.previewHeight()).width('100%');this.choices();}.width('100%');}.edgeEffect(EdgeEffect.Spring).layoutWeight(1).width('100%').constraintSize({minHeight:0}).scrollBar(BarState.Off).align(Alignment.Top);}
    if(this.footerStacked()){Column({space:12}){Button('取消').labelStyle({maxLines:2,overflow:TextOverflow.Clip}).id('watermark-style-cancel').width('100%').height(this.footerButtonHeight(2)).fontSize(16).backgroundColor(PAGE_RAISED).fontColor(PAGE_TEXT).onClick(()=>this.onCancel());Button('使用此样式').labelStyle({maxLines:5,overflow:TextOverflow.Clip}).id('watermark-style-confirm').width('100%').height(this.footerButtonHeight()).fontSize(16).backgroundColor(SITE_ACCENT).fontColor('#231A00').onClick(()=>this.onConfirm(this.draft));}.width('100%').height(this.footerHeight()).flexShrink(0);}
    else{Row({space:12}){Button('取消').labelStyle({maxLines:2,overflow:TextOverflow.Clip}).id('watermark-style-cancel').layoutWeight(1).height(this.footerButtonHeight()).fontSize(16).backgroundColor(PAGE_RAISED).fontColor(PAGE_TEXT).onClick(()=>this.onCancel());Button('使用此样式').labelStyle({maxLines:5,overflow:TextOverflow.Clip}).id('watermark-style-confirm').layoutWeight(1).height(this.footerButtonHeight()).fontSize(16).backgroundColor(SITE_ACCENT).fontColor('#231A00').onClick(()=>this.onConfirm(this.draft));}.width('100%').height(this.footerHeight()).flexShrink(0);}
  }.width('100%').height('100%').padding({left:16,right:16,top:16,bottom:32}).backgroundColor(PAGE_CARD).onAreaChange((oldArea,newArea)=>{this.availableWidth=Number(newArea.width);this.availableHeight=Number(newArea.height);});}
}

@Component
export struct WatermarkCustomizationPanel {
  @Prop saveAtBottom:boolean=false;@State availableHeight:number=640;@Prop previewPath:string='';@Prop pageTitle:string='水印编辑';@Prop actionTitle:string='完成';@Prop submitting:boolean=false;onSave:()=>void=()=>{};
  @Prop snapshot:Snapshot=new Snapshot();@Prop fields:Field[]=[];@Prop selectedStyle:string='CLASSIC';@Prop fontScale:number=1;@Prop widthRatio:number=.6;@Prop wmOpacity:number=.85;@Prop wmPosition:string='BOTTOM_LEFT';
  @State availableWidth:number=390;@State tab:string='内容';@Link pickerVisible:boolean;@Link editorVisible:boolean;@State editingIndex:number=-1;@State draftLabel:string='';@State draftValue:string='';
  onReset:()=>void=()=>{};onBack:()=>void=()=>{};onFontScale:(v:number)=>void=()=>{};onWidthRatio:(v:number)=>void=()=>{};onOpacity:(v:number)=>void=()=>{};onPosition:(v:string)=>void=()=>{};onStyle:(v:string)=>void=()=>{};onFieldChange:(i:number,v:string)=>void=()=>{};onFieldLabelChange:(i:number,v:string)=>void=()=>{};onFieldToggle:(i:number,v:boolean)=>void=()=>{};onMove:(i:number,d:number)=>void=()=>{};onDelete:(i:number)=>void=()=>{};onAddField:(label:string,value:string)=>void=()=>{};
  preview():Snapshot{const s=snapshotFor(this.snapshot,this.selectedStyle);s.watermark.fontScale=this.fontScale;s.watermark.widthRatio=this.widthRatio;s.watermark.opacity=this.wmOpacity;s.watermark.position=this.wmPosition;s.watermark.fields=this.fields;return s;}
  private edit(index:number):void{this.editingIndex=index;this.draftLabel=index<0?'':fieldDisplayLabel(this.fields[index]);this.draftValue='';this.editorVisible=true;}
  @Builder previewPane(){Stack(){if(this.previewPath)Image('file://'+this.previewPath).width('100%').height('100%').objectFit(ImageFit.Contain);else WatermarkEditingPreview({snapshot:this.preview()});}.width('100%').height('100%').backgroundColor('#25323A').borderRadius(14);}
  @Builder controls(){Column({space:12}){
    Row({space:6}){ForEach(['内容','字段','外观'],(t:string)=>Button(t).layoutWeight(1).constraintSize({minHeight:44}).fontColor(this.tab===t?'#231A00':PAGE_TEXT).backgroundColor(this.tab===t?SITE_ACCENT:PAGE_CARD).onClick(()=>this.tab=t),(t:string)=>t);}.width('100%');
    Scroll(){Column({space:12}){
      if(this.tab==='内容'){ForEach(this.fields,(field:Field,index:number)=>Column({space:8}){Row(){Text(fieldDisplayLabel(field)).fontSize(16).fontWeight(FontWeight.Bold).fontColor(PAGE_TEXT).layoutWeight(1);Toggle({type:ToggleType.Switch,isOn:field.enabled}).selectedColor(SITE_ACCENT).onChange(v=>this.onFieldToggle(index,v));}.width('100%');TextInput({text:field.value,placeholder:fieldDisplayLabel(field)}).fontColor(PAGE_TEXT).backgroundColor(PAGE_RAISED).enabled(field.enabled).onChange(v=>this.onFieldChange(index,v));}.padding(14).width('100%').backgroundColor(PAGE_CARD).borderRadius(12),(field:Field,index:number)=>field.key+'_'+index);Button('添加字段').fontColor($r('app.color.text_accent')).backgroundColor(PAGE_CARD).onClick(()=>this.edit(-1));}
      else if(this.tab==='字段'){Flex({wrap:FlexWrap.Wrap}){Button('恢复默认').id('watermark-reset').fontColor($r('app.color.text_accent')).backgroundColor(Color.Transparent).onClick(()=>this.onReset());Button('添加字段').fontColor($r('app.color.text_accent')).backgroundColor(Color.Transparent).onClick(()=>this.edit(-1));}.width('100%');ForEach(this.fields,(field:Field,index:number)=>Column(){Row(){Text(fieldDisplayLabel(field)).fontSize(16).fontWeight(FontWeight.Bold).fontColor(PAGE_TEXT).layoutWeight(1);Toggle({type:ToggleType.Switch,isOn:field.enabled}).selectedColor(SITE_ACCENT).onChange(v=>this.onFieldToggle(index,v));}.width('100%');Flex({wrap:FlexWrap.Wrap}){Button('编辑').fontColor(PAGE_TEXT).backgroundColor(Color.Transparent).onClick(()=>this.edit(index));Button('↑').fontColor(PAGE_TEXT).backgroundColor(Color.Transparent).enabled(index>0).onClick(()=>this.onMove(index,-1));Button('↓').fontColor(PAGE_TEXT).backgroundColor(Color.Transparent).enabled(index<this.fields.length-1).onClick(()=>this.onMove(index,1));if(field.key.startsWith('custom_'))Button('删除').fontColor('#E53935').backgroundColor(Color.Transparent).onClick(()=>this.onDelete(index));}.width('100%');}.padding(12).width('100%').backgroundColor(PAGE_CARD).borderRadius(12),(field:Field,index:number)=>field.key+'_'+index);}
      else{Column({space:12}){Row(){Text('水印样式').fontSize(16).fontColor(PAGE_TEXT).layoutWeight(1);Button(styleName(this.selectedStyle)+' ›').fontColor($r('app.color.text_accent')).backgroundColor(Color.Transparent).onClick(()=>this.pickerVisible=true);}.width('100%');Row(){Text('字号').fontSize(16).fontColor(PAGE_TEXT).layoutWeight(1);Text(this.fontScale.toFixed(1)+'×').fontColor(PAGE_MUTED);}.width('100%');Slider({value:this.fontScale,min:.6,max:2,step:.1}).selectedColor(SITE_ACCENT).trackColor($r('app.color.divider')).onChange(v=>this.onFontScale(v));Row(){Text('水印宽度').fontSize(16).fontColor(PAGE_TEXT).layoutWeight(1);Text(Math.round(this.widthRatio*100)+'%').fontColor(PAGE_MUTED);}.width('100%');Slider({value:this.widthRatio*100,min:30,max:90,step:5}).selectedColor(SITE_ACCENT).trackColor($r('app.color.divider')).onChange(v=>this.onWidthRatio(v/100));Row(){Text('30%').fontColor(PAGE_MUTED);Blank();Text('90%').fontColor(PAGE_MUTED);}.width('100%');Row(){Text('不透明度').fontSize(16).fontColor(PAGE_TEXT).layoutWeight(1);Text(Math.round(this.wmOpacity*100)+'%').fontColor(PAGE_MUTED);}.width('100%');Slider({value:this.wmOpacity,min:.3,max:1,step:.1}).selectedColor(SITE_ACCENT).trackColor($r('app.color.divider')).onChange(v=>this.onOpacity(v));Text('水印位置').fontSize(16).fontColor(PAGE_TEXT).width('100%');Flex({wrap:FlexWrap.Wrap}){ForEach([['BOTTOM_LEFT','左下'],['BOTTOM_RIGHT','右下'],['TOP_LEFT','左上'],['TOP_RIGHT','右上']],(p:string[])=>Button(p[1]).fontColor(this.wmPosition===p[0]?'#231A00':PAGE_TEXT).backgroundColor(this.wmPosition===p[0]?SITE_ACCENT:PAGE_RAISED).margin(4).onClick(()=>this.onPosition(p[0])),(p:string[])=>p[0]);}.width('100%');}.padding(16).width('100%').backgroundColor(PAGE_CARD).borderRadius(14);}
    }.width('100%').padding({bottom:16});}.edgeEffect(EdgeEffect.Spring).width('100%').layoutWeight(1).scrollBar(BarState.Off).align(Alignment.Top);
  }.padding(16).width('100%').height('100%');}
  build(){Stack(){Column(){Row(){Button('‹').width(48).height(48).fontSize(30).fontColor(PAGE_TEXT).backgroundColor(Color.Transparent).onClick(()=>this.onBack());Text(this.pageTitle).fontSize(20).fontWeight(FontWeight.Bold).fontColor(PAGE_TEXT).layoutWeight(1);if(!this.saveAtBottom)Button(this.actionTitle).enabled(!this.submitting).fontColor($r('app.color.text_accent')).backgroundColor(Color.Transparent).onClick(()=>{if(this.previewPath)this.onSave();else this.onBack();});}.width('100%').constraintSize({minHeight:56}).flexShrink(this.saveAtBottom?0:1);
    if(this.availableWidth>=840)Row(){Column(){this.previewPane();}.layoutWeight(1).height('100%').padding(20);Column(){this.controls();}.width(360).height('100%');}.layoutWeight(1).width('100%');
    else if(this.saveAtBottom)Scroll(){Column(){Column(){this.previewPane();}.width('100%').height(Math.max(64,Math.min(220,this.availableHeight*.28))).padding({left:16,right:16});Column(){this.controls();}.width('100%').height(Math.max(240,this.availableHeight-224));}.width('100%');}.layoutWeight(1).width('100%').constraintSize({minHeight:0}).scrollBar(BarState.Off).align(Alignment.Top);
    else Column(){Column(){this.previewPane();}.width('100%').height('30%').constraintSize({minHeight:96,maxHeight:240}).padding({left:16,right:16});Column(){this.controls();}.layoutWeight(1).width('100%');}.layoutWeight(1).width('100%');
    if(this.saveAtBottom)Column(){Button(this.actionTitle).width('100%').constraintSize({minHeight:52}).fontSize(16).fontColor('#231A00').backgroundColor(SITE_ACCENT).enabled(!this.submitting).onClick(()=>this.onSave());}.width('100%').padding({left:16,right:16,top:8,bottom:16}).flexShrink(0);
  }.height('100%').width('100%').backgroundColor(PAGE_BG);
  if(this.pickerVisible){Rect().width('100%').height('100%').fill(PAGE_BG);WatermarkStylePickerPanel({snapshot:this.preview(),selected:this.selectedStyle,onConfirm:(v:string)=>{this.pickerVisible=false;this.onStyle(v);},onCancel:()=>this.pickerVisible=false}).width('100%').height('100%').transition(TransitionEffect.OPACITY.animation({curve:curves.springMotion(0.35,0.9)}));}
  if(this.editorVisible){Rect().width('100%').height('100%').fill('#00000099').onClick(()=>this.editorVisible=false);Scroll(){Column({space:16}){Text(this.editingIndex<0?'添加字段':'编辑字段').fontSize(20).fontWeight(FontWeight.Bold).fontColor(PAGE_TEXT);TextInput({text:this.draftLabel,placeholder:'字段名称'}).fontColor(PAGE_TEXT).backgroundColor(PAGE_RAISED).onChange(v=>this.draftLabel=v);if(this.editingIndex<0)TextInput({text:this.draftValue,placeholder:'字段内容'}).fontColor(PAGE_TEXT).backgroundColor(PAGE_RAISED).onChange(v=>this.draftValue=v);Row({space:12}){Button('取消').layoutWeight(1).fontColor(PAGE_TEXT).backgroundColor(PAGE_RAISED).onClick(()=>this.editorVisible=false);Button('保存').layoutWeight(1).fontColor('#231A00').backgroundColor(SITE_ACCENT).onClick(()=>{if(!this.draftLabel.trim())return;if(this.editingIndex<0)this.onAddField(this.draftLabel.trim(),this.draftValue);else this.onFieldLabelChange(this.editingIndex,this.draftLabel.trim());this.editorVisible=false;});}.width('100%');}.padding(20).width('100%').backgroundColor(PAGE_CARD).borderRadius(16);}.edgeEffect(EdgeEffect.Spring).width('90%').constraintSize({maxWidth:520,maxHeight:480}).scrollBar(BarState.Off).align(Alignment.Center).transition(TransitionEffect.OPACITY.animation({curve:curves.springMotion(0.35,0.9)}));}
  }.width('100%').height('100%').onAreaChange((oldArea,newArea)=>{this.availableWidth=Number(newArea.width);this.availableHeight=Number(newArea.height);});}
}

@Component
export struct QuickWatermarkPanel {
  @Prop panelWidth:number=390;
  @Prop snapshot: Snapshot = new Snapshot();
  @Prop fields: Field[] = [];
  @Prop selectedStyle: string = 'CLASSIC';
  @Prop fontScale: number = 1;
  @Prop widthRatio: number = .6;
  @Prop wmOpacity: number = .85;
  @Prop wmPosition: string = 'BOTTOM_LEFT';
  @Link pickerVisible:boolean;
  @State focused: number = 0;
  private fieldScroll:Scroller=new Scroller();
  private editProjectName():void{const index=this.fields.findIndex((field:Field)=>field.key==='project');if(index<0)return;if(!this.fields[index].enabled)this.onFieldToggle(index,true);this.focused=index;this.fieldScroll.scrollEdge(Edge.Bottom);setTimeout(()=>{focusControl.requestFocus('quick-watermark-project');},100);}
  @Link addVisible:boolean;
  @State addLabel: string = '';
  @State addValue: string = '';
  onDismiss: () => void = () => {};
  onFieldChange: (index: number,value: string) => void = (index: number,value: string) => {};
  onFieldToggle: (index: number,value: boolean) => void = (index: number,value: boolean) => {};
  onStyle: (value: string) => void = (value: string) => {};
  onScale: (value: number) => void = (value: number) => {};
  onWidth: (value: number) => void = (value: number) => {};
  onOpacity: (value: number) => void = (value: number) => {};
  onPosition: (value: string) => void = (value: string) => {};
  onAddField: (label: string) => void = (label: string) => {};
  onAddFieldValue: (label: string,value: string) => void = (label: string,value: string) => { this.onAddField(label); };
  build() {
    Stack(){Column({space:10}){Row(){Text('现场快速修改水印').fontSize(this.panelWidth<320?14:18).fontWeight(FontWeight.Bold).fontColor(SITE_TEXT).layoutWeight(1);Button('完成').fontColor('#231A00').backgroundColor(SITE_ACCENT).onClick(()=>this.onDismiss());}.width('100%');
      Button('工程名称').fontColor(SITE_ACCENT).backgroundColor(SITE_CARD_RAISED).onClick(()=>this.editProjectName());
      Scroll(this.fieldScroll){Column({space:10}){Row(){Text('当前水印样式').fontSize(13).fontColor(SITE_MUTED).layoutWeight(1);Button(styleName(this.selectedStyle)).fontColor(SITE_ACCENT).backgroundColor(Color.Transparent).onClick(()=>this.pickerVisible=true);}.width('100%').padding(10).backgroundColor(SITE_CARD).borderRadius(10);
      Text('⚡ 常用工程部位 / 施工标签').fontSize(12).fontColor(SITE_MUTED);
      Scroll(){Row({space:6}){ForEach(TAGS,(tag:string)=>Button('+ '+tag).fontSize(12).fontColor(SITE_ACCENT).backgroundColor(SITE_CARD_RAISED).borderRadius(14).onClick(()=>{const i=Math.min(this.focused,this.fields.length-1);if(i>=0)this.onFieldChange(i,this.fields[i].value?this.fields[i].value+' '+tag:tag);}), (tag:string)=>tag);}}.edgeEffect(EdgeEffect.Spring).scrollable(ScrollDirection.Horizontal).scrollBar(BarState.Off).width('100%');
      Row(){Text('字号缩放比例').fontSize(12).fontColor(SITE_MUTED).layoutWeight(1);Text(this.fontScale.toFixed(1)+'×').fontColor(SITE_ACCENT);}.width('100%');Slider({value:this.fontScale,min:.6,max:2,step:.1}).selectedColor('#FFB300').trackColor('#505050').blockColor('#FFFFFF').onChange((v:number)=>this.onScale(v));
      Row(){Text('水印宽度').fontSize(12).fontColor(SITE_MUTED).layoutWeight(1);Text(Math.round(this.widthRatio*100)+'%').fontColor(SITE_ACCENT);}.width('100%');Slider({value:this.widthRatio*100,min:30,max:90,step:5}).selectedColor('#FFB300').trackColor('#505050').blockColor('#FFFFFF').onChange((v:number)=>this.onWidth(v/100));
      Row(){Text('背景卡片不透明度').fontSize(12).fontColor(SITE_MUTED).layoutWeight(1);Text(Math.round(this.wmOpacity*100)+'%').fontColor(SITE_ACCENT);}.width('100%');Slider({value:this.wmOpacity,min:.3,max:1,step:.1}).selectedColor('#FFB300').trackColor('#505050').blockColor('#FFFFFF').onChange((v:number)=>this.onOpacity(v));
      Row({space:5}){ForEach([['BOTTOM_LEFT','左下'],['BOTTOM_RIGHT','右下'],['TOP_LEFT','左上'],['TOP_RIGHT','右上']],(p:string[])=>Button(p[1]).layoutWeight(1).fontSize(11).fontColor(this.wmPosition===p[0]?'#231A00':SITE_TEXT).backgroundColor(this.wmPosition===p[0]?SITE_ACCENT:SITE_CARD_RAISED).onClick(()=>this.onPosition(p[0])),(p:string[])=>p[0]);}.width('100%');
      Column({space:8}){ForEach(this.fields,(field:Field,index:number)=>Flex({direction:this.panelWidth<320?FlexDirection.Column:FlexDirection.Row,alignItems:ItemAlign.Center}){Row({space:6}){Toggle({type:ToggleType.Switch,isOn:field.enabled}).selectedColor('#FFB300').onChange((v:boolean)=>this.onFieldToggle(index,v));Text(fieldDisplayLabel(field)).width(68).fontSize(13).fontColor(SITE_TEXT);}.margin({right:6,bottom:this.panelWidth<320?6:0});TextInput({text:field.value,placeholder:'留空使用拍摄时信息'}).id(field.key==='project'?'quick-watermark-project':'quick-watermark-'+field.key).fontSize(13).fontColor('#FFFFFF').placeholderColor('#B0BEC5').backgroundColor('#303030').caretColor('#FFB300').borderRadius(10).enabled(field.enabled).width(this.panelWidth<320?'100%':Math.max(80,this.panelWidth-162)).onFocus(()=>this.focused=index).onChange((v:string)=>this.onFieldChange(index,v));},(field:Field,index:number)=>field.key+'_'+index);Button('+ 新增现场自定义字段').fontColor(SITE_ACCENT).backgroundColor(SITE_CARD_RAISED).onClick(()=>{this.addLabel='现场备注';this.addValue='';this.addVisible=true;});}.width('100%');}.width('100%').padding({bottom:12});}.edgeEffect(EdgeEffect.Spring).layoutWeight(1).width('100%').scrollable(ScrollDirection.Vertical).scrollBar(BarState.Off);
    }.width('100%').height('100%').padding(16).backgroundColor(SITE_BG);if(this.pickerVisible){Rect().width('100%').height('100%').fill(SITE_BG);WatermarkStylePickerPanel({snapshot:this.snapshot,selected:this.selectedStyle,onConfirm:(v:string)=>{this.pickerVisible=false;this.onStyle(v);},onCancel:()=>this.pickerVisible=false}).width('100%').height('100%').align(Alignment.Center).transition(TransitionEffect.OPACITY.animation({curve:curves.springMotion(0.35,0.9)}));}if(this.addVisible){Column({space:12}){Text('新增现场自定义字段').fontSize(19).fontWeight(FontWeight.Bold).fontColor(SITE_TEXT);TextInput({text:this.addLabel,placeholder:'字段名称'}).fontColor('#FFFFFF').placeholderColor('#B0BEC5').backgroundColor('#303030').caretColor('#FFB300').borderRadius(10).onChange((v:string)=>this.addLabel=v);TextInput({text:this.addValue,placeholder:'默认值（可留空）'}).fontColor('#FFFFFF').placeholderColor('#B0BEC5').backgroundColor('#303030').caretColor('#FFB300').borderRadius(10).onChange((v:string)=>this.addValue=v);Row({space:10}){Button('取消').layoutWeight(1).onClick(()=>this.addVisible=false);Button('添加').layoutWeight(1).backgroundColor(SITE_ACCENT).fontColor('#231A00').onClick(()=>{if(!this.addLabel.trim())return;this.onAddFieldValue(this.addLabel.trim(),this.addValue);this.addVisible=false;});}.width('100%');}.width('90%').constraintSize({maxWidth:520}).padding(18).backgroundColor(SITE_CARD).borderRadius(14).align(Alignment.Center).transition(TransitionEffect.OPACITY.animation({curve:curves.springMotion(0.35,0.9)}));}}
  }
}
