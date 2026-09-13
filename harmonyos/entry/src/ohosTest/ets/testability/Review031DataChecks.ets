import { common } from '@kit.AbilityKit';
import { Issue, MediaItem, Project } from '../../../main/ets/core/Models';
import { Store } from '../../../main/ets/core/Store';

function requireResult(value:boolean,message:string):void {
  if(!value)throw new Error(message);
}

/** Independent regression checks: each fixture uses a separate database. */
export class Review031DataChecks {
  static async preservesMovedMediaAndNewIssue(context:common.Context):Promise<void> {
    const db=new Store('review031_move_'+Date.now());
    await db.open(context);
    try {
      const source=new Project();source.name='审核来源';await db.putProject(source);
      const target=new Project();target.name='审核目标';await db.putProject(target);
      const media=new MediaItem();media.projectId=source.id;media.type='VIDEO';
      media.path='/review/original.mp4';media.source=media.path;
      media.snapshot=JSON.stringify({projectId:source.id,project:source.name});
      media.issueTitle='拍摄时问题';media.status='FAILED';await db.putMedia(media);

      // Processing began before the media was moved and its issue was edited.
      await db.updateMediaStatus(media.id,'PROCESSING');
      await db.moveMedia([media.id],target.id);
      const issue=new Issue();issue.mediaId=media.id;issue.projectId=source.id;
      issue.title='移动后更新';issue.description='保留最新说明';issue.status='COMPLETED';
      await db.putIssue(issue);
      await db.updateMediaProcessingResult(media.id,'/review/output.mp4','output.mp4',640,480,2000,0,'READY');
      await db.updateMediaAlbumUri(media.id,'review://album-copy');

      const saved=await db.mediaById(media.id);
      requireResult(saved!==null,'处理后媒体丢失');
      requireResult(saved!.projectId===target.id,'处理或另存覆盖了移动后的工程');
      requireResult(saved!.issueTitle==='移动后更新'&&saved!.issueNotes==='保留最新说明'&&saved!.issueStatus==='COMPLETED','处理或另存覆盖了最新问题');
      requireResult(saved!.source==='/review/original.mp4'&&saved!.snapshot===media.snapshot,'处理改写了原片或历史拍摄快照');
      requireResult(saved!.path==='/review/output.mp4'&&saved!.albumUri==='review://album-copy','处理结果或相册地址未保存');
      const related=(await db.issues()).filter(v=>v.mediaId===media.id);
      requireResult(related.length===1&&related[0].projectId===target.id&&related[0].title==='移动后更新','问题关联回到了旧工程或重复生成');

      // Failed/cancelled processing must not restore a stale whole-row copy.
      await db.updateMediaStatus(media.id,'FAILED');
      const failed=await db.mediaById(media.id);
      requireResult(failed!==null&&failed!.projectId===target.id&&failed!.issueTitle==='移动后更新'&&failed!.path==='/review/output.mp4','失败状态写入破坏了当前资料');
    } finally {await db.close();}
  }

  static async deletedTargetsAreRejected(context:common.Context):Promise<void> {
    const db=new Store('review031_deleted_'+Date.now());
    await db.open(context);
    try {
      const project=new Project();project.name='审核删除';await db.putProject(project);
      const media=new MediaItem();media.projectId=project.id;media.issueTitle='待处理';await db.putMedia(media);
      await db.deleteMedia(media.id);
      let statusRejected=false,outputRejected=false,albumRejected=false,issueRejected=false;
      try {await db.updateMediaStatus(media.id,'FAILED');}catch(_){statusRejected=true;}
      try {await db.updateMediaProcessingResult(media.id,'/review/output.mp4','output.mp4',640,480,2000,0,'READY');}catch(_){outputRejected=true;}
      try {await db.updateMediaAlbumUri(media.id,'review://album-copy');}catch(_){albumRejected=true;}
      const issue=new Issue();issue.mediaId=media.id;issue.projectId=project.id;issue.title='迟到的问题写入';
      try {await db.putIssue(issue);}catch(_){issueRejected=true;}
      requireResult(statusRejected&&outputRejected&&albumRejected&&issueRejected,'已删除的媒体仍被当作写入成功');
      requireResult(await db.mediaById(media.id)===null,'已删除媒体被重新创建');
      requireResult(!(await db.issues()).some(v=>v.mediaId===media.id),'删除后生成了孤立问题');
    } finally {await db.close();}
  }
}
