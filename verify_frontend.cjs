// 前端源码轻量校验：SFC 编译 + JS 语法解析（替代被沙箱阻断的 esbuild 构建）
const fs = require('fs');
const path = require('path');

const BASE = './frontend';
const PNPM = path.join(BASE, 'node_modules', '.pnpm');

const babel = require(path.join(PNPM, '@babel+parser@7.29.8', 'node_modules', '@babel', 'parser'));
const sfc = require(path.join(PNPM, '@vue+compiler-sfc@3.5.41', 'node_modules', '@vue', 'compiler-sfc', 'dist', 'compiler-sfc.cjs.js'));

function walk(dir, exts, out = []) {
  for (const name of fs.readdirSync(dir)) {
    const full = path.join(dir, name);
    const stat = fs.statSync(full);
    if (stat.isDirectory()) {
      if (name === 'node_modules' || name === 'dist' || name === '.pnpm-store') continue;
      walk(full, exts, out);
    } else if (exts.some((e) => full.endsWith(e))) {
      out.push(full);
    }
  }
  return out;
}

const vueFiles = walk(path.join(BASE, 'src'), ['.vue']);
const jsFiles = walk(path.join(BASE, 'src'), ['.js']).concat([path.join(BASE, 'vite.config.js')]);

let fail = 0;

for (const f of vueFiles) {
  const rel = path.relative(BASE, f);
  const source = fs.readFileSync(f, 'utf-8');
  const { descriptor, errors } = sfc.parse(source, { filename: f });
  const errs = [...errors];
  if (descriptor.script || descriptor.scriptSetup) {
    try { sfc.compileScript(descriptor, { id: 'x' }); } catch (e) { errs.push('script: ' + e.message); }
  }
  if (descriptor.template) {
    try { sfc.compileTemplate({ source: descriptor.template.content, filename: f, id: 'x' }); } catch (e) { errs.push('template: ' + e.message); }
  }
  if (errs.length) {
    fail++;
    console.log('FAIL', rel);
    errs.forEach((e) => console.log('   -', String(e.message || e).split('\n')[0]));
  } else {
    console.log('PASS', rel);
  }
}

for (const f of jsFiles) {
  const rel = path.relative(BASE, f);
  try {
    babel.parse(fs.readFileSync(f, 'utf-8'), { sourceType: 'module', plugins: ['jsx'] });
    console.log('PASS', rel);
  } catch (e) {
    fail++;
    console.log('FAIL', rel, '-', e.message.split('\n')[0]);
  }
}

console.log('\n===== frontend verify:', fail === 0 ? 'ALL PASS' : fail + ' FAILURE(S)', '=====');
process.exit(fail === 0 ? 0 : 1);
