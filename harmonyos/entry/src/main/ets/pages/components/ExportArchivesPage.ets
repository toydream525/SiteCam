import { ExportArchives, ExportArchive } from '../../core/ExportArchives';
import { timeText } from '../../core/Models';

@Component
export struct ExportArchivesPage {
  service:ExportArchives|null=null;
  onBack:()=>void=()=>{};
  onMessage:(message:string)=>void=()=>{};
  @State files:ExportArchive[]=[];
  @State selected:string[]=[];
  @State busy:boolean=false;
  aboutToAppear():void{void this.refresh();}
  private async load():Promise<void>{const result=await this.service!.list();this.files=result.files;this.selected=this.selected.filter((id:string)=>this.files.some((f:ExportArchive)=>f.id===id));if(result.unreadable)this.onMessage('部分目录无法读取，请重新添加旧包');}
  private async refresh():Promise<void>{if(this.busy)return;this.busy=true;try{await this.load();}catch(e){this.onMessage('读取压缩包失败');}finally{this.busy=false;}}
  private async choose():Promise<void>{if(this.busy)return;this.busy=true;try{if(await this.service!.chooseDirectory())await this.load();}catch(e){this.onMessage('无法访问所选目录');}finally{this.busy=false;}}
  private toggle(id:string):void{if(this.busy)return;this.selected=this.selected.includes(id)?this.selected.filter((value:string)=>value!==id):this.selected.concat([id]);}
  private confirmDelete():void{
    const ids=this.selected.slice();
    AlertDialog.show({title:`删除 ${ids.length} 个压缩包？`,message:'',primaryButton:{value:'取消',action:()=>{}},secondaryButton:{value:'删除',fontColor:'#F44336',action:()=>{void this.remove(ids);}}});
  }
  private async remove(ids:string[]):Promise<void>{if(this.busy)return;this.busy=true;try{const result=await this.service!.delete(ids);this.selected=this.selected.filter((id:string)=>!result.deleted.includes(id));await this.load();if(result.failed.length)this.onMessage(`${result.failed.length} 个压缩包未能删除`);}catch(e){this.onMessage('删除压缩包失败');}finally{this.busy=false;}}
  private formatSize(value:number):string{return value>=1048576?(value/1048576).toFixed(1)+' MB':value>=1024?(value/1024).toFixed(1)+' KB':value+' B';}
  build(){
    Column({space:8}){
      Row(){Button(){Image($r('app.media.ic_arrow_back')).width(24).height(24).fillColor($r('app.color.text_primary'));}.width(48).height(48).backgroundColor(Color.Transparent).accessibilityText('返回').enabled(!this.busy).onClick(()=>this.onBack());Text('导出压缩包').fontSize(21).fontWeight(FontWeight.Bold).fontColor($r('app.color.text_primary')).layoutWeight(1);Button('刷新').fontColor($r('app.color.text_accent')).backgroundColor(Color.Transparent).enabled(!this.busy).onClick(()=>{void this.refresh();});}.constraintSize({minHeight:64}).width('100%');
      Flex({wrap:FlexWrap.Wrap,alignItems:ItemAlign.Center}){Button(this.files.length&&this.selected.length===this.files.length?'取消全选':'全选').backgroundColor(Color.Transparent).fontColor($r('app.color.text_accent')).enabled(!this.busy&&this.files.length>0).onClick(()=>this.selected=this.selected.length===this.files.length?[]:this.files.map((f:ExportArchive)=>f.id));Button('添加旧包').backgroundColor(Color.Transparent).fontColor($r('app.color.text_accent')).enabled(!this.busy).onClick(()=>{void this.choose();});Text(`已选 ${this.selected.length} 个`).fontSize(14).fontColor($r('app.color.text_secondary'));}.width('100%');
      if(this.busy)LoadingProgress().width(28).height(28);
      if(!this.files.length&&!this.busy){Column(){Text('暂无压缩包').fontSize(17).fontColor($r('app.color.text_secondary'));}.width('100%').layoutWeight(1).justifyContent(FlexAlign.Center);}
      else List({space:8}){ForEach(this.files,(f:ExportArchive)=>{ListItem(){Row({space:8}){Checkbox().select(this.selected.includes(f.id)).enabled(!this.busy).onChange(()=>this.toggle(f.id));Column({space:6}){Text(f.name).fontSize(16).fontColor($r('app.color.text_primary')).maxLines(2).textOverflow({overflow:TextOverflow.Ellipsis});Text(timeText(f.date).substring(0,16)+' · '+this.formatSize(f.size)).fontSize(14).fontColor($r('app.color.text_secondary'));}.layoutWeight(1).alignItems(HorizontalAlign.Start);}.width('100%').padding(12).backgroundColor($r('app.color.card_bg')).borderRadius(14).onClick(()=>this.toggle(f.id));}},(f:ExportArchive)=>f.id);}.width('100%').layoutWeight(1);
      Button('删除所选').width('100%').constraintSize({minHeight:52}).backgroundColor('#C64242').fontColor(Color.White).enabled(!this.busy&&this.selected.length>0).onClick(()=>this.confirmDelete());
    }.width('100%').height('100%').padding({left:16,right:16,bottom:16}).backgroundColor($r('app.color.page_bg'));
  }
}
