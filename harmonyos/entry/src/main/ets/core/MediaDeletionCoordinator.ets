import { fileIo as fs } from '@kit.CoreFileKit';
import { util } from '@kit.ArkTS';
import { MediaItem } from './Models';
import { Store } from './Store';

class DeletionFile {
  original:string='';
  staged:string='';
}
class DeletionJournal {
  mediaIds:number[]=[];
  projectIds:number[]=[];
  files:DeletionFile[]=[];
}

/** Renames retain the only copy until the database commits; this is not a media backup. */
export class MediaDeletionCoordinator {
  private directory:string;
  private queue:Promise<void>=Promise.resolve();
  private sequence:number=0;
  private root:string;
  private data:Store;
  constructor(root:string,data:Store){this.root=root;this.data=data;this.directory=root+'/.deleting';}

  private enqueue(action:()=>Promise<void>):Promise<void>{const next=this.queue.catch(()=>{}).then(action);this.queue=next;return next;}
  async recover():Promise<void>{await this.enqueue(()=>this.recoverNow());}
  async deleteMedia(ids:number[]):Promise<void>{await this.enqueue(()=>this.deleteNow(ids.slice(),[]));}
  async deleteProjects(ids:number[]):Promise<void>{await this.enqueue(()=>this.deleteNow([],ids.slice()));}

  private async ensureDirectory():Promise<void>{if(!fs.accessSync(this.directory))await fs.mkdir(this.directory);}
  private async writeJournal(folder:string,journal:DeletionJournal):Promise<void>{
    const temporary=folder+'/journal.tmp',destination=folder+'/journal.json';
    const bytes=new util.TextEncoder().encodeInto(JSON.stringify(journal));
    const file=await fs.open(temporary,fs.OpenMode.CREATE|fs.OpenMode.WRITE_ONLY|fs.OpenMode.TRUNC);
    try{let offset=0;while(offset<bytes.byteLength){const count=await fs.write(file.fd,bytes.buffer.slice(offset));if(count<=0)throw new Error('无法记录删除进度');offset+=count;}await fs.fsync(file.fd);}finally{await fs.close(file);}
    await fs.rename(temporary,destination);
  }
  private async deleteNow(ids:number[],projectIds:number[]):Promise<void>{
    await this.recoverNow();
    // Query only the rows the action names instead of loading the whole library.
    const items=await this.data.mediaByIds(ids);const known=new Set<number>();for(const item of items)known.add(item.id);
    for(const projectId of projectIds)for(const item of await this.data.mediaByProject(projectId))if(!known.has(item.id)){known.add(item.id);items.push(item);}
    if(!items.length&&!projectIds.length)return;
    await this.ensureDirectory();
    const folder=this.directory+'/delete-'+Date.now()+'-'+(++this.sequence);
    await fs.mkdir(folder);
    const journal=new DeletionJournal();journal.mediaIds=items.map(item=>item.id);journal.projectIds=projectIds;
    const paths:string[]=[];
    for(const item of items){
      paths.push(item.path,item.source,item.edited);
      for(const annotation of await this.data.annotations(item.id))paths.push(annotation.annotatedContentUri);
    }
    const staged=new Set<string>();
    for(const path of paths){
      if(!path||staged.has(path))continue;
      // Only app-private media belong to these deletion actions. Album copies do not.
      if(!path.startsWith(this.root+'/')||path.startsWith(this.directory+'/'))throw new Error('资料路径不属于当前相册');
      staged.add(path);const entry=new DeletionFile();entry.original=path;entry.staged=folder+'/'+journal.files.length+'.media';journal.files.push(entry);
    }
    // The complete mapping is durable before the first rename. Recovery can
    // distinguish a staged file from an original even if the process stops mid-loop.
    await this.writeJournal(folder,journal);
    try{
      for(const entry of journal.files)if(await this.exists(entry.original))await fs.rename(entry.original,entry.staged);
      await this.data.commitMediaDeletion(items,projectIds);
    }catch(e){
      try{await this.reconcile(folder,journal);}catch(restoreError){console.warn('SITECAM_DELETE_RESTORE '+String(restoreError));throw new Error('删除未完成，请重试');}
      throw e as Error;
    }
    // The rows are now gone. A cleanup failure keeps this journal for the next
    // recovery instead of restoring files whose records were already committed away.
    try{await this.discard(folder,journal);}catch(e){console.warn('SITECAM_DELETE_CLEANUP '+String(e));}
  }
  private async exists(path:string):Promise<boolean>{try{await fs.stat(path);return true;}catch(e){return false;}}
  private async restore(folder:string,journal:DeletionJournal):Promise<void>{
    for(const entry of journal.files){
      if(!await this.exists(entry.staged))continue;
      if(await this.exists(entry.original))throw new Error('资料恢复路径已被占用');
      await fs.rename(entry.staged,entry.original);
    }
    await this.removeJournal(folder);
  }
  private async discard(folder:string,journal:DeletionJournal):Promise<void>{
    for(const entry of journal.files)if(await this.exists(entry.staged))await fs.unlink(entry.staged);
    await this.removeJournal(folder);
  }
  private async removeJournal(folder:string):Promise<void>{
    if(await this.exists(folder+'/journal.tmp'))await fs.unlink(folder+'/journal.tmp');
    if(await this.exists(folder+'/journal.json'))await fs.unlink(folder+'/journal.json');
    await fs.rmdir(folder);
  }
  private async reconcile(folder:string,journal:DeletionJournal):Promise<void>{
    let hasRows=false;
    for(const id of journal.mediaIds)if(await this.data.mediaById(id)){hasRows=true;break;}
    if(!journal.mediaIds.length&&journal.projectIds.length){const wanted=new Set<number>(journal.projectIds);hasRows=(await this.data.projects()).some(project=>wanted.has(project.id));}
    if(hasRows)await this.restore(folder,journal);
    else await this.discard(folder,journal);
  }
  private async recoverNow():Promise<void>{
    if(!fs.accessSync(this.directory))return;
    const folders=await fs.listFile(this.directory);
    for(const name of folders){
      if(!name.startsWith('delete-'))continue;
      const folder=this.directory+'/'+name,path=folder+'/journal.json';
      // One damaged journal must not stop the remaining folders from recovering.
      try{
        if(!await this.exists(path)){await this.removeJournal(folder);continue;}
        const journal=JSON.parse(await fs.readText(path)) as DeletionJournal;
        await this.reconcile(folder,journal);
      }catch(e){console.warn('SITECAM_DELETE_RECOVER '+folder+' '+String(e));}
    }
  }
}
