import { common } from '@kit.AbilityKit';
import { fileIo as fs, fileUri, picker } from '@kit.CoreFileKit';
import { font } from '@kit.ArkUI';
import { util } from '@kit.ArkTS';
import { fontFamily } from './Models';

export class FontChoice {id:string='';name:string='默认字体';}
export class FontLibrary {
  private root:string;
  private context:common.UIAbilityContext;
  constructor(context:common.UIAbilityContext){this.context=context;this.root=context.filesDir+'/fonts';}
  async init():Promise<void>{if(!fs.accessSync(this.root))await fs.mkdir(this.root);for(const item of await this.list())if(item.id){try{this.register(item.id);}catch(e){console.warn('SITECAM_FONT_REGISTER '+String(e));}}}
  private register(id:string):void{font.registerFont({familyName:fontFamily(id),familySrc:fileUri.getUriFromPath(this.root+'/'+id)});}
  async list():Promise<FontChoice[]>{
    const result:FontChoice[]=[new FontChoice()];if(!fs.accessSync(this.root))return result;
    for(const name of await fs.listFile(this.root)){if(!name.toLowerCase().endsWith('.ttf')&&!name.toLowerCase().endsWith('.otf'))continue;const item=new FontChoice();item.id=name;item.name=name.substring(name.indexOf('__')+2).replace(/\.(ttf|otf)$/i,'');result.push(item);}
    return result;
  }
  async importFont():Promise<FontChoice|null>{
    const selected=await new picker.DocumentViewPicker(this.context).select({fileSuffixFilters:['字体|.ttf,.otf'],maxSelectNumber:1});
    if(!selected.length)return null;
    if(!fs.accessSync(this.root))await fs.mkdir(this.root);
    const original=new fileUri.FileUri(selected[0]).name;
    const extension=original.toLowerCase().endsWith('.otf')?'otf':original.toLowerCase().endsWith('.ttf')?'ttf':'';
    if(!extension)throw new Error('请选择 TTF 或 OTF 字体');
    const label=original.replace(/\.(ttf|otf)$/i,'').replace(/[\\/:*?"<>|\r\n]/g,'_').slice(0,50)||'字体';
    const id=util.generateRandomUUID()+'__'+label+'.'+extension,pending=this.root+'/'+id+'.pending',target=this.root+'/'+id;
    const source=await fs.open(selected[0],fs.OpenMode.READ_ONLY);
    try{
      const output=await fs.open(pending,fs.OpenMode.CREATE|fs.OpenMode.WRITE_ONLY|fs.OpenMode.TRUNC);
      try{await fs.copyFile(source.fd,output.fd);}finally{await fs.close(output);}
      const check=await fs.open(pending,fs.OpenMode.READ_ONLY),header=new ArrayBuffer(4);
      try{if(await fs.read(check.fd,header)!==4)throw new Error('字体文件无效');}finally{await fs.close(check);}
      const bytes=new Uint8Array(header),signature=Array.from(bytes).map((value:number)=>String.fromCharCode(value)).join('');
      if(!(bytes[0]===0&&bytes[1]===1&&bytes[2]===0&&bytes[3]===0)&&signature!=='OTTO'&&signature!=='true')throw new Error('字体文件无效');
      await fs.rename(pending,target);this.register(id);
      const result=new FontChoice();result.id=id;result.name=label;return result;
    }catch(e){if(fs.accessSync(pending))await fs.unlink(pending);if(fs.accessSync(target))await fs.unlink(target);throw e as Error;}
    finally{await fs.close(source);}
  }
}
