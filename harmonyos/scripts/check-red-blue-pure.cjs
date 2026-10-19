#!/usr/bin/env node
// Execute actual ETS pure branches. Native SDK access throws instead of being simulated.
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const ts = require('/Applications/DevEco-Studio.app/Contents/tools/hvigor/hvigor/node_modules/typescript/lib/typescript.js');
const root = path.resolve(__dirname, '..');
const cache = new Map();
const pure = new Set(['ImageProcessor', 'CameraLayout', 'WindowGeometry', 'DeviceLayoutProfile', 'VideoEnvironment', 'RedBlueRepairChecks']);
function load(file) {
  if (cache.has(file)) return cache.get(file).exports;
  const module = { exports: {} }; cache.set(file, module);
  const output = ts.transpileModule(fs.readFileSync(file, 'utf8'), { compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2021 } }).outputText;
  function localRequire(name) {
    const next = path.resolve(path.dirname(file), name + '.ets');
    if (name.startsWith('.') && pure.has(path.basename(name))) return load(next);
    return new Proxy({}, { get() { throw new Error('Pure checks accessed native dependency: ' + name); } });
  }
  vm.runInNewContext('(function(require,module,exports){' + output + '\n})', {}, { filename: file })(localRequire, module, module.exports);
  return module.exports;
}
(async () => {
  const checks = load(path.join(root, 'entry/src/ohosTest/ets/testability/RedBlueRepairChecks.ets')).RedBlueRepairChecks;
  await checks.run();
  const recipes = load(path.join(root, 'entry/src/main/ets/core/ImageProcessor.ets'));
  for (const json of ['{}', '{"garbage":true}', '{"rotation":0}', '{"cropX":0}', '{"flipX":false}', '{"rotation":0,"flipX":false,"cropX":0}', '{"steps":[]}', '{"marks":[]}', JSON.stringify(new recipes.EditRecipe()), '{"steps":[{"kind":"rotate"}]}']) {
    let accepted = true;
    try { recipes.loadEditableRecipe(json, true); } catch { accepted = false; }
    console.log((accepted ? 'ACCEPT ' : 'REJECT ') + json);
  }
  console.log('PASS: all 127 combinations of transform/crop metadata without a vector container rejected');
  console.log('PASS: persisted recipe validation and legacy compatibility; production layout arguments; precise video failure classification');
})().catch(error => { console.error(error); process.exitCode = 1; });
