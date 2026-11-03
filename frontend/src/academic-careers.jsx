import React from 'react';
import { useApp } from './App';
import { Panel, Badge, Button, ErrorBox, useLoad } from './ui';
import { fnum } from './api';

export function AcademicCareerSuggestions({ onAsk }) {
  const { user } = useApp();
  const d = useLoad(user.role === 'ALUMNO' ? '/ai/careers' : null);
  if (user.role !== 'ALUMNO') return null;
  return <Panel title="Carreras según tu rendimiento" action={<Button variant="secondary" onClick={d.reload} disabled={d.loading}>Actualizar recomendaciones</Button>}>
    <ErrorBox error={d.error} />
    {d.loading ? <p>Cargando tus notas y opciones de carrera…</p> : d.data && <>
      <p>{d.data.message}</p>
      <div className="career-grid">{d.data.careers.map(c => <article className="exam-question" key={c.name}>
        <Badge>{c.coverage === 100 ? 'Todas las áreas con notas' : 'Cobertura parcial de áreas'}</Badge>
        <h3>{c.name}</h3>
        <p><strong>{fnum(c.score)}/20</strong> · Promedio orientativo de cursos relacionados</p>
        <ul>{c.evidence.map(e => <li key={e.course}>{e.course}: <strong>{fnum(e.grade)}/20</strong>{!e.complete && ' (provisional)'}<small> · {e.cycle} · {e.section}</small></li>)}</ul>
        {c.reinforce.length > 0 && <p>Por reforzar: {c.reinforce.join(', ')}. Puedes mejorar estas bases; no descartan la carrera.</p>}
        {c.missing.length > 0 && <p className="muted">Sin notas de: {c.missing.join(', ')}. Cobertura de los pesos: {c.coverage}%.</p>}
        <p>{c.activity}</p>
        <Button variant="subtle" onClick={() => onAsk('Explícame la recomendación de la carrera de ' + c.name + ' según mis notas y qué cursos debo reforzar.')}>Consultar al asistente</Button>
      </article>)}</div>
      <p className="hint">{d.data.notice}</p>
      <details><summary>Cómo se relacionan mis notas y las carreras</summary><p>{d.data.method}</p><p>Las relaciones y pesos son reglas orientativas de la academia. No evalúan todas las habilidades de una profesión.</p>{d.data.unmapped_courses.length > 0 && <p>Cursos sin una relación específica definida: {d.data.unmapped_courses.join(', ')}.</p>}</details>
    </>}
  </Panel>;
}
