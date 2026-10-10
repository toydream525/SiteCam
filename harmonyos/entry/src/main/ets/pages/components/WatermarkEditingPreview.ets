import { LengthMetricsUnit } from '@kit.ArkUI';
import { Snapshot } from '../../core/Models';
import { paintWatermark, layoutWatermark } from '../../core/WatermarkRenderer';

/** Fits the existing stamp for editing; capture snapshots and the output renderer are unchanged. */
@Component
export struct WatermarkEditingPreview {
  @Prop @Watch('draw') snapshot:Snapshot=new Snapshot();
  private ctx:CanvasRenderingContext2D=new CanvasRenderingContext2D(new RenderingContextSettings(true),LengthMetricsUnit.PX);
  private canvasWidth:number=0;
  private canvasHeight:number=0;
  private draw():void {
    if(this.canvasWidth<=0||this.canvasHeight<=0)return;
    this.ctx.save();
    const layout=layoutWatermark(this.ctx,1080,1440,this.snapshot);
    const fitSnapshot=JSON.parse(JSON.stringify(this.snapshot)) as Snapshot;
    fitSnapshot.watermark.fontScale=1;
    const fitLayout=layoutWatermark(this.ctx,1080,1440,fitSnapshot);
    this.ctx.restore();
    this.ctx.clearRect(0,0,this.canvasWidth,this.canvasHeight);
    if(layout.w<=0||layout.h<=0)return;
    const baseScale=Math.min(this.canvasWidth*.76/fitLayout.w,this.canvasHeight*.76/fitLayout.h);
    const scale=Math.min(baseScale,this.canvasWidth*.94/layout.w,this.canvasHeight*.92/layout.h);
    const inset=Math.min(this.canvasWidth,this.canvasHeight)*.03;
    const x=this.snapshot.watermark.position.endsWith('RIGHT')?this.canvasWidth-inset-layout.w*scale:inset;
    const y=this.snapshot.watermark.position.startsWith('TOP')?inset:this.canvasHeight-inset-layout.h*scale;
    this.ctx.save();this.ctx.translate(x-layout.x*scale,y-layout.y*scale);this.ctx.scale(scale,scale);
    paintWatermark(this.ctx,1080,1440,this.snapshot);
    this.ctx.restore();
  }
  build(){Canvas(this.ctx).width('100%').height('100%').onReady(()=>this.draw()).onAreaChange((oldArea,newArea)=>{this.canvasWidth=vp2px(Number(newArea.width));this.canvasHeight=vp2px(Number(newArea.height));this.draw();}).hitTestBehavior(HitTestMode.None);}
}
