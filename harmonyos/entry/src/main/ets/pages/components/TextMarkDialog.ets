import { SITE_ACCENT, SITE_CARD, SITE_TEXT } from './UiPrimitives';
import { FontChoice } from '../../core/FontLibrary';
import { TextAppearanceControls } from './TextAppearanceControls';
import { fontFamily } from '../../core/Models';
@CustomDialog
export struct TextMarkDialog {
 controller:CustomDialogController; maxHeight:number=360;initial:string=''; onConfirm:(text:string,fontId:string,color:string,whiteboard:boolean)=>void=()=>{};
 initialFont:string='';initialColor:string='#FFB300';initialBoard:boolean=false;fonts:FontChoice[]=[];
 onImport:()=>Promise<FontChoice|null>=async()=>null;onMessage:(value:string)=>void=()=>{};
 @State fontId:string='';@State color:string='#FFB300';@State whiteboard:boolean=false;
 @State availableFonts:FontChoice[]=[];
 private async importFont():Promise<FontChoice|null>{const choice=await this.onImport();if(choice&&!this.availableFonts.some((f:FontChoice)=>f.id===choice.id))this.availableFonts=this.availableFonts.concat([choice]);return choice;}
 @State value:string=''; aboutToAppear():void{this.value=this.initial;this.fontId=this.initialFont;this.color=this.initialColor;this.whiteboard=this.initialBoard;this.availableFonts=this.fonts.slice();}
 build(){Column({space:12}){
  Text('编辑文字').fontSize(20).fontColor(SITE_TEXT).flexShrink(0);
  Scroll(){Column({space:12}){TextArea({text:this.value}).height(110).onChange(v=>this.value=v);
   TextAppearanceControls({dark:true,fontId:this.fontId,textColor:this.color,choices:this.availableFonts,showBoard:true,whiteboard:this.whiteboard,defaultColor:false,onFont:(value:string)=>this.fontId=value,onColor:(value:string)=>this.color=value,onBoard:(value:boolean)=>this.whiteboard=value,onImport:()=>this.importFont(),onMessage:this.onMessage});
   if(this.value)Text(this.value).fontFamily(fontFamily(this.fontId)).fontSize(20).fontColor(this.color).padding(12).width('100%').maxLines(1).textOverflow({overflow:TextOverflow.Ellipsis}).backgroundColor(this.whiteboard?'#FAFAF7':'#CC121212').border({width:this.whiteboard?1:0,color:'#17212B'});
  }.width('100%');}.width('100%').layoutWeight(1).scrollBar(BarState.Off);
  Row({space:16}){Button('取消').layoutWeight(1).onClick(()=>this.controller.close());Button('确定').layoutWeight(1).backgroundColor(SITE_ACCENT).enabled(!!this.value.trim()).onClick(()=>{this.onConfirm(this.value,this.fontId,this.color,this.whiteboard);this.controller.close();});}.width('100%').flexShrink(0);
 }.padding(20).width('90%').height(Math.min(620,this.maxHeight)).constraintSize({maxWidth:520,maxHeight:this.maxHeight}).backgroundColor(SITE_CARD).borderRadius(16);}
}
