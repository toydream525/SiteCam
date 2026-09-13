import { AddressRefreshPlacement, AddressRefreshRect, chooseAddressRefreshPlacement } from '../../../main/ets/core/AddressRefreshPlacement';

function requireResult(value: boolean, message: string): void {
  if (!value) throw new Error(message);
}

function intersects(first: AddressRefreshPlacement, second: AddressRefreshRect): boolean {
  return first.x < second.x + second.width && first.x + first.width > second.x &&
    first.y < second.y + second.height && first.y + first.height > second.y;
}

function inside(placement: AddressRefreshPlacement, width: number, height: number): boolean {
  return placement.x >= 0 && placement.y >= 0 &&
    placement.x + placement.width <= width && placement.y + placement.height <= height;
}

/** Pure geometry checks for the in-preview address refresh action. */
export class AddressRefreshPlacementChecks {
  static check(): void {
    const previewWidth = 1080;
    const previewHeight = 1440;
    const buttonWidth = 180;
    const buttonHeight = 56;
    const spacing = 12;
    const bottomWatermark: AddressRefreshRect = {x: 24, y: 1180, width: 720, height: 236};
    const clear = chooseAddressRefreshPlacement({previewWidth, previewHeight, buttonWidth, buttonHeight, watermarkRect: bottomWatermark, safeSpacing: spacing});
    requireResult(clear !== null && clear.corner === 'TOP_END', '普通水印未选择右上角');
    requireResult(inside(clear!, previewWidth, previewHeight), '普通水印按钮越出预览');
    requireResult(!intersects(clear!, {x: bottomWatermark.x - spacing, y: bottomWatermark.y - spacing, width: bottomWatermark.width + spacing * 2, height: bottomWatermark.height + spacing * 2}), '普通水印仍与按钮重叠');

    const topRightWatermark: AddressRefreshRect = {x: 840, y: 0, width: 240, height: 180};
    const boundary = chooseAddressRefreshPlacement({previewWidth, previewHeight, buttonWidth, buttonHeight, watermarkRect: topRightWatermark, safeSpacing: spacing});
    requireResult(boundary !== null && boundary.corner === 'TOP_START', '边界水印未避让右上角');
    requireResult(inside(boundary!, previewWidth, previewHeight), '边界水印按钮越出预览');

    const none = chooseAddressRefreshPlacement({previewWidth: 320, previewHeight: 240, buttonWidth: 96, buttonHeight: 40, watermarkRect: null, safeSpacing: 8});
    requireResult(none !== null && none.corner === 'TOP_END' && inside(none!, 320, 240), '无水印未返回安全角落');

    const fullyCovered = chooseAddressRefreshPlacement({previewWidth: 500, previewHeight: 500, buttonWidth: 180, buttonHeight: 60, watermarkRect: {x: 0, y: 0, width: 500, height: 500}, safeSpacing: 8});
    requireResult(fullyCovered === null, '完整遮挡仍返回重叠位置');

    const oversized = chooseAddressRefreshPlacement({previewWidth: 120, previewHeight: 80, buttonWidth: 128, buttonHeight: 72, watermarkRect: null, safeSpacing: 8});
    requireResult(oversized === null, '超大控件被压缩或放到预览外');

    const tiny = chooseAddressRefreshPlacement({previewWidth: 1, previewHeight: 1, buttonWidth: 1, buttonHeight: 1, watermarkRect: null, safeSpacing: 1});
    requireResult(tiny === null, '极小预览返回了窗外控件');

    const invalid = chooseAddressRefreshPlacement({previewWidth: 320, previewHeight: 240, buttonWidth: 96, buttonHeight: 40, watermarkRect: null, safeSpacing: -1});
    requireResult(invalid === null, '负安全间距未被拒绝');
  }
}
