// Original MIT-licensed semantic pixel primitives. No external artwork or image-model output.
// Development prototypes, deliberately unpolished. Run from the repository: node tools/journal/generate-prototypes.mjs
import { mkdirSync, writeFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { deflateSync } from 'node:zlib';
import { fileURLToPath } from 'node:url';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '../../src/main/resources/assets/mobvats');
function crc(bytes) {
  let c = 0xffffffff;
  for (const b of bytes) { c ^= b; for (let i=0;i<8;i++) c = (c>>>1) ^ ((c&1)?0xedb88320:0); }
  return (c^0xffffffff)>>>0;
}
function chunk(name, data) {
  const body = Buffer.concat([Buffer.from(name),data]), header = Buffer.alloc(4), tail=Buffer.alloc(4);
  header.writeUInt32BE(data.length); tail.writeUInt32BE(crc(body));
  return Buffer.concat([header,body,tail]);
}
function png(path,w,h,pixel) {
  const raw=Buffer.alloc((w*4+1)*h), ihdr=Buffer.alloc(13);
  ihdr.writeUInt32BE(w); ihdr.writeUInt32BE(h,4); ihdr[8]=8; ihdr[9]=6;
  for(let y=0;y<h;y++) for(let x=0;x<w;x++) {
    const rgba=pixel(x,y); for(let c=0;c<4;c++) raw[y*(w*4+1)+1+x*4+c]=Math.max(0,Math.min(255,Math.round(rgba[c])));
  }
  const target=resolve(root,`textures/gui/journal/${path}.png`); mkdirSync(dirname(target),{recursive:true});
  writeFileSync(target,Buffer.concat([Buffer.from([137,80,78,71,13,10,26,10]),chunk('IHDR',ihdr),chunk('IDAT',deflateSync(raw)),chunk('IEND',Buffer.alloc(0))]));
}
function json(path,value) { const target=resolve(root,path); mkdirSync(dirname(target),{recursive:true}); writeFileSync(target,JSON.stringify(value,null,2)+'\n'); }
function noise(x,y,k) { let n=Math.imul(x+311*k,374761393)^Math.imul(y+59,668265263); n=Math.imul(n^(n>>>13),1274126177); return ((n^(n>>>16))>>>0)/4294967296; }
const counts={paper:3,masks:5,blood:3,chemical:3,ink:2,burns:2,wear:2};
for(const [category,count] of Object.entries(counts)) for(let i=0;i<count;i++) {
  const id=`${category}_${i+1}`, path=category==='paper'?`paper/${id}`:`damage/${category}/${id}`;
  const w=category==='paper'?256:80, h=category==='paper'?332:80;
  png(path,w,h,(x,y)=>{
    const n=noise(Math.floor(x/2),Math.floor(y/2),i+3);
    if(category==='paper') {
      const edge=Math.max(0,1-Math.min(x,w-1-x,y,h-1-y)/18)*12;
      const grain=(n-0.5)*8-edge;
      return [239-i*12+grain,228-i*15+grain,197-i*18+grain,255];
    }
    const xx=(Math.floor(x/2)*2-40)/34, yy=(Math.floor(y/2)*2-40)/34;
    const r=Math.sqrt(xx*xx+yy*yy), irregular=0.72+0.18*Math.sin(Math.atan2(yy,xx)*(5+i)+i)+n*0.12;
    if(category==='masks') {
      const inside=i===0 ? Math.abs(xx)<0.45+yy*0.35 && yy>-0.8 : i===1 ? r<irregular : i===2 ? yy>Math.abs(xx)*0.8-0.5 : i===3 ? Math.abs(yy+xx*0.4)<0.2+n*0.15 : r<irregular*0.7;
      return [255,255,255,inside?255:0];
    }
    if(category==='wear') {
      const crease=Math.abs(yy+xx*(i?0.4:-0.7));
      return [112,84,48,crease<0.12?150*(1-crease/0.12):0];
    }
    const alpha=r<irregular ? (0.45+0.55*(1-r/1.2))*(170+n*70) : 0;
    const color=category==='blood'?[99+i*12,22,19]:category==='chemical'?[69+i*13,111+i*9,55]:category==='ink'?[26,35,49]:[52,31,16];
    return [...color,alpha];
  });
  json(`journal/texture_library/${id}.json`,{texture:`mobvats:textures/gui/journal/${path}.png`,category,weight:1,
    placement:category==='masks'||category==='wear'?'edge':'anywhere',scale:[0.7,1.2],opacity:category==='masks'?[1,1]:[0.65,1],rotation:'free',mirror:true,minimum_severity:0});
}
png('binding/prototype',272,178,(x,y)=>{
  if(x<3||x>269||y<1||y>176) return [0,0,0,0];
  if(x>131&&x<140) return [66+Math.abs(x-136)*5,43,27,255];
  if(x>6&&x<266&&y>4&&y<174) return [161+(y%3)*12,145+(y%3)*9,111,255];
  return [71+((x+y)%4)*3,46,30,255];
});
console.log('Wrote 20 modular RGBA prototypes, binding, and asset metadata.');
