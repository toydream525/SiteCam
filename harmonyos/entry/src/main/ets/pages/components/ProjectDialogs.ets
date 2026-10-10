import { Project } from '../../core/Models';
import { OFFLINE_BUILD } from '../../core/BuildVariant';
import { SITE_ACCENT, PAGE_CARD as SITE_CARD, PAGE_MUTED as SITE_MUTED, PAGE_TEXT as SITE_TEXT } from './UiPrimitives';

@CustomDialog
export struct ProjectEditorDialog {
  controller: CustomDialogController;
  @Prop offlineMode: boolean = OFFLINE_BUILD;
  @Prop project: Project = new Project();
  @Prop categories: string[] = [];
  onConfirm: (project: Project) => Promise<void> = async (project: Project):Promise<void> => {};
  @State pending:boolean=false;
  @State error:string='';
  private async submit():Promise<void>{if(this.pending||!this.name.trim())return;const p=JSON.parse(JSON.stringify(this.project)) as Project;p.name=this.name.trim();p.route=this.route;p.category=this.category||'未分类';p.address=this.address;p.notes=this.notes;this.pending=true;this.error='';try{await this.onConfirm(p);this.controller.close();}catch(e){this.project=p;this.error=(e as Error).message||'保存失败，请重试';}finally{this.pending=false;}}
  onCancel: () => void = () => {};
  @State name: string = '';
  @State route: string = '';
  @State category: string = '';
  @State address: string = '';
  @State notes: string = '';
  aboutToAppear(): void { this.name=this.project.name;this.route=this.project.route;this.category=this.project.category;this.address=this.project.address;this.notes=this.project.notes; }
  @Builder
  field(label: string, value: string, set: (value: string) => void) {
    TextInput({ text: value, placeholder: label }).fontColor($r('app.color.text_primary')).placeholderColor($r('app.color.text_secondary')).backgroundColor($r('app.color.card_raised')).caretColor('#FFB300').borderRadius(10).onChange(set).fontColor(SITE_TEXT).backgroundColor($r('app.color.card_raised')).width('100%');
  }
  build() {
    Scroll(){Column({ space: 10 }) {
      Text(this.project.id ? '编辑工程包' : '新建工程包').fontSize(20).fontWeight(FontWeight.Bold).fontColor(SITE_TEXT);
      this.field('工程名称',this.name,(v: string)=>this.name=v);
      this.field('线路名称（可选）',this.route,(v: string)=>this.route=v);
      Scroll(){Row({ space: 6 }) { ForEach(this.categories,(value: string)=>{Button(value).fontSize(12).fontColor(this.category===value?'#231A00':SITE_TEXT).backgroundColor(this.category===value?SITE_ACCENT:$r('app.color.card_raised')).onClick(()=>this.category=value);},(value: string)=>value); }}.scrollable(ScrollDirection.Horizontal).scrollBar(BarState.Off).width('100%');
      this.field(this.offlineMode?'工程地点（手动填写，可选）':'工程地点（可选）',this.address,(v: string)=>this.address=v);
      TextArea({text:this.notes,placeholder:'工程备注（可选）'}).fontColor($r('app.color.text_primary')).placeholderColor($r('app.color.text_secondary')).backgroundColor($r('app.color.card_raised')).caretColor('#FFB300').borderRadius(10).height(72).fontColor(SITE_TEXT).backgroundColor($r('app.color.card_raised')).width('100%').onChange((v:string)=>this.notes=v);
      if(this.error)Text(this.error).fontSize(14).fontColor('#D32F2F').width('100%');
      Row({ space: 12 }) { Button('取消').enabled(!this.pending).fontColor(SITE_MUTED).backgroundColor(Color.Transparent).onClick(()=>{this.controller.close();this.onCancel();});Button('保存').enabled(!this.pending&&!!this.name.trim()).fontColor('#231A00').backgroundColor(SITE_ACCENT).onClick(()=>this.submit()); }.width('100%').justifyContent(FlexAlign.End);
    }.padding(18).backgroundColor(SITE_CARD).borderRadius(14).width('90%');
    }.width('100%').constraintSize({maxWidth:600,maxHeight:'90%'}).align(Alignment.Top).scrollBar(BarState.Off);
  }
}
