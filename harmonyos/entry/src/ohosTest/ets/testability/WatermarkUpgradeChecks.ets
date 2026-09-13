import { image } from '@kit.ImageKit';
import { MediaService } from '../../../main/ets/core/MediaService';
import { watermarkPixelMap } from '../../../main/ets/core/WatermarkRenderer';
import { common } from '@kit.AbilityKit';
import { LengthMetricsUnit } from '@kit.ArkUI';
import { Field, Fix, Snapshot, STYLES, Watermark, WatermarkField, migrateWatermarkFields, resetWatermarkFields } from '../../../main/ets/core/Models';
import { resolveFields, layoutWatermark } from '../../../main/ets/core/WatermarkRenderer';
import { Store } from '../../../main/ets/core/Store';
function check(ok:boolean,message:string):void {if(!ok)throw new Error(message);}
export class WatermarkUpgradeChecks {
  static async presentation(context:common.UIAbilityContext):Promise<void> {
    const s=new Snapshot();s.project='工程';s.address='地址';
    const f=new Field('custom_test','自定义首行','第一行\n第二行');s.watermark.fields.unshift(f);
    s.watermark.fields[1].label='今日水印这是需要完整显示的长标签';
    for(const style of STYLES){s.watermark.style=style.id;const fields=resolveFields(s);check(fields[0].key===f.key,style.id+'排序错误');check(fields[1].label===s.watermark.fields[1].label,style.id+'改名丢失');
      const ctx=new OffscreenCanvas(1080,1440,LengthMetricsUnit.PX).getContext('2d');const l=layoutWatermark(ctx,1080,1440,s);
      check(l.lines.filter(v=>v.key==='project').map(v=>v.text).join('').includes(s.watermark.fields[1].label),style.id+'未绘制标签');
      check(l.lines.filter(v=>!!v.key)[0].key===f.key,style.id+'绘制重排');
      for(const line of l.lines)check(line.x+line.width<=l.x+l.w+1&&line.y+line.descent<=l.y+l.h+1,'文字越界');
      const pm=watermarkPixelMap(1080,1440,s);const pack=image.createImagePacker();try{await new MediaService(context).write(context.filesDir+'/upgrade_'+style.id+'.png',await pack.packing(pm,{format:'image/png',quality:100}));}finally{await pack.release();await pm.release();}
      for(const r of l.rects.filter(v=>v.color===l.accent))check(r.y>l.lines.filter(v=>!!v.key)[0].y,'背景遮盖前面的字段');
    }
    const legacy=JSON.parse(JSON.stringify(s).replace('"presentationVersion":2,','')) as Snapshot;
    legacy.watermark.style='ENGINEERING_BLUE';check(resolveFields(legacy)[0].key==='project','旧快照排序变化');check(resolveFields(legacy)[0].label==='工程名称','旧快照标签变化');
    s.watermark.fields[0].enabled=false;check(!resolveFields(s).some(v=>v.key===f.key),'禁用字段仍显示');
    const elevation=new Snapshot();elevation.watermark.fields=[new Field('elevation','海拔')];elevation.watermark.fields[0].enabled=true;elevation.locationStatus='FRESH';
    for(const value of [12,0,-3]){const fix=new Fix();fix.altitude=value;fix.timestamp=elevation.timestamp;elevation.fix=fix;const rendered=resolveFields(elevation).find(v=>v.key==='elevation');check(!!rendered&&rendered!.value===`${value.toFixed(1)} m`,'海拔正/零/负值格式错误');}
    const missingFix=new Fix();missingFix.altitude=null;missingFix.timestamp=elevation.timestamp;elevation.fix=missingFix;check(resolveFields(elevation).find(v=>v.key==='elevation')!.value==='暂不可用','缺失海拔未显示暂不可用');
    const staleFix=new Fix();staleFix.altitude=12;staleFix.timestamp=elevation.timestamp;elevation.fix=staleFix;elevation.locationStatus='STALE';check(resolveFields(elevation).find(v=>v.key==='elevation')!.value==='暂不可用','陈旧海拔未显示暂不可用');
    const oldSnapshot=new Snapshot();oldSnapshot.presentationVersion=1;oldSnapshot.locationStatus='FRESH';oldSnapshot.fix=staleFix;oldSnapshot.watermark.fields=[new Field('project','工程名称'),new Field('category','工程类型'),new Field('time','拍摄时间'),new Field('address','现场位置'),new Field('gps','GPS定位'),new Field('user','拍摄人员')];check(!resolveFields(oldSnapshot).some(v=>v.key==='elevation'),'旧快照被补入海拔');
  }
  static reset():void {
    const w=new Watermark();w.style='MINIMAL';w.fields[0].label='改名';w.fields[0].value='保留内容';w.fields[0].enabled=false;w.fields.unshift(new Field('custom_test','自定义','保留自定义'));const before=JSON.stringify(w);
    const r=resetWatermarkFields(w);check(JSON.stringify(w)===before,'修改了输入草稿');check(r.fields[0].label==='工程名称'&&r.fields[0].value==='保留内容'&&!r.fields[0].enabled,'恢复破坏字段内容或开关');check(r.fields[r.fields.length-1].value==='保留自定义'&&r.style==='MINIMAL','自定义字段或样式丢失');check(JSON.stringify(resetWatermarkFields(r))===JSON.stringify(r),'恢复不幂等');
    check(r.fields.some((f:Field)=>f.key==='elevation'&&!f.enabled),'恢复后海拔未保持关闭');
    const reordered=new Watermark();reordered.fields=[new Field('custom_first','自定义首项','首项内容'),new Field('gps','旧坐标','旧坐标内容'),new Field('address','旧地点','旧地点内容')];reordered.fields[0].enabled=false;const beforeMigration=JSON.stringify(reordered);const migrated=migrateWatermarkFields(reordered);check(JSON.stringify(reordered)===beforeMigration,'迁移修改了输入模板');check(migrated.fields.map((f:Field)=>f.key).join(',')==='custom_first,gps,address,elevation','重排模板海拔位置错误');check(migrated.fields[0].label==='自定义首项'&&migrated.fields[0].value==='首项内容'&&!migrated.fields[0].enabled&&migrated.fields[1].label==='旧坐标'&&migrated.fields[1].value==='旧坐标内容','重排模板旧字段未保留');check(migrated.fields[3].key==='elevation'&&!migrated.fields[3].enabled,'迁移海拔未默认关闭');
    const partial=new Watermark();partial.fields=[new Field('custom_only','仅自定义','自定义内容'),new Field('gps','坐标','坐标内容')];const partialMigrated=migrateWatermarkFields(partial);check(partialMigrated.fields.map((f:Field)=>f.key).join(',')==='custom_only,gps,elevation'&&!partialMigrated.fields[2].enabled,'部分模板未补入关闭海拔');
  }
  static async reopen(ctx:common.Context):Promise<void>{
    const name='wm_upgrade_'+Date.now();const db=new Store(name);await db.open(ctx);const id=db.settings.templateId;
    db.settings.watermark.fields[0].label='今日水印';db.settings.watermark.fields[0].value='现场内容';await db.saveCurrentWatermark();
    const gps=new WatermarkField();gps.templateId=id;gps.fieldKey='gps';gps.label='旧坐标';gps.defaultValue='旧坐标值';gps.displayOrder=0;gps.isEnabled=true;const address=new WatermarkField();address.templateId=id;address.fieldKey='address';address.label='旧地点';address.defaultValue='旧地点值';address.displayOrder=1;address.isEnabled=false;const custom=new WatermarkField();custom.templateId=id;custom.fieldKey='custom_reordered';custom.label='自定义';custom.defaultValue='自定义值';custom.displayOrder=2;custom.isEnabled=true;await db.putTemplateField(gps);await db.putTemplateField(address);await db.putTemplateField(custom);await db.close();
    const reopened=new Store(name);await reopened.open(ctx);try{check(reopened.settings.watermark.fields[0].label==='今日水印'&&reopened.settings.watermark.fields[0].value==='现场内容','设置重开丢失');const t=(await reopened.templates()).find(v=>v.id===id);check(!!t,'模板缺失');const wm=JSON.parse(t!.config) as Watermark;check(wm.fields[0].label==='今日水印'&&wm.fields[0].value==='现场内容','模板未同步');const fields=await reopened.templateFields(id);check(fields.map((f:WatermarkField)=>f.fieldKey).join(',')==='gps,address,elevation,custom_reordered','模板字段迁移顺序错误');check(fields[0].label==='旧坐标'&&fields[0].defaultValue==='旧坐标值'&&fields[1].label==='旧地点'&&fields[1].defaultValue==='旧地点值'&&!fields[1].isEnabled,'模板旧字段内容或开关丢失');check(fields[2].fieldKey==='elevation'&&!fields[2].isEnabled,'模板重开海拔未默认关闭');}finally{await reopened.close();}
  }
}
