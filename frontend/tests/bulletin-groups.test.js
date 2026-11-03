import test from 'node:test';
import assert from 'node:assert/strict';
import { groupBulletins, matchesGroup, isReady } from '../src/bulletin-groups.js';
test('groups legacy full names and abbreviations without duplicating material', () => {
  const groups=groupBulletins([{name:'Universidad Nacional de Ingeniería',acronym:'UNI',public_university:true}], [{id:'1',university:'UNI'},{id:'2',university:'Universidad Nacional de Ingeniería'},{id:'3',university:'General'},{id:'4',university:'Otra institución'}]);
  assert.equal(groups.find(g=>g.key==='UNI').bulletins.length,2);
  assert.equal(groups.reduce((sum,g)=>sum+g.bulletins.length,0),4);
  assert.equal(groups.find(g=>g.key==='General').bulletins.length,1);
  assert.ok(groups.find(g=>g.key==='Otra institución'));
});
test('search finds accents, regions and bulletin titles; readiness reflects processing state', () => {
  const group={name:'Universidad del Centro',acronym:'UNCP',region:'Junín',bulletins:[{title:'Admisión 2026'}]};
  assert.ok(matchesGroup(group,'junin'));assert.ok(matchesGroup(group,'admision'));
  assert.ok(!matchesGroup(group,'Cusco'));
  assert.ok(isReady({processing_status:'LISTO_CON_OBSERVACIONES'}));
  assert.ok(!isReady({processing_status:'ESPERANDO_IA'}));
});
