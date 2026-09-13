/** The fixed inset keeps the project chooser away from the window edges. */
export const PROJECT_PANEL_SAFE_MARGIN: number = 8;
export const PROJECT_PANEL_MAX_WIDTH: number = 600;
export const PROJECT_PANEL_MAX_HEIGHT: number = 520;

/** Window flags supplied by the camera page's current layout profile. */
export interface ProjectPanelGeometryRequest {
  windowWidth: number;
  windowHeight: number;
  phonePortrait: boolean;
  wideSidebar: boolean;
  smallOuterScreen: boolean;
  /** Width reserved for the leading sidebar in a wide layout. */
  sidebarWidth: number;
}

/** Window-local bounds for the project chooser panel. */
export interface ProjectPanelGeometry {
  x: number;
  y: number;
  width: number;
  height: number;
}

function nonNegativeFinite(value: number): number {
  return Number.isFinite(value) && value > 0 ? value : 0;
}

function safeMargin(width: number, height: number): number {
  return Math.min(PROJECT_PANEL_SAFE_MARGIN, width / 2, height / 2);
}

function clamp(value: number, minimum: number, maximum: number): number {
  return Math.min(maximum, Math.max(minimum, value));
}

/**
 * Select window-local bounds for the project switch panel.
 *
 * The returned rectangle always stays in the measured window.  A portrait
 * phone anchors the panel to the bottom safe edge, a wide layout reserves the
 * leading sidebar and places the panel beside it, and a small cover screen
 * consumes the available compact window.  Dimensions are never expanded to
 * a desktop-sized minimum, so narrow windows remain valid too.
 */
export function projectPanelGeometry(request: ProjectPanelGeometryRequest): ProjectPanelGeometry {
  const windowWidth = nonNegativeFinite(request.windowWidth);
  const windowHeight = nonNegativeFinite(request.windowHeight);
  const margin = safeMargin(windowWidth, windowHeight);
  const availableWidth = Math.max(0, windowWidth - margin * 2);
  const availableHeight = Math.max(0, windowHeight - margin * 2);
  const sidebar = clamp(nonNegativeFinite(request.sidebarWidth), 0, windowWidth);
  const readableSideWidth = Math.min(240, availableWidth);
  const sideSpace = Math.max(0, availableWidth - sidebar);

  let width = Math.min(PROJECT_PANEL_MAX_WIDTH, availableWidth);
  const canFitBesideSidebar = request.wideSidebar && !request.smallOuterScreen && sideSpace >= readableSideWidth;
  if (canFitBesideSidebar) {
    width = Math.min(width, Math.max(0, availableWidth - sidebar));
  }
  const height = Math.min(PROJECT_PANEL_MAX_HEIGHT, availableHeight);

  let x = (windowWidth - width) / 2;
  let y = (windowHeight - height) / 2;
  if (request.smallOuterScreen) {
    x = margin;
    y = margin;
  } else if (request.phonePortrait) {
    x = (windowWidth - width) / 2;
    y = windowHeight - margin - height;
  } else if (request.wideSidebar) {
    const maximumX = Math.max(margin, windowWidth - margin - width);
    x = canFitBesideSidebar ? clamp(sidebar + margin, margin, maximumX) : (windowWidth - width) / 2;
    y = margin;
  }

  return {
    x: clamp(x, 0, Math.max(0, windowWidth - width)),
    y: clamp(y, 0, Math.max(0, windowHeight - height)),
    width,
    height
  };
}
