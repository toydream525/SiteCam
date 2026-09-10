import { MediaItem, Project } from '../../core/Models';
import { SITE_ACCENT, SITE_BG, SITE_CARD, SITE_CARD_RAISED, SITE_MUTED, SITE_TEXT } from './UiPrimitives';

const GALLERY_SELECTED: string = '#4A4358';
const GALLERY_CARD: string = '#34313A';
const GALLERY_OUTLINE: string = '#4A4650';

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
  private syncColumns():void{if(this.windowWidth>=840&&this.pathStack.size()===0){this.pathStack.pushPath({name:'media-placeholder'});}else if(this.windowWidth<840&&this.pathStack.getAllPathName().includes('media-placeholder')){this.pathStack.clear();}}
  @Builder listContent() {
    Column(){
      Row({space:10}){Button(){Image($r('app.media.ic_arrow_back')).width(24).height(24)}.width(44).height(44).backgroundColor(Color.Transparent).accessibilityText('返回').onClick(()=>this.onBack());Text(this.currentProjectName).fontSize(18).fontWeight(FontWeight.Bold).fontColor(SITE_TEXT).maxLines(1).layoutWeight(1);Button('切换工程').fontSize(13).fontColor(SITE_ACCENT).backgroundColor(Color.Transparent).onClick(()=>this.onSwitchProject());}.width('100%').padding({left:4,right:10,top:4,bottom:4});
      Column({space:3}){
        this.filterDateRow([['ALL','全部'],['TODAY','今天'],['ISSUES_ONLY','问题']],this.selectedFilter,this.onFilter);
        Scroll(){Row({space:6}){Button('全部类别').height(30).padding({left:10,right:10}).fontSize(12).fontColor(!this.selectedCategory?SITE_TEXT:SITE_TEXT).backgroundColor(!this.selectedCategory?GALLERY_SELECTED:SITE_BG).border({width:1,color:GALLERY_OUTLINE}).borderRadius(8).onClick(()=>this.onCategory(''));ForEach(this.categories,(v:string)=>Button(v).height(30).padding({left:10,right:10}).fontSize(12).fontColor(SITE_TEXT).backgroundColor(this.selectedCategory===v?GALLERY_SELECTED:SITE_BG).border({width:1,color:GALLERY_OUTLINE}).borderRadius(8).onClick(()=>this.onCategory(this.selectedCategory===v?'':v)),(v:string)=>v);}}.height(32).scrollable(ScrollDirection.Horizontal).align(Alignment.Start).width('100%');
        Scroll(){Row({space:6}){Button('全部问题状态').height(30).padding({left:10,right:10}).fontSize(12).fontColor(SITE_TEXT).backgroundColor(!this.selectedStatus&&!this.selectedSeverity?GALLERY_SELECTED:SITE_BG).border({width:1,color:GALLERY_OUTLINE}).borderRadius(8).onClick(()=>{this.onStatus('');this.onSeverity('');});ForEach([['PENDING','待处理'],['IN_PROGRESS','处理中'],['COMPLETED','已完成']],(v:string[])=>Button(v[1]).height(30).padding({left:10,right:10}).fontSize(12).fontColor(SITE_TEXT).backgroundColor(this.selectedStatus===v[0]?GALLERY_SELECTED:SITE_BG).border({width:1,color:GALLERY_OUTLINE}).borderRadius(8).onClick(()=>this.onStatus(this.selectedStatus===v[0]?'':v[0])),(v:string[])=>v[0]);ForEach([['NORMAL','一般'],['IMPORTANT','重要'],['CRITICAL','严重']],(v:string[])=>Button(v[1]).height(30).padding({left:10,right:10}).fontSize(12).fontColor(SITE_TEXT).backgroundColor(this.selectedSeverity===v[0]?GALLERY_SELECTED:SITE_BG).border({width:1,color:GALLERY_OUTLINE}).borderRadius(8).onClick(()=>this.onSeverity(this.selectedSeverity===v[0]?'':v[0])),(v:string[])=>v[0]);}}.height(32).scrollable(ScrollDirection.Horizontal).align(Alignment.Start).width('100%');
      }.padding({left:10,right:10,bottom:4});
      Row(){Text(`${this.items.length} 项 · 照片 ${this.items.filter((m:MediaItem)=>m.type==='PHOTO').length} / 视频 ${this.items.filter((m:MediaItem)=>m.type==='VIDEO').length}`).fontSize(13).fontColor(SITE_MUTED).layoutWeight(1);}.width('100%').padding({left:12,right:8});
      Row(){Button('全选当前筛选').fontSize(12).fontColor(SITE_ACCENT).backgroundColor(Color.Transparent).onClick(()=>this.onSelectAll());if(this.selectedIds.length>0)Button(`取消 ${this.selectedIds.length} 项`).fontSize(12).fontColor(SITE_MUTED).backgroundColor(Color.Transparent).onClick(()=>this.onClearSelection());}.width('100%').padding({left:12,right:8});
      if(this.loading){Progress({value:0,total:1}).color(SITE_ACCENT).width('100%');Text(this.progressText).fontSize(12).fontColor(SITE_MUTED).padding(8);}
      if(this.selectedIds.length>0)Row({space:6}){Text(`已选 ${this.selectedIds.length} 项`).fontSize(12).fontColor(SITE_TEXT).layoutWeight(1);Button('导出').fontSize(11).fontColor('#231A00').backgroundColor(SITE_ACCENT).onClick(()=>this.onBatchAction('export'));Button('更多').id('sitecam-share-anchor').fontSize(11).fontColor(SITE_TEXT).backgroundColor(SITE_CARD_RAISED).onClick(()=>this.onBatchAction('more'));}.width('100%').padding({left:12,right:12,bottom:6});
      Row({space:10}) { Scroll(this.listScroller){Column({space:8}){ForEach(groupsOf(this.items),(group:GalleryGroup)=>{Text(`${group.day} · ${group.items.length} 项`).fontSize(14).fontWeight(FontWeight.Bold).fontColor(SITE_TEXT).width('100%').textAlign(TextAlign.Start).padding({left:10,top:6});GridRow({columns:this.windowWidth<600?3:this.windowWidth>=840?3:4,gutter:6}){ForEach(group.items,(item:MediaItem)=>{GridCol(){Column({space:4}){Stack(){if(item.type==='VIDEO'){Video({src:'file://'+(item.edited||item.path)}).width('100%').aspectRatio(1).controls(false).autoPlay(false);}else Image('file://'+(item.edited||item.path)).width('100%').aspectRatio(1).objectFit(ImageFit.Cover);if(this.selectedIds.includes(item.id))Checkbox({name:'selected'}).select(true).onChange(()=>this.onToggle(item)).width(22).height(22).align(Alignment.TopEnd);}.id('gallery-media-'+item.id).width('100%').onClick(()=>this.selectedIds.length?this.onToggle(item):this.onOpen(item)).gesture(LongPressGesture({repeat:false}).onAction(()=>this.onLongPress(item)));Text(group.day).fontSize(10).fontColor(SITE_MUTED).maxLines(1);Text(`${item.type==='VIDEO'?'视频':'照片'} · ${projectName(this.projects,item.projectId)}`).fontSize(11).fontColor(SITE_TEXT).maxLines(1);Button(issueText(item)).fontSize(10).fontColor(SITE_ACCENT).backgroundColor(Color.Transparent).onClick(()=>this.onIssue(item));}.padding(5).backgroundColor(GALLERY_CARD).borderRadius(8);}},(item:MediaItem,index:number)=>item.id.toString());}.width('100%').padding({left:6,right:6});},(group:GalleryGroup)=>group.day);if(this.items.length===0)Text('没有符合条件的照片或视频').fontSize(15).fontColor(SITE_MUTED).padding(24);}.padding({bottom:20}).justifyContent(FlexAlign.Start);}.layoutWeight(1).height('100%').scrollable(ScrollDirection.Vertical).align(Alignment.Top);
      }.alignItems(VerticalAlign.Top).layoutWeight(1).width('100%');
    }.width('100%').height('100%').backgroundColor(SITE_BG);
  }
  build() {
    Navigation(this.pathStack){this.listContent();}
      .mode(this.windowWidth>=840?NavigationMode.Split:NavigationMode.Stack)
      .navBarWidth('55%').minContentWidth(300).hideTitleBar(true)
      .navDestination(this.destination).width('100%').height('100%');
  }
  @Builder filterDateRow(values: string[][],selected: string,change: (value:string)=>void){Scroll(){Row({space:6}){ForEach(values,(v:string[])=>Button(v[1]).height(30).padding({left:10,right:10}).fontSize(12).fontColor(SITE_TEXT).backgroundColor(selected===v[0]?GALLERY_SELECTED:SITE_BG).border({width:1,color:GALLERY_OUTLINE}).borderRadius(8).onClick(()=>change(v[0])),(v:string[])=>v[0]);Button(this.dateRangeText||'日期 / 范围').height(30).padding({left:10,right:10}).fontSize(12).fontColor(SITE_TEXT).backgroundColor(this.dateRangeText?GALLERY_SELECTED:SITE_BG).border({width:1,color:GALLERY_OUTLINE}).borderRadius(8).onClick(()=>this.onDateRange());if(this.dateRangeText)Button('清除日期').height(30).padding({left:10,right:10}).fontSize(12).fontColor(SITE_MUTED).backgroundColor(Color.Transparent).border({width:1,color:GALLERY_OUTLINE}).borderRadius(8).onClick(()=>this.onClearDate());}}.height(32).scrollable(ScrollDirection.Horizontal).align(Alignment.Start).width('100%');}
}
