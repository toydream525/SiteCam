import {
  ProjectPanelGeometry,
  projectPanelGeometry
} from '../../../main/ets/core/ProjectPanelGeometry';

function requireResult(value: boolean, message: string): void {
  if (!value) throw new Error(message);
}

function inside(geometry: ProjectPanelGeometry, width: number, height: number): boolean {
  return geometry.x >= 0 && geometry.y >= 0 && geometry.width >= 0 && geometry.height >= 0 &&
    geometry.x + geometry.width <= width && geometry.y + geometry.height <= height;
}

/** Pure window geometry checks for the project switch panel. */
export class ProjectPanelGeometryChecks {
  static check(): void {
    const phone = projectPanelGeometry({
      windowWidth: 320,
      windowHeight: 640,
      phonePortrait: true,
      wideSidebar: false,
      smallOuterScreen: false,
      sidebarWidth: 0
    });
    requireResult(inside(phone, 320, 640), '普通手机竖屏面板越出窗口');
    requireResult(phone.y + phone.height === 632, '普通手机竖屏面板未贴底部安全边距');

    const wide = projectPanelGeometry({
      windowWidth: 736,
      windowHeight: 320,
      phonePortrait: false,
      wideSidebar: true,
      smallOuterScreen: false,
      sidebarWidth: 160
    });
    requireResult(inside(wide, 736, 320), '宽屏面板越出窗口');
    requireResult(wide.x >= 168, '宽屏面板未靠近侧栏');

    const wideSmallSidebar = projectPanelGeometry({
      windowWidth: 736,
      windowHeight: 320,
      phonePortrait: false,
      wideSidebar: true,
      smallOuterScreen: false,
      sidebarWidth: 88
    });
    requireResult(inside(wideSmallSidebar, 736, 320), '正常侧栏宽屏面板越出窗口');
    requireResult(wideSmallSidebar.x === 96 && wideSmallSidebar.width === 600, '正常侧栏宽屏未保留侧栏空间');

    const outer = projectPanelGeometry({
      windowWidth: 180,
      windowHeight: 180,
      phonePortrait: false,
      wideSidebar: false,
      smallOuterScreen: true,
      sidebarWidth: 80
    });
    requireResult(inside(outer, 180, 180), '小外屏面板越出窗口');
    requireResult(outer.width === 164 && outer.height === 164, '小外屏未使用紧凑可用尺寸');

    const tinyOuter = projectPanelGeometry({
      windowWidth: 96,
      windowHeight: 96,
      phonePortrait: false,
      wideSidebar: false,
      smallOuterScreen: true,
      sidebarWidth: 56
    });
    requireResult(inside(tinyOuter, 96, 96), '96x96 外屏面板越出窗口');
    requireResult(tinyOuter.width === 80 && tinyOuter.height === 80, '96x96 外屏未压缩到可用窗口');

    const narrow = projectPanelGeometry({
      windowWidth: 120,
      windowHeight: 72,
      phonePortrait: true,
      wideSidebar: false,
      smallOuterScreen: false,
      sidebarWidth: 0
    });
    requireResult(inside(narrow, 120, 72), '超窄窗口面板越界');

    const narrowWide = projectPanelGeometry({
      windowWidth: 96,
      windowHeight: 96,
      phonePortrait: false,
      wideSidebar: true,
      smallOuterScreen: false,
      sidebarWidth: 96
    });
    requireResult(inside(narrowWide, 96, 96), '超窄宽屏面板越界');
    requireResult(narrowWide.width > 0, '侧栏占满窗口时面板退化为零宽');

    const narrowWideWithSidebar = projectPanelGeometry({
      windowWidth: 96,
      windowHeight: 96,
      phonePortrait: false,
      wideSidebar: true,
      smallOuterScreen: false,
      sidebarWidth: 72
    });
    requireResult(inside(narrowWideWithSidebar, 96, 96), '超窄部分侧栏面板越界');
    requireResult(narrowWideWithSidebar.width > 0 && narrowWideWithSidebar.x === 8, '超窄部分侧栏未回退窗口内模态宽度');
  }
}
