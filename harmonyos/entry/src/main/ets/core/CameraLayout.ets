import { WindowRect, availablePanes } from './WindowGeometry';
export class CameraLayout {
  previewX: number = 0;
  previewY: number = 56;
  previewWidth: number = 1;
  previewHeight: number = 1;
  toolbarX:number=0;
  toolbarY:number=0;
  toolbarWidth: number = 1;
  toolbarHeight: number = 56;
  controlsX: number = 0;
  controlsY: number = 0;
  controlsWidth: number = 1;
  controlsHeight: number = 1;
  side: boolean = false;
  hover: boolean = false;
  immersivePhoneLandscape: boolean = false;
}

export function floatingZoomBounds(width:number,height:number,railWidth:number,railHeight:number,protectedRects:WindowRect[]):WindowRect|null {
  const margin=12,gap=8,left=width-margin-railWidth;
  if(left<margin||height<margin*2+48)return null;
  let spaces:WindowRect[]=[new WindowRect(0,margin,1,height-margin*2)];
  for(const rect of protectedRects){
    if(rect.x+rect.width+gap<=left||rect.x-gap>=width-margin)continue;
    const next:WindowRect[]=[];
    for(const space of spaces){
      const above=Math.min(space.y+space.height,rect.y-gap)-space.y;
      const below=Math.max(space.y,rect.y+rect.height+gap);
      if(above>=48)next.push(new WindowRect(0,space.y,1,above));
      if(space.y+space.height-below>=48)next.push(new WindowRect(0,below,1,space.y+space.height-below));
    }
    spaces=next;
  }
  let selected:WindowRect|null=null,distance=Number.MAX_VALUE;
  const target=(height-railHeight)/2;
  for(const space of spaces){
    const top=Math.max(space.y,Math.min(Math.max(space.y,space.y+space.height-railHeight),target));
    if(Math.abs(top-target)<distance){distance=Math.abs(top-target);selected=space;}
  }
  if(selected===null)return null;
  const rail=Math.min(railHeight,selected.height);
  const top=Math.max(selected.y,Math.min(selected.y+selected.height-rail,(height-rail)/2));
  return new WindowRect(left,top,railWidth,rail);
}

// Match Android CameraScreen: orientation follows the available window, never
// a width breakpoint or sensor angle. Square windows use the right dock too.
export function cameraLayout(width: number, height: number, ratio: number,
  halfFolded: boolean = false, creaseTop: number = -1, creaseHeight: number = 0, coverScreen: boolean = false, density: number = 1, creases:WindowRect[]=[], fontScale:number=1, phoneGrip:boolean=false, portraitControlsContentHeight:number=0, referenceLandscape:boolean=false): CameraLayout {
  const out = new CameraLayout();
  const w = Math.max(1, width), h = Math.max(1, height);
  const panes=availablePanes(new WindowRect(0,0,w,h),creases);
  if(panes.length>1){
    const first=panes[0],second=panes[1];
    if(Math.min(first.width,second.width)>=120&&Math.min(first.height,second.height)>=96){
      const vertical=first.x+first.width<=second.x||second.x+second.width<=first.x;
      const preview=vertical?(first.x<second.x?first:second):(first.y<second.y?first:second);
      const controls=preview===first?second:first;
      out.side=vertical;out.hover=!vertical;
      out.controlsWidth=vertical?Math.min(256,controls.width):controls.width;out.controlsHeight=controls.height;out.controlsX=controls.x+(controls.width-out.controlsWidth)/2;out.controlsY=controls.y;
      out.toolbarHeight=preview.width<400*Math.max(1,fontScale)?104:56;
      out.toolbarX=preview.x;out.toolbarY=preview.y;out.toolbarWidth=preview.width;
      out.previewWidth=Math.max(1,Math.min(preview.width,Math.max(1,preview.height-out.toolbarHeight)*ratio));out.previewHeight=out.previewWidth/ratio;
      out.previewX=preview.x+(preview.width-out.previewWidth)/2;out.previewY=preview.y+out.toolbarHeight+(preview.height-out.toolbarHeight-out.previewHeight)/2;
      return out;
    }
    if(panes.length){const pane=panes[0];const inner=cameraLayout(pane.width,pane.height,ratio,false,-1,0,coverScreen,density,[],fontScale);inner.previewX+=pane.x;inner.previewY+=pane.y;inner.controlsX+=pane.x;inner.controlsY+=pane.y;inner.toolbarX+=pane.x;inner.toolbarY+=pane.y;return inner;}
  }
  if(panes.length===1&&(panes[0].x>0||panes[0].y>0||panes[0].width<w||panes[0].height<h)){
    const pane=panes[0],inner=cameraLayout(pane.width,pane.height,ratio,false,-1,0,coverScreen,density,[],fontScale);inner.previewX+=pane.x;inner.previewY+=pane.y;inner.controlsX+=pane.x;inner.controlsY+=pane.y;inner.toolbarX+=pane.x;inner.toolbarY+=pane.y;return inner;
  }
  if(coverScreen){
    out.side=true;out.toolbarWidth=0;out.toolbarHeight=0;
    out.controlsWidth=Math.min(56,w);out.controlsHeight=h;out.controlsX=w-out.controlsWidth;out.controlsY=0;
    out.previewWidth=Math.max(1,Math.min(out.controlsX,h*ratio));out.previewHeight=out.previewWidth/ratio;
    out.previewX=(out.controlsX-out.previewWidth)/2;out.previewY=(h-out.previewHeight)/2;
    return out;
  }
  const center = creaseTop + creaseHeight / 2;
  const toolbar= w<368+120*(Math.max(1,fontScale)-1)?104:56;
  out.toolbarHeight=toolbar;
  out.hover = halfFolded && creaseTop >= 0 && center > 112 && center + 40 < h - 96;
  out.side = rightCameraDock(w,h,density);
  out.toolbarWidth=w;
  let availableW = w, availableH = Math.max(1,h - toolbar);
  if(out.hover&&out.side){
    const upper=Math.max(1,creaseTop-16),lower=Math.max(creaseTop+creaseHeight,center+40);
    out.toolbarWidth=w;out.toolbarHeight=56;
    out.controlsWidth=Math.min(160,w*.35);out.controlsX=w-out.controlsWidth;out.controlsY=lower;out.controlsHeight=h-lower;
    out.previewWidth=Math.max(1,Math.min(w,upper*ratio));out.previewHeight=out.previewWidth/ratio;
    out.previewX=(w-out.previewWidth)/2;out.previewY=(upper-out.previewHeight)/2;
    return out;
  }
  if (out.hover) {
    availableH = Math.max(1, Math.min(creaseTop, center - 16) - toolbar);
    out.controlsY = Math.max(creaseTop + creaseHeight, center + 40);
    out.controlsWidth = w; out.controlsHeight = h - out.controlsY;
  } else if (out.side) {
    if(referenceLandscape){
      // Both ordinary phones and tablets use the adopted immersive arrangement.
      // Reserve the additional grip budget only for ordinary phones.
      const grip=phoneGrip?48:0,tools=56;
      const dock=Math.min(w*.30,184+32*(Math.max(1,Math.min(2,fontScale))-1));
      const previewArea=Math.max(1,w-tools-dock-grip);
      out.previewWidth=Math.min(previewArea,h*ratio);out.previewHeight=out.previewWidth/ratio;
      out.previewX=tools+(previewArea-out.previewWidth)/2;out.previewY=(h-out.previewHeight)/2;
      out.toolbarX=out.previewX-tools;out.toolbarY=0;out.toolbarWidth=tools;out.toolbarHeight=h;
      out.controlsX=out.previewX+out.previewWidth;out.controlsY=0;out.controlsWidth=dock;out.controlsHeight=h;
      out.immersivePhoneLandscape=true;return out;
    }
    const gripRight=phoneGrip?Math.min(48,w*.06):0;
    const usableWidth=Math.max(1,w-gripRight);
    out.toolbarWidth=usableWidth;out.toolbarHeight=toolbar;out.toolbarX=0;out.toolbarY=0;
    availableH=Math.max(1,h-toolbar);
    const dock=Math.max(Math.min(200,usableWidth*.34),Math.min(256,usableWidth*.34,usableWidth-availableH*ratio));
    availableW=Math.max(1,usableWidth-dock);
    out.previewWidth=Math.max(1,Math.min(availableW,availableH*ratio));
    out.previewHeight=out.previewWidth/ratio;
    out.previewX=(availableW-out.previewWidth)/2;out.previewY=toolbar+(availableH-out.previewHeight)/2;
    out.controlsX=availableW;out.controlsY=toolbar;
    out.controlsWidth=dock;out.controlsHeight=availableH;
    return out;
  } else {
    out.controlsHeight = Math.min(184, Math.max(136, h * .28));
    if(phoneGrip){
      // The Scroll child measures zoom + modes + shutter + two gaps + grip padding.
      // Before its first measurement, reserve the mode row at the current font scale.
      const initialContentHeight=44+44*Math.max(1,fontScale)+82+8+72;
      const measuredContentHeight=Number.isFinite(portraitControlsContentHeight)&&portraitControlsContentHeight>0?portraitControlsContentHeight:initialContentHeight;
      out.controlsHeight=Math.min(Math.max(1,h-toolbar-1),Math.max(out.controlsHeight+64,measuredContentHeight));
    }
    out.controlsY = h - out.controlsHeight; out.controlsWidth = w;
    availableH = out.controlsY - toolbar;
  }
  out.previewWidth = Math.max(1, Math.min(availableW, Math.max(1, availableH) * ratio));
  out.previewHeight = out.previewWidth / ratio;
  out.previewX = (availableW - out.previewWidth) / 2;
  out.previewY = toolbar + (Math.max(1, availableH) - out.previewHeight) / 2;
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
