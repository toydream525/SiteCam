import { SITE_ACCENT, SITE_CARD, SITE_MUTED, SITE_TEXT } from './UiPrimitives';

@CustomDialog
export struct QuickIssueDialog {
  controller: CustomDialogController;
  @Prop maxHeight: number=520;
  @State title: string = '';
  @State severity: string = 'NORMAL';
  @State status: string = 'PENDING';
  @State notes: string = '';
  @State titleError: boolean = false;
  @Prop existingTitle: string = '';
  @Prop existingSeverity: string = 'NORMAL';
  @Prop existingStatus: string = 'PENDING';
  @Prop existingNotes: string = '';
  onRemove: () => void = () => {};
  aboutToAppear(): void { this.title=this.existingTitle; this.severity=this.existingSeverity||'NORMAL'; this.status=this.existingStatus||'PENDING'; this.notes=this.existingNotes; }
  onConfirm: (title: string, severity: string, status: string, notes: string) => void = (title: string, severity: string, status: string, notes: string) => {};
  build() {
    Scroll(){Column({ space: 10 }) {
      Text('记录现场问题').fontSize(20).fontWeight(FontWeight.Bold).fontColor(SITE_TEXT);
      TextInput({ text: this.title, placeholder: '问题名称（如：临边未设防护栏）' }).fontColor('#FFFFFF').placeholderColor('#B0BEC5').backgroundColor('#303030').caretColor('#FFB300').borderRadius(10).onChange(v=>{this.title=v;if(v.trim())this.titleError=false;}).fontColor(SITE_TEXT).backgroundColor('#303030');
      if(this.titleError)Text('问题名称不能为空').fontSize(12).fontColor('#F44336').margin({left:4,top:2});
      Text('严重等级').fontSize(13).fontWeight(FontWeight.Medium).fontColor(SITE_MUTED);
      Row({ space: 5 }) { ForEach(['NORMAL','IMPORTANT','CRITICAL'],(value:string)=>{Button(value==='NORMAL'?'一般':value==='IMPORTANT'?'重要':'严重').layoutWeight(1).fontColor(this.severity===value?(value==='CRITICAL'?Color.White:'#231A00'):SITE_TEXT).backgroundColor(this.severity===value?(value==='CRITICAL'?'#D32F2F':value==='IMPORTANT'?'#F6C344':SITE_ACCENT):'#303030').onClick(()=>this.severity=value);},(value:string)=>value); }.width('100%');
      Text('处理状态').fontSize(13).fontWeight(FontWeight.Medium).fontColor(SITE_MUTED);
      Row({ space: 5 }) { ForEach(['PENDING','IN_PROGRESS','COMPLETED'],(value:string)=>{Button(value==='PENDING'?'待处理':value==='IN_PROGRESS'?'处理中':'已完成').layoutWeight(1).fontColor(this.status===value?'#231A00':SITE_TEXT).backgroundColor(this.status===value?SITE_ACCENT:'#303030').onClick(()=>this.status=value);},(value:string)=>value); }.width('100%');
      TextInput({ text: this.notes, placeholder: '问题原因 / 整改要求 / 备注' }).fontColor('#FFFFFF').placeholderColor('#B0BEC5').backgroundColor('#303030').caretColor('#FFB300').borderRadius(10).onChange(v=>this.notes=v).fontColor(SITE_TEXT).backgroundColor('#303030').maxLines(3).height(86);
      Row({ space: 12 }) { if(this.existingTitle){Button('取消问题').fontColor('#F44336').backgroundColor(Color.Transparent).onClick(()=>{this.controller.close();this.onRemove();});}Button('取消').fontColor(SITE_MUTED).backgroundColor(Color.Transparent).onClick(()=>this.controller.close());Button('保存问题记录').fontColor('#231A00').backgroundColor(SITE_ACCENT).onClick(()=>{if(!this.title.trim()){this.titleError=true;return;}this.controller.close();this.onConfirm(this.title.trim(),this.severity,this.status,this.notes.trim());}); }.width('100%').justifyContent(FlexAlign.End);
    }.padding(18).backgroundColor(SITE_CARD).borderRadius(14).width('100%');}.width('92%').constraintSize({maxWidth:560,maxHeight:this.maxHeight}).align(Alignment.Top).scrollBar(BarState.Off);
  }
}
