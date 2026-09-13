import { display, window } from '@kit.ArkUI';
import { usesInnerScreen } from './CameraLayout';
import { screenFamily,largeScreenScheme,smallCoverScreen,compactPhonePortrait } from './DeviceLayoutProfile';
import { deviceInfo } from '@kit.BasicServicesKit';

export class WindowEnvironment {
  width: number = 390;
  density: number = 1;
  height: number = 720;
  top: number = 0;
  bottom: number = 0;
  left: number = 0;
  right: number = 0;
  rotation: number = 0;
  halfFolded: boolean = false;
  innerScreen: boolean = false;
  clamshell: boolean = false;
  largeScreenScheme: boolean = false;
  coverScreen: boolean = false;
  family: string = 'REGULAR';
  foldable: boolean = false;
  deviceType: string = 'phone';
  compactPhonePortrait: boolean = false;
  creaseTop: number = -1;
  creaseHeight: number = 0;
}

/** Window coordinates and posture are independent event streams (API 12 baseline). */
export class WindowEnvironmentController {
  private win: window.Window | undefined;
  private env: WindowEnvironment = new WindowEnvironment();
  private density: number = 1;
  private family: string = 'REGULAR';
  private widthPixels: number=390;
  private heightPixels: number=720;
  private offsetY: number = 0;
  private creaseY: number = -1;
  private creaseH: number = 0;
  private areas: Map<window.AvoidAreaType, window.AvoidArea> = new Map();
  private sizeChanged = (size: window.Size): void => {
    this.widthPixels=size.width;this.heightPixels=size.height;
    this.publish();
  };
  private rectChanged = (event: window.RectChangeOptions): void => {
    this.offsetY = event.rect.top;
    this.publish();
  };
  private areaChanged = (event: window.AvoidAreaOptions): void => {
    this.areas.set(event.type, event.area);
    this.publish();
  };
  private displayChanged = (): void => { this.readDisplay(); this.publish(); };
  private foldChanged = (status: display.FoldStatus): void => {
    this.env.halfFolded = status === display.FoldStatus.FOLD_STATUS_HALF_FOLDED;
    this.env.innerScreen = usesInnerScreen(display.isFoldable(),status);
    this.readFoldMode(); this.readCrease(); this.publish();
  };
  private modeChanged = (): void => { this.readDisplay(); this.publish(); };

  async attach(win: window.Window): Promise<void> {
    this.win = win;
    this.readDisplay();
    const rect = win.getWindowProperties().windowRect;
    this.offsetY = rect.top;
    this.sizeChanged({ width: rect.width, height: rect.height });
    await win.setWindowLayoutFullScreen(true);
    for (const type of [window.AvoidAreaType.TYPE_SYSTEM, window.AvoidAreaType.TYPE_CUTOUT,
      window.AvoidAreaType.TYPE_NAVIGATION_INDICATOR, window.AvoidAreaType.TYPE_KEYBOARD]) {
      this.areas.set(type, win.getWindowAvoidArea(type));
    }
    win.on('windowSizeChange', this.sizeChanged);
    win.on('windowRectChange', this.rectChanged);
    win.on('avoidAreaChange', this.areaChanged);
    display.on('change', this.displayChanged);
    display.on('foldStatusChange', this.foldChanged);
    display.on('foldDisplayModeChange', this.modeChanged);
    this.publish();
  }
  detach(): void {
    this.win?.off('windowSizeChange', this.sizeChanged);
    this.win?.off('windowRectChange', this.rectChanged);
    this.win?.off('avoidAreaChange', this.areaChanged);
    display.off('change', this.displayChanged);
    display.off('foldStatusChange', this.foldChanged);
    display.off('foldDisplayModeChange', this.modeChanged);
    this.win = undefined;
  }
  private readDisplay(): void {
    try {
      const current = display.getDefaultDisplaySync();
      this.density = current.densityPixels || 1;
      this.env.rotation = current.rotation * 90;
      const status=display.getFoldStatus();
      this.env.halfFolded = display.isFoldable() && status === display.FoldStatus.FOLD_STATUS_HALF_FOLDED;
      this.env.innerScreen = usesInnerScreen(display.isFoldable(),status);
      this.readFoldMode(); this.readCrease();
    } catch (e) { console.warn('SITECAM_DISPLAY ' + (e as Error).message); }
  }
  private readFoldMode():void {
    const mode=display.getFoldDisplayMode(),status=display.getFoldStatus(),foldable=display.isFoldable();
    const panel=display.getDefaultDisplaySync(),density=panel.densityPixels||1;
    this.family=screenFamily(deviceInfo.marketName+' '+deviceInfo.productModel,foldable,status,mode,panel.width/density,panel.height/density,this.family);
    this.env.family=this.family;
    this.env.foldable=foldable;
    this.env.deviceType=deviceInfo.deviceType;
    this.env.coverScreen=smallCoverScreen(this.family,foldable,mode);
    this.env.clamshell=this.family==='SMALL';
    this.env.largeScreenScheme=largeScreenScheme(this.family,foldable,status,deviceInfo.deviceType);
  }
  private readCrease(): void {
    this.creaseY = -1; this.creaseH = 0;
    try {
      if (this.env.halfFolded) {
        const region=display.getCurrentFoldCreaseRegion();
        if(!region||!region.creaseRects){return;}
        const rects=region.creaseRects;
        const horizontal = rects.find((r: display.Rect) => r.width > r.height);
        if (horizontal) { this.creaseY = horizontal.top; this.creaseH = horizontal.height; }
      }
    } catch (e) { console.warn('SITECAM_CREASE ' + (e as Error).message); }
  }
  private publish(): void {
    this.env.density=this.density;
    this.env.width=this.widthPixels/this.density;this.env.height=this.heightPixels/this.density;
    this.env.compactPhonePortrait=compactPhonePortrait(this.env.family,this.env.foldable,this.env.deviceType,this.env.width,this.env.height);
    this.env.top = 0; this.env.bottom = 0; this.env.left = 0; this.env.right = 0;
    this.areas.forEach((area: window.AvoidArea) => {
      this.env.top = Math.max(this.env.top, area.topRect.height / this.density);
      this.env.bottom = Math.max(this.env.bottom, area.bottomRect.height / this.density);
      this.env.left = Math.max(this.env.left, area.leftRect.width / this.density);
      this.env.right = Math.max(this.env.right, area.rightRect.width / this.density);
    });
    this.env.creaseTop = this.creaseY < 0 ? -1 : (this.creaseY - this.offsetY) / this.density - this.env.top;
    this.env.creaseHeight = this.creaseH / this.density;
    AppStorage.setOrCreate('windowEnvironment', JSON.parse(JSON.stringify(this.env)) as WindowEnvironment);
  }
}
