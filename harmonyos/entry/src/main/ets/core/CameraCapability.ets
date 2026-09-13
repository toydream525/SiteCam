export type LensRole = 'ULTRA_WIDE' | 'MAIN' | 'TELEPHOTO' | 'UNKNOWN';

/** Public camera facts used by the camera UI and by diagnostics. */
export class CameraLensCapability {
  cameraId: string = '';
  cameraPosition: number = 0;
  cameraType: number = 0;
  connectionType: number = 0;
  role: LensRole = 'UNKNOWN';
  logical: boolean = false;
  constituentRoles: LensRole[] = [];
  constituentEquivalentFocalLengths: number[] = [];
  equivalentFocalLengths: number[] = [];
  focalLength: number = 0;
  /** Output profiles are a candidate only; sessionVerified is set after bind/start. */
  photoOutputCandidate: boolean = false;
  videoOutputCandidate: boolean = false;
  photoSessionVerified: boolean = false;
  videoSessionVerified: boolean = false;
  active: boolean = false;
  /** Ratio against the independent rear main camera's public equivalent focal length. */
  referenceZoomFactor: number = 1;
  referenceZoomMapped: boolean = false;
  appAccessible: boolean = true;
  hardwareTelephoto: boolean = false;
}

/** CameraType values are stable from API 10; keep this helper SDK-neutral. */
export function lensRoleForCameraType(cameraType: number): LensRole {
  if (cameraType === 2) return 'ULTRA_WIDE';
  if (cameraType === 3) return 'TELEPHOTO';
  if (cameraType === 1) return 'MAIN';
  return 'UNKNOWN';
}

/** Equivalent focal length is a 35 mm reference and can be compared across sensors. */
export function lensRoleForEquivalentFocalLength(focalLength: number): LensRole {
  if (!Number.isFinite(focalLength) || focalLength <= 0) return 'UNKNOWN';
  if (focalLength <= 20) return 'ULTRA_WIDE';
  if (focalLength >= 50) return 'TELEPHOTO';
  return 'MAIN';
}

export function lensRoleFromPublicFacts(cameraType: number, equivalentFocalLengths: number[]): LensRole {
  // More than one equivalent focal length describes a public aggregate
  // entry, not one physical member.  Keep the role unknown until the camera
  // API exposes an independently selectable device.
  if (equivalentFocalLengths.length > 1) return 'UNKNOWN';
  const typed = lensRoleForCameraType(cameraType);
  if (typed !== 'UNKNOWN') return typed;
  for (const value of equivalentFocalLengths) {
    const role = lensRoleForEquivalentFocalLength(value);
    if (role !== 'UNKNOWN') return role;
  }
  // The sensor's physical millimetre value is intentionally not accepted here:
  // it cannot establish a cross-camera role without an equivalent focal length.
  return 'UNKNOWN';
}

function cleanZoom(value: number): number {
  return Math.round(value * 100) / 100;
}

/** Return a reference-camera ratio only when both public equivalent focal lengths exist. */
export function referenceZoomFactor(mainEquivalentFocalLength: number, lensEquivalentFocalLength: number): number {
  if(!Number.isFinite(mainEquivalentFocalLength)||!Number.isFinite(lensEquivalentFocalLength)||mainEquivalentFocalLength<=0||lensEquivalentFocalLength<=0)return 0;
  return cleanZoom(lensEquivalentFocalLength/mainEquivalentFocalLength);
}

export function sessionZoomFromReference(referenceZoom: number, factor: number, minimum: number, maximum: number): number {
  const safeFactor=Number.isFinite(factor)&&factor>0?factor:1;
  const requested=Number.isFinite(referenceZoom)?referenceZoom/safeFactor:minimum;
  return Math.max(minimum,Math.min(maximum,requested));
}

export function referenceZoomFromSession(sessionZoom: number, factor: number): number {
  const safeFactor=Number.isFinite(factor)&&factor>0?factor:1;
  return cleanZoom((Number.isFinite(sessionZoom)?sessionZoom:1)*safeFactor);
}

/** Reject SDK error-code returns and values outside the active session range. */
export function isValidZoomRatio(value: number, minimum: number, maximum: number): boolean {
  if(!Number.isFinite(value)||!Number.isFinite(minimum)||!Number.isFinite(maximum)||maximum<minimum)return false;
  return value>=Math.max(0.01,minimum-0.05)&&value<=maximum+0.05;
}

/**
 * Read an equivalent focal length only when a zoom point is actually close
 * to the current ratio.  Sparse point metadata must not turn a distant
 * point into a claimed current physical lens.
 */
export function nearbyZoomPointFocalLength(ratio: number, pointRatios: number[], focalLengths: number[]): number {
  if(!Number.isFinite(ratio)||ratio<=0||!pointRatios.length)return 0;
  let nearest=-1;
  let distance=Number.POSITIVE_INFINITY;
  for(let i=0;i<pointRatios.length;i++){
    const point=pointRatios[i];
    if(!Number.isFinite(point)||point<=0)continue;
    const next=Math.abs(point-ratio);
    if(next<distance){distance=next;nearest=i;}
  }
  const tolerance=Math.max(0.02,Math.abs(ratio)*0.015);
  if(nearest<0||distance>tolerance||nearest>=focalLengths.length)return 0;
  const focal=focalLengths[nearest];
  return Number.isFinite(focal)&&focal>0?focal:0;
}

/**
 * Build the small set of common controls from the actual session range.
 * API 26 zoom points describe views, but do not prove an independently
 * accessible physical telephoto member, so they never become extra buttons.
 * The lower bound is kept as a real entry (for example 0.54).
 */
export function buildZoomPresets(minimum: number, maximum: number, points: number[] = []): number[] {
  if (!Number.isFinite(minimum) || !Number.isFinite(maximum) || maximum < minimum) return [];
  const min = cleanZoom(Math.max(0.01, minimum));
  const max = cleanZoom(Math.max(min, maximum));
  const values: number[] = [];
  const add = (value: number, force: boolean = false): void => {
    if (!Number.isFinite(value)) return;
    const candidate = cleanZoom(Math.max(min, Math.min(max, value)));
    // `force` keeps a real lower bound even when it is close to a common
    // control, but it must never create a duplicate key for ForEach.
    if (values.some((existing: number) => Math.abs(existing - candidate) < 0.0001)) return;
    if (force || values.every((existing: number) => Math.abs(existing - candidate) >= 0.08)) values.push(candidate);
  };
  add(min, true);
  if (1 >= min && 1 <= max) add(1);
  if (2 >= min && 2 <= max) add(2);
  // A range below 1× still needs a reachable upper control.  Larger ranges
  // keep the UI focused on 1×/2×; pinch zoom remains continuous above 2×.
  if (max < 1 && max - min >= 0.08) add(max);
  values.sort((a: number, b: number) => a - b);
  return values;
}

/**
 * Build display presets in the main-camera reference scale.  A physical
 * telephoto entry is added only when the caller has independently confirmed
 * that the current session is that physical member; zoom point metadata alone
 * is intentionally insufficient.
 */
export function buildReferenceZoomPresets(minimum: number, maximum: number, factor: number=1, points: number[] = [], physicalTelephoto: boolean = false): number[] {
  const safeFactor=Number.isFinite(factor)&&factor>0?factor:1;
  if(!Number.isFinite(minimum)||!Number.isFinite(maximum)||maximum<minimum)return [];
  const referenceMinimum=cleanZoom(Math.max(0.01,minimum*safeFactor));
  const referenceMaximum=cleanZoom(Math.max(referenceMinimum,maximum*safeFactor));
  const values:number[]=[];
  const add=(value:number, force: boolean = false): void=>{
    if(!Number.isFinite(value))return;
    const candidate=cleanZoom(Math.max(referenceMinimum,Math.min(referenceMaximum,value)));
    // A forced physical-lens entry may be close to the minimum, but exact
    // duplicates are still invalid UI keys and do not represent two controls.
    if(values.some((existing:number)=>Math.abs(existing-candidate)<0.0001))return;
    if(force||values.every((existing:number)=>Math.abs(existing-candidate)>=0.08))values.push(candidate);
  };
  add(referenceMinimum, true);
  if(1>=referenceMinimum&&1<=referenceMaximum)add(1);
  if(2>=referenceMinimum&&2<=referenceMaximum)add(2);
  if(physicalTelephoto&&safeFactor>1)add(safeFactor, true);
  if(referenceMaximum<1&&referenceMaximum-referenceMinimum>=0.08)add(referenceMaximum);
  values.sort((a:number,b:number)=>a-b);
  return values;
}

export function zoomPresetLabel(value: number): string {
  const clean = cleanZoom(value);
  return Number.isInteger(clean) ? `${clean}×` : `${clean.toFixed(2).replace(/0+$/, '').replace(/\.$/, '')}×`;
}

/** Exactly one closest preset is selected, even when the range has close points. */
export function isNearestZoomPreset(value: number, preset: number, presets: number[]): boolean {
  if (!presets.length) return false;
  let nearest = presets[0];
  for (const candidate of presets) {
    if (Math.abs(candidate - value) < Math.abs(nearest - value)) nearest = candidate;
  }
  return Math.abs(nearest - preset) < 0.0001;
}
