export class CameraLayout {
  previewX: number = 0;
  previewY: number = 56;
  previewWidth: number = 1;
  previewHeight: number = 1;
  controlsX: number = 0;
  controlsY: number = 0;
  controlsWidth: number = 1;
  controlsHeight: number = 1;
  side: boolean = false;
  hover: boolean = false;
}

export function cameraLayout(width: number, height: number, ratio: number,
  halfFolded: boolean = false, creaseTop: number = -1, creaseHeight: number = 0): CameraLayout {
  const out = new CameraLayout();
  const w = Math.max(1, width), h = Math.max(1, height);
  const center = creaseTop + creaseHeight / 2;
  out.hover = halfFolded && creaseTop >= 0 && center > 112 && center + 40 < h - 96;
  out.side = !out.hover && (w >= 840 || w > h || (w >= 600 && w / h >= .85));
  let availableW = w, availableH = h - 56;
  if (out.hover) {
    availableH = Math.max(1, Math.min(creaseTop, center - 16) - 56);
    out.controlsY = Math.max(creaseTop + creaseHeight, center + 40);
    out.controlsWidth = w; out.controlsHeight = h - out.controlsY;
  } else if (out.side) {
    out.controlsWidth = Math.min(260, Math.max(160, w * .26));
    out.controlsX = w - out.controlsWidth; out.controlsY = 56;
    out.controlsHeight = h - 56; availableW = out.controlsX;
  } else {
    out.controlsHeight = Math.min(184, Math.max(136, h * .28));
    out.controlsY = h - out.controlsHeight; out.controlsWidth = w;
    availableH = out.controlsY - 56;
  }
  out.previewWidth = Math.max(1, Math.min(availableW, Math.max(1, availableH) * ratio));
  out.previewHeight = out.previewWidth / ratio;
  out.previewX = (availableW - out.previewWidth) / 2;
  out.previewY = 56 + (Math.max(1, availableH) - out.previewHeight) / 2;
  return out;
}

/** Keep the last valid direction when the device is upside down. */
export function captureDegrees(mode: string, displayDegrees: number, last: number): number {
  if (mode === 'PORTRAIT') return 0;
  if (mode === 'LEFT') return 90;
  if (mode === 'RIGHT') return 270;
  return displayDegrees === 180 ? last : displayDegrees;
}
