#!/usr/bin/env node
/* Colour-code every muscle and label it, so a wrong outline is obvious.
   node tools/figure-debug.js  ->  figure-debug.png  */
const fs=require('fs'),vm=require('vm'),path=require('path');
const ctx=vm.createContext({console,Math,JSON});
['10-data.js','12-muscles.js','14-figure.js'].forEach(f=>vm.runInContext(fs.readFileSync(path.join(__dirname,'..','src',f),'utf8'),ctx,{filename:f}));
const V=ctx.VOS;
const PAL=['#e6194b','#3cb44b','#ffe119','#4363d8','#f58231','#911eb4','#46f0f0','#f032e6','#bcf60c','#fabebe','#008080','#e6beff','#9a6324','#fffac8','#800000','#aaffc3','#808000','#ffd8b1','#000075','#a9a9a9','#ff7f50','#7fffd4','#dda0dd','#20b2aa'];
const ids=Object.keys(V.MUSCLES);
const css=ids.map((m,i)=>`.mz[data-m="${m}"]{fill:${PAL[i%PAL.length]};fill-opacity:.85}`).join('\n');
const legend=ids.map((m,i)=>`<span style="color:${PAL[i%PAL.length]}">■</span> ${m}`).join(' &nbsp; ');
const figs=[['male',false],['male',true]].map(([s,b])=>V.Figure.svg({sex:s,back:b,idPrefix:s+(b?'b':'f')+'-'}));
const grid=`<svg class="grid" viewBox="0 0 240 540" style="position:absolute;inset:0;height:100%;width:100%">
${Array.from({length:11},(_,i)=>`<line x1="0" y1="${i*50}" x2="240" y2="${i*50}" stroke="#2a3140" stroke-width=".5"/><text x="2" y="${i*50-2}" fill="#4a5568" font-size="7">${i*50}</text>`).join('')}
${Array.from({length:9},(_,i)=>`<line x1="${i*30}" y1="0" x2="${i*30}" y2="540" stroke="#2a3140" stroke-width=".5"/><text x="${i*30+1}" y="10" fill="#4a5568" font-size="7">${i*30}</text>`).join('')}
</svg>`;
const html=`<!doctype html><meta charset="utf-8"><style>
body{background:#0a0b0d;margin:0;padding:10px;font:11px sans-serif;color:#8b93a3}
.row{display:flex;gap:30px}
.cell{position:relative;width:340px}
svg.figure{height:660px;width:auto;display:block;margin:auto;position:relative;z-index:2}
.body{fill:none;stroke:#fff;stroke-width:1.4}
.hairline{fill:#0a0b0d;stroke:#fff;stroke-width:1.2}
.mz{stroke:#0a0b0d;stroke-width:.7}
.ln{stroke:#fff;stroke-width:.8;fill:none;opacity:.6}
${css}
</style><div class="row">${figs.map(f=>`<div class="cell">${f}</div>`).join('')}</div>
<div style="margin-top:8px;line-height:1.8">${legend}</div>`;
fs.writeFileSync('/tmp/figdebug.html',html);
const {chromium}=require('playwright');
(async()=>{const br=await chromium.launch({executablePath:'/opt/pw-browsers/chromium'}).catch(()=>chromium.launch());
const p=await br.newPage({viewport:{width:760,height:760},deviceScaleFactor:2});
await p.goto('file:///tmp/figdebug.html');await p.waitForTimeout(200);
await p.screenshot({path:path.join(__dirname,'..','figure-debug.png'),fullPage:true});await br.close();})();
