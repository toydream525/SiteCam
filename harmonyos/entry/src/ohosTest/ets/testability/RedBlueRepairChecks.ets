import { common } from '@kit.AbilityKit';
import { fileIo as fs } from '@kit.CoreFileKit';
import { relationalStore, preferences } from '@kit.ArkData';
import { Store } from '../../../main/ets/core/Store';
import { Project, MediaItem, Annotation } from '../../../main/ets/core/Models';
import { loadEditableRecipe, moveTextStep, EditRecipe, EditStep, Mark } from '../../../main/ets/core/ImageProcessor';
import { cameraLayout } from '../../../main/ets/core/CameraLayout';
import { ordinaryPhoneGrip } from '../../../main/ets/core/DeviceLayoutProfile';
import { missingVideoCodec } from './VideoEnvironment';

function check(value:boolean,message:string):void{if(!value)throw new Error(message);}
export class RedBlueRepairChecks {
  static async run():Promise<void>{RedBlueRepairChecks.recipes();RedBlueRepairChecks.productionLayout();RedBlueRepairChecks.videoClassification();}
  static missingContainerRecipes():string[]{
    const fields=['"rotation":0','"flipX":false','"flipY":false','"cropX":0','"cropY":0','"cropW":1','"cropH":1'];const out:string[]=[];
    for(let mask=1;mask<(1<<fields.length);mask++)out.push('{'+fields.filter((v:string,i:number)=>(mask&(1<<i))!==0).join(',')+'}');return out;
  }
  static recipes():void{
    const invalid=['{}','{"garbage":true}','{"steps":[{}]}','{"steps":[{"kind":"text","mark":[]}]}','{"steps":[{"kind":"text","mark":{"kind":"text","x":0.2,"y":0.3}}]}','{"steps":[{"kind":"unknown","mark":{}}]}','{"steps":[null]}','{"steps":[{"kind":"rotate","mark":{}}]}','{"marks":[{}]}','{"steps":"bad"}','{"rotation":"90"}','{"steps":[{"kind":"crop","mark":{"x":0,"y":0,"endX":0,"endY":1}}]}','{"marks":[{"kind":"pen","x":0.1,"y":0.1,"points":[0.1]}]}'];
    for(const json of invalid.concat(RedBlueRepairChecks.missingContainerRecipes())){let rejected=false;try{loadEditableRecipe(json,true);}catch(e){rejected=true;}check(rejected,'损坏记录被默认化：'+json);}
    let absent=false;try{loadEditableRecipe('',true);}catch(e){absent=true;}check(absent,'已有成品缺失矢量仍可进入保存');
    check(loadEditableRecipe('{"marks":[]}',true).steps.length===0,'显式旧marks空容器不兼容');
    const empty=loadEditableRecipe('{"steps":[]}',true);check(empty.steps.length===0,'显式空记录不兼容');check(loadEditableRecipe(JSON.stringify(new EditRecipe()),true).steps.length===0,'真实writer的空记录不兼容');
    const legacy=new EditRecipe();legacy.rotation=90;legacy.flipX=true;legacy.cropW=.8;const mark=new Mark();mark.kind='text';mark.text='原有文字';legacy.marks=[mark];const json=JSON.stringify(legacy);const loaded=loadEditableRecipe(json,true);check(loaded.steps.map((s:EditStep)=>s.kind).join(',')==='flipX,rotate,crop,text','旧格式顺序改变');check(JSON.stringify(legacy)===json,'加载修改了原记录');
    const modern=new EditRecipe();const text=new EditStep('text');text.mark.kind='text';text.mark.text='保留全文';modern.steps=[text,new EditStep('rotate')];check(loadEditableRecipe(JSON.stringify(modern),true).steps[0].mark.text==='保留全文','合法新格式不兼容');
    const clippedText=loadEditableRecipe('{"steps":[{"kind":"text","mark":{"kind":"text","x":-2.5,"y":1.2,"text":"历史超长文字"}}]}',true);check(clippedText.steps[0].mark.x===-2.5,'历史文字基线被夹回或拒绝');
    const transformOnly=loadEditableRecipe('{"steps":[{"kind":"rotate"},{"kind":"flipY"}]}',true);check(transformOnly.steps.length===2,'无mark的合法变换步骤不兼容');
  }
  static async damagedRecordPreserved(ctx:common.Context):Promise<void>{
    const namespace='damaged_recipe_'+Date.now(),data=new Store(namespace),edited=ctx.filesDir+'/'+namespace+'.jpg',content='existing-edited-file';
    await data.open(ctx);
    try{const file=await fs.open(edited,fs.OpenMode.CREATE|fs.OpenMode.WRITE_ONLY);try{await fs.write(file.fd,content);}finally{await fs.close(file);}
      const project=new Project();project.name='损坏记录回归';await data.putProject(project);const media=new MediaItem();media.projectId=project.id;media.path=ctx.filesDir+'/missing-original.jpg';media.edited=edited;await data.putMedia(media);
      for(const json of ['{}','{"garbage":true}','{"steps":[{}]}','{"rotation":0}','{"cropX":0}','{"flipX":false}','{"rotation":0,"flipX":false,"flipY":false,"cropX":0,"cropY":0,"cropW":1,"cropH":1}']){const annotation=new Annotation();annotation.mediaId=media.id;annotation.annotatedContentUri=edited;annotation.vectorDataJson=json;await data.putAnnotation(annotation);const stored=(await data.annotations(media.id))[0];let blocked=false;try{loadEditableRecipe(stored.vectorDataJson,true);}catch(e){blocked=true;}check(blocked,'损坏记录未阻断编辑');check((await data.annotations(media.id))[0].vectorDataJson===json,'拒绝编辑后原JSON变化');check((await data.mediaById(media.id))?.edited===edited,'拒绝编辑后成品引用变化');check(fs.readTextSync(edited)===content,'拒绝编辑后旧成品被删除或覆盖');}
    }finally{await data.close();if(fs.accessSync(edited))await fs.unlink(edited);await relationalStore.deleteRdbStore(ctx,namespace+'.db');await preferences.deletePreferences(ctx,namespace+'_settings');}
  }
  static async nativeTextHistory():Promise<void>{
    const recipe=new EditRecipe(),text=new EditStep('text');text.mark.kind='text';text.mark.x=.2;text.mark.y=.5;text.mark.text='超长文字'.repeat(50);recipe.steps=[text,new EditStep('flipX')];
    moveTextStep(recipe,0,0,0,1000,600);check(recipe.steps[0].mark.x<0,'未复现镜像超长文字原始基线出界');const json=JSON.stringify(recipe);const reopened=loadEditableRecipe(json,true);check(reopened.steps[0].mark.x===recipe.steps[0].mark.x,'应用自身保存的裁切文字被拒绝或夹回');
  }
  static productionLayout():void{
    for(const size of [[560,320],[600,360],[640,360],[844,390]]){const foldable=false,phone=ordinaryPhoneGrip(foldable,'phone',size[0],size[1]);const layout=cameraLayout(size[0],size[1],4/3,false,-1,0,false,1,[],1,phone,0,!foldable);check(layout.immersivePhoneLandscape,'手机偏离已采用沉浸结构');check(size[0]-layout.controlsX-layout.controlsWidth>=48-.001,'生产实参手机未留48vp握持区');check(Math.abs(layout.previewWidth/layout.previewHeight-4/3)<.00001,'手机取景比例变化');}
    for(const width of [559,560,561]){const phone=ordinaryPhoneGrip(false,'phone',width,320);check(phone===(width>=560),'普通手机长边阈值变化');const layout=cameraLayout(width,320,4/3,false,-1,0,false,1,[],1,phone,0,true);if(phone)check(width-layout.controlsX-layout.controlsWidth>=48-.001,'560阈值握持区不足48vp');}
    const tablet=cameraLayout(1280,800,4/3,false,-1,0,false,1,[],1,false,0,true);const expectedPreview=Math.min(1280-56-184,800*4/3);check(Math.abs(tablet.previewWidth-expectedPreview)<.001,'平板被添加手机握持预算');
    const fold=cameraLayout(844,390,4/3,false,-1,0,false,1,[],1,false,0,false);check(!fold.immersivePhoneLandscape,'折叠布局被切换到普通手机分支');
  }
  static videoClassification():void{
    check(missingVideoCodec('没有可用 H.264 编解码器'),'明确缺codec未标记环境限制');
    for(const error of ['H.264 codec-data 未包含完整 SPS/PPS','H.264 codec-data 既不是 avcC 也不是 Annex-B','编码帧既不是 AVCC 也不是 Annex-B','编码输出格式缺少 H.264 SPS/PPS','编解码器输出错误','没有可用 H.264 编解码器：SPS/PPS损坏'])check(!missingVideoCodec(error),'真实视频故障被跳过：'+error);
  }
}
