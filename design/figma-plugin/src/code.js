// Saber-Theme Builder — generates the Saber-Theme design file.
// Safe to re-run: everything it generates is tagged with plugin data and
// removed before rebuilding. Foundations (variables, styles, wallpapers,
// GlassSurface) are reused when present and created when missing.

const KEY = 'saber';
const FAM_PREF = 'Google Sans Flex';
const PAGE_NAMES = ['Foundations & Components', 'Widgets & Icons', 'Screens'];
const W = 360, H = 780;
const SZ = { '2x1': [156, 76], '2x2': [156, 164], '4x1': [324, 76], '4x2': [324, 164] };
const THEMES = ['light', 'dark'];

// Glass material tokens — the single place to tune transparency.
// [lightHex, darkHex, lightAlpha, darkAlpha, scopes]
const GLASS_COLORS = {
  'glass/tint': ['#FFFFFF', '#1A1C22', 0.14, 0.2, ['FRAME_FILL', 'SHAPE_FILL']],
  // thick = overlays (sheets, dock, menus, folder): frosted enough to hide busy content behind
  'glass/tint-thick': ['#FFFFFF', '#1A1C22', 0.34, 0.42, ['FRAME_FILL', 'SHAPE_FILL']],
  'glass/tint-thin': ['#FFFFFF', '#1A1C22', 0.06, 0.1, ['FRAME_FILL', 'SHAPE_FILL']],
  'glass/border': ['#FFFFFF', '#FFFFFF', 0.7, 0.24, ['STROKE_COLOR']],
  'glass/highlight': ['#FFFFFF', '#FFFFFF', 0.85, 0.4, ['FRAME_FILL', 'SHAPE_FILL', 'STROKE_COLOR']],
};
const GLASS_FLOATS = { 'blur/thin': 6, 'blur/regular': 12, 'blur/thick': 36, 'refraction/regular': 0.35, 'highlight/regular': 0.6, 'border/width': 1 };
// Glance (RemoteViews) cannot blur, so its fallback stays more opaque for legibility.
const GLANCE_ALPHA = { light: 0.6, dark: 0.7 };

let FAM = FAM_PREF;
const VARS = {}, TS = {}, ES = {}, GLYPH = {};
const WALL = {}, TILE = {}, APPICON = {}, FOLDER = {}, DOCK = {}, SEARCH = {}, STATUS = {}, GESTURE = {}, INDICATOR = {}, MENU = {}, WIDGET = {};

// Glyph data (P, C, R, APPS, UI, label) is prepended from design/icons/glyphs.js
// by tools/build-plugin.mjs.

// ---------------------------------------------------------------- helpers
const mark = n => { n.setPluginData(KEY, 'gen'); return n; };
function hex(h) { return { r: parseInt(h.slice(1, 3), 16) / 255, g: parseInt(h.slice(3, 5), 16) / 255, b: parseInt(h.slice(5, 7), 16) / 255 }; }
function rgba(h, a) { const c = hex(h); return { r: c.r, g: c.g, b: c.b, a: a === undefined ? 1 : a }; }
function solid(h, o) { return { type: 'SOLID', color: hex(h), opacity: o === undefined ? 1 : o }; }
function tv(theme, name) {
  const v = VARS[`${theme}/${name}`];
  const p = { type: 'SOLID', color: { r: 0, g: 0, b: 0 } };
  return v ? figma.variables.setBoundVariableForPaint(p, 'color', v) : p;
}
function cap(s) { return s[0].toUpperCase() + s.slice(1); }

function al(dir, o) {
  o = o || {};
  const f = figma.createFrame();
  f.layoutMode = dir || 'VERTICAL';
  f.primaryAxisSizingMode = 'AUTO';
  f.counterAxisSizingMode = 'AUTO';
  f.fills = [];
  f.clipsContent = false;
  f.itemSpacing = o.gap || 0;
  const p = o.pad || 0;
  f.paddingLeft = f.paddingRight = o.px !== undefined ? o.px : p;
  f.paddingTop = f.paddingBottom = o.py !== undefined ? o.py : p;
  if (o.align) f.counterAxisAlignItems = o.align;
  if (o.justify) f.primaryAxisAlignItems = o.justify;
  if (o.name) f.name = o.name;
  return f;
}
function fixed(f, w, h) { f.resize(w, h); f.primaryAxisSizingMode = 'FIXED'; f.counterAxisSizingMode = 'FIXED'; return f; }
function fixW(f, w) { // fixed width, hug height (vertical frames)
  f.resize(w, Math.max(1, f.height));
  if (f.layoutMode === 'VERTICAL') { f.counterAxisSizingMode = 'FIXED'; f.primaryAxisSizingMode = 'AUTO'; }
  else { f.primaryAxisSizingMode = 'FIXED'; f.counterAxisSizingMode = 'AUTO'; }
  return f;
}
function add(parent, child, o) {
  parent.appendChild(child);
  o = o || {};
  if (o.fillW) child.layoutSizingHorizontal = 'FILL';
  if (o.fillH) child.layoutSizingVertical = 'FILL';
  return child;
}
function spacer(parent) { const s = figma.createFrame(); s.name = 'Spacer'; s.fills = []; s.resize(1, 1); parent.appendChild(s); s.layoutGrow = 1; return s; }
function rect(w, h, r, paint) { const n = figma.createRectangle(); n.resize(w, h); n.cornerRadius = r || 0; n.fills = paint ? [paint] : []; return n; }

async function text(chars, style, theme, color, o) {
  const t = figma.createText();
  if (TS[style]) await t.setTextStyleIdAsync(TS[style]);
  else t.fontName = { family: FAM, style: 'Regular' };
  t.characters = chars;
  t.fills = [tv(theme, color || 'text/primary')];
  if (o && o.center) t.textAlignHorizontal = 'CENTER';
  return t;
}

async function glass(n, theme, mat, r, glance) {
  mat = mat || 'regular';
  const tint = { thin: 'glass/tint-thin', regular: 'glass/tint', thick: 'glass/tint-thick' }[mat];
  n.cornerRadius = r === undefined ? 28 : r;
  n.strokes = [tv(theme, 'glass/border')];
  n.strokeWeight = 1;
  n.strokeAlign = 'INSIDE';
  n.clipsContent = true;
  if (glance) {
    n.fills = [solid(theme === 'light' ? '#FFFFFF' : '#1A1C22', GLANCE_ALPHA[theme])];
    n.effects = [];
  } else {
    n.fills = [tv(theme, tint)];
    if (ES['Glass/' + cap(mat)]) await n.setEffectStyleIdAsync(ES['Glass/' + cap(mat)]);
  }
}

function recolor(node, theme, color) {
  const nodes = node.findAll(n => n.type === 'VECTOR' || n.type === 'ELLIPSE' || n.type === 'RECTANGLE' || n.type === 'BOOLEAN_OPERATION');
  for (const n of nodes) {
    if (n.strokes && n.strokes.length) n.strokes = [tv(theme, color)];
    if (Array.isArray(n.fills) && n.fills.length) n.fills = [tv(theme, color)];
  }
}
function glyph(name, theme, size, color) {
  const i = GLYPH[name][theme].createInstance();
  i.name = 'Glyph';
  if (color && color !== 'glyph/default') recolor(i, theme, color);
  if (size && size !== 24) i.rescale(size / 24);
  return i;
}
function setGlyph(inst, name, theme) {
  const g = inst.findOne(n => n.type === 'INSTANCE' && n.name === 'Glyph');
  if (!g) return;
  const w = g.width;
  g.swapComponent(GLYPH[name][theme]);
  if (Math.abs(g.width - w) > 0.1) g.rescale(w / g.width);
}

function splitBg() {
  return {
    type: 'GRADIENT_LINEAR', gradientTransform: [[1, 0, 0], [0, 1, 0]],
    gradientStops: [
      { position: 0, color: rgba('#B9C6FF') }, { position: 0.25, color: rgba('#FFD3E2') }, { position: 0.4999, color: rgba('#CDEFE7') },
      { position: 0.5, color: rgba('#141A36') }, { position: 0.75, color: rgba('#2A1B4A') }, { position: 1, color: rgba('#0B2A2E') },
    ],
  };
}
function makeSet(comps, parent, name, desc, dir) {
  const s = figma.combineAsVariants(comps, parent);
  s.name = name;
  s.layoutMode = dir || 'HORIZONTAL';
  s.primaryAxisSizingMode = 'AUTO';
  s.counterAxisSizingMode = 'AUTO';
  s.itemSpacing = 24;
  s.paddingLeft = s.paddingRight = s.paddingTop = s.paddingBottom = 32;
  s.counterAxisAlignItems = 'CENTER';
  s.fills = [splitBg()];
  s.strokes = [];
  s.cornerRadius = 24;
  if (desc) s.description = desc;
  return s;
}

async function board(title, subtitle, x, y) {
  const b = al('VERTICAL', { gap: 24, pad: 48, name: title });
  b.fills = [solid('#F4F5F8')];
  b.cornerRadius = 40;
  figma.currentPage.appendChild(b);
  b.x = x; b.y = y;
  mark(b);
  b.appendChild(await text(title, 'Display/Large', 'light'));
  if (subtitle) {
    const s = await text(subtitle, 'Body/Regular', 'light', 'text/secondary');
    s.resize(880, s.height); s.textAutoResize = 'HEIGHT';
    b.appendChild(s);
  }
  return b;
}
async function heading(parent, t) { parent.appendChild(await text(t, 'Title/Large', 'light')); }

function wallpaperInstance(theme, w, h) {
  const i = WALL[theme].createInstance();
  i.name = 'Wallpaper';
  const s = Math.max(w / W, h / H);
  if (s !== 1) i.rescale(s);
  i.x = (w - i.width) / 2; i.y = (h - i.height) / 2;
  return i;
}
function stage(theme, w, h, name) {
  const f = figma.createFrame();
  f.name = name || `Stage · ${theme}`;
  f.resize(w, h);
  f.clipsContent = true;
  f.fills = [solid(theme === 'light' ? '#EEF0F6' : '#0A0C14')];
  f.appendChild(wallpaperInstance(theme, w, h));
  return f;
}
function nextX(page, gap) {
  let m = 0;
  for (const c of page.children) m = Math.max(m, c.x + c.width);
  return m + (gap || 160);
}

// ---------------------------------------------------------------- foundations
async function ensureFoundations(page1) {
  // fonts
  const all = await figma.listAvailableFontsAsync();
  const styles = all.filter(f => f.fontName.family === FAM_PREF).map(f => f.fontName.style);
  if (!styles.length) FAM = 'Inter';
  const pick = function () { const avail = all.filter(f => f.fontName.family === FAM).map(f => f.fontName.style); for (const s of arguments) if (avail.indexOf(s) >= 0) return s; return 'Regular'; };
  const F = { light: pick('Light', 'Regular'), reg: pick('Regular'), med: pick('Medium', 'Regular'), semi: pick('SemiBold', 'Semi Bold', 'Medium') };
  for (const s of new Set([F.light, F.reg, F.med, F.semi])) await figma.loadFontAsync({ family: FAM, style: s });
  await figma.loadFontAsync({ family: 'Inter', style: 'Regular' });

  // variables
  let vars = await figma.variables.getLocalVariablesAsync();
  if (!vars.some(v => v.name === 'light/text/primary')) {
    const colors = {
      'text/primary': ['#0E0F12', '#F5F6F8', 1, 1, ['TEXT_FILL', 'STROKE_COLOR']],
      'text/secondary': ['#0E0F12', '#F5F6F8', 0.65, 0.7, ['TEXT_FILL', 'STROKE_COLOR']],
      'text/tertiary': ['#0E0F12', '#F5F6F8', 0.44, 0.44, ['TEXT_FILL', 'STROKE_COLOR']],
      'glyph/default': ['#0E0F12', '#F5F6F8', 1, 1, ['STROKE_COLOR', 'SHAPE_FILL']],
      'accent/default': ['#5B7CFA', '#8EA6FF', 1, 1, ['FRAME_FILL', 'SHAPE_FILL', 'TEXT_FILL', 'STROKE_COLOR']],
    };
    const col = figma.variables.createVariableCollection('Color');
    const m = col.modes[0].modeId;
    for (const name in colors) {
      const [l, d, la, da, scopes] = colors[name];
      for (const [th, h, a] of [['light', l, la], ['dark', d, da]]) {
        const v = figma.variables.createVariable(`${th}/${name}`, col, 'COLOR');
        v.setValueForMode(m, rgba(h, a)); v.scopes = scopes;
      }
    }
    for (const name in GLASS_COLORS) {
      const scopes = GLASS_COLORS[name][4];
      for (const th of THEMES) { const v = figma.variables.createVariable(`${th}/${name}`, col, 'COLOR'); v.scopes = scopes; }
    }
    const glassCol = figma.variables.createVariableCollection('Glass material');
    for (const n in GLASS_FLOATS) { const v = figma.variables.createVariable(n, glassCol, 'FLOAT'); v.scopes = n.indexOf('blur') === 0 ? ['EFFECT_FLOAT'] : n.indexOf('border') === 0 ? ['STROKE_FLOAT'] : []; }
    const dim = figma.variables.createVariableCollection('Dimension');
    const dm = dim.modes[0].modeId;
    const dims = { 'space/1': 4, 'space/2': 8, 'space/3': 12, 'space/4': 16, 'space/5': 20, 'space/6': 24, 'space/8': 32, 'radius/icon': 18, 'radius/sm': 16, 'radius/md': 24, 'radius/lg': 28, 'radius/xl': 32, 'radius/pill': 999 };
    for (const n in dims) { const v = figma.variables.createVariable(n, dim, 'FLOAT'); v.setValueForMode(dm, dims[n]); v.scopes = n.indexOf('space') === 0 ? ['GAP', 'WIDTH_HEIGHT'] : ['CORNER_RADIUS']; }
    vars = await figma.variables.getLocalVariablesAsync();
  }
  for (const v of vars) VARS[v.name] = v;

  // Glass tokens are re-applied on every run so tuning them here updates the file.
  const modeOf = async v => (await figma.variables.getVariableCollectionByIdAsync(v.variableCollectionId)).modes[0].modeId;
  for (const name in GLASS_COLORS) {
    const [l, d, la, da] = GLASS_COLORS[name];
    for (const [th, h, a] of [['light', l, la], ['dark', d, da]]) {
      const v = VARS[`${th}/${name}`];
      if (v) v.setValueForMode(await modeOf(v), rgba(h, a));
    }
  }
  for (const n in GLASS_FLOATS) { const v = VARS[n]; if (v) v.setValueForMode(await modeOf(v), GLASS_FLOATS[n]); }

  // text styles
  let tstyles = await figma.getLocalTextStylesAsync();
  const ramp = [['Display/Clock', 72, F.light, -2], ['Display/Large', 44, F.light, -1], ['Title/Large', 22, F.med, -0.2], ['Title/Medium', 17, F.med, 0], ['Body/Regular', 15, F.reg, 0], ['Label/Medium', 13, F.med, 0.1], ['Caption/Icon', 11, F.med, 0.2]];
  for (const [n, size, st, ls] of ramp) {
    if (tstyles.some(s => s.name === n)) continue;
    const t = figma.createTextStyle(); t.name = n; t.fontName = { family: FAM, style: st }; t.fontSize = size;
    t.letterSpacing = { unit: 'PIXELS', value: ls }; t.lineHeight = { unit: 'PERCENT', value: size > 40 ? 100 : 130 };
  }
  tstyles = await figma.getLocalTextStylesAsync();
  for (const s of tstyles) { TS[s.name] = s.id; await figma.loadFontAsync(s.fontName); }

  // effect styles
  let estyles = await figma.getLocalEffectStylesAsync();
  for (const k of ['thin', 'regular', 'thick']) {
    const n = 'Glass/' + cap(k);
    if (estyles.some(s => s.name === n)) continue;
    let bb = { type: 'BACKGROUND_BLUR', blurType: 'NORMAL', radius: { thin: 12, regular: 24, thick: 40 }[k], visible: true };
    if (VARS['blur/' + k]) bb = figma.variables.setBoundVariableForEffect(bb, 'radius', VARS['blur/' + k]);
    const s = figma.createEffectStyle(); s.name = n;
    s.effects = [bb,
      { type: 'INNER_SHADOW', color: { r: 1, g: 1, b: 1, a: k === 'thin' ? 0.35 : 0.55 }, offset: { x: 0, y: 1 }, radius: 0, spread: 0, visible: true, blendMode: 'NORMAL' },
      { type: 'INNER_SHADOW', color: { r: 1, g: 1, b: 1, a: 0.18 }, offset: { x: 0, y: -1 }, radius: 6, spread: 0, visible: true, blendMode: 'NORMAL' },
      { type: 'DROP_SHADOW', color: { r: 0, g: 0, b: 0, a: k === 'thick' ? 0.18 : 0.1 }, offset: { x: 0, y: 8 }, radius: 24, spread: -4, visible: true, blendMode: 'NORMAL', showShadowBehindNode: false }];
  }
  estyles = await figma.getLocalEffectStylesAsync();
  for (const s of estyles) ES[s.name] = s.id;

  // wallpapers
  await page1.loadAsync();
  const find = name => page1.findOne(n => n.type === 'COMPONENT' && n.name === name);
  WALL.dark = find('Wallpaper/Aurora Night');
  WALL.light = find('Wallpaper/Aurora Dawn');
  const mk = (name, bg, blobs, x) => {
    const c = figma.createComponent(); c.name = name; c.resize(W, H); c.clipsContent = true; c.fills = [solid(bg)]; c.x = x; c.y = 0;
    for (const [cx, cy, r, col, op] of blobs) { const e = figma.createEllipse(); e.resize(r * 2, r * 2); e.x = cx - r; e.y = cy - r; e.fills = [solid(col, op)]; e.effects = [{ type: 'LAYER_BLUR', blurType: 'NORMAL', radius: 110, visible: true }]; c.appendChild(e); }
    page1.appendChild(c);
    return c;
  };
  if (!WALL.dark) WALL.dark = mk('Wallpaper/Aurora Night', '#0A0C14', [[60, 140, 170, '#4054E0', 0.9], [320, 300, 160, '#8A3CF0', 0.75], [90, 560, 190, '#00A894', 0.6], [300, 700, 150, '#E2557A', 0.55], [200, 420, 90, '#5BD3FF', 0.4]], 0);
  if (!WALL.light) WALL.light = mk('Wallpaper/Aurora Dawn', '#EEF0F6', [[70, 150, 170, '#9FB2FF', 0.9], [320, 320, 160, '#FFC2D9', 0.9], [80, 580, 190, '#A8EEDF', 0.9], [310, 690, 150, '#FFDDA6', 0.9], [200, 420, 90, '#C9B8FF', 0.7]], 400);
  WALL.dark.description = 'Bundled abstract wallpaper (dark). The launcher draws its own wallpaper so glass can blur and refract it.';
  WALL.light.description = 'Bundled abstract wallpaper (light). The launcher draws its own wallpaper so glass can blur and refract it.';
}

// ---------------------------------------------------------------- icons page
async function buildIcons(page) {
  const b = await board('Icon pack', 'Minimalist line glyphs on a 24-unit grid · 1.75 stroke · round caps and joins · 2u padding. Glyph colour is bound to glyph/default. Exported as SVG → VectorDrawable for :core:icons and the standalone icon-pack APK (appfilter.xml).', 0, 0);

  // keyline
  await heading(b, 'Keyline');
  const kl = figma.createComponent(); kl.name = 'Icon keyline (24)'; kl.resize(24, 24); kl.fills = [];
  const ks = [[rect(20, 20, 0), 2, '#FF4D80'], [figma.createEllipse(), 3.5, '#3380FF'], [rect(15, 15, 3.5), 4.5, '#33CC99']];
  ks[1][0].resize(17, 17);
  for (const [n, o, c] of ks) { n.x = o; n.y = o; n.fills = []; n.strokes = [solid(c, 0.6)]; n.strokeWeight = 0.25; kl.appendChild(n); }
  kl.description = 'Keyline: 2u padding, circle Ø17, square 15 r3.5. Stroke 1.75, round caps & joins.';
  const klRow = al('HORIZONTAL', { gap: 32, align: 'CENTER' });
  klRow.appendChild(kl);
  const big = kl.createInstance(); big.rescale(8); klRow.appendChild(big);
  b.appendChild(klRow);

  // glyph components
  const mkGrid = (title) => { const g = al('HORIZONTAL', { gap: 28, pad: 24, name: title }); g.layoutWrap = 'WRAP'; g.counterAxisSpacing = 28; g.fills = [solid('#FFFFFF')]; g.cornerRadius = 24; return g; };
  await heading(b, 'App glyphs');
  const gApps = mkGrid('App glyphs'); b.appendChild(gApps); fixW(gApps, 880);
  await heading(b, 'UI glyphs');
  const gUi = mkGrid('UI glyphs'); b.appendChild(gUi); fixW(gUi, 880);
  const failed = [];
  const all = Object.keys(APPS).map(k => [k, APPS[k][1], gApps]).concat(Object.keys(UI).map(k => [k, UI[k], gUi]));
  for (const [name, body, parent] of all) {
    try {
      const svg = `<svg width="24" height="24" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg" stroke="#0E0F12" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round">${body}</svg>`;
      // One variant per theme: recolouring a swapped nested instance doesn't
      // stick, so screens swap to the right-coloured variant instead.
      const comps = {};
      for (const t of THEMES) {
        const comp = figma.createComponentFromNode(figma.createNodeFromSvg(svg));
        comp.name = `Theme=${t}`;
        comp.fills = [];
        recolor(comp, t, 'glyph/default');
        comps[t] = comp;
      }
      const set = figma.combineAsVariants([comps.light, comps.dark], parent);
      set.name = `glyph/${name}`;
      set.layoutMode = 'HORIZONTAL'; set.primaryAxisSizingMode = 'AUTO'; set.counterAxisSizingMode = 'AUTO';
      set.itemSpacing = 8; set.paddingLeft = set.paddingRight = set.paddingTop = set.paddingBottom = 6;
      set.fills = [splitBg()]; set.strokes = []; set.cornerRadius = 8;
      set.description = `Saber glyph "${name}". 24-unit grid, 1.75 stroke.`;
      GLYPH[name] = comps;
    } catch (e) { failed.push(name + ': ' + e.message); }
  }
  return failed;
}

async function buildIconPreview(page) {
  const names = Object.keys(APPS);
  const x0 = nextX(page);
  let x = x0;
  for (const t of THEMES) {
    const rows = Math.ceil(names.length / 4);
    const s = stage(t, W, 64 + rows * 96, `Icon preview · ${t}`);
    page.appendChild(s); mark(s); s.x = x; s.y = 0;
    names.forEach((n, i) => { const ic = appIcon(t, n); s.appendChild(ic); ic.x = 18 + (i % 4) * 84; ic.y = 32 + Math.floor(i / 4) * 96; });
    x += W + 60;
  }
}

// ---------------------------------------------------------------- components page
async function buildTile(parent) {
  const comps = [];
  // theme outer loop: combineAsVariants keeps creation order, and the split
  // background expects light variants first
  for (const t of THEMES) for (const style of ['Tile', 'Bare']) {
    const c = figma.createComponent();
    c.name = `Style=${style}, Theme=${t}`;
    c.layoutMode = 'HORIZONTAL'; c.primaryAxisAlignItems = 'CENTER'; c.counterAxisAlignItems = 'CENTER';
    fixed(c, 56, 56);
    if (style === 'Tile') await glass(c, t, 'regular', 18); else { c.fills = []; c.clipsContent = false; }
    c.appendChild(glyph('phone', t, style === 'Tile' ? 24 : 30));
    comps.push(c);
    TILE[style + t] = c;
  }
  return makeSet(comps, parent, 'IconTile', 'Glass squircle (radius/icon 18) holding one glyph. Style=Bare drops the glass for a glyph-only home screen.');
}

async function buildAppIcon(parent) {
  const comps = [];
  for (const t of THEMES) for (const style of ['Tile', 'Bare']) {
    const c = figma.createComponent();
    c.name = `Style=${style}, Theme=${t}`;
    c.layoutMode = 'VERTICAL'; c.counterAxisAlignItems = 'CENTER'; c.itemSpacing = 6; c.fills = [];
    c.paddingTop = 2;
    c.appendChild(TILE[style + t].createInstance());
    c.appendChild(await text('Phone', 'Caption/Icon', t, 'text/primary', { center: true }));
    fixW(c, 72);
    comps.push(c);
    APPICON[style + t] = c;
  }
  return makeSet(comps, parent, 'AppIcon', 'Home-grid cell: IconTile + label (Caption/Icon). Cell 72 wide on a 4-column grid, 12 gutter, 18 side margin.');
}
function appIcon(t, name, style) {
  const i = APPICON[(style || 'Tile') + t].createInstance();
  setGlyph(i, name, t);
  const tx = i.findOne(n => n.type === 'TEXT');
  if (tx) tx.characters = label(name);
  return i;
}
function tile(t, name, style) { const i = TILE[(style || 'Tile') + t].createInstance(); setGlyph(i, name, t); return i; }

async function buildFolder(parent) {
  const comps = [];
  for (const t of THEMES) {
    const c = figma.createComponent();
    c.name = `Theme=${t}`;
    c.layoutMode = 'VERTICAL'; c.counterAxisAlignItems = 'CENTER'; c.itemSpacing = 6; c.fills = []; c.paddingTop = 2;
    const tl = al('VERTICAL', { gap: 6, align: 'CENTER', justify: 'CENTER', name: 'Folder tile' });
    fixed(tl, 56, 56); await glass(tl, t, 'regular', 18);
    for (const pair of [['chat', 'instagram'], ['x', 'telegram']]) {
      const r = al('HORIZONTAL', { gap: 6 });
      for (const g of pair) r.appendChild(glyph(g, t, 14));
      tl.appendChild(r);
    }
    c.appendChild(tl);
    c.appendChild(await text('Social', 'Caption/Icon', t, 'text/primary', { center: true }));
    fixW(c, 72);
    comps.push(c); FOLDER[t] = c;
  }
  return makeSet(comps, parent, 'Folder', 'Closed folder: 2×2 preview of the first four glyphs on a glass tile.');
}

async function buildDock(parent) {
  const comps = [];
  for (const t of THEMES) {
    const c = figma.createComponent();
    c.name = `Theme=${t}`;
    c.layoutMode = 'HORIZONTAL'; c.primaryAxisAlignItems = 'SPACE_BETWEEN'; c.counterAxisAlignItems = 'CENTER';
    c.paddingLeft = c.paddingRight = 14;
    fixed(c, 324, 80); await glass(c, t, 'thick', 30);
    // Direct glyph instances (no nested swap) so the main component keeps theme colours.
    for (const g of ['phone', 'messages', 'browser', 'camera']) {
      const tl = al('HORIZONTAL', { justify: 'CENTER', align: 'CENTER', name: 'IconTile' });
      fixed(tl, 56, 56); await glass(tl, t, 'regular', 18);
      tl.appendChild(glyph(g, t));
      c.appendChild(tl);
    }
    comps.push(c); DOCK[t] = c;
  }
  return makeSet(comps, parent, 'Dock', 'Thick glass dock, four pinned apps, no labels.');
}

async function buildSearch(parent) {
  const comps = [];
  for (const t of THEMES) {
    const c = figma.createComponent();
    c.name = `Theme=${t}`;
    c.layoutMode = 'HORIZONTAL'; c.counterAxisAlignItems = 'CENTER'; c.itemSpacing = 10; c.paddingLeft = c.paddingRight = 16;
    fixed(c, 324, 48); await glass(c, t, 'regular', 24);
    c.appendChild(glyph('ui-search', t, 20, 'text/secondary'));
    const q = await text('Search apps, contacts & web', 'Body/Regular', t, 'text/secondary'); q.name = 'Query';
    c.appendChild(q);
    spacer(c);
    c.appendChild(glyph('ui-mic', t, 20, 'text/secondary'));
    comps.push(c); SEARCH[t] = c;
  }
  return makeSet(comps, parent, 'SearchPill', 'Universal search entry: apps, contacts, settings, web.');
}

async function buildStatus(parent) {
  const comps = [];
  for (const t of THEMES) {
    const c = figma.createComponent();
    c.name = `Theme=${t}`;
    c.layoutMode = 'HORIZONTAL'; c.counterAxisAlignItems = 'CENTER'; c.itemSpacing = 6; c.paddingLeft = c.paddingRight = 24; c.fills = [];
    fixed(c, W, 36);
    c.appendChild(await text('9:41', 'Label/Medium', t));
    spacer(c);
    for (const g of ['ui-signal', 'ui-wifi', 'ui-battery']) c.appendChild(glyph(g, t, 16, 'text/primary'));
    comps.push(c); STATUS[t] = c;
  }
  return makeSet(comps, parent, 'StatusBar', 'System status bar placeholder (drawn by One UI, shown for layout only).');
}

async function buildGesture(parent) {
  const comps = [];
  for (const t of THEMES) {
    const c = figma.createComponent();
    c.name = `Theme=${t}`;
    c.layoutMode = 'HORIZONTAL'; c.primaryAxisAlignItems = 'CENTER'; c.counterAxisAlignItems = 'CENTER'; c.fills = [];
    fixed(c, W, 20);
    const p = rect(108, 4, 2, tv(t, 'text/primary')); p.opacity = 0.7; c.appendChild(p);
    comps.push(c); GESTURE[t] = c;
  }
  return makeSet(comps, parent, 'GestureBar', 'Navigation gesture handle (system).');
}

async function buildIndicator(parent) {
  const comps = [];
  for (const t of THEMES) for (const page of [1, 2]) {
    const c = figma.createComponent();
    c.name = `Theme=${t}, Page=${page}`;
    c.layoutMode = 'HORIZONTAL'; c.counterAxisAlignItems = 'CENTER'; c.itemSpacing = 6; c.fills = [];
    c.primaryAxisSizingMode = 'AUTO'; c.counterAxisSizingMode = 'AUTO';
    for (const i of [1, 2]) c.appendChild(i === page ? rect(18, 6, 3, tv(t, 'text/primary')) : rect(6, 6, 3, tv(t, 'text/tertiary')));
    comps.push(c); INDICATOR[t + page] = c;
  }
  return makeSet(comps, parent, 'PageIndicator', 'Active page is an 18×6 pill; others are 6×6 dots.');
}

async function buildMenu(parent) {
  const comps = [];
  for (const t of THEMES) {
    const c = figma.createComponent();
    c.name = `Theme=${t}`;
    c.layoutMode = 'VERTICAL'; c.itemSpacing = 0; c.paddingTop = c.paddingBottom = 6;
    await glass(c, t, 'thick', 24);
    c.primaryAxisSizingMode = 'AUTO';
    const items = [['ui-edit', 'Edit home screen'], ['ui-widgets', 'Widgets'], ['ui-wallpaper', 'Wallpaper & style'], ['settings', 'Launcher settings']];
    for (const [g, l] of items) {
      const r = al('HORIZONTAL', { gap: 12, px: 16, py: 12, align: 'CENTER', name: l });
      r.appendChild(glyph(g, t, 20));
      r.appendChild(await text(l, 'Body/Regular', t));
      add(c, r);
    }
    c.counterAxisSizingMode = 'FIXED'; c.resize(232, c.height);
    for (const r of c.children) r.layoutSizingHorizontal = 'FILL';
    comps.push(c); MENU[t] = c;
  }
  return makeSet(comps, parent, 'ContextMenu', 'Long-press on empty home space.');
}

async function buildWidgetFrames(parent) {
  const comps = [];
  for (const t of THEMES) for (const size of Object.keys(SZ)) {
    const c = figma.createComponent();
    c.name = `Size=${size.replace('x', '×')}, Theme=${t}`;
    c.layoutMode = 'VERTICAL'; c.primaryAxisAlignItems = 'CENTER'; c.counterAxisAlignItems = 'CENTER';
    fixed(c, SZ[size][0], SZ[size][1]); await glass(c, t, 'regular', size[0] === '2' ? 24 : 28);
    c.appendChild(await text(size.replace('x', ' × '), 'Label/Medium', t, 'text/tertiary'));
    comps.push(c);
  }
  return makeSet(comps, parent, 'WidgetFrame', 'Widget sizes on the home grid. 1 column = 72, gutter 12; 1 row = 76, row gap 12.');
}

async function buildComponents(page) {
  let y = 0; for (const c of page.children) y = Math.max(y, c.y + c.height);
  const b = await board('Components', 'Every component has light and dark variants (left half: light glass on Aurora Dawn tones, right half: dark glass on Aurora Night tones). Glass = tint fill + glass/border stroke + Glass/* effect style (background blur, rim highlights, soft shadow).', 0, y + 200);
  const sections = [['IconTile', buildTile], ['AppIcon', buildAppIcon], ['Folder', buildFolder], ['Dock', buildDock], ['SearchPill', buildSearch], ['PageIndicator', buildIndicator], ['StatusBar · GestureBar', async p => { await buildStatus(p); await buildGesture(p); }], ['ContextMenu', buildMenu], ['WidgetFrame', buildWidgetFrames]];
  for (const [title, fn] of sections) {
    await heading(b, title);
    const row = al('HORIZONTAL', { gap: 32, align: 'MIN', name: title });
    b.appendChild(row);
    await fn(row);
  }
  return b;
}

// ---------------------------------------------------------------- widgets
function row(o) { return al('HORIZONTAL', Object.assign({ align: 'CENTER' }, o || {})); }
function col(o) { return al('VERTICAL', o || {}); }
function ring(size, p, t, color) {
  const f = figma.createFrame(); f.name = 'Ring'; f.resize(size, size); f.fills = []; f.clipsContent = false;
  const tr = figma.createEllipse(); tr.resize(size, size); tr.arcData = { startingAngle: 0, endingAngle: 2 * Math.PI, innerRadius: 0.84 }; tr.fills = [tv(t, 'text/tertiary')]; tr.opacity = 0.4;
  const pr = figma.createEllipse(); pr.resize(size, size); pr.arcData = { startingAngle: -Math.PI / 2, endingAngle: -Math.PI / 2 + 2 * Math.PI * p, innerRadius: 0.84 }; pr.fills = [tv(t, color || 'accent/default')];
  f.appendChild(tr); f.appendChild(pr);
  return f;
}

const WIDGETS = [
  ['Clock', '4x2', 'VERTICAL', async (c, t) => {
    c.primaryAxisAlignItems = 'CENTER';
    c.appendChild(await text('09:41', 'Display/Clock', t));
    c.appendChild(await text('Friday, 9 October', 'Title/Medium', t, 'text/secondary'));
    const r = row({ gap: 6 }); r.appendChild(glyph('ui-alarm', t, 14, 'text/secondary')); r.appendChild(await text('06:30 · Tomorrow', 'Label/Medium', t, 'text/secondary')); c.appendChild(r);
  }],
  ['Clock', '2x2', 'VERTICAL', async (c, t) => {
    c.primaryAxisAlignItems = 'CENTER';
    c.appendChild(await text('09', 'Display/Large', t));
    c.appendChild(await text('41', 'Display/Large', t, 'accent/default'));
    c.appendChild(await text('Fri 9 Oct', 'Label/Medium', t, 'text/secondary'));
  }],
  ['Calendar', '2x2', 'VERTICAL', async (c, t) => {
    c.appendChild(await text('FRIDAY', 'Label/Medium', t, 'accent/default'));
    c.appendChild(await text('9', 'Display/Large', t));
    const ev = row({ gap: 8, align: 'MIN' }); ev.appendChild(rect(3, 30, 1.5, tv(t, 'accent/default')));
    const ec = col(); ec.appendChild(await text('Design review', 'Label/Medium', t)); ec.appendChild(await text('10:30 – 11:15', 'Caption/Icon', t, 'text/secondary')); ev.appendChild(ec);
    c.appendChild(ev);
    c.appendChild(await text('+2 more today', 'Caption/Icon', t, 'text/tertiary'));
  }],
  ['Calendar', '4x1', 'HORIZONTAL', async (c, t) => {
    c.counterAxisAlignItems = 'CENTER'; c.itemSpacing = 14;
    const d = col({ align: 'CENTER' }); d.appendChild(await text('FRI', 'Label/Medium', t, 'accent/default')); d.appendChild(await text('9', 'Title/Large', t)); c.appendChild(d);
    c.appendChild(rect(1, 40, 0, tv(t, 'text/tertiary')));
    const e = col(); e.appendChild(await text('Design review', 'Title/Medium', t)); e.appendChild(await text('10:30 – 11:15 · Meet', 'Label/Medium', t, 'text/secondary')); c.appendChild(e);
    spacer(c);
    c.appendChild(await text('+2', 'Label/Medium', t, 'text/tertiary'));
  }],
  ['Weather', '2x2', 'VERTICAL', async (c, t) => {
    const top = row(); add(c, top); top.layoutSizingHorizontal = 'FILL';
    top.appendChild(glyph('ui-cloud-sun', t, 28)); spacer(top); top.appendChild(await text('Now', 'Caption/Icon', t, 'text/tertiary'));
    c.appendChild(await text('24°', 'Display/Large', t));
    c.appendChild(await text('Partly cloudy', 'Label/Medium', t));
    c.appendChild(await text('H 27°  L 19°', 'Caption/Icon', t, 'text/secondary'));
  }],
  ['Weather', '4x1', 'HORIZONTAL', async (c, t) => {
    c.counterAxisAlignItems = 'CENTER'; c.itemSpacing = 10;
    c.appendChild(glyph('ui-cloud-sun', t, 28));
    c.appendChild(await text('24°', 'Title/Large', t));
    const d = col(); d.appendChild(await text('Partly cloudy', 'Label/Medium', t)); d.appendChild(await text('H 27° L 19°', 'Caption/Icon', t, 'text/secondary')); c.appendChild(d);
    spacer(c);
    for (const [h, g, tmp] of [['11', 'ui-sun', '25°'], ['12', 'ui-cloud-sun', '26°'], ['13', 'ui-rain', '24°']]) {
      const hc = col({ gap: 2, align: 'CENTER' });
      hc.appendChild(await text(h, 'Caption/Icon', t, 'text/tertiary')); hc.appendChild(glyph(g, t, 16, 'text/secondary')); hc.appendChild(await text(tmp, 'Caption/Icon', t));
      c.appendChild(hc);
    }
  }],
  ['Battery', '2x1', 'HORIZONTAL', async (c, t) => {
    c.counterAxisAlignItems = 'CENTER'; c.itemSpacing = 12;
    const r = ring(42, 0.82, t);
    const g = glyph('ui-device', t, 16); r.appendChild(g); g.x = 13; g.y = 13;
    c.appendChild(r);
    const d = col(); d.appendChild(await text('82%', 'Title/Medium', t)); d.appendChild(await text('Buds 64%', 'Caption/Icon', t, 'text/secondary')); c.appendChild(d);
  }],
  ['Alarm', '2x1', 'HORIZONTAL', async (c, t) => {
    c.counterAxisAlignItems = 'CENTER'; c.itemSpacing = 12;
    c.appendChild(glyph('ui-alarm', t, 24));
    const d = col(); d.appendChild(await text('06:30', 'Title/Large', t)); d.appendChild(await text('Tomorrow', 'Caption/Icon', t, 'text/secondary')); c.appendChild(d);
  }],
  ['Media', '4x1', 'HORIZONTAL', async (c, t) => {
    c.counterAxisAlignItems = 'CENTER'; c.itemSpacing = 12;
    const art = rect(48, 48, 12); art.name = 'Artwork';
    art.fills = [{ type: 'GRADIENT_LINEAR', gradientTransform: [[1, 0, 0], [0, 1, 0]], gradientStops: [{ position: 0, color: rgba('#5B7CFA') }, { position: 1, color: rgba('#E2557A') }] }];
    c.appendChild(art);
    const d = col(); d.appendChild(await text('Midnight City', 'Label/Medium', t)); d.appendChild(await text('M83 · Spotify', 'Caption/Icon', t, 'text/secondary')); c.appendChild(d);
    spacer(c);
    const ctl = row({ gap: 14 }); ctl.appendChild(glyph('ui-prev', t, 20)); ctl.appendChild(glyph('ui-play', t, 22)); ctl.appendChild(glyph('ui-next', t, 20)); c.appendChild(ctl);
  }],
];

async function buildWidgets(page) {
  const b = await board('Widgets', 'Native Compose glass widgets (Render=Glass) and the Glance fallback exported to other launchers (Render=Glance: RemoteViews cannot blur, so tokens map to a translucent tint + 1 px border). Data: Clock (system), Calendar (READ_CALENDAR), Weather (Open-Meteo + coarse location), Battery (sticky broadcast), Media (MediaSession via notification access), Alarm (AlarmManager).', nextX(page), 0);
  for (const [type, size, dir, fill] of WIDGETS) {
    await heading(b, `${type} · ${size.replace('x', '×')}`);
    const holder = al('HORIZONTAL', { name: `${type} ${size}` }); b.appendChild(holder);
    const comps = [];
    for (const t of THEMES) for (const render of ['Glass', 'Glance']) {
      const c = figma.createComponent();
      c.name = `Theme=${t}, Render=${render}`;
      c.layoutMode = dir;
      c.paddingLeft = c.paddingRight = 16; c.paddingTop = c.paddingBottom = 14; c.itemSpacing = 4;
      fixed(c, SZ[size][0], SZ[size][1]);
      await glass(c, t, 'regular', size[0] === '2' ? 24 : 28, render === 'Glance');
      await fill(c, t);
      comps.push(c);
      WIDGET[`${type}${size}${t}${render}`] = c;
    }
    makeSet(comps, holder, `Widget/${type} ${size.replace('x', '×')}`, `${type} widget, ${size.replace('x', '×')} cells. Glass = in-launcher Compose; Glance = exported AppWidget.`);
  }
  return b;
}
function widget(type, size, t, render) { return WIDGET[`${type}${size}${t}${render || 'Glass'}`].createInstance(); }

// ---------------------------------------------------------------- screens
function place(parent, node, x, y) { parent.appendChild(node); node.x = x; node.y = y; return node; }
function centerX(node) { return (W - node.width) / 2; }
function screenFrame(t, name) {
  const f = figma.createFrame(); f.name = `${name} · ${t}`; f.resize(W, H); f.clipsContent = true; f.cornerRadius = 0;
  f.fills = [solid(t === 'light' ? '#EEF0F6' : '#0A0C14')];
  f.appendChild(wallpaperInstance(t, W, H));
  return f;
}
function chrome(f, t, withStatus) {
  if (withStatus !== false) place(f, STATUS[t].createInstance(), 0, 0);
  place(f, GESTURE[t].createInstance(), 0, H - 22);
}
function scrim(f, t, a) { const r = rect(W, H, 0, solid('#000000', a)); r.name = 'Scrim'; f.appendChild(r); return r; }
function blurLayer(f, t, radius) {
  const r = rect(W, H, 0, tv(t, 'glass/tint-thin')); r.name = 'Backdrop blur';
  r.effects = [{ type: 'BACKGROUND_BLUR', blurType: 'NORMAL', radius: radius || 30, visible: true }];
  f.appendChild(r); return r;
}
function bottomBar(f, t, page) {
  const ind = INDICATOR[t + page].createInstance(); place(f, ind, centerX(ind), 592);
  place(f, SEARCH[t].createInstance(), 18, 612);
  place(f, DOCK[t].createInstance(), 18, 674);
}
function homeWidgets(parent, t, dx, dy) {
  dx = dx || 0; dy = dy || 0;
  place(parent, widget('Clock', '4x2', t), 18 + dx, 48 + dy);
  place(parent, widget('Weather', '2x2', t), 18 + dx, 224 + dy);
  place(parent, widget('Calendar', '2x2', t), 186 + dx, 224 + dy);
  place(parent, widget('Media', '4x1', t), 18 + dx, 400 + dy);
  place(parent, widget('Battery', '2x1', t), 18 + dx, 488 + dy);
  place(parent, widget('Alarm', '2x1', t), 186 + dx, 488 + dy);
}
const GRID_APPS = ['gallery', 'calendar', 'clock', 'mail', 'maps', 'video', 'music', 'weather', 'notes', 'files', 'store', 'settings', 'health', 'wallet', 'drive', 'calculator', 'netflix', 'tasks', 'translate'];
function appGrid(parent, t, x0, y0, apps, cols, folderAt) {
  cols = cols || 4;
  let i = 0;
  for (let k = 0; k < apps.length + (folderAt !== undefined ? 1 : 0); k++) {
    const x = x0 + (k % cols) * 84, y = y0 + Math.floor(k / cols) * 96;
    if (k === folderAt) { place(parent, FOLDER[t].createInstance(), x, y); continue; }
    place(parent, appIcon(t, apps[i++]), x, y);
  }
}

async function scrHome1(t) { const f = screenFrame(t, 'Home · Widgets'); chrome(f, t); homeWidgets(f, t); bottomBar(f, t, 1); return f; }
async function scrHome2(t) { const f = screenFrame(t, 'Home · Apps'); chrome(f, t); appGrid(f, t, 18, 52, GRID_APPS, 4, 5); bottomBar(f, t, 2); return f; }

async function scrDrawer(t) {
  const f = screenFrame(t, 'App drawer');
  blurLayer(f, t, 24);
  chrome(f, t);
  const sheet = al('VERTICAL', { gap: 16, px: 18, py: 14, name: 'Drawer sheet' });
  fixed(sheet, W, H - 44); await glass(sheet, t, 'thick', 32); sheet.bottomLeftRadius = 0; sheet.bottomRightRadius = 0;
  sheet.primaryAxisSizingMode = 'FIXED';
  place(f, sheet, 0, 44);
  const hr = row({ justify: 'CENTER' }); add(sheet, hr, { fillW: true }); hr.appendChild(rect(36, 4, 2, tv(t, 'text/tertiary')));
  const s = SEARCH[t].createInstance(); add(sheet, s, { fillW: true });
  sheet.appendChild(await text('Suggested', 'Label/Medium', t, 'text/secondary'));
  const sug = row({ gap: 12, align: 'MIN' }); for (const n of ['chat', 'instagram', 'music', 'maps']) sug.appendChild(appIcon(t, n)); sheet.appendChild(sug);
  add(sheet, rect(W - 36, 1, 0, tv(t, 'glass/border')));
  sheet.appendChild(await text('All apps', 'Label/Medium', t, 'text/secondary'));
  const names = Object.keys(APPS).sort((a, b) => label(a).localeCompare(label(b))).slice(0, 20);
  for (let r = 0; r < 5; r++) { const rr = row({ gap: 12, align: 'MIN' }); for (const n of names.slice(r * 4, r * 4 + 4)) rr.appendChild(appIcon(t, n)); sheet.appendChild(rr); }
  // alphabet rail
  const rail = col({ gap: 2, align: 'CENTER', name: 'A–Z rail' });
  for (const ch of 'ABCDEFGHIJKLMNOPQRSTUVWXYZ'.split('').filter((_, i) => i % 2 === 0)) rail.appendChild(await text(ch, 'Caption/Icon', t, 'text/tertiary'));
  place(f, rail, W - 14, 270);
  return f;
}

async function scrSearch(t) {
  const f = screenFrame(t, 'Search');
  blurLayer(f, t, 30);
  chrome(f, t);
  const s = SEARCH[t].createInstance(); place(f, s, 18, 52);
  const q = s.findOne(n => n.type === 'TEXT' && n.name === 'Query'); if (q) { q.characters = 'ca'; q.fills = [tv(t, 'text/primary')]; }
  const card = al('VERTICAL', { gap: 14, pad: 18, name: 'Results' });
  await glass(card, t, 'thick', 28);
  card.appendChild(await text('Apps', 'Label/Medium', t, 'text/secondary'));
  const apps = row({ gap: 12, align: 'MIN' }); for (const n of ['camera', 'calendar', 'calculator']) apps.appendChild(appIcon(t, n)); card.appendChild(apps);
  const resultRow = async (lead, title, sub) => {
    const r = row({ gap: 12 }); r.appendChild(lead);
    const c = col(); c.appendChild(await text(title, 'Body/Regular', t)); if (sub) c.appendChild(await text(sub, 'Caption/Icon', t, 'text/secondary')); r.appendChild(c);
    return r;
  };
  card.appendChild(await text('Contacts', 'Label/Medium', t, 'text/secondary'));
  const av = al('HORIZONTAL', { justify: 'CENTER', align: 'CENTER' }); fixed(av, 36, 36); av.cornerRadius = 18; av.fills = [tv(t, 'accent/default')];
  av.appendChild(await text('C', 'Title/Medium', 'dark'));
  card.appendChild(await resultRow(av, 'Carla Mendes', 'Mobile · 077 123 4567'));
  card.appendChild(await text('Settings', 'Label/Medium', t, 'text/secondary'));
  card.appendChild(await resultRow(tile(t, 'settings'), 'Call settings', 'Phone › Settings'));
  card.appendChild(await text('Web', 'Label/Medium', t, 'text/secondary'));
  card.appendChild(await resultRow(glyph('ui-search', t, 20, 'text/secondary'), 'ca', 'Search the web'));
  fixW(card, 324);
  place(f, card, 18, 116);
  return f;
}

async function scrPicker(t) {
  const f = screenFrame(t, 'Widget picker');
  homeWidgets(f, t);
  scrim(f, t, t === 'light' ? 0.18 : 0.35);
  chrome(f, t);
  const sheet = al('VERTICAL', { gap: 14, px: 18, py: 14, name: 'Widget sheet' });
  fixed(sheet, W, H - 150); await glass(sheet, t, 'thick', 32); sheet.bottomLeftRadius = 0; sheet.bottomRightRadius = 0;
  place(f, sheet, 0, 150);
  const hr = row({ justify: 'CENTER' }); add(sheet, hr, { fillW: true }); hr.appendChild(rect(36, 4, 2, tv(t, 'text/tertiary')));
  sheet.appendChild(await text('Widgets', 'Title/Large', t));
  const chips = row({ gap: 8 });
  for (const [c, on] of [['All', true], ['Time', false], ['Weather', false], ['Calendar', false]]) {
    const chip = al('HORIZONTAL', { px: 14, py: 7 }); chip.cornerRadius = 16;
    if (on) chip.fills = [tv(t, 'accent/default')]; else await glass(chip, t, 'thin', 16);
    chip.appendChild(await text(c, 'Label/Medium', on ? 'dark' : t)); chips.appendChild(chip);
  }
  sheet.appendChild(chips);
  sheet.appendChild(await text('Clock · 4×2', 'Label/Medium', t, 'text/secondary'));
  sheet.appendChild(widget('Clock', '4x2', t));
  sheet.appendChild(await text('Weather · 4×1', 'Label/Medium', t, 'text/secondary'));
  sheet.appendChild(widget('Weather', '4x1', t));
  sheet.appendChild(await text('Calendar · 2×2   ·   Alarm · 2×1', 'Label/Medium', t, 'text/secondary'));
  const r = row({ gap: 12, align: 'MIN' }); r.appendChild(widget('Calendar', '2x2', t)); r.appendChild(widget('Alarm', '2x1', t)); sheet.appendChild(r);
  return f;
}

async function scrEdit(t) {
  const f = screenFrame(t, 'Edit mode');
  chrome(f, t);
  const top = row({ gap: 8 }); fixed(top, 324, 36); top.primaryAxisAlignItems = 'SPACE_BETWEEN';
  top.appendChild(await text('Page 1 of 2', 'Label/Medium', t, 'text/secondary'));
  const done = al('HORIZONTAL', { px: 16, py: 8 }); done.cornerRadius = 18; done.fills = [tv(t, 'accent/default')]; done.appendChild(await text('Done', 'Label/Medium', 'dark')); top.appendChild(done);
  place(f, top, 18, 42);
  const pageF = figma.createFrame(); pageF.name = 'Page preview'; pageF.resize(W, 560); pageF.fills = []; pageF.clipsContent = false;
  homeWidgets(pageF, t, 0, -40);
  pageF.rescale(0.8);
  const outline = figma.createFrame(); outline.name = 'Page outline'; outline.resize(300, 470); outline.fills = [tv(t, 'glass/tint-thin')]; outline.cornerRadius = 28;
  outline.strokes = [tv(t, 'glass/border')]; outline.strokeWeight = 1.5; outline.dashPattern = [6, 6]; outline.clipsContent = false;
  place(f, outline, 30, 92);
  place(outline, pageF, 6, -4);
  // page thumbnails
  const thumbs = row({ gap: 10, name: 'Pages' });
  for (const on of [true, false]) { const th = figma.createFrame(); th.resize(36, 64); th.cornerRadius = 8; th.fills = [tv(t, on ? 'glass/tint-thick' : 'glass/tint-thin')]; th.strokes = [tv(t, on ? 'accent/default' : 'glass/border')]; th.strokeWeight = on ? 2 : 1; thumbs.appendChild(th); }
  const plus = al('HORIZONTAL', { justify: 'CENTER', align: 'CENTER' }); fixed(plus, 36, 64); plus.cornerRadius = 8; plus.fills = []; plus.strokes = [tv(t, 'glass/border')]; plus.dashPattern = [4, 4]; plus.appendChild(glyph('ui-plus', t, 16, 'text/secondary')); thumbs.appendChild(plus);
  place(f, thumbs, (W - 128) / 2, 580);
  const bar = al('HORIZONTAL', { name: 'Edit toolbar', align: 'CENTER', justify: 'SPACE_BETWEEN', px: 28 }); fixed(bar, 324, 80); await glass(bar, t, 'thick', 30);
  for (const [g, l] of [['ui-wallpaper', 'Wallpaper'], ['ui-widgets', 'Widgets'], ['settings', 'Settings']]) {
    const c = col({ gap: 4, align: 'CENTER' }); c.appendChild(glyph(g, t, 22)); c.appendChild(await text(l, 'Caption/Icon', t)); bar.appendChild(c);
  }
  place(f, bar, 18, 674);
  return f;
}

async function scrFolder(t) {
  const f = screenFrame(t, 'Folder open');
  appGrid(f, t, 18, 52, GRID_APPS, 4, 5);
  bottomBar(f, t, 2);
  blurLayer(f, t, 28);
  chrome(f, t);
  const panel = al('VERTICAL', { gap: 18, pad: 22, align: 'CENTER', name: 'Folder panel' });
  await glass(panel, t, 'thick', 32);
  panel.appendChild(await text('Social', 'Title/Large', t));
  const apps = ['chat', 'instagram', 'x', 'telegram', 'facebook', 'discord', 'work'];
  for (let r = 0; r < 3; r++) { const rr = row({ gap: 12, align: 'MIN' }); for (const n of apps.slice(r * 3, r * 3 + 3)) rr.appendChild(appIcon(t, n)); if (rr.children.length) panel.appendChild(rr); }
  place(f, panel, 0, 0); panel.x = (W - panel.width) / 2; panel.y = (H - panel.height) / 2 - 20;
  return f;
}

async function toggle(t, on) {
  const tg = figma.createFrame(); tg.name = 'Toggle'; tg.resize(44, 26); tg.cornerRadius = 13;
  tg.fills = on ? [tv(t, 'accent/default')] : [tv(t, 'glass/tint-thick')]; tg.strokes = on ? [] : [tv(t, 'glass/border')];
  const k = figma.createEllipse(); k.resize(22, 22); k.x = on ? 20 : 2; k.y = 2; k.fills = [solid('#FFFFFF')];
  k.effects = [{ type: 'DROP_SHADOW', color: { r: 0, g: 0, b: 0, a: 0.15 }, offset: { x: 0, y: 1 }, radius: 3, spread: 0, visible: true, blendMode: 'NORMAL' }];
  tg.appendChild(k); return tg;
}
async function slider(t, v) {
  const s = figma.createFrame(); s.name = 'Slider'; s.resize(120, 22); s.fills = []; s.clipsContent = false;
  const tr = rect(120, 4, 2, tv(t, 'text/tertiary')); tr.y = 9; tr.opacity = 0.5; s.appendChild(tr);
  const fi = rect(120 * v, 4, 2, tv(t, 'accent/default')); fi.y = 9; s.appendChild(fi);
  const k = figma.createEllipse(); k.resize(22, 22); k.x = 120 * v - 11; k.fills = [solid('#FFFFFF')]; k.strokes = [tv(t, 'glass/border')];
  k.effects = [{ type: 'DROP_SHADOW', color: { r: 0, g: 0, b: 0, a: 0.15 }, offset: { x: 0, y: 1 }, radius: 3, spread: 0, visible: true, blendMode: 'NORMAL' }];
  s.appendChild(k); return s;
}
async function scrSettings(t) {
  const f = screenFrame(t, 'Settings');
  blurLayer(f, t, 40);
  chrome(f, t);
  const body = al('VERTICAL', { gap: 10, name: 'Settings body' });
  body.appendChild(await text('Saber', 'Display/Large', t));
  const groups = [
    ['Appearance', [['Theme', 'Auto'], ['Glass intensity', 'slider'], ['Wallpaper', 'Aurora Night']]],
    ['Home screen', [['Grid', '4 × 5'], ['Icon style', 'Glass tile'], ['Show labels', 'on']]],
    ['Widgets', [['Weather units', '°C'], ['Calendar accounts', '2'], ['Glance widgets', 'on']]],
    ['About', [['Version', '0.1.0']]],
  ];
  for (const [g, rows] of groups) {
    body.appendChild(await text(g, 'Label/Medium', t, 'text/secondary'));
    const card = al('VERTICAL', { name: g }); await glass(card, t, 'regular', 22);
    add(body, card);
    for (let i = 0; i < rows.length; i++) {
      const [l, v] = rows[i];
      const r = row({ gap: 8, px: 16, py: 12, name: l }); add(card, r);
      r.appendChild(await text(l, 'Body/Regular', t)); spacer(r);
      if (v === 'on') r.appendChild(await toggle(t, true));
      else if (v === 'slider') r.appendChild(await slider(t, 0.6));
      else { r.appendChild(await text(v, 'Body/Regular', t, 'text/secondary')); r.appendChild(glyph('ui-chevron', t, 16, 'text/tertiary')); }
      if (i < rows.length - 1) { const d = rect(292, 1, 0, tv(t, 'glass/border')); add(card, d); }
    }
  }
  fixW(body, 324);
  for (const c of body.children) if (c.type === 'FRAME') { c.layoutSizingHorizontal = 'FILL'; for (const r of c.children) if (r.type === 'FRAME') r.layoutSizingHorizontal = 'FILL'; }
  place(f, body, 18, 48);
  return f;
}

async function buildScreens(page) {
  const builders = [['Home · Widgets', scrHome1], ['Home · Apps', scrHome2], ['App drawer', scrDrawer], ['Search', scrSearch], ['Widget picker', scrPicker], ['Edit mode', scrEdit], ['Folder open', scrFolder], ['Settings', scrSettings]];
  const errors = [];
  for (let ti = 0; ti < THEMES.length; ti++) {
    const t = THEMES[ti];
    for (let i = 0; i < builders.length; i++) {
      const [name, fn] = builders[i];
      try {
        // Figma already labels top-level frames with their name; no caption text.
        const f = await fn(t);
        page.appendChild(f); f.x = i * 440; f.y = ti * 920; mark(f);
      } catch (e) { errors.push(`${name}/${t}: ${e.message}`); }
    }
  }
  return errors;
}

// ---------------------------------------------------------------- main
async function getPage(name, i) {
  let p = figma.root.children.find(pg => pg.name === name);
  if (!p && figma.root.children[i]) { p = figma.root.children[i]; p.name = name; }
  if (!p) { p = figma.createPage(); p.name = name; }
  await p.loadAsync();
  return p;
}
function clean(page) { for (const n of page.children.slice()) if (n.getPluginData(KEY) === 'gen') n.remove(); }

async function main() {
  const pages = [];
  for (let i = 0; i < PAGE_NAMES.length; i++) pages.push(await getPage(PAGE_NAMES[i], i));
  const [p1, p2, p3] = pages;
  pages.forEach(clean);
  await ensureFoundations(p1);
  const report = [];

  await figma.setCurrentPageAsync(p2);
  figma.notify('Saber: building icon pack…', { timeout: 1500 });
  const fails = await buildIcons(p2);
  if (fails.length) report.push('Glyph failures: ' + fails.join('; '));

  await figma.setCurrentPageAsync(p1);
  figma.notify('Saber: building components…', { timeout: 1500 });
  await buildComponents(p1);

  await figma.setCurrentPageAsync(p2);
  await buildIconPreview(p2);
  figma.notify('Saber: building widgets…', { timeout: 1500 });
  await buildWidgets(p2);

  await figma.setCurrentPageAsync(p3);
  figma.notify('Saber: building screens…', { timeout: 1500 });
  const se = await buildScreens(p3);
  if (se.length) report.push('Screen errors: ' + se.join('; '));

  figma.viewport.scrollAndZoomIntoView(p3.children);
  return report;
}

main().then(report => {
  const msg = report.length ? 'Saber: done with issues — ' + report.join(' | ') : 'Saber: done ✓';
  console.log(msg);
  figma.closePlugin(msg.slice(0, 400));
}).catch(e => {
  console.error(e);
  figma.closePlugin('Saber: failed — ' + (e && e.message ? e.message : e) + (e && e.stack ? ' @ ' + String(e.stack).split('\n')[1] : ''));
});
