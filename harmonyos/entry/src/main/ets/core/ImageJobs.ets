import { ImageProcessor, EditRecipe } from './ImageProcessor';
import { CaptureJobResult, Snapshot, MediaItem } from './Models';

@Concurrent
export async function captureWorker(root: string,bytes: ArrayBuffer,snapshot: string,quality: number,rotation: number,originalPath:string): Promise<string> {
  const processor=new ImageProcessor(root);
  const result=new CaptureJobResult();
  result.item=await processor.captureLocal(bytes,JSON.parse(snapshot) as Snapshot,quality,rotation);
  if(originalPath){
    try{await processor.write(originalPath,bytes);result.originalSaved=true;}
    catch(e){
      console.warn('SITECAM_ORIGINAL_WRITE '+String(e));
    }
  }
  return JSON.stringify(result);
}
@Concurrent
export async function editWorker(root: string,mediaJson: string,recipeJson: string,quality: number): Promise<string> {
  return await new ImageProcessor(root).editLocal(JSON.parse(mediaJson) as MediaItem,JSON.parse(recipeJson) as EditRecipe,quality);
}

@Concurrent
export async function importWorker(root:string,path:string,snapshot:string,quality:number,preview:boolean):Promise<string>{return JSON.stringify(await new ImageProcessor(root).importWatermarked(path,JSON.parse(snapshot) as Snapshot,quality,preview));}

@Concurrent
export async function issueShareWorker(root:string,path:string,target:string,title:string,status:string,notes:string):Promise<void>{
  await new ImageProcessor(root).renderIssueShare(path,target,title,status,notes);
}
