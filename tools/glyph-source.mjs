// Loads design/icons/glyphs.js (a plain script) and converts its SVG
// fragments into VectorDrawable path data.
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import vm from 'node:vm';

export const ROOT = fileURLToPath(new URL('..', import.meta.url));
export const GLYPHS_JS = `${ROOT}design/icons/glyphs.js`;

export function loadGlyphs() {
  const ctx = {};
  vm.createContext(ctx);
  vm.runInContext(`${readFileSync(GLYPHS_JS, 'utf8')}\n;globalThis.__out = { APPS, UI };`, ctx);
  return ctx.__out;
}

export function loadPackages() {
  const json = JSON.parse(readFileSync(`${ROOT}design/icons/packages.json`, 'utf8'));
  delete json._comment;
  return json;
}

const num = n => Number(n.toFixed(3)).toString();

function circle(cx, cy, r) {
  return `M${num(cx - r)},${num(cy)}a${num(r)},${num(r)} 0 1,0 ${num(2 * r)},0a${num(r)},${num(r)} 0 1,0 ${num(-2 * r)},0z`;
}

function rect(x, y, w, h, rx) {
  rx = Math.min(rx || 0, w / 2, h / 2);
  if (!rx) return `M${num(x)},${num(y)}h${num(w)}v${num(h)}h${num(-w)}z`;
  const a = (dx, dy) => `a${num(rx)},${num(rx)} 0 0,1 ${num(dx)},${num(dy)}`;
  return `M${num(x + rx)},${num(y)}h${num(w - 2 * rx)}${a(rx, rx)}v${num(h - 2 * rx)}${a(-rx, rx)}`
    + `h${num(-(w - 2 * rx))}${a(-rx, -rx)}v${num(-(h - 2 * rx))}${a(rx, -rx)}z`;
}

const attrs = s => Object.fromEntries([...s.matchAll(/([\w-]+)="([^"]*)"/g)].map(m => [m[1], m[2]]));

/** SVG fragment (path/circle/rect elements) -> list of pathData strings. */
export function toPathData(svg) {
  const out = [];
  for (const [, tag, rest] of svg.matchAll(/<(path|circle|rect)\s([^>]*)\/>/g)) {
    const a = attrs(rest);
    if (tag === 'path') out.push(a.d);
    else if (tag === 'circle') out.push(circle(+a.cx, +a.cy, +a.r));
    else out.push(rect(+a.x, +a.y, +a.width, +a.height, +(a.rx || 0)));
  }
  if (!out.length) throw new Error(`no shapes in: ${svg}`);
  return out;
}

/** Resource-safe name: "ui-search" -> "ui_search". */
export const resName = key => key.replace(/[^a-z0-9]+/gi, '_').toLowerCase();
