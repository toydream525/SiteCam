export function screenFamily(name:string,foldable:boolean,status:number,mode:number,width:number,height:number,previous:string='REGULAR'):string{return Math.min(width,height)<240?'SMALL':width>=840?'WIDE':'REGULAR';}
export function largeScreenScheme(family:string,foldable:boolean,status:number,deviceType:string='phone'):boolean{return family==='WIDE';}
export function smallCoverScreen(family:string,foldable:boolean,mode:number):boolean{return family==='SMALL';}
export function compactPhonePortrait(family:string,foldable:boolean,deviceType:string,width:number,height:number):boolean{return height>width;}
/** Extra grip space belongs only to an ordinary elongated phone window. */
export function ordinaryPhoneGrip(foldable:boolean,deviceType:string,width:number,height:number):boolean {
  const shorter=Math.min(width,height),longer=Math.max(width,height);
  return !foldable&&deviceType==='phone'&&shorter>=280&&shorter<600&&longer>=560&&longer/shorter>=1.65;
}
