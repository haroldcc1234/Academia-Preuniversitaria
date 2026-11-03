import test from 'node:test';
import assert from 'node:assert/strict';
import { canVisit, roleLinks } from './permissions.js';
test('Cada rol tiene navegación propia y rechaza URLs ajenas', () => {
 for (const [role, yes, no] of [
  ['DIRECTOR',['/users','/reports','/bulletins'],['/questions','/exams','/chat','/challenges']],
  ['DOCENTE',['/questions','/exams','/grades'],['/users','/reports','/bulletins','/chat']],
  ['ALUMNO',['/grades','/bulletins','/chat','/challenges'],['/questions','/users','/reports']]
 ]) {
  for(const path of yes) assert.equal(canVisit(role,path),true,`${role} ${path}`);
  for(const path of no) assert.equal(canVisit(role,path),false,`${role} ${path}`);
  assert.equal(new Set(roleLinks(role).map(([path])=>path)).size,roleLinks(role).length);
  assert.equal(canVisit(role,'/profile'),true);
 }
 assert.equal(canVisit('DESCONOCIDO','/profile'),false);
});
