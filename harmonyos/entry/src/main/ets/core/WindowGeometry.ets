export class WindowRect {
  x:number=0; y:number=0; width:number=0; height:number=0;
  constructor(x:number=0,y:number=0,width:number=0,height:number=0){this.x=x;this.y=y;this.width=width;this.height=height;}
}
export function intersectRect(a:WindowRect,b:WindowRect):WindowRect {
  const x=Math.max(a.x,b.x),y=Math.max(a.y,b.y);
  return new WindowRect(x,y,Math.max(0,Math.min(a.x+a.width,b.x+b.width)-x),Math.max(0,Math.min(a.y+a.height,b.y+b.height)-y));
}
/** All inputs and outputs are in the current window's local vp coordinates. */
export function availablePanes(bounds:WindowRect,creases:WindowRect[]):WindowRect[] {
  let panes:WindowRect[]=[bounds];
  for(const crease of creases){const next:WindowRect[]=[];
    for(const pane of panes){const c=intersectRect(pane,crease);
      if(c.width<=0||c.height<=0){next.push(pane);continue;}
      // Split around the entire occlusion. This also handles partial hinges and multiple folds.
      const candidates:WindowRect[]=[new WindowRect(pane.x,pane.y,c.x-pane.x,pane.height),new WindowRect(c.x+c.width,pane.y,pane.x+pane.width-c.x-c.width,pane.height),new WindowRect(c.x,pane.y,c.width,c.y-pane.y),new WindowRect(c.x,c.y+c.height,c.width,pane.y+pane.height-c.y-c.height)];
      for(const value of candidates)if(value.width>0&&value.height>0)next.push(value);
    }panes=next;
  }
  return panes.sort((a:WindowRect,b:WindowRect)=>b.width*b.height-a.width*a.height);
}
export function splitContentFits(width:number,height:number,fontScale:number):boolean {
  const scale=Math.max(1,fontScale);
  return width>=Math.max(840,320*scale+360*scale+48)&&height>=280;
}
export function mediaGridColumns(width:number,fontScale:number=1):number {
  return Math.max(1,Math.min(6,Math.floor((width+4)/(104*Math.max(1,fontScale)+4))));
}
