export const fold = value => String(value || '').normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLowerCase().trim();
export const isReady = bulletin => ['LISTO', 'LISTO_CON_OBSERVACIONES'].includes(bulletin.processing_status);

export function groupBulletins(universities, bulletins) {
  const groups = universities.map(u => ({ ...u, key: u.acronym, bulletins: [] }));
  const general = { key: 'General', acronym: 'General', name: 'Prácticas generales', region: 'Material para distintas universidades', bulletins: [] };
  const lookup = new Map();
  for (const group of groups) { lookup.set(fold(group.acronym), group); lookup.set(fold(group.name), group); }
  lookup.set('general', general); groups.push(general);
  for (const bulletin of bulletins) {
    const name = String(bulletin.university || 'General').trim();
    let group = lookup.get(fold(name));
    if (!group) { group = { key: name, acronym: name, name, region: '', bulletins: [] }; groups.push(group); lookup.set(fold(name), group); }
    group.bulletins.push(bulletin);
  }
  return groups.sort((a,b) => Number(b.public_university || false) - Number(a.public_university || false) || Number(b.key === 'General') - Number(a.key === 'General') || a.name.localeCompare(b.name,'es'));
}

export function matchesGroup(group, search) {
  const query = fold(search);
  return fold([group.name, group.acronym, group.region, ...group.bulletins.map(b => b.title)].join(' ')).includes(query);
}
