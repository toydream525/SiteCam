import { FontChoice } from '../../core/FontLibrary';

@CustomDialog
struct FontPickerDialog {
  controller:CustomDialogController;
  choices:FontChoice[]=[];
  selected:string='';
  onSelect:(id:string)=>void=()=>{};
  onImport:()=>Promise<FontChoice|null>=async()=>null;
  onMessage:(value:string)=>void=()=>{};
  @State importing:boolean=false;
  private async importFont():Promise<void>{if(this.importing)return;this.importing=true;try{const font=await this.onImport();if(font){this.onSelect(font.id);this.controller.close();}}catch(e){this.onMessage('无法导入字体，请选择有效的 TTF 或 OTF 文件');}finally{this.importing=false;}}
  build(){Column({space:12}){Text('字体').fontSize(21).fontWeight(FontWeight.Bold).fontColor($r('app.color.text_primary'));Scroll(){Column(){ForEach(this.choices,(item:FontChoice)=>{Row(){Text(item.name).fontSize(16).fontColor($r('app.color.text_primary')).layoutWeight(1);Radio({value:item.id,group:'font'}).checked(item.id===this.selected).radioStyle({checkedBackgroundColor:'#FFB300',indicatorColor:'#231A00'}).onChange((value:boolean)=>{if(value){this.onSelect(item.id);this.controller.close();}});}.width('100%').constraintSize({minHeight:52}).onClick(()=>{this.onSelect(item.id);this.controller.close();});},(item:FontChoice)=>item.id);}.width('100%');}.layoutWeight(1).width('100%');if(this.importing)LoadingProgress().width(28).height(28);Button('导入字体').width('100%').fontColor('#231A00').backgroundColor('#FFB300').enabled(!this.importing).onClick(()=>{void this.importFont();});Button('完成').width('100%').fontColor($r('app.color.text_primary')).backgroundColor(Color.Transparent).enabled(!this.importing).onClick(()=>this.controller.close());}.padding(20).width('90%').height('90%').constraintSize({maxWidth:520,maxHeight:560}).backgroundColor($r('app.color.card_bg')).borderRadius(16);}
}

@Component
export struct TextAppearanceControls {
  @Prop dark:boolean=false;
  private foreground():ResourceColor{return this.dark?'#ECEFF1':$r('app.color.text_primary');}
  private muted():ResourceColor{return this.dark?'#B0BEC5':$r('app.color.text_secondary');}
  @Prop fontId:string='';@Prop textColor:string='';@Prop choices:FontChoice[]=[];
  @Prop showBoard:boolean=false;@Prop whiteboard:boolean=false;@Prop defaultColor:boolean=true;
  onFont:(value:string)=>void=()=>{};onColor:(value:string)=>void=()=>{};onBoard:(value:boolean)=>void=()=>{};
  onImport:()=>Promise<FontChoice|null>=async()=>null;onMessage:(value:string)=>void=()=>{};
  private picker:CustomDialogController|null=null;
  private open():void{this.picker=new CustomDialogController({customStyle:true,builder:FontPickerDialog({choices:this.choices,selected:this.fontId,onSelect:this.onFont,onImport:this.onImport,onMessage:this.onMessage})});this.picker.open();}
  build(){Column({space:12}){
    Row(){Text('字体').fontSize(16).fontColor(this.foreground()).layoutWeight(1);Text(this.choices.find((value:FontChoice)=>value.id===this.fontId)?.name||'默认字体').fontSize(14).fontColor(this.muted()).maxLines(2);Text('›').fontSize(24).fontColor(this.muted());}.width('100%').constraintSize({minHeight:52}).onClick(()=>this.open());
    Text('文字颜色').fontSize(16).fontColor(this.foreground()).width('100%');
    Flex({wrap:FlexWrap.Wrap}){ForEach(['#FFFFFF','#FFB300','#17212B','#F44336','#1262AE','#168D80'],(value:string)=>{Button().width(48).height(48).padding(6).backgroundColor(value).borderRadius(24).border({width:this.textColor===value?3:1,color:this.textColor===value?'#FFB300':'#808080'}).margin({right:8,bottom:8}).accessibilityText('文字颜色 '+value).onClick(()=>this.onColor(value));},(value:string)=>value);}.width('100%');
    if(this.defaultColor)Button('跟随样式').fontColor($r('app.color.text_accent')).backgroundColor(Color.Transparent).onClick(()=>this.onColor(''));
    if(this.showBoard)Flex({wrap:FlexWrap.Wrap}){Button('普通').backgroundColor(this.whiteboard?$r('app.color.card_raised'):'#FFB300').fontColor(this.whiteboard?this.foreground():'#231A00').onClick(()=>this.onBoard(false));Button('白板框').backgroundColor(this.whiteboard?'#FFB300':$r('app.color.card_raised')).fontColor(this.whiteboard?'#231A00':this.foreground()).onClick(()=>{this.onBoard(true);this.onColor('#17212B');});}.width('100%');
  }.width('100%').alignItems(HorizontalAlign.Start);}
}
