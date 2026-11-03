import React, { useState, useEffect } from 'react';
import { ResponsiveContainer, LineChart, Line, XAxis, YAxis, Tooltip, CartesianGrid } from 'recharts';
import { api } from './api';
import { Modal, Field, Button, ErrorBox, Panel, DataTable, useLoad, option } from './ui';
import { useApp } from './App';

export function StudentProgress() {
  const { user } = useApp();
  const d = useLoad(user.role === 'ALUMNO' ? '/ai/progress' : null);
  if (user.role !== 'ALUMNO') return null;
  return <section><h2>Tu progreso de notas por curso</h2><ErrorBox error={d.error} />
    <Button variant="subtle" onClick={d.reload}>Actualizar notas</Button>
    {d.loading && <p>Cargando notas…</p>}
    {(d.data || []).map(r => <div className="exam-question" key={r.id}>
      <h3>{r.course_name} · {r.section_name} · {r.cycle_name}</h3>
      <p>Promedio: {r.grade == null ? 'Sin notas publicadas' : r.grade + '/20'} · {r.complete ? 'Completo' : 'Provisional'}</p>
      <p>{r.trend}{r.change != null ? ' · Cambio entre primera y última nota: ' + r.change + ' puntos' : ''}</p>
      {r.entries.length > 0 && <div style={{ height: 220 }}><ResponsiveContainer width="100%" height="100%"><LineChart data={r.entries} margin={{ left: 0, right: 20, bottom: 10 }}><CartesianGrid strokeDasharray="3 3" /><XAxis dataKey="title" tick={false} label={{ value: 'Evaluaciones en orden cronológico', position: 'insideBottom', offset: -5 }} /><YAxis domain={[0, 20]} /><Tooltip /><Line dataKey="grade" name="Nota" stroke="#2463eb" strokeWidth={2} /></LineChart></ResponsiveContainer></div>}
      <ol>{r.entries.map(e => <li key={e.source + e.id}>{e.title} · {e.category}: {e.grade}/20</li>)}</ol>
    </div>)}
    {!d.loading && !d.error && !d.data?.length && <p>Aún no tienes cursos matriculados.</p>}
  </section>;
}

export function CourseRanking({ data }) {
  const [course, setCourse] = useState('');
  const courses = Array.from(new Map((data.data || []).map(r => [r.course_id, { value: r.course_id, label: r.course_name }])).values());
  const rows = (data.data || []).map(r => {
    const group = data.data.filter(a => a.course_id === r.course_id);
    return { ...r, id: r.id + ':' + r.course_id, position: 1 + group.filter(a => Number(a.points) > Number(r.points)).length };
  }).filter(r => !course || r.course_id === course);
  return <Panel title="Ranking por curso"><ErrorBox error={data.error} />
    <Field label="Filtrar curso" type="select" value={course} onChange={setCourse} options={[{ value: '', label: 'Todos los cursos' }, ...courses]} />
    <DataTable rows={rows} columns={[{ label: 'Puesto', key: 'position' }, { label: 'Alumno', key: 'student_name' }, { label: 'Curso', key: 'course_name' }, { label: 'Puntaje', key: 'points' }, { label: 'Retos', key: 'challenges' }]} />
    <p className="hint">El puntaje suma los aciertos de los retos. Los empates comparten puesto dentro del curso.</p>
  </Panel>;
}

