#!/usr/bin/env node
// Runs production ETS data/layout code. Native SDK access throws rather than being simulated.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const ts = require(process.env.TYPESCRIPT_PATH || '/Applications/DevEco-Studio.app/Contents/tools/hvigor/hvigor/node_modules/typescript/lib/typescript.js');
const root = path.resolve(__dirname, '../entry/src/main/ets/core');
const cache = new Map();
const allowed = new Set(['Models', 'BuildVariant', 'WatermarkRenderer', 'CameraLayout', 'WindowGeometry', 'DeviceLayoutProfile']);
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
for (const width of [280, 320, 360, 400, 600]) {
  const geometry = layout.cameraLayout(width, 900, 3 / 4);
  assert.ok(Math.abs(geometry.previewWidth / geometry.previewHeight - 3 / 4) < .001);
  if (width < 368) assert.ok(geometry.toolbarHeight >= 104, 'four tools and project chip need two rows');
}
assert.ok(models.QUALITY_EDGES[2] >= 1920);
assert.ok(models.QUALITY_JPEG[2] >= 85);
console.log('PASS: capture defaults; weather upgrade preserves values/order; unavailable weather hidden; snapshots frozen; narrow-screen toolbar; readable output profile');
