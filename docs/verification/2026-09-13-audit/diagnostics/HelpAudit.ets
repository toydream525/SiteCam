import { common } from '@kit.AbilityKit';
import { fileIo as fs } from '@kit.CoreFileKit';
import { Driver, ON, MatchPattern } from '@ohos.UiTest';
import { HelpPageView } from '../../../../main/ets/pages/components/HelpPageView';
class Case {title:string='';expected:string='';clicked:boolean=false;pass:boolean=false;oldInstallBodyVisible:boolean=false;error:string='';}
class Report {source:string='Unmodified production HelpPageView; temporary component interaction test';cases:Case[]=[];error:string='';}
@Entry @Component
struct HelpAudit {
  private running:boolean=false;
  @State status:string='教程切换检查';
  private async audit():Promise<void>{
    if(this.running)return;this.running=true;
    const report=new Report();
    try{
      const driver=Driver.create();await driver.delayMs(1200);
      const titles:string[]=['安装与权限','使用指南','首次功能指引'];
      const bodies:string[]=['从项目官网或 GitHub Releases 下载 APK','本版：0.2.8','新装或首次使用会看到 4 页指引'];
      for(let i=0;i<titles.length;i++){
        const row=new Case();row.title=titles[i];row.expected=bodies[i];
        try{const tab=await driver.findComponent(ON.text(row.title));if(!tab)throw new Error('找不到章节按钮');await tab.click();row.clicked=true;await driver.delayMs(600);row.pass=!!(await driver.findComponent(ON.text(row.expected,MatchPattern.CONTAINS)));row.oldInstallBodyVisible=!!(await driver.findComponent(ON.text(bodies[0],MatchPattern.CONTAINS)));}catch(e){row.error=(e as Error).message;}
        report.cases.push(row);
      }
    }catch(e){report.error=(e as Error).message;}
    const context=getContext(this) as common.UIAbilityContext;
    const file=fs.openSync(context.filesDir+'/help-audit.json',fs.OpenMode.CREATE|fs.OpenMode.READ_WRITE|fs.OpenMode.TRUNC);
    try{fs.writeSync(file.fd,JSON.stringify(report,null,2));}finally{fs.closeSync(file);}
    console.info('SITECAM_HELP_AUDIT '+JSON.stringify(report));
    this.status=`教程检查结束：${report.cases.filter((v:Case)=>v.pass).length}/${report.cases.length} ${report.error}`;
  }
  build(){Column(){Text(this.status).padding(8);HelpPageView({}).layoutWeight(1);}.width('100%').height('100%').onAppear(()=>this.audit());}
}
