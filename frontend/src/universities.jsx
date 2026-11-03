import React, { useState } from 'react';
import { PageHead, Panel, Badge, Field, Loading, ErrorBox, useLoad } from './ui';
import { parse } from './api';
const normalize = text => String(text || '').normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLowerCase();
export function UniversityCard({ university: u, recommendation = false }) {
  const programs = Object.values(parse(u.programs));
  return <Panel className="university-card"><div className="spread"><strong>{u.acronym}</strong><Badge tone={u.public_university ? 'green' : 'neutral'}>{u.public_university ? 'Pública' : 'Privada'}</Badge></div><h3>{u.name}</h3><p className="muted">{u.city} · {u.region}</p>{recommendation ? <><strong>{u.program_name}</strong><p>{u.recommendation_reason}</p></> : programs.length ? <details><summary>{programs.length} programas contrastados</summary><ul>{programs.map(p => <li key={p}>{p}</li>)}</ul></details> : <p className="hint">Institución del catálogo SUNEDU. Su oferta de carreras aún no se ha contrastado para las recomendaciones.</p>}<div className="university-sources"><a href={u.license_source || u.source_url} target="_blank" rel="noreferrer">Registro SUNEDU</a>{programs.length > 0 && <a href={u.source_url} target="_blank" rel="noreferrer">Oferta oficial</a>}</div><small className="muted">Consulta: {u.verified_on || u.catalogue_checked_on}. Confirma programa, sede y admisión vigente.</small></Panel>;
}
export function UniversityRecommendations({ result }) {
  const options = result.university_recommendations || [];
  if (!options.length) return null;
  return <section className="university-recommendations"><h2>Universidades para tu perfil</h2><p className="muted">{result.recommendation_notice}</p>{[true, false].map(pub => { const group = options.filter(u => !!u.public_university === pub); return <section key={String(pub)}><h3>{pub ? 'Universidades públicas' : 'Universidades privadas'} · {group.length} opciones</h3>{group.length < 4 && <p className="hint">Mostramos las opciones con oferta contrastada para este perfil.</p>}<div className="university-grid">{group.map(u => <UniversityCard key={u.id} university={u} recommendation />)}</div></section>; })}</section>;
}
export default function UniversitiesPage() {
  const catalog = useLoad('/catalog');
  const [search, setSearch] = useState(''), [region, setRegion] = useState(''), [type, setType] = useState('');
  if (catalog.loading) return <Loading />;
  if (catalog.error) return <ErrorBox error={catalog.error} />;
  const universities = catalog.data?.universities || [];
  const regions = [...new Set(universities.map(u => u.region).filter(Boolean))].sort((a,b) => a.localeCompare(b,'es'));
  const filtered = universities.filter(u => (!region || u.region === region) && (!type || !!u.public_university === (type === 'public')) && normalize([u.name,u.acronym,u.city,u.region,u.careers].join(' ')).includes(normalize(search)));
  return <><PageHead eyebrow="ORIENTACIÓN UNIVERSITARIA" title="Universidades del Perú" description="Explora instituciones públicas y privadas. Las públicas aparecen primero; consulta siempre la oferta oficial de cada sede." /><div className="university-totals"><strong>{universities.length} instituciones</strong><span>{universities.filter(u => u.public_university).length} públicas</span><span>{universities.filter(u => !u.public_university).length} privadas</span></div><Panel><div className="university-filters"><Field label="Buscar universidad o carrera contrastada" value={search} onChange={setSearch} placeholder="Nombre, sigla, ciudad o carrera" /><Field label="Región principal (vacío: todo el Perú)" type="select" value={region} onChange={setRegion} options={regions} /><Field label="Gestión (vacío: ambas)" type="select" value={type} onChange={setType} options={[{value:'public',label:'Públicas'},{value:'private',label:'Privadas'}]} /></div><p className="hint">Tabla universitaria de SUNEDU consultada el 26/10/2026. Las carreras contrastadas son una selección para la orientación, no la oferta completa de cada institución.</p></Panel><p role="status" className="muted">{filtered.length} resultados</p><div className="university-grid">{filtered.map(u => <UniversityCard key={u.id} university={u} />)}</div>{!filtered.length && <Panel>No se encontraron instituciones con esos filtros.</Panel>}</>;
}
