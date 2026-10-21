// Full-size contact sheets of Rational pieces: light from the baked PNGs, dark from the
// themable SVGs (the dark look only exists in a browser, which reads the CSS variables).
//
//   node .claude/skills/rational/sheet.mjs <outDir> <destDir> [Style,Style,...] [--name sheet] [--cols 3]
//
// Writes <destDir>/<name>-light.png and <name>-dark.png (plus .jpg copies for phones).
// Needs Playwright: PLAYWRIGHT=/abs/path/to/node_modules/playwright/index.mjs if it is
// not resolvable from here (gart has no package.json).
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
const { chromium } = await import(process.env.PLAYWRIGHT ?? 'playwright');
const here = path.dirname(fileURLToPath(import.meta.url));
const args = process.argv.slice(2);
const opt = (k, d) => { const i = args.indexOf(k); return i < 0 ? d : args.splice(i, 2)[1]; };
const name = opt('--name', 'sheet');
const cols = Number(opt('--cols', 3));
const [outDir, destDir, pick] = args;
const { styles } = JSON.parse(fs.readFileSync(path.join(here, 'catalogue.json'), 'utf8'));
const chosen = pick ? pick.split(',').map((s) => styles.find((x) => x.name.toLowerCase() === s.trim().toLowerCase()) ?? { name: s.trim(), variants: [] }) : styles;
const pieces = chosen.flatMap((s) => {
  const found = fs.readdirSync(outDir).filter((f) => new RegExp(`^rational${s.name}\\d+\\.svg$`).test(f)).sort();
  return found.map((f, i) => ({ file: f.replace(/\.svg$/, ''), label: `${s.name} ${i + 1}`, note: s.variants[i] ?? '' }));
});
const THEMES = {
  light: { page: '#f6f2e7', ground: '#ece5d1', text: '#1d1f20', muted: '#6b6658', ink: '#1d1f20', accent: '#c84e22' },
  dark: { page: '#0f1011', ground: '#161718', text: '#f2f0eb', muted: '#9a968c', ink: '#f2f0eb', accent: '#ec8f62' },
};
const S = 800, GAP = 48, PAD = 64;
const width = cols * S + (cols - 1) * GAP + 2 * PAD;
const browser = await chromium.launch();
for (const [theme, t] of Object.entries(THEMES)) {
  const cells = pieces.map((p) => {
    const art = theme === 'light'
      ? `<img src="data:image/png;base64,${fs.readFileSync(path.join(outDir, p.file + '.png')).toString('base64')}">`
      : fs.readFileSync(path.join(outDir, p.file + '.svg'), 'utf8').replace(/<\?xml[^>]*>/, '');
    return `<figure><div class="art">${art}</div><figcaption><b>${p.label}</b>${p.note ? ' · ' + p.note : ''}</figcaption></figure>`;
  }).join('');
  const html = `<!doctype html><meta charset="utf-8"><style>
*{box-sizing:border-box}body{margin:0;padding:${PAD}px;background:${t.page};color:${t.text};font:24px/1.4 ui-sans-serif,system-ui,-apple-system,sans-serif;width:${width}px}
h1{font:700 48px/1.1 Georgia,serif;margin:0 0 40px}
.grid{display:grid;grid-template-columns:repeat(${cols},${S}px);gap:56px ${GAP}px}
figure{margin:0}.art{width:${S}px;height:${S}px;background:${t.ground};--gen-ink:${t.ink};--gen-accent:${t.accent}}
.art img,.art svg{width:${S}px;height:${S}px;display:block}
figcaption{margin-top:14px;color:${t.muted}}figcaption b{color:${t.text}}
</style><h1>Rational · ${theme}</h1><div class="grid">${cells}</div>`;
  const page = await browser.newPage({ viewport: { width, height: 1000 }, deviceScaleFactor: 1 });
  await page.setContent(html, { waitUntil: 'load' });
  const dest = path.join(destDir, `${name}-${theme}.png`);
  await page.screenshot({ path: dest, fullPage: true });
  await page.screenshot({ path: dest.replace(/\.png$/, '.jpg'), fullPage: true, type: 'jpeg', quality: 90 });
  await page.close();
  console.log('wrote', dest);
}
await browser.close();
