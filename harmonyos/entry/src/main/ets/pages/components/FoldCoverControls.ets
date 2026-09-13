/** Small clamshell cover controls; full settings remain on the inner screen. */
@Component
export struct FoldCoverControls {
  @Prop panelWidth: number=56;
  @Prop panelHeight: number=320;
  @Prop recording: boolean=false;
  @Prop canCapture: boolean=false;
  @Prop canSwitch: boolean=false;
  onGallery:()=>void=()=>{};
  onCapture:()=>void=()=>{};
  onSwitch:()=>void=()=>{};
  onLongPress:()=>void=()=>{};
  @State longPressConsumed: boolean=false;
  build(){
    Column({space:12}){
      if(this.panelHeight>=156)Button(){Image($r('app.media.icon_folder')).width(22).height(22);}.width(44).height(44).padding(0).backgroundColor(Color.Transparent).id('camera-gallery').accessibilityText('最近照片，长按打开相机工具').onTouch(e=>{if(e.type===TouchType.Down)this.longPressConsumed=false;}).onClick(()=>{if(this.longPressConsumed){this.longPressConsumed=false;return;}this.onGallery();}).gesture(LongPressGesture({repeat:false}).onAction(()=>{this.longPressConsumed=true;this.onLongPress();}));
      Button(){Circle().width(40).height(40).fill(this.recording?'#E53935':Color.White);}.width(44).height(44).padding(0).backgroundColor(Color.Transparent).id('camera-shutter').accessibilityText(this.recording?'停止录像':'拍摄').enabled(this.canCapture).onClick(()=>this.onCapture());
      if(this.panelHeight>=156)Button(){Image($r('app.media.ic_switch_lens')).width(22).height(22);}.width(44).height(44).padding(0).backgroundColor(Color.Transparent).id('camera-switch-lens').accessibilityText('切换镜头').enabled(this.canSwitch).onClick(()=>this.onSwitch());
    }.width(this.panelWidth).height(this.panelHeight).justifyContent(FlexAlign.Center).backgroundColor(Color.Black).id('camera-controls');
  }
}
