// A single HTML review page of every Rational SVG, each on the light paper and the dark
// ground side by side, with the captions from catalogue.json. No dependencies.
//
//   node .claude/skills/rational/review.mjs <outDir> <dest.html>
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
const here = path.dirname(fileURLToPath(import.meta.url));
const [,, outDir, dest] = process.argv;
const { styles } = JSON.parse(fs.readFileSync(path.join(here, 'catalogue.json'), 'utf8'));
const read = (f) => fs.readFileSync(path.join(outDir, f), 'utf8').replace(/<\?xml[^>]*>/, '');
let body = '';
for (const s of styles) {
  const files = fs.readdirSync(outDir).filter((f) => new RegExp(`^rational${s.name}\\d+\\.svg$`).test(f)).sort();
  if (!files.length) continue;
  body += `<section><h2>${s.name} <small>${s.group}</small></h2><p class="desc">${s.description}</p><div class="row">`;
  files.forEach((f, i) => {
    const svg = read(f);
    body += `<figure><div class="pair"><div class="panel light">${svg}</div><div class="panel dark">${svg}</div></div><figcaption><b>${s.name} ${i + 1}</b>${s.variants[i] ? ' · ' + s.variants[i] : ''} <code>${f}</code></figcaption></figure>`;
  });
  body += `</div></section>`;
}
const html = `<!doctype html><html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Rational decorations</title>
<style>
*{box-sizing:border-box}
body{margin:0;padding:32px 24px 64px;background:#f6f2e7;color:#1d1f20;font:15px/1.5 ui-sans-serif,system-ui,-apple-system,sans-serif}
h1{font:700 32px/1.1 Georgia,serif;margin:0 0 8px}.lead{max-width:70ch;margin:0 0 32px;color:#555}
section{margin:0 0 40px}h2{font:700 22px/1.2 Georgia,serif;margin:0 0 4px}h2 small{font:13px ui-sans-serif,system-ui,sans-serif;color:#888;margin-left:6px}
.desc{max-width:80ch;margin:0 0 14px;color:#555;font-size:14px}
.row{display:grid;grid-template-columns:repeat(auto-fit,minmax(min(100%,520px),1fr));gap:20px}
figure{margin:0}figcaption{font-size:13px;color:#555;margin-top:6px}code{font-size:12px;color:#888}
.pair{display:grid;grid-template-columns:1fr 1fr;border-radius:10px;overflow:hidden;border:1px solid rgb(29 31 32/.12)}
.panel{aspect-ratio:1;display:grid;place-items:center}.panel svg{width:100%;height:100%;display:block}
.light{background:#ece5d1;--gen-ink:#1d1f20;--gen-accent:#c84e22}
.dark{background:#161718;--gen-ink:#f2f0eb;--gen-accent:#ec8f62}
</style></head><body>
<h1>Rational decorations</h1>
<p class="lead">Generated in gart (arts/gen/src/gen/Rational.kt). Every placement comes from the Gaussian primes; a seed only picks where in the plane to look. Each SVG has no background and takes its two colours from the page (--gen-ink, --gen-accent), shown here on the light paper and the dark ground.</p>
${body}
</body></html>`;
fs.writeFileSync(dest, html);
console.log('wrote', dest, Math.round(html.length / 1024) + 'KB');
