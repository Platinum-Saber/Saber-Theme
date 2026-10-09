// Dumps launcher activities from the connected device (adb) for the packages in
// design/icons/packages.json and merges them into design/icons/components.json.
// Existing entries are never dropped. Run: node tools/dump-components.mjs
import { execFileSync } from 'node:child_process';
import { existsSync, readFileSync, writeFileSync } from 'node:fs';
import { ROOT, loadPackages } from './glyph-source.mjs';

const OUT = `${ROOT}design/icons/components.json`;

function adbPath() {
  if (process.env.ADB) return process.env.ADB;
  const sdk = process.env.ANDROID_HOME || process.env.ANDROID_SDK_ROOT
    || (process.env.LOCALAPPDATA && `${process.env.LOCALAPPDATA}/Android/Sdk`);
  const exe = sdk && `${sdk}/platform-tools/adb${process.platform === 'win32' ? '.exe' : ''}`;
  return exe && existsSync(exe) ? exe : 'adb';
}

const raw = execFileSync(adbPath(), ['shell', 'cmd', 'package', 'query-activities', '--brief',
  '-a', 'android.intent.action.MAIN', '-c', 'android.intent.category.LAUNCHER'], { encoding: 'utf8' });

/** "pkg/.Cls" -> "pkg/pkg.Cls" */
const expand = c => {
  const [pkg, cls] = c.split('/');
  return `${pkg}/${cls.startsWith('.') ? pkg + cls : cls}`;
};
const found = raw.split(/\r?\n/).map(l => l.trim()).filter(l => /^[\w.]+\/[\w.$]+$/.test(l)).map(expand);

const packages = loadPackages();
const glyphOf = new Map(Object.entries(packages).flatMap(([k, pkgs]) => pkgs.map(p => [p, k])));
const out = existsSync(OUT) ? JSON.parse(readFileSync(OUT, 'utf8')) : {};
let added = 0;
for (const c of found) {
  const key = glyphOf.get(c.split('/')[0]);
  if (!key) continue;
  const list = (out[key] ??= []);
  if (!list.includes(c)) { list.push(c); added++; }
}

const sorted = Object.fromEntries(Object.keys(packages).filter(k => out[k]).map(k => [k, [...out[k]].sort()])
  .concat(Object.keys(out).filter(k => !packages[k]).map(k => [k, out[k]])));
writeFileSync(OUT, `${JSON.stringify(sorted, null, 2)}\n`);
console.log(`components: ${found.length} launcher activities on device, ${added} added, `
  + `${Object.values(sorted).flat().length} total in ${Object.keys(sorted).length} glyphs`);
