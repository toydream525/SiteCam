export class CameraLayout {
  previewX: number = 0;
  previewY: number = 56;
  previewWidth: number = 1;
  previewHeight: number = 1;
  toolbarWidth: number = 1;
  toolbarHeight: number = 56;
  controlsX: number = 0;
  controlsY: number = 0;
  controlsWidth: number = 1;
  controlsHeight: number = 1;
  side: boolean = false;
  hover: boolean = false;
}

// Match Android CameraScreen: orientation follows the available window, never
// a width breakpoint or sensor angle. Square windows use the right dock too.
export function cameraLayout(width: number, height: number, ratio: number,
  halfFolded: boolean = false, creaseTop: number = -1, creaseHeight: number = 0, coverScreen: boolean = false, density: number = 1): CameraLayout {
  const out = new CameraLayout();
  const w = Math.max(1, width), h = Math.max(1, height);
  if(coverScreen){
    out.side=true;out.toolbarWidth=0;out.toolbarHeight=0;
    out.controlsWidth=Math.min(56,w);out.controlsHeight=h;out.controlsX=w-out.controlsWidth;out.controlsY=0;
    out.previewWidth=Math.max(1,Math.min(out.controlsX,h*ratio));out.previewHeight=out.previewWidth/ratio;
    out.previewX=(out.controlsX-out.previewWidth)/2;out.previewY=(h-out.previewHeight)/2;
    return out;
  }
  const center = creaseTop + creaseHeight / 2;
  out.hover = halfFolded && creaseTop >= 0 && center > 112 && center + 40 < h - 96;
  out.side = rightCameraDock(w,h,density);
  out.toolbarWidth=w;
  let availableW = w, availableH = Math.max(1,h - 56);
  if(out.hover&&out.side){
    const upper=Math.max(1,creaseTop-16),lower=Math.max(creaseTop+creaseHeight,center+40);
    out.toolbarWidth=Math.min(102,w*.2);out.toolbarHeight=upper;
    out.controlsWidth=Math.min(160,w*.35);out.controlsX=w-out.controlsWidth;out.controlsY=lower;out.controlsHeight=h-lower;
    out.previewWidth=Math.max(1,Math.min(w-out.toolbarWidth,upper*ratio));out.previewHeight=out.previewWidth/ratio;
    out.previewX=out.toolbarWidth;out.previewY=(upper-out.previewHeight)/2;
    return out;
  }
  if (out.hover) {
    availableH = Math.max(1, Math.min(creaseTop, center - 16) - 56);
    out.controlsY = Math.max(creaseTop + creaseHeight, center + 40);
    out.controlsWidth = w; out.controlsHeight = h - out.controlsY;
  } else if (out.side) {
    out.toolbarWidth=Math.min(102,w*.2);out.toolbarHeight=h;
    const dock=Math.min(160,w*.35);
    availableW=Math.max(1,w-out.toolbarWidth-dock);availableH=h;
    out.previewWidth=Math.max(1,Math.min(availableW,h*ratio));
    out.previewHeight=out.previewWidth/ratio;
    out.previewX=out.toolbarWidth;out.previewY=(h-out.previewHeight)/2;
    out.controlsX=out.previewX+out.previewWidth;out.controlsY=0;
    out.controlsWidth=w-out.controlsX;out.controlsHeight=h;
    return out;
  } else {
    out.controlsHeight = Math.min(184, Math.max(136, h * .28));
    out.controlsY = h - out.controlsHeight; out.controlsWidth = w;
    availableH = out.controlsY - 56;
  }
  out.previewWidth = Math.max(1, Math.min(availableW, Math.max(1, availableH) * ratio));
  out.previewHeight = out.previewWidth / ratio;
  out.previewX = (availableW - out.previewWidth) / 2;
  out.previewY = 56 + (Math.max(1, availableH) - out.previewHeight) / 2;
  return out;
}

/** Keep the last valid direction when the device is upside down. */
export function captureDegrees(mode: string, displayDegrees: number, last: number): number {
  if (mode === 'PORTRAIT') return 0;
  if (mode === 'LEFT') return 90;
  if (mode === 'RIGHT') return 270;
  return displayDegrees === 180 ? last : displayDegrees;
}

/**
 * The capture lock follows the orientation that is already visible on screen.
 * It deliberately does not translate capture metadata degrees into a window
 * orientation, which would turn a naturally landscape tablet upside down.
 */
export function windowOrientationTarget(mode: string, lockAuto: boolean): string {
  if (mode === 'PORTRAIT') return 'PORTRAIT';
  if (mode === 'LEFT') return 'LANDSCAPE';
  if (mode === 'RIGHT') return 'LANDSCAPE_INVERTED';
  if (lockAuto) return 'LOCKED';
  return 'AUTO_ROTATION_RESTRICTED';
}

// Numeric status values keep API 12 compatibility; dual-axis values are emitted
// by API 15+ without accessing newer enum members on older systems.
export function usesInnerScreen(foldable: boolean,status: number): boolean {
  return foldable && [1,3,11,12,13,21,22,23].includes(status);
}

/** Treat a difference of at most one physical pixel as a square. */
export function rightCameraDock(width: number,height: number,density: number=1):boolean {
  return (height-width)*Math.max(1,density) <= 1+0.000001;
}
