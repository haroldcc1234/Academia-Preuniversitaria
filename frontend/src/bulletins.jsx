import React, { useEffect, useRef, useState } from 'react';
import { api, BASE, token, download, fnum, datetime } from './api';
import { useApp } from './App';
import { PageHead, Panel, Button, Badge, Field, Modal, ErrorBox, Loading, Empty, SearchBox, useLoad } from './ui';

import logos from './university-logos.json';
import {summarizePractice} from './bulletin-results';
import {groupBulletins, matchesGroup, fold, isReady as ready} from './bulletin-groups';

function UniversityLogo({group}) {
  const [failed,setFailed]=useState(false);const logo=logos[group.acronym];
  return <div className={'bulletin-logo '+(logo?.dark&&!failed?'dark':'')}>{logo&&!failed?<img src={logo.src} alt={'Logo de '+group.name} loading="lazy" onError={()=>setFailed(true)}/>:<span>{group.key==='General'?'Práctica general':group.acronym}</span>}</div>;
}
const states = { ARCHIVO: 'PDF disponible', EN_COLA: 'En cola', PROCESANDO: 'Separando y resolviendo preguntas', ESPERANDO_IA: 'Esperando conexión con IA', ERROR: 'Procesamiento interrumpido', LISTO: 'Listo para practicar', LISTO_CON_OBSERVACIONES: 'Listo para practicar' };

export default function BulletinsPage() {
  const { director, user, notify } = useApp(), d = useLoad('/bulletins'), catalog = useLoad('/catalog'), ai = useLoad(director ? '/bulletins/ai/status' : null);
  const [university, setUniversity] = useState(null), [onlyWithMaterial, setOnlyWithMaterial] = useState(false), [classification, setClassification] = useState(null);
  const [checking, setChecking] = useState(false), [connection, setConnection] = useState(null);
  const [upload, setUpload] = useState(null), [search, setSearch] = useState(''), [selected, setSelected] = useState(null);
  const pending = d.data?.some(b => ['EN_COLA', 'PROCESANDO', 'ESPERANDO_IA'].includes(b.processing_status));
  useEffect(() => { if (!pending) return; const timer = setInterval(d.reload, 5000); return () => clearInterval(timer); }, [pending]);
  const groups=groupBulletins(catalog.data?.universities||[],d.data||[]), active=groups.find(g=>g.key===university);
  const visible=groups.filter(g=>matchesGroup(g,search)&&(!onlyWithMaterial||g.bulletins.length));
  const items=(active?.bulletins||[]).filter(b=>fold(b.title+' '+b.career+' '+b.admission_year).includes(fold(search)));
  return <><PageHead eyebrow="BIBLIOTECA DE ADMISIÓN" title={active?active.name:'Balotarios por universidad'} description={active?'Elige un balotario y practica pregunta por pregunta.':'Explora el material de cada universidad. Las públicas aparecen primero.'}>
    {director && <Button onClick={() => setUpload({ career: 'General', university: active?.key || 'General', admission_year: new Date().getFullYear(), process: 'Práctica libre' })}>Subir PDF</Button>}
  </PageHead><ErrorBox error={d.error || catalog.error} />
  {active && <div className="bulletin-breadcrumb"><Button variant="secondary" onClick={()=>{setUniversity(null);setSearch('');}}>Volver a universidades</Button><span>{active.bulletins.length} balotarios · {active.bulletins.filter(ready).length} listos</span></div>}
  <div className="bulletin-browser-tools"><SearchBox value={search} onChange={setSearch} placeholder={active?'Buscar por título, carrera o año':'Buscar universidad, región o balotario'} />{!active&&<label className="check-line"><input type="checkbox" checked={onlyWithMaterial} onChange={e=>setOnlyWithMaterial(e.target.checked)}/>Solo con balotarios</label>}</div>
  {director && <p className="info-strip">Sube el PDF. El sistema separa las preguntas, conserva sus figuras y prepara las claves y explicaciones con IA. Se publicará automáticamente cuando termine; puedes salir de esta pantalla.</p>}
  {director && <details className="bulletin-ai-details"><summary>Estado y configuración del procesamiento de PDF</summary><Panel title="Motor de procesamiento"><ErrorBox error={ai.error} />
    {ai.data && <><p><strong>{ai.data.provider}</strong> · {ai.data.model}</p><p className="hint">{ai.data.message}</p>
      {!ai.data.configured && <p>Falta configurar la clave de Gemini en el servidor.</p>}
      <Button variant="secondary" disabled={checking || !ai.data.configured} onClick={async () => { setChecking(true); try { setConnection(await api('/bulletins/ai/check', 'POST')); } catch (e) { setConnection({ ready: false, message: e.message }); } finally { setChecking(false); } }}>{checking ? 'Comprobando…' : 'Comprobar conexión'}</Button>
      {connection && <p role="status">{connection.ready ? 'Disponible: ' : 'Pendiente: '}{connection.message}</p>}</>}
    <p className="hint">Las claves generadas por IA son propuestas, no claves oficiales. Las respuestas que no coinciden en las comprobaciones quedan excluidas de la nota.</p>
  </Panel></details>}
  {(!d.data&&d.loading)||(!catalog.data&&catalog.loading)?<Loading/>:!active?<><p className="muted" role="status">{visible.length} colecciones · {(d.data||[]).length} balotarios disponibles para tu cuenta</p><div className="bulletin-universities">{visible.map(g=><button className="bulletin-university-card" key={g.key} onClick={()=>{setUniversity(g.key);setSearch('');}}><UniversityLogo group={g}/><div className="bulletin-university-copy"><div className="spread"><strong>{g.acronym}</strong><Badge tone={g.public_university?'green':''}>{g.key==='General'?'Academia':g.public_university===undefined?'Colección':g.public_university?'Pública':'Privada'}</Badge></div><h2>{g.name}</h2><p>{g.region}</p><div className="bulletin-card-count"><span>{g.bulletins.length} balotarios</span><span>{g.bulletins.filter(ready).length} listos</span></div><span className="bulletin-card-open">{g.bulletins.length?'Ver balotarios':director?'Añadir material':'Ver colección'}</span></div></button>)}</div>{!visible.length&&<Empty text="No se encontraron universidades ni balotarios con esa búsqueda."/>}</>:<><div className="cards-grid">{items.map(b => <Panel key={b.id}>
    <Badge tone={ready(b) ? 'green' : 'gold'}>{states[b.processing_status] || b.processing_status}</Badge>
    <h2>{b.title}</h2>{b.description&&<p className="hint">{b.description}</p>}<p>{b.university} · {b.career} · {b.admission_year}</p>
    {b.question_count > 0 && <p>{b.question_count} preguntas · {b.processed_count} procesadas</p>}
    {b.processing_status === 'PROCESANDO' && <progress aria-label="Progreso del balotario" max={b.question_count || 1} value={b.processed_count} style={{ width: '100%' }} />}
    {b.processing_error && <p className="hint">{director || ready(b) ? b.processing_error : 'El material sigue en preparación. Vuelve a consultar más tarde.'}</p>}
    <div className="actions">
      {user.role === 'ALUMNO' && ready(b) && <Button onClick={() => setSelected(b)}>Practicar / ver resultados</Button>}
      {director && ['ARCHIVO', 'ERROR', 'ESPERANDO_IA'].includes(b.processing_status) && b.file_key && <Button onClick={async () => { try { await api('/bulletins/' + b.id + '/process', 'POST'); d.reload(); } catch (e) { notify(e.message); } }}>Procesar automáticamente</Button>}
      {director && b.file_key && <Button variant="subtle" onClick={() => download('/files/bulletin/' + b.id, b.file_name).catch(e => notify(e.message))}>Ver original</Button>}
      {director && <Button variant="subtle" onClick={()=>setClassification({id:b.id,university:b.university})}>Cambiar universidad</Button>}
      {director && <Button variant="subtle" onClick={() => setUpload({ ...b, id: undefined, bulletin_group: b.bulletin_group })}>Subir nueva versión</Button>}
      {director && <Button variant="danger subtle" onClick={async () => { if (!confirm('¿Archivar este balotario?')) return; try { await api('/bulletins/' + b.id, 'DELETE'); d.reload(); } catch (e) { notify(e.message); } }}>Archivar</Button>}
    </div>
  </Panel>)}</div>{!items.length&&<Empty text={search?'No hay balotarios que coincidan con esa búsqueda.':director?'Esta universidad todavía no tiene material. Usa Subir PDF para añadirlo.':'Todavía no hay balotarios disponibles para tu cuenta en esta universidad.'}/>}</>}
  <p className="hint">Los logos identifican a las universidades; el material y su publicación son responsabilidad de la academia.</p>
  {classification&&<Classification initial={classification} universities={catalog.data?.universities||[]} onClose={()=>setClassification(null)} onSaved={()=>{setClassification(null);d.reload();notify('Universidad actualizada');}}/>}
  {upload && <Upload initial={upload} universities={catalog.data?.universities || []} onClose={() => { setUpload(null); d.reload(); }} />}
  {selected && <PracticeHome bulletin={selected} onClose={() => setSelected(null)} />}
  </>;
}

function Classification({initial,universities,onClose,onSaved}) {
  const [university,setUniversity]=useState(initial.university),[busy,setBusy]=useState(false),[error,setError]=useState('');
  return <Modal title="Clasificar balotario" onClose={onClose}><form onSubmit={async e=>{e.preventDefault();setBusy(true);try{await api('/bulletins/'+initial.id+'/university','PUT',{university});onSaved();}catch(e){setError(e.message);}finally{setBusy(false);}}}><Field label="Universidad" type="select" value={university} onChange={setUniversity} required options={[{value:'General',label:'Prácticas generales'},...universities.map(u=>({value:u.acronym,label:u.name}))]}/><ErrorBox error={error}/><Button disabled={busy}>{busy?'Guardando…':'Guardar clasificación'}</Button></form></Modal>;
}

function Upload({ initial, universities, onClose }) {
  const [form, setForm] = useState(initial), [file, setFile] = useState(null), [busy, setBusy] = useState(false), [error, setError] = useState('');
  return <Modal title="Subir balotario PDF" onClose={onClose}><form onSubmit={async e => {
    e.preventDefault(); if (!file || busy) return; setBusy(true); setError('');
    try { const data = new FormData(); for (const key of ['title', 'university', 'career', 'admission_year', 'process', 'bulletin_group']) if (form[key] != null) data.append(key, form[key]); data.append('file', file); await api('/bulletins', 'POST', data); onClose(); }
    catch (e) { setError(e.message); } finally { setBusy(false); }
  }}>
    <label className="field"><span>PDF (máximo 25 MB)</span><input type="file" accept="application/pdf,.pdf" required disabled={busy} onChange={e => { const f = e.target.files[0]; setFile(f); if (f && !form.title) setForm({ ...form, title: f.name.replace(/\.pdf$/i, '') }); }} /></label>
    <Field label="Título" value={form.title} onChange={v => setForm({ ...form, title: v })} required maxLength={200} />
    <Field label="Universidad" type="select" value={form.university} onChange={v => setForm({ ...form, university: v })} options={[{ value: 'General', label: 'Todas las universidades' }, ...universities.map(u => ({ value: u.acronym, label: u.name }))]} required />
    <Field label="Carrera (General para todos)" value={form.career} onChange={v => setForm({ ...form, career: v })} required />
    <p className="hint">La preparación es automática y puede tardar según la cantidad de preguntas y la velocidad del equipo. Verás el avance en la lista.</p>
    <ErrorBox error={error} /><Button disabled={!file || busy}>{busy ? 'Subiendo…' : 'Subir y preparar automáticamente'}</Button>
  </form></Modal>;
}

function PracticeHome({ bulletin, onClose }) {
  const d = useLoad('/bulletins/' + bulletin.id + '/practice'), [attempt, setAttempt] = useState(null), [busy, setBusy] = useState(false), [error, setError] = useState('');
  if (attempt) return <TakePractice id={attempt} onClose={() => { setAttempt(null); d.reload(); }} />;
  return <Modal title={bulletin.title} wide onClose={onClose}><ErrorBox error={d.error || error} />
    {bulletin.description&&<p className="info-strip">{bulletin.description}</p>}<p>Disponible en cualquier momento. Puedes guardar y continuar. Al enviar verás tus aciertos, la respuesta propuesta y el desarrollo de cada pregunta.</p>
    <p className="hint">Consulta la procedencia del material antes de practicar. Las claves de los simulacros originales están preparadas de antemano; los PDF procesados por IA pueden contener errores. Las preguntas sin clave consistente quedan fuera de la nota.</p>
    <Button disabled={busy || d.loading} onClick={async () => { setBusy(true); try { const a = await api('/bulletins/' + bulletin.id + '/practice/start', 'POST'); setAttempt(a.id); } catch (e) { setError(e.message); } finally { setBusy(false); } }}>{d.data?.attempts.some(a => a.status === 'EN_CURSO') ? 'Continuar práctica' : 'Comenzar práctica'}</Button>
    <h3>Mis intentos</h3>{d.data?.attempts.map(a => <div className="challenge-row" key={a.id}><div className="grow"><strong>{a.status === 'EN_CURSO' ? 'En curso' : a.grade == null ? 'Finalizado sin preguntas calificables' : fnum(a.grade) + '/20'}</strong><small>{datetime(a.created_at)}</small></div><Button variant="secondary" onClick={() => setAttempt(a.id)}>{a.status === 'EN_CURSO' ? 'Continuar' : 'Ver resultados'}</Button></div>)}
  </Modal>;
}

function QuestionImage({ id, index }) {
  const [src, setSrc] = useState(''), [error, setError] = useState('');
  useEffect(() => {
    const controller = new AbortController(); let url;
    setSrc(''); setError('');
    fetch(BASE + '/bulletin-questions/' + id + '/images/' + index, { headers: { Authorization: 'Bearer ' + token() }, signal: controller.signal })
      .then(r => { if (!r.ok) throw new Error('No se pudo cargar la figura. Reabre la pregunta para intentarlo otra vez.'); return r.blob(); })
      .then(blob => { if (controller.signal.aborted) return; url = URL.createObjectURL(blob); setSrc(url); })
      .catch(e => { if (e.name !== 'AbortError') setError(e.message); });
    return () => { controller.abort(); if (url) URL.revokeObjectURL(url); };
  }, [id, index]);
  return <><ErrorBox error={error} />{src ? <img src={src} alt={'Enunciado y figuras, parte ' + (index + 1)} style={{ maxWidth: '100%', height: 'auto', display: 'block', margin: '12px auto' }} /> : !error && <p>Cargando pregunta…</p>}</>;
}

function TakePractice({ id, onClose }) {
  const [data, setData] = useState(null), [answers, setAnswers] = useState({}), [position, setPosition] = useState(0), [busy, setBusy] = useState(false), [error, setError] = useState(''), [saved, setSaved] = useState('');
  const latest = useRef({}), queue = useRef(Promise.resolve()), finishing = useRef(false);
  useEffect(() => { api('/bulletin-attempts/' + id).then(r => { setData(r); setAnswers(r.attempt.answers); latest.current = r.attempt.answers; }).catch(e => setError(e.message)); }, [id]);
  const active = data?.attempt.status === 'EN_CURSO';
  function saveNow() {
    const values = { ...latest.current };
    queue.current = queue.current.catch(() => {}).then(() => api('/bulletin-attempts/' + id + '/answers', 'PUT', { answers: values }));
    return queue.current;
  }
  useEffect(() => {
    if (!active || finishing.current) return;
    const timer = setTimeout(() => { if (finishing.current) return; saveNow().then(() => setSaved('Respuestas guardadas')).catch(e => { setSaved('Pendiente de guardar'); setError(e.message); }); }, 650);
    return () => clearTimeout(timer);
  }, [answers, active]);
  async function submit() {
    if (finishing.current || !confirm('¿Finalizar y ver tus resultados? Las preguntas sin responder contarán como omitidas.')) return;
    finishing.current = true; setBusy(true); setError('');
    try { await saveNow(); setData(await api('/bulletin-attempts/' + id + '/submit', 'POST')); setPosition(0); }
    catch (e) { setError(e.message); } finally { finishing.current = false; setBusy(false); }
  }
  async function leave() {
    setBusy(true); try { if (active) await saveNow(); onClose(); } catch (e) { setError(e.message); setBusy(false); }
  }
  const q = data?.questions[position], result = summarizePractice(data?.questions || []);
  return <Modal title={data?.title || 'Balotario'} wide onClose={() => { if (!busy) leave(); }}><ErrorBox error={error} />
    {!data ? <Loading /> : <>
      {!active && <div className="result-callout"><h2>{data.attempt.grade == null ? 'Práctica terminada' : fnum(data.attempt.grade) + '/20'}</h2><p>{result.correct} aciertos · {result.incorrect} incorrectas · {result.omitted} sin responder · {result.excluded} sin calificar.</p><p>Revisa tu respuesta, la clave y el desarrollo de cada ejercicio. La nota se calcula sobre {data.attempt.graded_count} preguntas calificables.</p></div>}
      
      {!active&&<div className="practice-course-results"><h3>Resultados por curso</h3><div className="table-wrap"><table><thead><tr><th>Curso</th><th>Aciertos</th><th>Incorrectas</th><th>Sin responder</th><th>Sin calificar</th></tr></thead><tbody>{result.courses.map(c=><tr key={c.name}><th scope="row">{c.name}</th><td>{c.correct}</td><td>{c.incorrect}</td><td>{c.omitted}</td><td>{c.excluded}</td></tr>)}</tbody></table></div></div>}
      <div className="prompt-chips" aria-label="Navegación de preguntas">{data.questions.map((r, i) => <button key={r.id} className={!active?'practice-result-'+r.result:''} aria-label={'Pregunta '+r.source_number+(!active?' · '+({CORRECTA:'correcta',INCORRECTA:'incorrecta',SIN_RESPONDER:'sin responder',NO_CALIFICADA:'sin calificar'})[r.result]:'')} aria-current={position === i ? 'step' : undefined} onClick={() => setPosition(i)} style={{ borderColor: position === i ? '#2463eb' : undefined }}>{r.source_number}{answers[r.id] !== undefined ? ' · Respondida' : ''}</button>)}</div>
      {q && <section className="exam-question"><div className="spread"><h3>Pregunta {q.source_number}</h3><Badge>{q.course_name}</Badge></div>
        {Array.from({ length: q.image_count }, (_, i) => <QuestionImage key={q.id + ':' + i} id={q.id} index={i} />)}
        <p className="prewrap question-prompt">{q.prompt}</p>
        <fieldset disabled={!active || busy} className="answer-options"><legend>Tu respuesta</legend>{q.options.map((o, i) => <label key={i} className={answers[q.id] === String(i) ? 'selected' : ''}><input type="radio" name={q.id} checked={answers[q.id] === String(i)} onChange={() => { const a = { ...latest.current, [q.id]: String(i) }; latest.current = a; setAnswers(a); setSaved('Guardando…'); }} /><span>{String.fromCharCode(65 + i)}.</span>{o}</label>)}</fieldset>
        {!q.options.length && <p>No se reconocieron las alternativas; esta pregunta no cuenta para la nota.</p>}
        {!active && <div className="feedback"><strong>{({ CORRECTA: 'Respuesta correcta', INCORRECTA: 'Respuesta incorrecta', SIN_RESPONDER: 'Sin responder', NO_CALIFICADA: 'Sin calificación' })[q.result]}</strong>{q.answer != null && <p>Clave de referencia: {String.fromCharCode(65 + Number(q.answer))}. {q.options[Number(q.answer)]}</p>}<p className="hint">Procedencia de la solución: {q.model || 'Procesamiento del balotario'}</p><h4>Desarrollo y retroalimentación</h4><p className="prewrap">{q.explanation}</p></div>}
      </section>}
      <div className="actions"><Button variant="secondary" disabled={!position} onClick={() => setPosition(position - 1)}>Anterior</Button><Button variant="secondary" disabled={position === data.questions.length - 1} onClick={() => setPosition(position + 1)}>Siguiente</Button></div>
      {active && <div className="form-actions"><small role="status">{saved} · {Object.keys(answers).length}/{data.questions.length} respondidas</small><Button variant="secondary" disabled={busy} onClick={leave}>Guardar y salir</Button><Button disabled={busy} onClick={submit}>{busy ? 'Guardando…' : 'Finalizar y ver resultados'}</Button></div>}
    </>}
  </Modal>;
}
