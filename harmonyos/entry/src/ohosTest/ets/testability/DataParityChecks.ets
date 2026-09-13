import { Store } from '../../../main/ets/core/Store';
import { relationalStore } from '@kit.ArkData';
import { fileIo as fs } from '@kit.CoreFileKit';
import { common } from '@kit.AbilityKit';
import { image } from '@kit.ImageKit';
import { Project, MediaItem, Issue, Annotation, WatermarkTemplate, WatermarkField, Snapshot, Fix, Field, timeText } from '../../../main/ets/core/Models';
import { ExportService } from '../../../main/ets/core/ExportService';
import { MediaService } from '../../../main/ets/core/MediaService';
interface LegacyFix {latitude:number;longitude:number;altitude:number|null;accuracy:number;timestamp:number;}
interface LegacySnapshot {locationStatus:string;address:string;fix:LegacyFix;}
export class DataParityChecks {
  static async shutterSoundFixture(ctx:common.Context):Promise<void>{const name='shutter_sound_'+Date.now();const first=new Store(name);await first.open(ctx);if(first.settings.shutterSoundEnabled!==true)throw new Error('shutter sound default is not enabled');first.settings.shutterSoundEnabled=false;await first.saveSettings();await first.close();const reopened=new Store(name);await reopened.open(ctx);if(reopened.settings.shutterSoundEnabled!==false)throw new Error('shutter sound disabled state was not retained');await reopened.close();}
  static async templateReopenFixture(ctx:common.Context):Promise<void>{const name='template_reopen_'+Date.now();const first=new Store(name);await first.open(ctx);first.settings.watermark.style='INFO_BOARD';first.settings.watermark.fontScale=1.7;first.settings.watermark.opacity=0.6;first.settings.watermark.position='TOP_RIGHT';first.settings.watermark.fields=[new Field('project','工程','现场A'),new Field('address','地点','北段'),new Field('gps','坐标','30.5,120.25')];first.settings.watermark.fields[0].enabled=true;first.settings.watermark.fields[1].enabled=false;first.settings.watermark.fields[2].enabled=true;await first.saveSettings();const t=new WatermarkTemplate();t.name='持久化样式';t.config=JSON.stringify({style:'INFO_BOARD',fontScale:1.7,opacity:0.6,position:'TOP_RIGHT'});await first.putTemplate(t);const field=new WatermarkField();field.templateId=t.id;field.fieldKey='address';field.label='工程地点';field.displayOrder=3;field.isEnabled=true;await first.putTemplateField(field);await first.close();const reopened=new Store(name);await reopened.open(ctx);const settings=reopened.settings.watermark;const saved=(await reopened.templates()).find(v=>v.id===t.id);const fields=await reopened.templateFields(t.id);if(settings.style!=='INFO_BOARD'||settings.fontScale!==1.7||settings.opacity!==0.6||settings.position!=='TOP_RIGHT')throw new Error('template reopen settings mismatch');if(settings.fields.length!==4||settings.fields[1].key!=='address'||settings.fields[1].enabled||settings.fields[2].key!=='elevation'||settings.fields[2].enabled||settings.fields[3].value!=='30.5,120.25')throw new Error('settings watermark fields mismatch');if(!saved||!saved.config.includes('INFO_BOARD')||!saved.config.includes('1.7')||!saved.config.includes('0.6')||!saved.config.includes('TOP_RIGHT'))throw new Error('template reopen config mismatch');if(fields.length!==2||fields[0].fieldKey!=='address'||fields[0].label!=='工程地点'||!fields[0].isEnabled||fields[1].fieldKey!=='elevation'||fields[1].isEnabled)throw new Error('template reopen fields mismatch');await reopened.close();}
  static dms(text:string):number{const values=text.split(',');if(values.length<3)throw new Error('GPS EXIF DMS missing');return Number(values[0].trim())+Number(values[1].trim())/60+Number(values[2].trim())/3600;}
  static async exportExifFixture(ctx:common.UIAbilityContext):Promise<void>{const files=new MediaService(ctx);const project=new Project();project.id=302;project.name='导出EXIF';const snap=new Snapshot();snap.project='导出EXIF';snap.projectId=project.id;snap.timestamp=Date.now();snap.locationStatus='FRESH';const fix=new Fix();fix.latitude=30.5;fix.longitude=120.25;fix.altitude=12;fix.timestamp=snap.timestamp;snap.fix=fix;const expectedDate=timeText(snap.timestamp).replace(/-/g,':');const pixels=new Uint8Array(320*240*4);for(let i=0;i<pixels.length;i+=4){pixels[i]=80;pixels[i+1]=120;pixels[i+2]=160;pixels[i+3]=255;}const pm=await image.createPixelMap(pixels.buffer,{editable:true,pixelFormat:image.PixelMapFormat.RGBA_8888,size:{width:320,height:240}});const pack=image.createImagePacker();const bytes=await pack.packing(pm,{format:'image/jpeg',quality:95});await pack.release();await pm.release();const media=await files.capture(bytes,snap,3,0);const stage=ctx.cacheDir+'/exif_stage_'+Date.now();await fs.mkdir(stage);const exporter=new ExportService(ctx,files);await exporter.stage([project],[media],stage,0,()=>{});const path=stage+'/导出EXIF_p302/Photos/'+media.id+'_'+media.name;const source=image.createImageSource(path);try{const date=await source.getImageProperty(image.PropertyKey.DATE_TIME_ORIGINAL);const lat=await source.getImageProperty(image.PropertyKey.GPS_LATITUDE);const lon=await source.getImageProperty(image.PropertyKey.GPS_LONGITUDE);const latRef=await source.getImageProperty(image.PropertyKey.GPS_LATITUDE_REF);const lonRef=await source.getImageProperty(image.PropertyKey.GPS_LONGITUDE_REF);if(String(date)!==expectedDate)throw new Error('compressed export date EXIF mismatch: '+String(date));if(Math.abs(DataParityChecks.dms(String(lat))-30.5)>0.001||String(latRef)!=='N')throw new Error('compressed export latitude EXIF mismatch: '+String(lat)+' '+String(latRef));if(Math.abs(DataParityChecks.dms(String(lon))-120.25)>0.001||String(lonRef)!=='E')throw new Error('compressed export longitude EXIF mismatch: '+String(lon)+' '+String(lonRef));}finally{await source.release();}}
  static async exportDeliveryFixture(ctx:common.UIAbilityContext):Promise<void>{
    const root=ctx.cacheDir+'/delivery_fixture_'+Date.now();const source=root+'/工程_p1';const photos=source+'/Photos';const target=ctx.cacheDir+'/delivery_target_'+Date.now();await fs.mkdir(root);await fs.mkdir(source);await fs.mkdir(photos);const csv='序号,文件名\r\n1,a.jpg\r\n2,b.jpg\r\n';const csvFile=await fs.open(source+'/photo_index.csv',fs.OpenMode.CREATE|fs.OpenMode.READ_WRITE|fs.OpenMode.TRUNC);try{await fs.write(csvFile.fd,csv);}finally{await fs.close(csvFile);}for(const name of ['1_a.jpg','2_b.jpg']){const f=await fs.open(photos+'/'+name,fs.OpenMode.CREATE|fs.OpenMode.READ_WRITE|fs.OpenMode.TRUNC);try{await fs.write(f.fd,'fixture');}finally{await fs.close(f);}}const exporter=new ExportService(ctx,new MediaService(ctx));const failed=await exporter.deliverTreeForTest(root,target,['工程_p1/Photos/1_a.jpg','工程_p1/Photos/2_b.jpg']);if(failed!==2)throw new Error('delivery fixture failure count mismatch');const original=fs.readTextSync(target+'/工程_p1/photo_index.csv');if(!original.includes('1,a.jpg')||!original.includes('2,b.jpg'))throw new Error('delivery fixture lost original CSV');const report=fs.readTextSync(target+'/工程_p1/delivery_failures.json');if(!report.includes('1_a.jpg')||!report.includes('2_b.jpg'))throw new Error('delivery fixture report incomplete');const failureCsv=fs.readTextSync(target+'/工程_p1/delivery_failures.csv');if(!failureCsv.includes('1_a.jpg')||!failureCsv.includes('2_b.jpg'))throw new Error('delivery fixture CSV incomplete');
  }
  static async upgradeFixture(ctx:common.Context):Promise<void>{
    const namespace='legacy_v2_'+Date.now();
    const legacy=await relationalStore.getRdbStore(ctx,{name:namespace+'.db',securityLevel:relationalStore.SecurityLevel.S1});
    await legacy.executeSql('CREATE TABLE projects (id INTEGER PRIMARY KEY AUTOINCREMENT,name TEXT NOT NULL,route TEXT,category TEXT,address TEXT,notes TEXT,archived INTEGER,locked INTEGER,created INTEGER,updated INTEGER)');
    await legacy.executeSql('CREATE TABLE media (id INTEGER PRIMARY KEY AUTOINCREMENT,projectId INTEGER NOT NULL,path TEXT,source TEXT,edited TEXT,albumUri TEXT,name TEXT,type TEXT,timestamp INTEGER,width INTEGER,height INTEGER,duration INTEGER,rotation INTEGER,status TEXT,snapshot TEXT,issueTitle TEXT,issueNotes TEXT,severity TEXT,issueStatus TEXT)');
    const legacyFix:LegacyFix={latitude:0,longitude:0,altitude:null,accuracy:0,timestamp:123};const legacySnapshot:LegacySnapshot={locationStatus:'STALE',address:'旧工程地址',fix:legacyFix};const snapshot=JSON.stringify(legacySnapshot);
    const projectValue:relationalStore.ValuesBucket={name:'旧工程',route:'旧线路',category:'道路',address:'旧工程地址',notes:'旧备注',archived:0,locked:0,created:1,updated:1};
    // Construct a previously committed v2 database explicitly.  The
    // pre-close count below keeps fixture-write failures separate from any
    // close/reopen or migration failure.
    legacy.beginTransaction();
    try {
      const projectId=await legacy.insert('projects',projectValue);
      if(projectId<=0)throw new Error(`legacy fixture project insert failed: ${projectId}`);
      const mediaValue:relationalStore.ValuesBucket={projectId:projectId,path:'/legacy.jpg',source:'/legacy.jpg',name:'旧照片',type:'PHOTO',timestamp:123,width:1,height:1,status:'READY',snapshot,issueTitle:'旧问题',issueNotes:'旧整改',severity:'HIGH',issueStatus:'PENDING'};
      const mediaId=await legacy.insert('media',mediaValue);
      if(mediaId<=0)throw new Error(`legacy fixture media insert failed: ${mediaId}`);
      legacy.commit();
    } catch(e) {
      try { legacy.rollBack(); } catch(rollbackError) {}
      throw e as Error;
    }
    // Mark the committed fixture as the schema version that the migration
    // path is meant to upgrade.  This does not change its table shape.
    legacy.version=2;
    const beforeClose=await legacy.querySql('SELECT COUNT(*) AS count FROM media');
    let legacyMediaCount=0;
    try { if(beforeClose.goToNextRow())legacyMediaCount=beforeClose.getLong(beforeClose.getColumnIndex('count')); }
    finally { beforeClose.close(); }
    console.info(`SITECAM_LEGACY_FIXTURE namespace=${namespace} media=${legacyMediaCount}`);
    if(legacyMediaCount!==1)throw new Error(`legacy fixture media count before close: expected 1, got ${legacyMediaCount}`);
    await legacy.close();
    const reopenedRaw=await relationalStore.getRdbStore(ctx,{name:namespace+'.db',securityLevel:relationalStore.SecurityLevel.S1});
    const afterClose=await reopenedRaw.querySql('SELECT COUNT(*) AS count FROM media');
    let reopenedMediaCount=0;
    try { if(afterClose.goToNextRow())reopenedMediaCount=afterClose.getLong(afterClose.getColumnIndex('count')); }
    finally { afterClose.close(); await reopenedRaw.close(); }
    console.info(`SITECAM_LEGACY_REOPEN namespace=${namespace} media=${reopenedMediaCount}`);
    if(reopenedMediaCount!==1)throw new Error(`legacy v2 media count after reopen: expected 1, got ${reopenedMediaCount}`);
    const upgraded=new Store(namespace);await upgraded.open(ctx);try{
      const media=await upgraded.media();const issues=await upgraded.issues();
      if(media.length!==1)throw new Error(`legacy v2 media count: expected 1, got ${media.length}`);
      if(media[0].latitude!==0)throw new Error(`legacy v2 latitude: expected 0, got ${media[0].latitude}`);
      if(media[0].locationStatus!=='STALE')throw new Error(`legacy v2 status: expected STALE, got ${media[0].locationStatus}`);
      if(media[0].addressText!=='旧工程地址')throw new Error(`legacy v2 fixture address mismatch: ${media[0].addressText}`);
      if(issues.length!==1)throw new Error(`legacy v2 issue count: expected 1, got ${issues.length}`);
    }finally{await upgraded.close();}
  }
  static async check(store:Store):Promise<void>{
    const p=new Project();p.name='parity';p.notes='';p.coverMediaId=7;p.watermarkTemplateId=1;await store.putProject(p);
    const loaded=(await store.projects('UPDATED',false,'ALL')).find(v=>v.id===p.id);if(!loaded||loaded.notes!=='')throw new Error('project notes/cover roundtrip failed');
    const t=new WatermarkTemplate();t.name='style-parity';t.config=JSON.stringify({style:'INFO_BOARD',fontScale:1.7,opacity:0.6,position:'TOP_RIGHT'});await store.putTemplate(t);const rt=(await store.templates()).find(v=>v.id===t.id);if(!rt||!rt.config.includes('INFO_BOARD')||!rt.config.includes('1.7')||!rt.config.includes('0.6'))throw new Error('template config roundtrip failed');
    const m=new MediaItem();m.projectId=p.id;m.name='gps.jpg';m.path='/tmp/gps.jpg';m.latitude=0;m.longitude=0;m.altitude=null;m.locationAccuracy=0;m.locationTimestamp=Date.now();await store.putMedia(m);
    const got=(await store.media()).find(v=>v.id===m.id);if(!got||got.latitude!==0||got.longitude!==0||got.altitude!==null)throw new Error('GPS roundtrip failed');
    const i=new Issue();i.projectId=p.id;i.mediaId=m.id;i.title='问题';await store.putIssue(i);i.title='更新';await store.putIssue(i);if((await store.issues()).filter(v=>v.mediaId===m.id).length!==1)throw new Error('issue upsert failed');
    const a=new Annotation();a.mediaId=m.id;a.annotatedContentUri='/tmp/a.jpg';await store.putAnnotation(a);a.annotatedContentUri='/tmp/b.jpg';await store.putAnnotation(a);if((await store.annotations(m.id)).length!==1)throw new Error('annotation upsert failed');
    await store.moveMedia([m.id],p.id);if((await store.issues()).find(v=>v.mediaId===m.id)?.projectId!==p.id)throw new Error('issue move failed');await store.deleteMedia(m.id);if((await store.annotations(m.id)).length!==0)throw new Error('annotation cleanup failed');await store.deleteProject(p.id);
  }
}
