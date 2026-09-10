/** Shared presentation state for the native ArkUI pages.
 *
 * This file deliberately contains no persistence or service calls.  Index owns
 * the state and passes small, explicit values to the page components.
 */
export enum SitePage {
  Projects = 'projects',
  ProjectEdit = 'projectEdit',
  Camera = 'camera',
  Gallery = 'gallery',
  Detail = 'detail',
  Editor = 'editor',
  Watermark = 'watermark',
  Settings = 'settings',
  Guide = 'guide',
  Permissions = 'permissions',
  Export = 'export',
  Help = 'help',
  Category = 'category',
  Move = 'move'
}

export enum ProjectSort {
  Recent = 0,
  Created = 1,
  Updated = 2,
  Name = 3,
  Route = 4
}

export interface GalleryFilterState {
  year: number;
  month: number;
  from: string;
  to: string;
  category: string;
  projectId: number;
  issue: number;
  severity: number;
}

export const PROJECT_SORT_LABELS: string[] = ['最近拍摄', '创建时间', '更新时间', '工程名', '线路'];
export const ISSUE_STATUS_LABELS: string[] = ['全部问题状态', '全部问题', '待处理', '处理中', '已完成'];
export const SEVERITY_LABELS: string[] = ['全部等级', '一般', '重要', '严重'];
