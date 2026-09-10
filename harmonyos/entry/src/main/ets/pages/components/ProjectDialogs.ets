import { Project } from '../../core/Models';
import { SITE_ACCENT, SITE_CARD, SITE_MUTED, SITE_TEXT } from './UiPrimitives';

@CustomDialog
export struct ProjectEditorDialog {
  controller: CustomDialogController;
  @Prop project: Project = new Project();
  @Prop categories: string[] = [];
  onConfirm: (project: Project) => void = (project: Project) => {};
  onCancel: () => void = () => {};
  @State name: string = '';
  @State route: string = '';
  @State category: string = '';
  @State address: string = '';
  @State notes: string = '';
  aboutToAppear(): void { this.name=this.project.name;this.route=this.project.route;this.category=this.project.category;this.address=this.project.address;this.notes=this.project.notes; }
  @Builder
  field(label: string, value: string, set: (value: string) => void) {
    TextInput({ text: value, placeholder: label }).fontColor('#FFFFFF').placeholderColor('#B0BEC5').backgroundColor('#303030').caretColor('#FFB300').borderRadius(10).onChange(set).fontColor(SITE_TEXT).backgroundColor('#303030').width('100%');
  }
  build() {
    Scroll(){Column({ space: 10 }) {
      Text(this.project.id ? '编辑工程包' : '新建工程包').fontSize(20).fontWeight(FontWeight.Bold).fontColor(SITE_TEXT);
      this.field('工程名称',this.name,(v: string)=>this.name=v);
      this.field('线路名称（可选）',this.route,(v: string)=>this.route=v);
      Scroll(){Row({ space: 6 }) { ForEach(this.categories,(value: string)=>{Button(value).fontSize(12).fontColor(this.category===value?'#231A00':SITE_TEXT).backgroundColor(this.category===value?SITE_ACCENT:'#303030').onClick(()=>this.category=value);},(value: string)=>value); }}.scrollable(ScrollDirection.Horizontal).width('100%');
      this.field('工程地点（可选）',this.address,(v: string)=>this.address=v);
      TextArea({text:this.notes,placeholder:'工程备注（可选）'}).fontColor('#FFFFFF').placeholderColor('#B0BEC5').backgroundColor('#303030').caretColor('#FFB300').borderRadius(10).height(72).fontColor(SITE_TEXT).backgroundColor('#303030').width('100%').onChange((v:string)=>this.notes=v);
      Row({ space: 12 }) { Button('取消').fontColor(SITE_MUTED).backgroundColor(Color.Transparent).onClick(()=>{this.controller.close();this.onCancel();});Button('保存').enabled(!!this.name.trim()).fontColor('#231A00').backgroundColor(SITE_ACCENT).onClick(()=>{const p=JSON.parse(JSON.stringify(this.project)) as Project;p.name=this.name.trim();p.route=this.route;p.category=this.category||'未分类';p.address=this.address;p.notes=this.notes;this.controller.close();this.onConfirm(p);}); }.width('100%').justifyContent(FlexAlign.End);
    }.padding(18).backgroundColor(SITE_CARD).borderRadius(14).width('90%');
    }.width('100%').constraintSize({maxWidth:600,maxHeight:'90%'}).align(Alignment.Top);
  }
}
