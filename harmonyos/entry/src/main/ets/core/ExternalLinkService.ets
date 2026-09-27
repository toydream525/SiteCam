import { common } from '@kit.AbilityKit';

/** Online variant's explicit external-page boundary. Offline builds replace this file with a local-only stub. */
export async function openExternalLink(context: common.UIAbilityContext, url: string): Promise<void> {
  await context.startAbility({ uri: url, action: 'ohos.want.action.viewData' });
}
