import { ImageProcessor, EditRecipe } from './ImageProcessor';
import { Snapshot, MediaItem } from './Models';
@Concurrent
export async function captureWorker(root: string,bytes: ArrayBuffer,snapshot: string,quality: number,rotation: number): Promise<string> {
  const processor=new ImageProcessor(root);return JSON.stringify(await processor.captureLocal(bytes,JSON.parse(snapshot) as Snapshot,quality,rotation));
}
@Concurrent
export async function editWorker(root: string,mediaJson: string,recipeJson: string,quality: number): Promise<string> {
  return await new ImageProcessor(root).editLocal(JSON.parse(mediaJson) as MediaItem,JSON.parse(recipeJson) as EditRecipe,quality);
}
