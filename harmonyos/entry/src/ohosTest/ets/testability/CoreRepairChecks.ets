import { common } from '@kit.AbilityKit';
import { fileIo as fs } from '@kit.CoreFileKit';
import { relationalStore } from '@kit.ArkData';
import { Store } from '../../../main/ets/core/Store';
import { MediaDeletionCoordinator } from '../../../main/ets/core/MediaDeletionCoordinator';
import { Project, MediaItem, Snapshot, Fix, Annotation, Watermark } from '../../../main/ets/core/Models';

class FailingDeletionStore extends Store {
  rejectDeletion:boolean=true;
  async commitMediaDeletion(items:MediaItem[],projectIds:number[]):Promise<void>{
    if(this.rejectDeletion)throw new Error('injected deletion commit failure');
    await super.commitMediaDeletion(items,projectIds);
  }
}
interface RecoveryFile {original:string;staged:string;}
interface RecoveryJournal {mediaIds:number[];projectIds:number[];files:RecoveryFile[];}

export class CoreRepairChecks {
  private static assert(value:boolean,message:string):void{if(!value)throw new Error(message);}
  private static async write(path:string,value:string):Promise<void>{const file=await fs.open(path,fs.OpenMode.CREATE|fs.OpenMode.WRITE_ONLY|fs.OpenMode.TRUNC);try{await fs.write(file.fd,value);}finally{await fs.close(file);}}

  static async settingsOrderAndReopen(ctx:common.Context):Promise<void>{
    const namespace='core_settings_'+Date.now(),first=new Store(namespace);await first.open(ctx);
    const templateId=first.settings.templateId;
    first.settings.cameraControlStyle='BLUR';first.settings.currentProject=31;
    const a=first.saveSettings();first.settings.watermark.opacity=0.3;const b=first.saveCurrentWatermark();
    first.settings.currentProject=47;first.settings.photoQuality=2;const c=first.saveSettings();
    first.settings.watermark.opacity=0.8;const d=first.saveCurrentWatermark();
    await Promise.all([a,b,c,d]);await first.close();
    const reopened=new Store(namespace);await reopened.open(ctx);
    try{
      CoreRepairChecks.assert(reopened.settings.cameraControlStyle==='BLUR','camera appearance was lost on reopen');
      CoreRepairChecks.assert(reopened.settings.currentProject===47&&reopened.settings.photoQuality===2,'watermark save overwrote newer project or quality');
      CoreRepairChecks.assert(reopened.settings.watermark.opacity===0.8,'latest watermark settings were lost');
      const template=(await reopened.templates()).find(value=>value.id===templateId);
      CoreRepairChecks.assert(!!template&&(JSON.parse(template!.config) as Watermark).opacity===0.8,'current template did not keep the latest watermark');
    }finally{await reopened.close();}
  }

  static async videoMetadataAndLegacyAddress(ctx:common.Context):Promise<void>{
    const namespace='core_metadata_'+Date.now(),data=new Store(namespace);await data.open(ctx);
    const project=new Project();project.name='snapshot fixture';await data.putProject(project);
    const snapshot=new Snapshot();snapshot.projectId=project.id;snapshot.address='拍摄时的工程地址';snapshot.locationStatus='FRESH';snapshot.captureTimestamp=123456;
    const fix=new Fix();fix.latitude=0;fix.longitude=120.25;fix.altitude=12;fix.accuracy=3;fix.timestamp=123450;snapshot.fix=fix;
    const located=new MediaItem();located.type='VIDEO';located.snapshot=JSON.stringify(snapshot);await data.putMedia(located);
    const got=await data.mediaById(located.id);
    CoreRepairChecks.assert(!!got&&got!.latitude===0&&got!.longitude===120.25&&got!.locationStatus==='FRESH'&&got!.addressText===snapshot.address&&got!.locationTimestamp===123450,'video metadata missing before reopening');
    snapshot.fix=null;snapshot.locationStatus='UNAVAILABLE';snapshot.address='手动工程地址';
    const manual=new MediaItem();manual.type='VIDEO';manual.snapshot=JSON.stringify(snapshot);await data.putMedia(manual);
    const manualNow=await data.mediaById(manual.id);
    CoreRepairChecks.assert(!!manualNow&&manualNow!.latitude===null&&manualNow!.addressText==='手动工程地址','manual address missing before reopening');
    await data.close();
    const raw=await relationalStore.getRdbStore(ctx,{name:namespace+'.db',securityLevel:relationalStore.SecurityLevel.S1});
    await raw.executeSql("UPDATE media SET addressText='' WHERE id=?",[manual.id]);await raw.close();
    const reopened=new Store(namespace);await reopened.open(ctx);
    try{const legacy=await reopened.mediaById(manual.id);CoreRepairChecks.assert(legacy?.addressText==='手动工程地址','legacy snapshot without GPS did not recover its address');}finally{await reopened.close();}
  }

  static async deletionRollbackAndRecovery(ctx:common.Context):Promise<void>{
    const suffix=Date.now(),data=new FailingDeletionStore('core_delete_'+suffix),root=ctx.cacheDir+'/core_delete_'+suffix;
    await data.open(ctx);await fs.mkdir(root);
    try{
      const project=new Project();project.name='delete fixture';await data.putProject(project);
      const item=new MediaItem();item.projectId=project.id;item.path=root+'/photo.jpg';item.source=item.path;item.edited=root+'/edited.jpg';item.albumUri=root+'/album-copy.jpg';
      await CoreRepairChecks.write(item.path,'original');await CoreRepairChecks.write(item.edited,'edited');await CoreRepairChecks.write(item.albumUri,'album');await data.putMedia(item);
      const annotation=new Annotation();annotation.mediaId=item.id;annotation.annotatedContentUri=item.edited;await data.putAnnotation(annotation);
      const deletion=new MediaDeletionCoordinator(root,data);let rejected=false;
      try{await deletion.deleteMedia([item.id]);}catch(e){rejected=true;}
      CoreRepairChecks.assert(rejected&&!!await data.mediaById(item.id),'failed commit lost the media row');
      CoreRepairChecks.assert(await fs.readText(item.path)==='original'&&await fs.readText(item.edited)==='edited','failed commit did not restore both files');
      CoreRepairChecks.assert((await data.annotations(item.id)).length===1,'failed commit lost annotation');

      // Simulate interruption after staging but before the database commit.
      const folder=root+'/.deleting/delete-recovery';await fs.mkdir(folder);
      const journal:RecoveryJournal={mediaIds:[item.id],projectIds:[],files:[{original:item.path,staged:folder+'/0.media'},{original:item.edited,staged:folder+'/1.media'}]};
      await CoreRepairChecks.write(folder+'/journal.json',JSON.stringify(journal));await fs.rename(item.path,journal.files[0].staged);await fs.rename(item.edited,journal.files[1].staged);
      await new MediaDeletionCoordinator(root,data).recover();
      CoreRepairChecks.assert(await fs.readText(item.path)==='original'&&await fs.readText(item.edited)==='edited','interrupted deletion did not restore the retained row');

      // Simulate interruption after commit: recovery must finish deletion.
      await fs.mkdir(folder);await CoreRepairChecks.write(folder+'/journal.json',JSON.stringify(journal));await fs.rename(item.path,journal.files[0].staged);await fs.rename(item.edited,journal.files[1].staged);
      data.rejectDeletion=false;await data.commitMediaDeletion([item],[]);
      await new MediaDeletionCoordinator(root,data).recover();
      CoreRepairChecks.assert(!fs.accessSync(item.path)&&!fs.accessSync(item.edited)&&!fs.accessSync(folder)&&!await data.mediaById(item.id),'committed deletion was restored or left staged files');
      CoreRepairChecks.assert(await fs.readText(item.albumUri)==='album','deletion removed the independent album copy');

      const second=new MediaItem();second.projectId=project.id;second.path=root+'/second.jpg';second.source=second.path;await CoreRepairChecks.write(second.path,'second');await data.putMedia(second);
      await deletion.deleteProjects([project.id]);
      CoreRepairChecks.assert(!await data.mediaById(second.id)&&!(await data.projects()).some(value=>value.id===project.id)&&!fs.accessSync(second.path),'project deletion left its row or media file');
    }finally{await data.close();await fs.rmdir(root);}
  }
}
