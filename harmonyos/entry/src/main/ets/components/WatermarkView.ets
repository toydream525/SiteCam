import { LengthMetricsUnit } from '@kit.ArkUI';
import { Snapshot } from '../core/Models';
import { paintWatermark } from '../core/WatermarkRenderer';
@Component
export struct WatermarkView {
  @Prop @Watch('draw') snapshot: Snapshot = new Snapshot();
  @Prop ratio: number = 0.75;
  @Prop reportBounds: boolean = false;
  private lastBounds: string = '';
  private ctx: CanvasRenderingContext2D=new CanvasRenderingContext2D(new RenderingContextSettings(true),LengthMetricsUnit.PX);
  private w: number=0;
  private h: number=0;
  draw(): void {
    if(this.w<=0||this.h<=0)return;
    this.ctx.clearRect(0,0,this.w,this.h);
    const scale=this.w/1080;
    this.ctx.save();
    this.ctx.scale(scale,scale);
    const layout=paintWatermark(this.ctx,1080,this.h/scale,this.snapshot);
    this.ctx.restore();
    if(this.reportBounds){
      const pxPerVp=Math.max(0.001,vp2px(1));
      const bounds=`${layout.x*scale/pxPerVp},${layout.y*scale/pxPerVp},${layout.w*scale/pxPerVp},${layout.h*scale/pxPerVp}`;
      if(bounds!==this.lastBounds){this.lastBounds=bounds;AppStorage.setOrCreate('watermarkBounds',bounds);}
    }
  }
  build(){Canvas(this.ctx).width('100%').height('100%').onReady(()=>this.draw()).onAreaChange((old,value)=>{this.w=vp2px(Number(value.width));this.h=vp2px(Number(value.height));this.draw();}).hitTestBehavior(HitTestMode.None)}
}
