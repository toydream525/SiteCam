import { usesInnerScreen } from './CameraLayout';

// Use physical display dimensions, never a rotated app-window breakpoint, to
// distinguish wide straight phones from ordinary phones in landscape.
export function screenFamily(name: string,foldable: boolean,status: number,mode: number,
  width: number,height: number,previous: string='REGULAR'): string {
  const model=name.toLowerCase().replace(/[\s_-]/g,'');
  if(model.includes('pocket')||model.includes('novaflip'))return 'SMALL';
  if(model.includes('purax'))return 'WIDE';
  if(previous==='SMALL'||previous==='WIDE')return previous;
  const short=Math.min(width,height),long=Math.max(width,height);
  if(short<=0)return 'REGULAR';
  if(!foldable&&short>=420&&long/short<=1.85)return 'WIDE';
  if(foldable&&usesInnerScreen(true,status)&&mode===2){
    if(short>=420&&long/short<=1.85)return 'WIDE';
    if(long/short>=1.9)return 'SMALL';
  }
  if(foldable&&mode===3&&long<=220)return 'SMALL';
  return 'REGULAR';
}
export function largeScreenScheme(family: string,foldable: boolean,status: number,deviceType: string='phone'):boolean {
  return deviceType==='tablet'||family==='WIDE'||(family!=='SMALL'&&usesInnerScreen(foldable,status));
}
export function smallCoverScreen(family: string,foldable: boolean,mode: number):boolean {
  return family==='SMALL'&&foldable&&mode===3;
}

/**
 * The compact zoom rail is reserved for a regular, straight phone in
 * portrait.  Foldables and all large or wide layouts keep their existing
 * spacing and weight distribution, including the outer screen of a foldable.
 */
export function compactPhonePortrait(family: string,foldable: boolean,deviceType: string,
  width: number,height: number): boolean {
  return family==='REGULAR'&&!foldable&&deviceType==='phone'&&height>width;
}
