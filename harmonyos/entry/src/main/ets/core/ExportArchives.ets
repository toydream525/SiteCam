import { common } from '@kit.AbilityKit';
import { fileIo as fs, fileUri, fileShare, picker } from '@kit.CoreFileKit';
import { preferences } from '@kit.ArkData';
import { deviceInfo } from '@kit.BasicServicesKit';

export class ExportArchive { id:string='';name:string='';size:number=0;date:number=0; }
class ArchiveLocation {uri:string='';directory:boolean=false;}
export class ArchiveListing {files:ExportArchive[]=[];unreadable:number=0;}
export class ArchiveDeletion {deleted:string[]=[];failed:string[]=[];}
export class ExportArchives {
  private context:common.UIAbilityContext;
  constructor(context:common.UIAbilityContext){this.context=context;}
  private async prefs():Promise<preferences.Preferences>{return preferences.getPreferences(this.context,{name:'sitecam_export_archives'});}
  private async locations():Promise<ArchiveLocation[]>{const p=await this.prefs();const value=await p.get('locations','[]');return JSON.parse(value as string) as ArchiveLocation[];}
  private mode():number{return fileShare.OperationMode.READ_MODE|fileShare.OperationMode.WRITE_MODE|(deviceInfo.sdkApiVersion>=20?fileShare.OperationMode.DELETE_MODE:0);}
  private path(uri:string):string{return uri.startsWith('file://')?new fileUri.FileUri(uri).path:uri;}
  private async remember(uri:string,directory:boolean):Promise<void>{
    try{await fileShare.persistPermission([{uri:uri,operationMode:this.mode()}]);}catch(e){console.info('SITECAM_ARCHIVE_PERSIST '+String(e));}
    const p=await this.prefs(),values=await this.locations();
    if(!values.some((v:ArchiveLocation)=>v.uri===uri&&v.directory===directory)){const entry=new ArchiveLocation();entry.uri=uri;entry.directory=directory;values.push(entry);await p.put('locations',JSON.stringify(values));await p.flush();}
  }
  async rememberFile(uri:string):Promise<void>{await this.remember(uri,false);}
  async chooseDirectory():Promise<boolean>{
    if(!canIUse('SystemCapability.FileManagement.UserFileService.FolderSelection')){
      const files=await new picker.DocumentViewPicker(this.context).select({fileSuffixFilters:['ZIP 压缩包|.zip'],maxSelectNumber:500});
      for(const file of files)await this.remember(file,false);
      return files.length>0;
    }
    const selected=await new picker.DocumentViewPicker(this.context).select({selectMode:picker.DocumentSelectMode.FOLDER,maxSelectNumber:1});
    if(!selected.length)return false;
    await this.remember(selected[0],true);return true;
  }
  async list():Promise<ArchiveListing>{
    const result=new ArchiveListing(),files:Map<string,ExportArchive>=new Map();
    const add=async(path:string):Promise<void>=>{if(!path.toLowerCase().endsWith('.zip'))return;const stat=await fs.stat(path);if(!stat.isFile())return;const entry=new ExportArchive();entry.id=path;entry.name=path.substring(path.lastIndexOf('/')+1);entry.size=stat.size;entry.date=stat.mtime*1000;files.set(path,entry);};
    const roots=[this.context.cacheDir,this.context.filesDir+'/SiteCam/Exports'];
    for(const root of roots){try{if(!fs.accessSync(root))continue;for(const name of await fs.listFile(root))await add(root+'/'+name);}catch(e){result.unreadable++;}}
    for(const location of await this.locations()){
      try{
        try{await fileShare.activatePermission([{uri:location.uri,operationMode:this.mode()}]);}catch(e){console.info('SITECAM_ARCHIVE_ACTIVATE '+String(e));}
        const path=this.path(location.uri);if(!fs.accessSync(path))continue;
        if(location.directory){for(const name of await fs.listFile(path))await add(path+'/'+name);}else await add(path);
      }catch(e){result.unreadable++;}
    }
    result.files=Array.from(files.values()).sort((a:ExportArchive,b:ExportArchive)=>b.date-a.date||a.name.localeCompare(b.name));return result;
  }
  async delete(ids:string[]):Promise<ArchiveDeletion>{
    const result=new ArchiveDeletion(),files=(await this.list()).files;
    for(const id of ids){const file=files.find((value:ExportArchive)=>value.id===id);if(!file){result.failed.push(id);continue;}
      try{if(!file.name.toLowerCase().endsWith('.zip')||!(await fs.stat(file.id)).isFile())throw new Error('文件已变化');await fs.unlink(file.id);result.deleted.push(id);}catch(e){result.failed.push(file.name);}
    }
    return result;
  }
}
