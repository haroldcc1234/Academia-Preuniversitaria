export function summarizePractice(questions) {
  const totals = {correct:0, incorrect:0, omitted:0, excluded:0};
  const courses = new Map();
  for (const q of questions) {
    const key = ({CORRECTA:'correct',INCORRECTA:'incorrect',SIN_RESPONDER:'omitted',NO_CALIFICADA:'excluded'})[q.result];
    if (!key) continue;
    totals[key]++;
    const name=q.course_name||'General';
    if(!courses.has(name)) courses.set(name,{name,correct:0,incorrect:0,omitted:0,excluded:0});
    courses.get(name)[key]++;
  }
  return {...totals,courses:[...courses.values()]};
}
