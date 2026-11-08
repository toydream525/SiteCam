#!/usr/bin/env node
// Runs production ETS data/layout code. Native SDK access throws rather than being simulated.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const ts = require(process.env.TYPESCRIPT_PATH || '/Applications/DevEco-Studio.app/Contents/tools/hvigor/hvigor/node_modules/typescript/lib/typescript.js');
const root = path.resolve(__dirname, '../entry/src/main/ets/core');
const cache = new Map();
const allowed = new Set(['Models', 'BuildVariant', 'WatermarkRenderer', 'CameraLayout', 'WindowGeometry', 'DeviceLayoutProfile', 'ImageProcessor']);
function load(name) {
  if (cache.has(name)) return cache.get(name).exports;
  const module = { exports: {} }; cache.set(name, module);
  const file = path.join(root, name + '.ets');
  const compiled = ts.transpileModule(fs.readFileSync(file, 'utf8'), {
    compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2021 }, reportDiagnostics: true
  });
  assert.deepEqual(compiled.diagnostics.filter(d => d.category === ts.DiagnosticCategory.Error), [], name + ' syntax');
  const req = dependency => {
    const next = path.basename(dependency);
    if (dependency.startsWith('.') && allowed.has(next)) return load(next);
    return new Proxy({}, { get() { throw new Error('Native dependency accessed: ' + dependency); } });
  };
  vm.runInNewContext('(function(require,module,exports){' + compiled.outputText + '\n})', {}, { filename: file })(req, module, module.exports);
  return module.exports;
}
const models = load('Models');
const settings = new models.Settings();
assert.equal(settings.snapshotEnabled, true);
assert.equal(settings.snapshotShutterSpeed, 250);
assert.equal(settings.albumOriginal, false);
const old = new models.Watermark();
old.fields = [new models.Field('custom_note', '备注', '保留'), new models.Field('project', '自定义工程标题', '现场工程')];
old.fields[1].enabled = false;
const migrated = models.migrateWatermarkFields(old);
const again = models.migrateWatermarkFields(migrated);
assert.equal(again.fields.filter(f => f.key === 'weather').length, 1);
assert.equal(again.fields[0].key, 'custom_note');
assert.equal(again.fields[0].value, '保留');
assert.equal(again.fields[1].label, '自定义工程标题');
assert.equal(again.fields[1].enabled, false);
assert.equal(old.fields.length, 2);
const snapshot = models.captureSnapshot(new models.Project(), settings, null);
const renderer = load('WatermarkRenderer');
assert.equal(renderer.resolveFields(snapshot).some(f => f.key === 'weather'), false);
snapshot.watermark.fields.find(f => f.key === 'weather').value = '小雨 16℃';
assert.equal(renderer.resolveFields(snapshot).find(f => f.key === 'weather').value, '小雨 16℃');
const frozen = JSON.parse(JSON.stringify(snapshot));
settings.watermark.fields.find(f => f.key === 'weather').value = '晴 20℃';
assert.equal(renderer.resolveFields(frozen).find(f => f.key === 'weather').value, '小雨 16℃');
const layout = load('CameraLayout');
// Screen rotation must be converted to the opposite device angle before PhotoOutput.
for (const [screen, device] of [[0, 0], [90, 270], [180, 180], [270, 90]]) {
  assert.equal(layout.photoDeviceDegrees(screen), device, 'photo angle for display ' + screen);
}
assert.equal(layout.photoDeviceDegrees(layout.captureDegrees('LEFT', 0, 0)), 270);
assert.equal(layout.photoDeviceDegrees(layout.captureDegrees('RIGHT', 0, 0)), 90);
assert.equal(layout.photoDeviceDegrees(layout.captureDegrees('PORTRAIT', 270, 270)), 0);
for (const width of [280, 320, 360, 400, 600]) {
  const geometry = layout.cameraLayout(width, 900, 3 / 4);
  assert.ok(Math.abs(geometry.previewWidth / geometry.previewHeight - 3 / 4) < .001);
  if (width < 368) assert.ok(geometry.toolbarHeight >= 104, 'four tools and project chip need two rows');
}
// The tablet dock must use its measured content height, not the old 184vp cap.
const tabletPortrait = layout.cameraLayout(674, 1044, 3 / 4, false, -1, 0, false, 1, [], 1, false, 248);
assert.ok(tabletPortrait.controlsHeight >= 248, 'tablet bottom controls need their complete measured height');
assert.ok(tabletPortrait.previewY + tabletPortrait.previewHeight <= tabletPortrait.controlsY + .001);
assert.equal(tabletPortrait.controlsY + tabletPortrait.controlsHeight, 1044);
assert.equal(layout.portraitControlsShareModeRow(320, 1), false, 'narrow portrait modes need their own row');
assert.equal(layout.portraitControlsShareModeRow(360, 1), true);
const landscapeTablet = layout.cameraLayout(1078, 674, 4 / 3, false, -1, 0, false, 1, [], 1, false, 0, true);
assert.equal(landscapeTablet.toolbarY, landscapeTablet.previewY);
assert.equal(landscapeTablet.toolbarHeight, landscapeTablet.previewHeight);
assert.equal(landscapeTablet.controlsY, landscapeTablet.previewY);
assert.equal(landscapeTablet.controlsHeight, landscapeTablet.previewHeight);
for (const [w, h, ratio] of [[752, 672, 4/3], [672, 752, 3/4]]) {
  const crease = new (load('WindowGeometry').WindowRect)(0, h/2-4, w, 8);
  const expanded = layout.cameraLayout(w, h, ratio, false, -1, 0, false, 1, [crease], 1, false, 0, false, true);
  assert.equal(expanded.side, true);
  assert.equal(expanded.hover, false);
  assert.equal(expanded.controlsWidth, 672*.34);
  assert.ok(expanded.previewX + expanded.previewWidth <= expanded.controlsX + .001);
  const hovering = layout.cameraLayout(w, h, ratio, false, -1, 0, false, 1, [crease]);
  assert.equal(hovering.hover, true);
  assert.equal(hovering.side, false);
}
assert.ok(models.QUALITY_EDGES[2] >= 1920);
assert.ok(models.QUALITY_JPEG[2] >= 85);
const exportTime = new Date(2026, 9, 31, 23, 59).getTime();
const project = new models.Project(); project.name = '建筑/工程';
assert.equal(models.exportZipName([project], exportTime), '建筑_工程_2026-10-31.zip');
assert.equal(models.exportZipName([project, new models.Project()], exportTime), '建筑_工程等2个工程_2026-10-31.zip');
settings.watermark.fontId = 'handwriting.ttf'; settings.watermark.textColor = '#1262AE'; settings.watermark.style = 'WHITEBOARD';
const styled = models.captureSnapshot(project, settings, null);
settings.watermark.fontId = ''; settings.watermark.textColor = '';
assert.equal(styled.watermark.fontId, 'handwriting.ttf'); assert.equal(styled.watermark.textColor, '#1262AE');
assert.equal(models.STYLES.filter(style => style.id === 'WHITEBOARD').length, 1);
const edits = load('ImageProcessor');
const recipe = new edits.EditRecipe(); const step = new edits.EditStep('text');
step.mark.kind = 'text'; step.mark.text = '工程 AB123'; step.mark.fontId = 'handwriting.ttf'; step.mark.color = '#1262AE'; step.mark.whiteboard = true;
recipe.steps.push(step);
const reopened = edits.loadEditableRecipe(edits.serializeEditRecipe(recipe));
assert.equal(reopened.steps[0].mark.fontId, 'handwriting.ttf'); assert.equal(reopened.steps[0].mark.color, '#1262AE'); assert.equal(reopened.steps[0].mark.whiteboard, true);
const legacy = edits.loadEditableRecipe('{"steps":[{"kind":"text","mark":{"kind":"text","text":"旧记录","x":0.2,"y":0.3}}]}');
assert.equal(legacy.steps[0].mark.fontId, ''); assert.equal(legacy.steps[0].mark.whiteboard, false);
console.log('PASS: capture defaults; weather migration; frozen snapshots; narrow-screen toolbar; export naming; frozen text appearance; editable style round-trip; legacy recipe defaults');
