import { EditRecipe, EditStep, Mark, canonicalRecipe, markPointInOutput, markPointFromOutput, textStepAt, textStepBounds, moveTextStep, serializeEditRecipe, deserializeEditRecipe, parseImportTime, parseImportTimeField, importOrientation } from '../../../main/ets/core/ImageProcessor';
import { watermarkTimeText } from '../../../main/ets/core/Models';

function check(value:boolean,message:string):void {if(!value)throw new Error(message);}
function near(actual:number,expected:number,message:string):void {check(Math.abs(actual-expected)<.00001,message);}
function textAt(x:number,y:number,text:string):EditStep {const step=new EditStep('text');step.mark.kind='text';step.mark.x=x;step.mark.y=y;step.mark.text=text;return step;}

export class Issue56Checks {
  static async annotations():Promise<void> {
    await Issue56Checks.geometry();
    await Issue56Checks.importMetadata();
    await Issue56Checks.hitTesting();
    await Issue56Checks.dragGeometry();
  }
  static async geometry():Promise<void> {
    // A point at (20%,30%) on a 1000x600 image rotates to (70%,20%),
    // mirrors to (30%,20%), then the central crop places it at (25%,25%).
    const recipe=new EditRecipe();recipe.steps=[textAt(.2,.3,'裂缝长约50cm'),new EditStep('rotate'),new EditStep('flipX')];
    const crop=new EditStep('crop');crop.mark.x=.1;crop.mark.y=.1;crop.mark.endX=.9;crop.mark.endY=.5;recipe.steps.push(crop);
    const before=serializeEditRecipe(recipe);
    const output=markPointInOutput(recipe,0,.2,.3);near(output.x,.25,'旋转镜像裁剪后横坐标');near(output.y,.25,'旋转镜像裁剪后纵坐标');
    const moved=markPointFromOutput(recipe,0,.5,.5);near(moved.x,.3,'拖动逆变换横坐标');near(moved.y,.5,'拖动逆变换纵坐标');
    check(serializeEditRecipe(recipe)===before,'命中或坐标换算修改了编辑记录');
    const reopened=deserializeEditRecipe(before);check(reopened.steps[0].mark.text==='裂缝长约50cm','重开未保留全文');
    check(reopened.steps.map((s:EditStep)=>s.kind).join(',')==='text,rotate,flipX,crop','重开改变了原步骤顺序');

    const clipped=new EditRecipe();clipped.steps=[textAt(.1,.2,'已裁掉')];
    const rightHalf=new EditStep('crop');rightHalf.mark.x=.5;rightHalf.mark.y=0;rightHalf.mark.endX=1;rightHalf.mark.endY=1;clipped.steps.push(rightHalf);
    check(markPointInOutput(clipped,0,.1,.2).x<0,'被裁掉的文字被夹回画面');

    const legacy=new EditRecipe();legacy.flipY=true;legacy.flipX=true;legacy.rotation=90;legacy.cropX=.1;legacy.cropW=.8;
    legacy.steps=[new EditStep('arrow')];const note=new Mark();note.kind='text';note.text='旧记录';legacy.marks=[note];
    const legacyBefore=serializeEditRecipe(legacy);const canonical=canonicalRecipe(legacy);
    check(canonical.steps.map((s:EditStep)=>s.kind).join(',')==='flipY,flipX,rotate,crop,arrow,text','旧格式绘制顺序发生变化');
    check(serializeEditRecipe(legacy)===legacyBefore,'规范化修改了原记录');
    check(serializeEditRecipe(canonicalRecipe(canonical))===serializeEditRecipe(canonical),'重复重开改变记录');
  }
  static async importMetadata():Promise<void> {
    check(parseImportTime('2026:10:17 12:30:05','+08:00')===Date.UTC(2026,9,17,4,30,5),'照片时区没有换算');
    check(parseImportTime('2024:02:29 12:00:00','-05:30')===Date.UTC(2024,1,29,17,30,0),'闰日或负时区换算错误');
    for(const value of ['2026:02:29 12:00:00','2026:02:30 12:00:00','2026:13:01 12:00:00','2026:01:01 24:00:00','']) {
      check(parseImportTime(value)===0,'错误 EXIF 日期被滚动为有效日期 '+value);
    }
    const labels=['top-left','top-right','bottom-right','bottom-left','left-top','right-top','right-bottom','left-bottom'];
    for(let i=1;i<=8;i++){check(importOrientation(String(i))===i,'数字方向丢失');check(importOrientation(labels[i-1])===i,'文本方向丢失');}
    check(importOrientation('unknown')===1,'未知方向应保持原方向');
    const expected=new Date(2026,9,17,12,30,5).getTime();
    check(parseImportTimeField('2026.10.17 12:30:05')===expected,'点分导入时间无法解析');
    check(parseImportTimeField('2026-10-17 12:30:05')===expected,'横线导入时间无法解析');
    check(parseImportTimeField('2026.02.30 12:00:00')===0,'非法导入时间被接受');
    check(watermarkTimeText(expected)==='2026.10.17 12:30:05','水印时间格式不一致');
  }
  static async dragGeometry():Promise<void> {
    const recipe=new EditRecipe();const step=textAt(.2,.3,'拖动文字');recipe.steps=[step];
    moveTextStep(recipe,0,.1,.05,1000,600);
    check(step.mark.x>.2&&step.mark.y>.3,'文字拖动没有更新位置');
    const bounds=textStepBounds(recipe,0,1000,600);
    check(bounds.left>=-.0001&&bounds.top>=-.0001&&bounds.right<=1.0001&&bounds.bottom<=1.0001,'拖动后文字超出图片');
  }
  static async hitTesting():Promise<void> {
    const recipe=new EditRecipe();recipe.steps=[textAt(.2,.3,'裂缝长约50cm'),new EditStep('rotate'),new EditStep('flipX')];
    const crop=new EditStep('crop');crop.mark.x=.1;crop.mark.y=.1;crop.mark.endX=.9;crop.mark.endY=.5;recipe.steps.push(crop);
    check(textStepAt(recipe,.25,.25,1000,600)===0,'旋转裁剪后无法命中旧文字');
    const overlap=new EditRecipe();overlap.steps=[textAt(.3,.5,'第一条'),textAt(.3,.5,'第二条')];
    check(textStepAt(overlap,.3,.5,1200,800)===1,'重叠文字没有优先命中顶层');
    check(textStepAt(overlap,.9,.9,1200,800)===-1,'空白处错误命中文字');
    const clipped=new EditRecipe();clipped.steps=[textAt(.1,.2,'已裁掉')];
    const rightHalf=new EditStep('crop');rightHalf.mark.x=.5;rightHalf.mark.y=0;rightHalf.mark.endX=1;rightHalf.mark.endY=1;clipped.steps.push(rightHalf);
    check(textStepAt(clipped,0,.2,1000,600)===-1,'被裁掉的文字仍抢占图片触区');
  }
}
