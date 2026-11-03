import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {createHash} from 'node:crypto';
const readJson = path => JSON.parse(readFileSync(new URL(path,import.meta.url),'utf8').replace(/^\uFEFF/,''));
test('every catalog university has a local, intact logo with a documented source',()=>{
 const catalog=readJson('../../backend/src/main/resources/universities-peru.json');
 const logos=readJson('../src/university-logos.json');
 for(const university of catalog){
  const logo=logos[university.acronym];assert.ok(logo,university.acronym);
  assert.match(logo.src,/^\/universities\/[a-z0-9]+\.(png|jpg|svg|webp|ico|gif)$/);
  assert.ok(logo.source.startsWith('https://'));assert.ok(logo.asset_source.startsWith('https://'));
  const data=readFileSync(new URL('../public'+logo.src,import.meta.url));
  assert.equal(createHash('sha256').update(data).digest('hex'),logo.sha256,university.acronym);
 }
 assert.equal(Object.keys(logos).length,catalog.length);
});
