import { common } from '@kit.AbilityKit';

/** Offline variant intentionally has no external activity or network path. */
export async function openExternalLink(context: common.UIAbilityContext, url: string): Promise<void> {
  throw new Error('纯离线版不支持打开外部网页');
}
