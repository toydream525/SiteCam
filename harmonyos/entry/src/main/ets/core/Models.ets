export class Project {
  id: number = 0;
  name: string = '';
  route: string = '';
  category: string = '建筑';
  address: string = '';
  notes: string = '';
  archived: boolean = false;
  locked: boolean = false;
  created: number = Date.now();
  updated: number = Date.now();
  count: number = 0;
  photoCount: number=0;videoCount: number=0;issueCount: number=0;
  first: number = 0;
  last: number = 0;
  categoryName: string = '建筑';
  description: string = '';
  coverMediaId: number = 0;
  watermarkTemplateId: number = 1;
  isCaptureLocked: boolean = false;
  current: boolean = false;
}
export class MediaItem {
  id: number = 0;
  projectId: number = 0;
  path: string = '';
  source: string = '';
  edited: string = '';
  albumUri: string = '';
  name: string = '';
  type: string = 'PHOTO';
  timestamp: number = Date.now();
  width: number = 0;
  height: number = 0;
  duration: number = 0;
  rotation: number = 0;
  status: string = 'READY';
  snapshot: string = '';
  issueTitle: string = '';
  issueNotes: string = '';
  severity: string = 'NORMAL';
  issueStatus: string = 'PENDING';
  contentUri: string = '';
  filePath: string = '';
  latitude: number | null = null;
  longitude: number | null = null;
  altitude: number | null = null;
  locationAccuracy: number | null = null;
  locationTimestamp: number | null = null;
  locationStatus: string = 'UNAVAILABLE';
  addressText: string = '';
  isIssue: boolean = false;
  isFavorite: boolean = false;
  createdAt: number = Date.now();
}
export class Field {
  key: string = '';
  label: string = '';
  value: string = '';
  enabled: boolean = true;
  constructor(key: string = '', label: string = '', value: string = '') {
    this.key = key; this.label = label; this.value = value;
  }
}
function defaultWatermarkFields(): Field[] {
  const elevation=new Field('elevation','海拔');
  elevation.enabled=false;
  return [new Field('project','工程名称'),new Field('category','工程类型'),new Field('time','拍摄时间'),new Field('address','现场位置'),elevation,new Field('gps','GPS定位'),new Field('user','拍摄人员')];
}
export class Watermark {
  style: string = 'CLASSIC';
  fontScale: number = 1;
  opacity: number = 0.85;
  marginDp: number = 16;
  position: string = 'BOTTOM_LEFT';
  user: string = '';
  fields: Field[] = defaultWatermarkFields();
}
export class WatermarkTemplate {
  id: number=0;name: string='默认模板';config: string='';updated: number=Date.now();
  styleType: string = 'CLASSIC'; fontSizeScale: number = 1; opacity: number = .85;
  marginDp: number = 16; position: string = 'BOTTOM_LEFT'; isDefault: boolean = false;
}
export class WatermarkField {
  id: number = 0; templateId: number = 0; fieldKey: string = ''; label: string = '';
  defaultValue: string = ''; displayOrder: number = 0; isEnabled: boolean = true;
}
export class ProjectCategory {
  id: number = 0; name: string = ''; isSystemDefault: boolean = false; displayOrder: number = 0;
}
export class Issue {
  id: number = 0; projectId: number = 0; mediaId: number = 0; title: string = '';
  description: string = ''; severity: string = 'NORMAL'; status: string = 'PENDING';
  createdAt: number = Date.now(); updatedAt: number = Date.now();
}
export class Annotation {
  id: number = 0; mediaId: number = 0; annotatedContentUri: string = '';
  vectorDataJson: string = ''; createdAt: number = Date.now();
}
export class Settings {
  icon: string='D';
  templateId: number=0;
  currentProject: number = 0;
  photoQuality: number = 1;
  editQuality: number = 1;
  orientation: string = 'AUTO';
  albumCopy: boolean = false;
  guideDone: boolean = false;
  permissionHandled: boolean = false;
  systemPermissionsRequested: boolean = false;
  watermark: Watermark = new Watermark();
  // Android v0.2.8's single capture/edit quality is photoQuality. Keep editQuality for old UI/data.
  namingPattern: string = '{project}_{date}_{time}';
  flashMode: string = 'AUTO';
  /** Play a short shutter sound when the camera accepts a photo request. */
  shutterSoundEnabled: boolean = true;
  quickIssueMode: boolean = false;
  jpegQuality: number = 95;
  exportTreeUri: string = '';
  projectSelectionCleared: boolean = false;
  /** Most recently selected capture projects, newest first. */
  recentProjectIds: number[] = [];
  browserSort: string = 'LAST_CAPTURE';
  browserAscending: boolean = false;
}
export class Fix {
  latitude: number = 0;
  longitude: number = 0;
  altitude: number | null = null;
  accuracy: number = 0;
  timestamp: number = 0;
  address: string = '';
}
export class Snapshot {
  presentationVersion: number = 2; // Missing in parsed v0.2.8 snapshots: retain legacy rendering.
  captureRatio: number = 0; // 0 preserves legacy/native framing; new photos freeze 3:4 or 4:3.
  timestamp: number = Date.now();
  project: string = '';
  route: string = '';
  category: string = '';
  address: string = '';
  locationStatus: string = 'UNAVAILABLE';
  fix: Fix | null = null;
  watermark: Watermark = new Watermark();
  projectId: number = 0;
  mediaId: number = 0;
  captureTimestamp: number = 0;
}
export class Style {
  id: string; name: string; accent: string; background: string;
  constructor(id: string,name: string,accent: string,background: string) {
    this.id=id; this.name=name; this.accent=accent; this.background=background;
  }
}
export const STYLES: Style[] = [
  new Style('CLASSIC','经典工程水印','#FFB300','#17232C'),
  new Style('MINIMAL','极简水印','#FFFFFF','#263238'),
  new Style('INFO_BOARD','工程信息板','#FFB300','#263238'),
  new Style('ENGINEERING_BLUE','蓝白工程记录','#1976D2','#F1F7FF'),
  new Style('CONSTRUCTION_TABLE','施工表格','#456A86','#EDF3F7'),
  new Style('INSPECTION','巡检记录','#26A69A','#173A36'),
  new Style('ACCEPTANCE','验收记录','#214A72','#F3F7FB'),
  new Style('TIME_LOCATION','大字时间地点','#FFCC66','#17232C')
];
export const QUALITY_NAMES: string[] = ['更小文件','标准省空间','较清晰','原尺寸高清'];
export const QUALITY_EDGES: number[] = [1280,1920,2560,0];
export const QUALITY_JPEG: number[] = [65,75,85,95];
export function pad(n: number): string { return n.toString().padStart(2,'0'); }
export function dateText(t: number): string { const d=new Date(t); return `${d.getFullYear()}-${pad(d.getMonth()+1)}-${pad(d.getDate())}`; }
export function timeText(t: number): string { const d=new Date(t); return `${dateText(t)} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`; }
export function safeName(s: string): string { return s.replace(/[\\/:*?"<>|\r\n]/g,'_').trim().slice(0,100)||'工程'; }
export function captureSnapshot(p: Project, settings: Settings, fix: Fix|null): Snapshot {
  const s=new Snapshot(); s.project=p.name;s.projectId=p.id;s.route=p.route;s.category=p.category || p.categoryName;
  s.captureTimestamp=s.timestamp;
  s.watermark=JSON.parse(JSON.stringify(settings.watermark)) as Watermark;
  if(fix) { s.fix=JSON.parse(JSON.stringify(fix)) as Fix; const age=s.timestamp-fix.timestamp; s.locationStatus=age>=-30000&&age<=300000?'FRESH':'STALE'; }
  s.address=p.address || (fix?.address || '定位不可用'); return s;
}

export function resetWatermarkFields(source: Watermark): Watermark {
  const out=JSON.parse(JSON.stringify(source)) as Watermark;
  const defaults=new Watermark().fields;
  const system: Field[]=[];
  const current=Array.isArray(out.fields)?out.fields:[];
  for(const d of defaults){const f=current.find((v:Field)=>v.key===d.key);if(f){f.label=d.label;system.push(f);}else{const missing=JSON.parse(JSON.stringify(d)) as Field;missing.enabled=d.key==='elevation'?false:d.enabled;system.push(missing);}}
  out.fields=system.concat(current.filter((f:Field)=>!defaults.some((d:Field)=>d.key===f.key)));
  return out;
}

/**
 * Load persisted settings without applying the editor's explicit "restore
 * defaults" action. User labels, values, enabled flags, custom fields and
 * their order are part of the saved document and must survive reopening.
 * Deliberately reordered, customized, and partial field lists are left intact;
 * only the newly introduced opt-in field is inserted when it is absent.
 */
export function migrateWatermarkFields(source: Watermark): Watermark {
  const out=JSON.parse(JSON.stringify(source)) as Watermark;
  const defaults=new Watermark().fields;
  if(!Array.isArray(out.fields)) {out.fields=JSON.parse(JSON.stringify(defaults)) as Field[];for(const f of out.fields)if(f.key==='elevation')f.enabled=false;return out;}
  const fields=out.fields as Field[];
  // Older serialized fields did not always include the enabled flag. Restore
  // the historical enabled default while leaving every present user value,
  // label, and relative position untouched.
  for(const f of fields){if((f.enabled as boolean)===undefined)f.enabled=f.key==='elevation'?false:true;}
  if(fields.some((f:Field)=>f.key==='elevation'))return out;
  const elevation=new Field('elevation','海拔');
  elevation.enabled=false;
  const address=fields.find((f:Field)=>f.key==='address');
  if(address)fields.splice(fields.indexOf(address)+1,0,elevation);else fields.push(elevation);
  return out;
}
