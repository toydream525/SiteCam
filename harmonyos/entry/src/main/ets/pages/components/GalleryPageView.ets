import { MediaItem, Project } from '../../core/Models';
import { mediaGridColumns } from '../../core/WindowGeometry';
import { SITE_ACCENT, PAGE_BG as SITE_BG, PAGE_CARD as SITE_CARD, PAGE_RAISED as SITE_CARD_RAISED, PAGE_MUTED as SITE_MUTED, PAGE_TEXT as SITE_TEXT } from './UiPrimitives';

const GALLERY_SELECTED:string='#FFB300';
const GALLERY_CARD:Resource=$r('app.color.card_bg');
const GALLERY_OUTLINE:Resource=$r('app.color.divider');

export class GalleryGroup { day: string = ''; items: MediaItem[] = []; }
function projectName(projects: Project[], id: number): string { const p=projects.find((v: Project)=>v.id===id); return p?p.name:'全部工程'; }
function issueText(item: MediaItem): string { if(!item.issueTitle)return '标记问题'; return item.issueStatus==='COMPLETED'?'问题 · 已完成':item.issueStatus==='IN_PROGRESS'?'问题 · 处理中':'问题 · 待处理'; }
function groupsOf(items: MediaItem[]): GalleryGroup[] { const days: string[]=[];for(const m of items){const d=new Date(m.timestamp),day=`${d.getFullYear()}-${(d.getMonth()+1).toString().padStart(2,'0')}-${d.getDate().toString().padStart(2,'0')}`;if(!days.includes(day))days.push(day);}days.sort((a:string,b:string)=>b.localeCompare(a));return days.map((day:string)=>{const g=new GalleryGroup();g.day=day;g.items=items.filter((m:MediaItem)=>{const d=new Date(m.timestamp);return `${d.getFullYear()}-${(d.getMonth()+1).toString().padStart(2,'0')}-${d.getDate().toString().padStart(2,'0')}`===day;});return g;}); }

@Component
export struct GalleryPageView {
  pathStack: NavPathStack=new NavPathStack();
  private listScroller: Scroller=new Scroller();
  @Builder emptyDetail(){Text('选择资料查看').fontColor(SITE_MUTED);}
  @BuilderParam detailContent: ()=>void=this.emptyDetail;
  @Builder destination(){NavDestination(){this.detailContent();}.hideTitleBar(true);}
  @Link filtersOpen:boolean;
  @Link selecting:boolean;
  @Prop fontScale:number=1;
  private listWidth():number{return this.parallelEnabled?Math.max(280,Math.min(420,this.windowWidth*.4)):this.windowWidth;}
  @Prop items: MediaItem[] = [];
  @Prop projects: Project[] = [];
  @Prop currentProjectName: string = '全部工程相册';
  @Prop categories: string[] = [];
  @Prop selectedFilter: string = 'ALL';
  @Prop selectedCategory: string = '';
  @Prop selectedStatus: string = '';
  @Prop selectedSeverity: string = '';
  @Prop dateRangeText: string = '';
  @Prop selectedIds: number[] = [];
  @Prop loading: boolean = false;
  @Prop progressText: string = '';
  @Prop @Watch('syncColumns') windowWidth: number = 390;
  @Prop @Watch('syncColumns') parallelEnabled: boolean = false;
  @Prop previewItem: MediaItem = new MediaItem();
  onBack: () => void = () => {};
  onSwitchProject: () => void = () => {};
  onFilter: (value: string) => void = (value: string) => {};
  onDateRange: () => void = () => {};
  onClearDate: () => void = () => {};
  onCategory: (value: string) => void = (value: string) => {};
  onStatus: (value: string) => void = (value: string) => {};
  onSeverity: (value: string) => void = (value: string) => {};
  onSelectAll: () => void = () => {};
  onClearSelection: () => void = () => {};
  onOpen: (item: MediaItem) => void = (item: MediaItem) => {};
  onToggle: (item: MediaItem) => void = (item: MediaItem) => {};
  onIssue: (item: MediaItem) => void = (item: MediaItem) => {};
  onLongPress: (item: MediaItem) => void = (item: MediaItem) => {};
  onBatchAction: (action: string) => void = (action: string) => {};
  onBatchMove: () => void = () => {};
  onBatchShare: () => void = () => {};
  onBatchIssue: () => void = () => {};
  onOpenFull: () => void = () => {};
  aboutToAppear():void{this.syncColumns();}
  private syncColumns():void{if(this.parallelEnabled&&this.pathStack.size()===0){this.pathStack.pushPath({name:this.previewItem.id?'media':'media-placeholder'});}else if(!this.parallelEnabled&&this.pathStack.getAllPathName().includes('media-placeholder')){this.pathStack.clear();}}
  @Builder listContent() {
    Column(){
      Row({space:10}){Button(){Image($r('app.media.ic_arrow_back')).width(24).height(24)}.width(48).height(48).backgroundColor(Color.Transparent).accessibilityText('返回').onClick(()=>this.onBack());Text(this.currentProjectName).fontSize(18).fontWeight(FontWeight.Bold).fontColor(SITE_TEXT).maxLines(1).layoutWeight(1);Button('切换工程').fontSize(13).fontColor(SITE_ACCENT).backgroundColor(Color.Transparent).onClick(()=>this.onSwitchProject());}.width('100%').padding({left:4,right:10,top:4,bottom:4});
      Row(){if(this.selecting||this.selectedIds.length>0){Text(`已选 ${this.selectedIds.length} 项`).fontSize(15).fontColor(SITE_TEXT).layoutWeight(1);Button('全选').fontColor(SITE_ACCENT).backgroundColor(Color.Transparent).onClick(()=>this.onSelectAll());Button('取消').fontColor(SITE_TEXT).backgroundColor(Color.Transparent).onClick(()=>{this.selecting=false;this.onClearSelection();});}else{Scroll(){Row({space:6}){ForEach([['ALL','全部'],['TODAY','今天'],['ISSUES_ONLY','问题']],(v:string[])=>Button(v[1]).fontSize(14).constraintSize({minHeight:44}).fontColor(this.selectedFilter===v[0]?'#231A00':SITE_TEXT).backgroundColor(this.selectedFilter===v[0]?GALLERY_SELECTED:SITE_CARD).onClick(()=>this.onFilter(v[0])),(v:string[])=>v[0]);Button('筛选').fontColor(SITE_ACCENT).backgroundColor(Color.Transparent).onClick(()=>this.filtersOpen=true);Button('选择').fontColor(SITE_TEXT).backgroundColor(Color.Transparent).onClick(()=>this.selecting=true);}}.scrollable(ScrollDirection.Horizontal).scrollBar(BarState.Off).width('100%');}}.width('100%').padding({left:12,right:12,bottom:6});
      Text(`${this.items.length} 项 · 照片 ${this.items.filter((m:MediaItem)=>m.type==='PHOTO').length} · 视频 ${this.items.filter((m:MediaItem)=>m.type==='VIDEO').length}`).fontSize(14).fontColor(SITE_MUTED).width('100%').padding({left:16,bottom:8});
      if(this.loading)Text(this.progressText||'正在处理，请稍候…').fontSize(12).fontColor(SITE_MUTED).padding(8);
      Row({space:10}) { Scroll(this.listScroller){Column({space:8}){ForEach(groupsOf(this.items),(group:GalleryGroup)=>{Text(`${group.day} · ${group.items.length} 项`).fontSize(14).fontWeight(FontWeight.Bold).fontColor(SITE_TEXT).width('100%').textAlign(TextAlign.Start).padding({left:10,top:6});GridRow({columns:mediaGridColumns(this.listWidth()-12,this.fontScale),gutter:4}){ForEach(group.items,(item:MediaItem)=>{GridCol(){Column({space:4}){Stack(){if(item.type==='VIDEO'){Video({src:'file://'+(item.edited||item.path)}).width('100%').aspectRatio(1).controls(false).autoPlay(false);}else Image('file://'+(item.edited||item.path)).width('100%').aspectRatio(1).objectFit(ImageFit.Cover);if(this.selecting||this.selectedIds.length>0)Checkbox({name:'selected'}).select(this.selectedIds.includes(item.id)).onChange(()=>this.onToggle(item)).width(22).height(22).align(Alignment.TopEnd);}.id('gallery-media-'+item.id).width('100%').onClick(()=>(this.selecting||this.selectedIds.length)?this.onToggle(item):this.onOpen(item)).gesture(LongPressGesture({repeat:false}).onAction(()=>{this.selecting=true;this.onLongPress(item);}));}.padding(0).backgroundColor(GALLERY_CARD).border({width:2,color:this.previewItem.id===item.id?SITE_ACCENT:Color.Transparent}).borderRadius(8);}},(item:MediaItem,index:number)=>item.id.toString());}.width('100%').padding({left:6,right:6});},(group:GalleryGroup)=>group.day);if(this.items.length===0)Text('没有符合条件的照片或视频').fontSize(15).fontColor(SITE_MUTED).padding(24);}.padding({bottom:20}).justifyContent(FlexAlign.Start);}.layoutWeight(1).height('100%').scrollable(ScrollDirection.Vertical).scrollBar(BarState.Off).align(Alignment.Top);
      }.alignItems(VerticalAlign.Top).layoutWeight(1).width('100%');
      if(this.selecting||this.selectedIds.length>0)Flex({wrap:FlexWrap.Wrap,justifyContent:FlexAlign.SpaceAround}){Button('导出').fontColor(SITE_ACCENT).backgroundColor(Color.Transparent).enabled(this.selectedIds.length>0).onClick(()=>this.onBatchAction('export'));Button('分享').id('gallery-share-anchor').fontColor(SITE_TEXT).backgroundColor(Color.Transparent).enabled(this.selectedIds.length>0).onClick(()=>this.onBatchShare());Button('移入工程').fontColor(SITE_TEXT).backgroundColor(Color.Transparent).enabled(this.selectedIds.length>0).onClick(()=>this.onBatchMove());Button('删除').fontColor('#E53935').backgroundColor(Color.Transparent).enabled(this.selectedIds.length>0).onClick(()=>this.onBatchAction('delete'));}.width('100%').padding(8).backgroundColor(SITE_CARD);

    }.width('100%').height('100%').backgroundColor(SITE_BG).id('gallery-list-pane');
  }
  @Builder filterPanel(){Column({space:12}){Row(){Text('筛选').fontSize(20).fontWeight(FontWeight.Bold).fontColor(SITE_TEXT).layoutWeight(1);Button('完成').fontColor(SITE_ACCENT).backgroundColor(Color.Transparent).onClick(()=>this.filtersOpen=false);}.width('100%');Scroll(){Column({space:12}){Button(this.dateRangeText||'拍摄日期').fontColor(SITE_TEXT).backgroundColor(SITE_CARD_RAISED).onClick(()=>this.onDateRange());Text('工程类别').fontColor(SITE_TEXT).width('100%');Flex({wrap:FlexWrap.Wrap}){Button('全部').fontColor(SITE_TEXT).backgroundColor(!this.selectedCategory?GALLERY_SELECTED:SITE_CARD_RAISED).onClick(()=>this.onCategory(''));ForEach(this.categories,(v:string)=>Button(v).fontColor(SITE_TEXT).backgroundColor(this.selectedCategory===v?GALLERY_SELECTED:SITE_CARD_RAISED).margin(3).onClick(()=>this.onCategory(v)),(v:string)=>v);}.width('100%');Text('问题状态').fontColor(SITE_TEXT).width('100%');Flex({wrap:FlexWrap.Wrap}){Button('全部').fontColor(SITE_TEXT).backgroundColor(!this.selectedStatus?GALLERY_SELECTED:SITE_CARD_RAISED).onClick(()=>this.onStatus(''));ForEach([['PENDING','待处理'],['IN_PROGRESS','处理中'],['COMPLETED','已完成']],(v:string[])=>Button(v[1]).fontColor(SITE_TEXT).backgroundColor(this.selectedStatus===v[0]?GALLERY_SELECTED:SITE_CARD_RAISED).margin(3).onClick(()=>this.onStatus(v[0])),(v:string[])=>v[0]);}.width('100%');Text('严重程度').fontColor(SITE_TEXT).width('100%');Flex({wrap:FlexWrap.Wrap}){Button('全部').fontColor(SITE_TEXT).backgroundColor(!this.selectedSeverity?GALLERY_SELECTED:SITE_CARD_RAISED).onClick(()=>this.onSeverity(''));ForEach([['NORMAL','一般'],['IMPORTANT','重要'],['CRITICAL','严重']],(v:string[])=>Button(v[1]).fontColor(SITE_TEXT).backgroundColor(this.selectedSeverity===v[0]?GALLERY_SELECTED:SITE_CARD_RAISED).margin(3).onClick(()=>this.onSeverity(v[0])),(v:string[])=>v[0]);}.width('100%');Button('重置').fontColor(SITE_ACCENT).backgroundColor(SITE_CARD_RAISED).onClick(()=>{this.onClearDate();this.onCategory('');this.onStatus('');this.onSeverity('');});}.width('100%');}.width('100%').layoutWeight(1).align(Alignment.Top).scrollBar(BarState.Off);}.padding(20).width('100%').height('100%').backgroundColor(SITE_CARD);}
  build() {
    Stack(){Navigation(this.pathStack){this.listContent();}
      .mode(this.parallelEnabled?NavigationMode.Split:NavigationMode.Stack)
      .navBarWidth(this.parallelEnabled?Math.max(280,Math.min(420,this.windowWidth*.4)):'100%').minContentWidth(320).hideTitleBar(true)
      .navDestination(this.destination).width('100%').height('100%');
      if(this.filtersOpen){Rect().width('100%').height('100%').fill('#00000099').onClick(()=>this.filtersOpen=false);Column(){this.filterPanel();}.width('100%').constraintSize({maxWidth:520}).height('85%').align(Alignment.Bottom);}
    }.width('100%').height('100%');
  }
  @Builder filterDateRow(values: string[][],selected: string,change: (value:string)=>void){Scroll(){Row({space:6}){ForEach(values,(v:string[])=>Button(v[1]).height(30).padding({left:10,right:10}).fontSize(12).fontColor(SITE_TEXT).backgroundColor(selected===v[0]?GALLERY_SELECTED:SITE_BG).border({width:1,color:GALLERY_OUTLINE}).borderRadius(8).onClick(()=>change(v[0])),(v:string[])=>v[0]);Button(this.dateRangeText||'日期 / 范围').height(30).padding({left:10,right:10}).fontSize(12).fontColor(SITE_TEXT).backgroundColor(this.dateRangeText?GALLERY_SELECTED:SITE_BG).border({width:1,color:GALLERY_OUTLINE}).borderRadius(8).onClick(()=>this.onDateRange());if(this.dateRangeText)Button('清除日期').height(30).padding({left:10,right:10}).fontSize(12).fontColor(SITE_MUTED).backgroundColor(Color.Transparent).border({width:1,color:GALLERY_OUTLINE}).borderRadius(8).onClick(()=>this.onClearDate());}}.height(32).scrollable(ScrollDirection.Horizontal).scrollBar(BarState.Off).align(Alignment.Start).width('100%');}
}
