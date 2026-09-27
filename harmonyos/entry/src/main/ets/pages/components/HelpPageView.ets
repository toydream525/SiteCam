import { OFFLINE_BUILD } from '../../core/BuildVariant';
import { OFFLINE_PRIVACY_LINES } from '../../core/OfflinePrivacy';
interface GuideSection {title:string;lines:string[];}
const BG='#121212';const CARD='#282828';const TEXT='#F5F5F5';const MUTED='#B0BEC5';const YELLOW='#FFB300';
const GUIDE:GuideSection[]=[
 {title:"使用指南",lines:["SiteCam（工程水印相机）面向施工记录、现场巡检和工程留档。工程资料默认留在设备本地，无需注册账号。下面用虚构的“滨江路雨污分流改造”工程说明常用操作。","本版：v0.3.5 · HarmonyOS 原生版","适用于 HarmonyOS 5 及以上；相机、定位和视频能力以设备支持情况为准"]},
 {title:"安装与权限",lines:["取得适用于本机的鸿蒙安装包后，按系统提示完成安装；具体分发状态以当前核实渠道为准。Android APK 不适用于本原生包。","完成或跳过首次功能指引后进入权限指引，点“开启所需权限”，集中申请当前尚未授权的相机、定位和麦克风权限。","定位和录音是可选的，可以暂时跳过；相机未授权时不能拍摄。已经授予的权限不会重复申请。","以后缺少权限时，从相机页出现的提示按需补开；定位拒绝后仍可拍照、录像和导出，录音拒绝后仍可录制无声视频。"]},
 {title:"首次功能指引",lines:["新装或首次使用会看到 4 页指引：先选工程、选择横竖方向、按日期找资料和导出锁定。","点“跳过”或最后一页的“开始使用”后，以后启动和正常升级都不会自动显示。","中途退出且没有跳过，下一次打开会继续显示；指引状态会在设备本地保存。","指引完成或跳过后进入权限指引，点“开启所需权限”，集中申请尚未授权的相机、定位和麦克风；定位和录音可暂时跳过，完成授权或点击暂时跳过后，之后不再自动弹出，已有全授予会直接跳过。","以后缺少权限时，从相机页的提示按需补开；进入“设置 → 使用技巧与教程 → 重新查看首次功能指引”可以随时重看，且不会清空已完成状态。"]},
 {title:"工程与线路",lines:["在相机页点左上角工程名称打开工程快捷切换，选择要拍摄或查看的工程；需要整理工程时再进入工程包页。","每个工程建一个工程包，线路名称可按需要填写。例如工程填写“滨江路雨污分流改造”，线路填写“北段雨水管”。","工程卡片上可以编辑名称、类别、地点和备注，也可以归档；在归档列表中可以恢复。","长按或进入批量操作，可批量分类、归档、锁定、解锁、删除或导出工程。删除前先导出需要留档的资料。","锁定只禁止该工程新拍照和录像，仍可切换到该工程查看、编辑、移动、删除和导出；需要继续拍摄时先解锁。搜索工程或线路名称即可定位资料。"]},
 {title:"拍照、录像与水印",lines:["先选工程和线路，再在取景页点击快门拍照；切换到录像后点一次开始、再点一次结束。当前工程会记录照片和视频数量。","闪光灯短按在自动、开启、关闭之间切换；长按闪光灯图标可开关常亮手电。","点取景页顶部的拍摄方向图标，默认使用自动；也可选择竖屏、左横屏或右横屏，应用会记住你的选择。画面和成片会保持同一方向。","允许定位后，水印可显示地点；内置“海拔”位于现场位置下方，默认关闭。打开后仅使用拍摄瞬间的有效定位海拔，正值、零值和负值都按米显示；未提供或已过期时显示“暂不可用”。","照片、视频和预览使用同一套水印排版；拍摄时冻结时间、工程信息、定位和水印设置，之后的设置变化不会改写旧照片或旧视频。地址不可用时可点击“刷新地址”重试定位和地址解析，刷新期间仍可拍摄。"]},
 {title:"更换水印样式",lines:["在设置打开“自定义水印字段与样式”，或在取景页点水印卡片进入快速修改，再点“更换样式”。","面板提供八款样式：经典工程水印、极简水印、工程信息板、蓝白工程记录、施工表格、巡检记录、验收记录、大字时间地点。","点卡片查看大预览，先比较版式和信息位置；原有字段、字号和不透明度会保留。","确定后点“使用此样式”才保存，点“取消”会回到原来的样式。恢复默认会恢复系统字段标签和默认顺序，保留已填写内容、自定义字段和开关；模板升级只补入关闭的海拔字段并保留原有相对顺序。"]},
 {title:"相册、搜索与日期",lines:["点相机页缩略图进入当前工程相册。工程卡片显示照片、视频和问题数量。工程会自动记录首拍日期和末拍日期；还没有拍摄时显示“尚未拍摄”。","在工程管理中搜索工程或线路，并按最近拍摄、创建时间、编辑时间、工程名或线路首字母升序/降序排列。","在相册中用年月日区间筛选照片，例如只看 2026 年 9 月 1 日到 9 月 3 日的夜间施工记录。","多选照片或视频后，可移动、分享、导出或删除；操作前确认当前工程范围，避免把资料放错线路。","相册可查看全部工程或指定工程。拍摄前回到相机页，确认左上角的工程名称。"]},
 {title:"记录和跟进问题",lines:["拍摄时点右上角三角形问题按钮，再拍下需要跟进的位置；也可以在相册里点“标记问题”。","填写问题名称和说明，选择“一般、重要、严重”级别，再保存。","打开已有问题的照片或视频，点“编辑问题”，选择“待处理”“处理中”或“已完成”后保存。","问题会关联照片或视频，导出工程包时会随清单和报告一起带走。"]},
 {title:"查看、编辑与标注",lines:["打开详情后可在 1 到 8 倍之间缩放；双击放大或还原，双指拖动查看画面细节。","编辑时可水平翻转、垂直翻转、旋转 90 度或自由裁剪，也可以撤销和重做。","现场标注支持箭头、矩形、圆形、画笔、文字和马赛克，适合指出管线、裂缝或需要整改的位置。","详情页可以切换原图和独立成品。保存成品不会覆盖原图，水印会跟着整张图的方向和裁剪一起变换。","分享或导出前检查成品是否包含需要的标注和水印，原图仍可留作复核。"]},
 {title:"导出工程资料",lines:["在工程管理或相册中选择一个工程、多个工程或选中的照片或视频，点击“导出”。","可选择一个 ZIP，或选择一个总文件夹。总文件夹里每个工程都有独立目录，便于交给不同班组。","每个工程目录包含照片、视频、编辑成品、工程信息、照片和视频清单及报告；文件名会保留工程和日期线索。","导出时可保持原文件（默认），也可生成压缩副本。压缩副本不会替换设备里保存的原文件。","完工确认后锁定工程，减少误拍；锁定后仍可补充备注、移动、删除和导出既有资料。"]},
 {title:"保存位置与换机",lines:["保存到系统相册默认关闭；新照片、视频和编辑成品默认保存在 SiteCam 内，打开设置后也可保存到系统相册供其他应用使用。","“另存到系统相册”会创建独立副本。原件与系统相册副本分开保存，开关只影响之后保存的资料，不搬动旧文件。","删除系统相册副本不会删除工程原件；删除工程资料也不会自动删除系统相册副本。取消授权不影响工程内保存。","换机或卸载前先导出各工程的 ZIP 或总文件夹，再把资料保存到新设备查看；当前版本不提供把工程恢复成可继续拍摄工程的功能。"]},
 {title:"隐私与常见处理",lines:["SiteCam 无广告、无账号和自动上传；工程、照片、视频、地点水印和问题记录默认只在本机使用。","地点没有显示时，检查系统定位开关和应用权限；地址不可用时点击“刷新地址”，服务会先尝试新鲜定位的地址解析；室内或刚到现场时等待定位稳定即可。","录像没有声音时，在设置中打开“拍照快门声音”不会影响录像；请重新开启麦克风权限后再开始下一段录像，已经拍好的无声视频不会自动补录声音。","字段太多时，到“设置 → 自定义水印字段与样式”减少字段或调整字号，再拍摄新的记录。","提交反馈前遮住工程名称、地址、地点、时间、人员和车辆信息，不要公开真实工程资料或签名文件。"]},
 {title:"获取帮助",lines:["在设置中打开“使用技巧与教程”，没有网络也能阅读本指南。","反馈问题时写明设备型号、HarmonyOS 系统版本、所在页面、操作步骤和预期结果。","需要复现问题时使用虚构工程和脱敏截图，保留导出的报告以便核对。"]},
 {title:"更换桌面图标",lines:["打开“设置 → 桌面图标”，先查看当前选中的图标样式。","新装默认使用 D「工程现场」；也可选择 A「蓝图镜头」、B「工程印记」或 C「现场坐标」，选择会立即生效，返回桌面查看。","切换只影响桌面入口，工程资料、拍摄记录和应用内页面不受影响。","重启或正常升级后会保留你的选择；如果桌面暂未刷新，稍等片刻或重新打开桌面。","作者：ninjaaqua"]},
 {title:"鸿蒙原生版与多设备",lines:["当前版本：v0.3.5 HarmonyOS 原生版，适用于 HarmonyOS 5 及以上；平板、折叠屏和分屏按窗口空间调整。","普通手机使用单栏，窗口达到 600 vp 显示更多列，840 vp 起使用列表和详情双栏；展开、旋转后保留当前资料及多选。","大屏照片编辑在右侧显示工具，相机侧边水印面板可滚动。","实际相机、定位、H.264 视频和悬停能力以设备支持情况为准；视频处理失败会保留原片供重试。","应用说明和分发渠道以当前核实信息为准。"]}
];
const OFFLINE_GUIDE:GuideSection[]=[
 {title:'纯离线版说明',lines:[
  '本应用为纯离线版，不连接网络，仅通过系统定位获取 GPS 坐标；具体地址无法自动识别，请手动填写。',
  'GPS 信息用于显示经纬度；工程地址、街道和门牌等具体信息请自行填写。具体地址水印默认关闭，可在“设置 → 自定义水印字段与样式 → 具体地址”按需开启。',
  '应用不申请联网权限、不调用在线地址解析，也不自动上传资料。坐标与照片在本机处理。系统定位服务可能采用系统自身的辅助定位方式，应用只接受系统明确标记为 GNSS 的坐标。',
  '卫星坐标需要精确位置权限，来源不明或网络定位不会显示为 GPS。只接受时间不超过 30 秒的新鲜读数；未取得读数时可以继续拍照、录像和填写工程地址。'
 ]},
 {title:'工程与手填地址',lines:[
  '新建或编辑工程包时，在“工程地点”填写工程地址、街道或门牌。地址保存在本机工程资料中。',
  '具体地址水印默认关闭。在“设置 → 自定义水印字段与样式 → 具体地址”按需开启后，工程手填地址才会用于新拍摄记录；GPS 经纬度由独立的定位字段控制。',
  '从原联网版本升级时，旧工程资料保留；旧水印地址字段不会自动开启离线版的具体地址显示偏好。'
 ]},
 {title:'卫星定位与拍摄',lines:[
  '相机页的“获取卫星坐标”会请求一次当前定位。应用只采用 sourceType 明确为 GNSS 的新鲜坐标，缺少来源、网络来源、室内定位或过期结果都会被丢弃。',
  '获取定位不会锁住相机。室内或卫星信号弱时仍可拍照；经纬度与海拔没有当前有效读数时不会用旧值或 0 值伪装成当前定位。',
  '应用通过 HarmonyOS 系统定位接口读取 GNSS 结果。系统可能使用自己的辅助定位能力；本应用不调用在线地址服务、高程服务或测绘服务。'
 ]},
 {title:'本机隐私政策',lines:OFFLINE_PRIVACY_LINES},
 {title:'其他使用说明',lines:[
  '本指南和隐私说明随应用安装在本机，没有网络也可以查看。',
  '拍摄前确认当前工程；无卫星定位时可手填工程地址并继续记录现场。'
 ]}
];
@Component export struct HelpPageView {
 @Prop sections:GuideSection[]=GUIDE;
 @Prop offlineMode:boolean=OFFLINE_BUILD;
 @State displaySections:GuideSection[]=GUIDE;
 @State selectedIndex:number=-1;
 @State windowWidth:number=0;
 private directoryScroller:Scroller=new Scroller();
 private bodyScroller:Scroller=new Scroller();
 onBack:()=>void=()=>{};
 onReplayGuide:()=>void=()=>{};
 aboutToAppear():void {this.displaySections=this.offlineMode?OFFLINE_GUIDE:this.sections;}
 private selectSection(index:number):void {if(index<0||index>=this.displaySections.length)return;this.selectedIndex=index;setTimeout(()=>{try{this.bodyScroller.scrollEdge(Edge.Top);}catch(e){}},0);}
 private showDirectory():void {this.selectedIndex=-1;}
 @Builder privacyLine(line:string){if(line.startsWith('§')){Text(line.slice(1)).fontSize(16).fontWeight(FontWeight.Bold).lineHeight(24).fontColor(YELLOW).width('100%').padding({top:8});}else{Text(line).fontSize(14).lineHeight(22).fontColor(TEXT).width('100%');}}
 @Builder directoryList(){Column({space:6}){ForEach(this.displaySections,(sec:GuideSection,index:number)=>{Button(sec.title).width('100%').height(44).fontSize(14).fontColor(this.selectedIndex===index?'#231A00':TEXT).backgroundColor(this.selectedIndex===index?YELLOW:CARD).borderRadius(8).onClick(()=>this.selectSection(index));},(sec:GuideSection)=>sec.title);}.width('100%').alignItems(HorizontalAlign.Start);}
 @Builder chapterBody(){if(this.selectedIndex>=0&&this.selectedIndex<this.displaySections.length){Column({space:8}){Text(this.displaySections[this.selectedIndex].title).fontSize(20).fontWeight(FontWeight.Bold).fontColor(YELLOW);ForEach(this.displaySections[this.selectedIndex].lines,(line:string,index:number)=>{if(this.offlineMode&&this.displaySections[this.selectedIndex].title==='本机隐私政策'){this.privacyLine(line);}else{Text('• '+line).fontSize(15).lineHeight(25).fontColor(TEXT).width('100%');}},(line:string,index:number)=>line+'_'+index);}.padding(18).backgroundColor(CARD).borderRadius(12).width('100%').alignItems(HorizontalAlign.Start);}else{Column({space:8}){Text('选择左侧章节开始阅读').fontSize(18).fontWeight(FontWeight.Bold).fontColor(YELLOW);Text('章节目录会一直保留，切换章节后正文自动回到开头。').fontSize(15).lineHeight(25).fontColor(TEXT);}.padding(18).backgroundColor(CARD).borderRadius(12).width('100%').alignItems(HorizontalAlign.Start);}}
 @Builder phoneDirectory(){Column({space:12}){Text(this.offlineMode?'工程现场使用指南 · v0.3.5 纯离线版':'工程现场使用指南 · v0.3.5').fontSize(20).fontWeight(FontWeight.Bold).fontColor(TEXT);Text('章节目录').fontSize(17).fontWeight(FontWeight.Bold).fontColor(YELLOW);Scroll(this.directoryScroller){this.directoryList();}.width('100%').layoutWeight(1).scrollable(ScrollDirection.Vertical).align(Alignment.Top).scrollBar(BarState.Off);}.width('100%').layoutWeight(1).alignItems(HorizontalAlign.Start).padding(16);}
 @Builder phoneChapter(){Column({space:8}){Row({space:8}){Button('返回章节目录').height(44).fontSize(14).fontColor('#231A00').backgroundColor(YELLOW).borderRadius(8).onClick(()=>this.showDirectory());Text(this.selectedIndex>=0&&this.selectedIndex<this.displaySections.length?this.displaySections[this.selectedIndex].title:'教程').fontSize(17).fontWeight(FontWeight.Bold).fontColor(TEXT).maxLines(1).textOverflow({overflow:TextOverflow.Ellipsis}).layoutWeight(1);}.width('100%').padding({left:16,right:16});Scroll(this.bodyScroller){this.chapterBody();}.width('100%').layoutWeight(1).scrollable(ScrollDirection.Vertical).align(Alignment.Top).scrollBar(BarState.Off);}.width('100%').layoutWeight(1).alignItems(HorizontalAlign.Start);}
 @Builder wideContent(){Row({space:12}){Column({space:8}){Text('章节目录').fontSize(17).fontWeight(FontWeight.Bold).fontColor(YELLOW);Scroll(this.directoryScroller){this.directoryList();}.width('100%').layoutWeight(1).scrollable(ScrollDirection.Vertical).align(Alignment.Top).scrollBar(BarState.Off);}.width(236).height('100%').padding(12).backgroundColor(CARD).borderRadius(12);Column({space:8}){Row({space:8}){Button('返回章节目录').height(40).fontSize(13).fontColor(this.selectedIndex>=0?'#231A00':MUTED).backgroundColor(this.selectedIndex>=0?YELLOW:CARD).borderRadius(8).enabled(this.selectedIndex>=0).onClick(()=>this.showDirectory());Text(this.selectedIndex>=0&&this.selectedIndex<this.displaySections.length?this.displaySections[this.selectedIndex].title:'正文').fontSize(18).fontWeight(FontWeight.Bold).fontColor(TEXT).maxLines(1).textOverflow({overflow:TextOverflow.Ellipsis}).layoutWeight(1);}.width('100%');Scroll(this.bodyScroller){this.chapterBody();}.width('100%').layoutWeight(1).scrollable(ScrollDirection.Vertical).align(Alignment.Top).scrollBar(BarState.Off);}.layoutWeight(1).height('100%').alignItems(HorizontalAlign.Start);}.width('100%').layoutWeight(1).padding(12);}
 build(){Column(){Row(){Button(){Image($r('app.media.ic_arrow_back')).width(24).height(24);}.width(48).height(48).backgroundColor(Color.Transparent).accessibilityText('返回设置').onClick(()=>this.onBack());Text('使用技巧与教程').fontSize(18).fontWeight(FontWeight.Bold).fontColor(TEXT);Blank();}.height(64).width('100%').alignItems(VerticalAlign.Center);if(this.windowWidth>=840)this.wideContent();else if(this.selectedIndex<0)this.phoneDirectory();else this.phoneChapter();Button('重新查看首次功能指引').width('calc(100% - 32vp)').height(48).fontColor('#231A00').backgroundColor(YELLOW).onClick(()=>this.onReplayGuide());}.width('100%').height('100%').alignItems(HorizontalAlign.Start).backgroundColor(BG).onAreaChange((oldValue,newValue)=>{this.windowWidth=Number(newValue.width);});}
}
