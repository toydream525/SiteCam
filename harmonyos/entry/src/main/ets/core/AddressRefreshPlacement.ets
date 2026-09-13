/** A preview-local rectangle. The origin is the top-left of the camera preview. */
export interface AddressRefreshRect {
  x: number;
  y: number;
  width: number;
  height: number;
}

export type AddressRefreshCorner = 'TOP_START' | 'TOP_END' | 'BOTTOM_START' | 'BOTTOM_END';

/** The measured, preview-local placement returned to the camera UI. */
export interface AddressRefreshPlacement {
  corner: AddressRefreshCorner;
  x: number;
  y: number;
  width: number;
  height: number;
}

/**
 * Inputs use the same local coordinate space as the preview.  `safeSpacing`
 * is both the preview edge inset and the clear gap around the watermark.
 */
export interface AddressRefreshPlacementRequest {
  previewWidth: number;
  previewHeight: number;
  buttonWidth: number;
  buttonHeight: number;
  watermarkRect: AddressRefreshRect | null;
  safeSpacing: number;
}

function finitePositive(value: number): boolean {
  return Number.isFinite(value) && value > 0;
}

function validRect(rect: AddressRefreshRect): boolean {
  return Number.isFinite(rect.x) && Number.isFinite(rect.y) &&
    Number.isFinite(rect.width) && Number.isFinite(rect.height) &&
    rect.width >= 0 && rect.height >= 0;
}

function overlaps(first: AddressRefreshPlacement, second: AddressRefreshRect): boolean {
  return first.x < second.x + second.width && first.x + first.width > second.x &&
    first.y < second.y + second.height && first.y + first.height > second.y;
}

function protectedWatermark(rect: AddressRefreshRect | null, gap: number): AddressRefreshRect | null {
  if (rect === null) return null;
  if (!validRect(rect)) return null;
  if (rect.width === 0 || rect.height === 0) return null;
  return {x: rect.x - gap, y: rect.y - gap, width: rect.width + gap * 2, height: rect.height + gap * 2};
}

/**
 * Choose a real in-preview corner for the measured refresh control.
 *
 * Candidates are tried in visual order from the top-right corner, then the
 * top-left and the two bottom corners.  A position is returned only when the
 * full measured control fits inside the preview and clears the protected
 * watermark rectangle.  There is deliberately no overlap-minimizing or
 * outside-preview fallback: null tells the caller to use its secondary action.
 */
export function chooseAddressRefreshPlacement(request: AddressRefreshPlacementRequest): AddressRefreshPlacement | null {
  if (!finitePositive(request.previewWidth) || !finitePositive(request.previewHeight) ||
    !finitePositive(request.buttonWidth) || !finitePositive(request.buttonHeight) ||
    !Number.isFinite(request.safeSpacing) || request.safeSpacing < 0 ||
    (request.watermarkRect !== null && !validRect(request.watermarkRect))) return null;

  const spacing = request.safeSpacing;
  const maxX = request.previewWidth - spacing - request.buttonWidth;
  const maxY = request.previewHeight - spacing - request.buttonHeight;
  if (maxX < spacing || maxY < spacing) return null;

  const candidates: AddressRefreshPlacement[] = [
    {corner: 'TOP_END', x: maxX, y: spacing, width: request.buttonWidth, height: request.buttonHeight},
    {corner: 'TOP_START', x: spacing, y: spacing, width: request.buttonWidth, height: request.buttonHeight},
    {corner: 'BOTTOM_END', x: maxX, y: maxY, width: request.buttonWidth, height: request.buttonHeight},
    {corner: 'BOTTOM_START', x: spacing, y: maxY, width: request.buttonWidth, height: request.buttonHeight}
  ];
  const protectedRect = protectedWatermark(request.watermarkRect, spacing);
  for (const candidate of candidates) {
    const inside = candidate.x >= 0 && candidate.y >= 0 &&
      candidate.x + candidate.width <= request.previewWidth &&
      candidate.y + candidate.height <= request.previewHeight;
    if (inside && (protectedRect === null || !overlaps(candidate, protectedRect))) return candidate;
  }
  return null;
}
