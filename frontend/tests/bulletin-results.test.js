import test from 'node:test';
import assert from 'node:assert/strict';
import {summarizePractice} from '../src/bulletin-results.js';
test('results distinguish wrong answers, omissions and ungraded questions per course',()=>{
 const qs=[{course_name:'Álgebra',result:'CORRECTA'},{course_name:'Álgebra',result:'INCORRECTA'},{course_name:'Física',result:'SIN_RESPONDER'},{course_name:'Física',result:'NO_CALIFICADA'},{course_name:'Física'}];
 const summary=summarizePractice(qs);
 assert.deepEqual([summary.correct,summary.incorrect,summary.omitted,summary.excluded],[1,1,1,1]);
 assert.equal(summary.courses[0].incorrect,1);assert.equal(summary.courses[1].omitted,1);
 assert.deepEqual(summarizePractice([]),{correct:0,incorrect:0,omitted:0,excluded:0,courses:[]});
});
