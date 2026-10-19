import { SITE_ACCENT, SITE_CARD, SITE_TEXT } from './UiPrimitives';
@CustomDialog
export struct TextMarkDialog {
 controller:CustomDialogController; maxHeight:number=360;initial:string=''; onConfirm:(text:string)=>void=()=>{};
 @State value:string=''; aboutToAppear():void{this.value=this.initial;}
 build(){Scroll(){Column({space:16}){Text('编辑文字').fontSize(20).fontColor(SITE_TEXT);TextArea({text:this.value}).height(150).onChange(v=>this.value=v);Row({space:16}){Button('取消').layoutWeight(1).onClick(()=>this.controller.close());Button('确定').layoutWeight(1).backgroundColor(SITE_ACCENT).enabled(!!this.value.trim()).onClick(()=>{this.onConfirm(this.value);this.controller.close();});}.width('100%');}.padding(20).width('100%');}.width('90%').constraintSize({maxWidth:520,maxHeight:this.maxHeight}).scrollBar(BarState.Off).backgroundColor(SITE_CARD).borderRadius(16);}
}
