import { display, window } from '@kit.ArkUI';
import { usesInnerScreen } from './CameraLayout';
import { WindowRect, intersectRect } from './WindowGeometry';
export class WindowEnvironment {
  width:number=390; height:number=720; density:number=1;
  top:number=0; bottom:number=0; left:number=0; right:number=0;
  keyboardTop:number=0; keyboardBottom:number=0; keyboardLeft:number=0; keyboardRight:number=0;
  rotation:number=0; displayId:number=0; windowX:number=0; windowY:number=0;
  halfFolded:boolean=false; innerScreen:boolean=false; clamshell:boolean=false;
  largeScreenScheme:boolean=false; coverScreen:boolean=false; family:string='REGULAR';
  foldable:boolean=false; deviceType:string=''; compactPhonePortrait:boolean=false;
  creaseTop:number=-1; creaseHeight:number=0; creases:WindowRect[]=[];
}
/** API 12: display crease rectangles are converted once; avoid areas are already window-local. */
export class WindowEnvironmentController {
  private win:window.Window|undefined;
  private env:WindowEnvironment=new WindowEnvironment();
  private density:number=1; private widthPixels:number=390; private heightPixels:number=720;
  private offsetX:number=0; private offsetY:number=0;
  private rawCreases:WindowRect[]=[]; private creaseDisplayId:number=-1;
  private areas:Map<window.AvoidAreaType,window.AvoidArea>=new Map();
  private sizeChanged=(size:window.Size):void=>{this.widthPixels=size.width;this.heightPixels=size.height;this.readWindow();this.publish();};
  private rectChanged=(event:window.RectChangeOptions):void=>{this.offsetX=event.rect.left;this.offsetY=event.rect.top;this.widthPixels=event.rect.width;this.heightPixels=event.rect.height;this.readDisplay();this.publish();};
  private areaChanged=(event:window.AvoidAreaOptions):void=>{this.areas.set(event.type,event.area);this.publish();};
  private displayChanged=():void=>{this.readWindow();this.readDisplay();this.publish();};
  private foldChanged=():void=>{this.readDisplay();this.publish();};
  private modeChanged=():void=>{this.readWindow();this.readDisplay();this.publish();};
  async attach(win:window.Window):Promise<void>{
    this.win=win;this.readWindow();this.readDisplay();await win.setWindowLayoutFullScreen(true);
    for(const type of [window.AvoidAreaType.TYPE_SYSTEM,window.AvoidAreaType.TYPE_CUTOUT,window.AvoidAreaType.TYPE_NAVIGATION_INDICATOR,window.AvoidAreaType.TYPE_KEYBOARD])this.areas.set(type,win.getWindowAvoidArea(type));
    win.on('windowSizeChange',this.sizeChanged);win.on('windowRectChange',this.rectChanged);win.on('avoidAreaChange',this.areaChanged);
    display.on('change',this.displayChanged);display.on('foldStatusChange',this.foldChanged);display.on('foldDisplayModeChange',this.modeChanged);this.publish();
  }
  detach():void{this.win?.off('windowSizeChange',this.sizeChanged);this.win?.off('windowRectChange',this.rectChanged);this.win?.off('avoidAreaChange',this.areaChanged);display.off('change',this.displayChanged);display.off('foldStatusChange',this.foldChanged);display.off('foldDisplayModeChange',this.modeChanged);this.win=undefined;}
  private readWindow():void{if(!this.win)return;const props=this.win.getWindowProperties();this.offsetX=props.windowRect.left;this.offsetY=props.windowRect.top;this.widthPixels=props.windowRect.width;this.heightPixels=props.windowRect.height;if(props.displayId!==undefined)this.env.displayId=props.displayId;}
  private readDisplay():void{try{
    const current=display.getDisplayByIdSync(this.env.displayId);this.density=current.densityPixels||1;this.env.rotation=current.rotation*90;this.env.displayId=current.id;
    this.env.foldable=display.isFoldable();const status=display.getFoldStatus();this.env.halfFolded=this.env.foldable&&status===display.FoldStatus.FOLD_STATUS_HALF_FOLDED;this.env.innerScreen=usesInnerScreen(this.env.foldable,status);
    this.rawCreases=[];this.creaseDisplayId=-1;
    if(this.env.foldable){const region=display.getCurrentFoldCreaseRegion();if(region){this.creaseDisplayId=region.displayId;this.rawCreases=region.creaseRects.map((r:display.Rect)=>new WindowRect(r.left,r.top,Math.max(1,r.width),Math.max(1,r.height)));}}
  }catch(e){console.warn('SITECAM_DISPLAY '+(e as Error).message);}}
  private publish():void{
    const e=this.env,d=this.density;e.density=d;e.width=this.widthPixels/d;e.height=this.heightPixels/d;e.windowX=this.offsetX/d;e.windowY=this.offsetY/d;
    e.top=0;e.bottom=0;e.left=0;e.right=0;e.keyboardTop=0;e.keyboardBottom=0;e.keyboardLeft=0;e.keyboardRight=0;
    this.areas.forEach((area:window.AvoidArea,type:window.AvoidAreaType)=>{
      if(type===window.AvoidAreaType.TYPE_KEYBOARD){e.keyboardTop=area.topRect.height/d;e.keyboardBottom=area.bottomRect.height/d;e.keyboardLeft=area.leftRect.width/d;e.keyboardRight=area.rightRect.width/d;}
      else{e.top=Math.max(e.top,area.topRect.height/d);e.bottom=Math.max(e.bottom,area.bottomRect.height/d);e.left=Math.max(e.left,area.leftRect.width/d);e.right=Math.max(e.right,area.rightRect.width/d);}
    });
    e.creases=[];if(this.creaseDisplayId===e.displayId){const bounds=new WindowRect(0,0,e.width,e.height);for(const raw of this.rawCreases){const local=intersectRect(bounds,new WindowRect((raw.x-this.offsetX)/d,(raw.y-this.offsetY)/d,raw.width/d,raw.height/d));if(local.width>0&&local.height>0)e.creases.push(local);}}
    const w=Math.max(1,e.width-e.left-e.right),h=Math.max(1,e.height-e.top-e.bottom);
    e.coverScreen=Math.min(w,h)<240;e.compactPhonePortrait=h>w;e.largeScreenScheme=w>=840;e.family=w>=840?'WIDE':'REGULAR';e.clamshell=false;
    const horizontal=e.creases.find((r:WindowRect)=>r.width>=w*.8&&r.width>r.height);e.creaseTop=horizontal?horizontal.y-e.top:-1;e.creaseHeight=horizontal?horizontal.height:0;
    AppStorage.setOrCreate('windowEnvironment',JSON.parse(JSON.stringify(e)) as WindowEnvironment);
  }
}
