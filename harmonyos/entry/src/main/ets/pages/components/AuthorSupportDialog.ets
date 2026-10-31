@CustomDialog
export struct AuthorSupportDialog {
  controller:CustomDialogController;
  @Prop maxHeight:number=620;
  @State wechat:boolean=false;
  build(){
    Column({space:12}){
      Row({space:12}){
        Text('支持作者').fontSize(22).fontWeight(FontWeight.Medium).fontColor($r('app.color.text_primary')).layoutWeight(1);
        Button(){Image($r('app.media.ic_close')).width(24).height(24).fillColor($r('app.color.text_primary'));}
          .width(48).height(48).backgroundColor(Color.Transparent).accessibilityText('关闭').onClick(()=>this.controller.close());
      }.width('100%').flexShrink(0);
      Row({space:8}){
        Button('支付宝').layoutWeight(1).constraintSize({minHeight:48}).fontSize(16).backgroundColor(this.wechat?$r('app.color.card_raised'):'#FFB300').fontColor(this.wechat?$r('app.color.text_primary'):'#231A00').onClick(()=>this.wechat=false);
        Button('微信').layoutWeight(1).constraintSize({minHeight:48}).fontSize(16).backgroundColor(this.wechat?'#FFB300':$r('app.color.card_raised')).fontColor(this.wechat?'#231A00':$r('app.color.text_primary')).onClick(()=>this.wechat=true);
      }.width('100%').flexShrink(0);
      Image(this.wechat?$r('app.media.author_support_wechat'):$r('app.media.author_support_alipay')).width('100%').layoutWeight(1).objectFit(ImageFit.Contain).accessibilityText(this.wechat?'微信收款二维码':'支付宝收款二维码');
    }.width('92%').constraintSize({maxWidth:400}).height(Math.min(620,this.maxHeight)).padding(16).backgroundColor($r('app.color.card_bg')).borderRadius(20);
  }
}
