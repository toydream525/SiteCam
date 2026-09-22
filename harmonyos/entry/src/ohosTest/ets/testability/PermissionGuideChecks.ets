import { PermissionStatus, permissionGuideAction, permissionGuideLabel } from '../../../main/ets/pages/components/PermissionGuidePageView';

function requireResult(value: boolean, message: string): void {
  if (!value) throw new Error(message);
}

function row(id: string, granted: boolean, needsSettings: boolean, optional: boolean = false): PermissionStatus {
  const value: PermissionStatus = { id: id, title: id, description: '', granted: granted, needsSettings: needsSettings, optional: optional };
  return value;
}

/**
 * Regression lock for the AppGallery 3.1 rejection: the app must never send a
 * user to the system settings page before it has asked for a permission at
 * least once, because that page has no switch to show yet.
 */
export class PermissionGuideChecks {
  static check(): void {
    const fresh: PermissionStatus[] = [row('camera', false, false), row('location', false, false, true), row('microphone', false, false, true)];
    requireResult(!fresh.some((v: PermissionStatus) => v.needsSettings), '未请求过权限时不应标记为需要系统设置');
    requireResult(permissionGuideAction(fresh, false, false) === 'request', '未请求过权限时仍会跳转系统设置');
    requireResult(permissionGuideLabel(permissionGuideAction(fresh, false, false), false) === '开启所需权限', '未请求过权限时按钮文案错误');

    const granted: PermissionStatus[] = [row('camera', true, false), row('location', true, false, true), row('microphone', false, false, true)];
    requireResult(permissionGuideAction(granted, false, false) === 'enter', '相机已授权时未提供进入相机');
    requireResult(permissionGuideLabel('enter', false) === '进入相机', '进入相机文案错误');

    const denied: PermissionStatus[] = [row('camera', false, true), row('location', false, true, true), row('microphone', false, true, true)];
    requireResult(permissionGuideAction(denied, true, false) === 'settings', '已请求且被拒后未提供系统设置入口');
    requireResult(permissionGuideLabel('settings', false) === '去系统设置开启', '系统设置入口文案错误');

    const requestable: PermissionStatus[] = [row('camera', false, false), row('location', false, true, true)];
    requireResult(permissionGuideAction(requestable, true, false) === 'request', '仍有可申请权限时未继续申请');

    requireResult(permissionGuideAction(fresh, false, true) === 'request', '请求进行中状态错误');
    requireResult(permissionGuideLabel('request', true) === '请完成系统权限询问…', '请求进行中文案错误');
  }
}
