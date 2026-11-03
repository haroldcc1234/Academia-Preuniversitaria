"""Importa solo simulacros originales nuevos en el Docker local de Academia Jauja.
Respalda toda la BD antes de insertar. UUID estables; no reemplaza preguntas ni intentos.
Ejecutar desde la carpeta del proyecto: python scripts/importar-simulacros-originales.py
"""
import json,pathlib,subprocess,datetime,hashlib,uuid,io,sys
ROOT=pathlib.Path(__file__).resolve().parents[1];DATA=ROOT/'data/balotarios-originales'
exams=json.loads((DATA/'exams.json').read_text(encoding='utf8'))
CAT=json.loads((ROOT/'backend/src/main/resources/universities-peru.json').read_text(encoding='utf-8-sig'))
assert {e['university'] for e in exams}=={u['acronym'] for u in CAT}
assert len(exams)==len(CAT)==105
ids=set();images=set()
for e in exams:
 assert 'no oficial' in e['title'] and len(e['questions'])==40
 for q in e['questions']:
  assert q['id'] not in ids;ids.add(q['id'])
  assert len(q['options'])==len(set(q['options']))==4
  assert q['answer'] in ['0','1','2','3'] and len(q['explanation'])>=70
  for key in q['images']:
   uuid.UUID(key);p=DATA/'images'/key;assert p.read_bytes().startswith(b'\x89PNG\r\n\x1a\n');images.add(key)
assert len(ids)==4200 and len(images)==315
if '--validate' in sys.argv:print('Validado: 105 simulacros, 4200 preguntas, 315 figuras.');sys.exit(0)
def mysql(sql):
 cmd=['docker','exec','-i','academia-mysql','sh','-c','exec mysql --default-character-set=utf8mb4 -N -B -uroot -p"$MYSQL_ROOT_PASSWORD" "$MYSQL_DATABASE"']
 r=subprocess.run(cmd,input=sql.encode('utf8'),capture_output=True,check=True);return r.stdout.decode('utf8')
def value(x):
 if x is None:return 'NULL'
 if isinstance(x,int):return str(x)
 if isinstance(x,(dict,list)):x=json.dumps(x,ensure_ascii=False)
 return "CONVERT(0x"+str(x).encode('utf8').hex()+" USING utf8mb4) COLLATE utf8mb4_unicode_ci" if str(x) else "''"
def insert(table,row):
 return f'INSERT INTO {table} ('+','.join(row)+') VALUES ('+','.join(value(v) for v in row.values())+');\n'
existing=set(mysql('SELECT id FROM bulletins;').splitlines());new=[e for e in exams if e['id'] not in existing]
if not new:
 check=mysql('SELECT COUNT(*),SUM(question_count),SUM(processed_count) FROM bulletins WHERE id IN ('+','.join(value(e['id']) for e in exams)+');')
 assert check.strip()=='105\t4200\t4200',check
 print('Verificado: 105 simulacros / 4200 preguntas. Sin duplicar ni modificar intentos.');sys.exit(0)
# Exact source metadata is kept in files; database inserts are parameter-safe hexadecimal UTF-8 literals.
backup=ROOT.parent/('respaldo-balotarios-'+datetime.datetime.now().strftime('%Y%m%d-%H%M%S'));backup.mkdir()
with (backup/'academia.sql').open('wb') as out:
 subprocess.run(['docker','exec','academia-mysql','sh','-c','exec mysqldump --single-transaction --no-tablespaces -uroot -p"$MYSQL_ROOT_PASSWORD" "$MYSQL_DATABASE"'],stdout=out,stderr=subprocess.PIPE,check=True)
# New keys are namespaced UUIDs; existing files are never overwritten without a matching hash.
keys=sorted(images)
r=subprocess.run(['docker','exec','academia-backend','sh','-c','for key in "$@"; do if [ -f "/app/private-files/$key" ]; then sha256sum "/app/private-files/$key"; fi; done','sh',*keys],capture_output=True,check=True)
for line in r.stdout.decode().splitlines():
 digest,target=line.split();key=target.rsplit('/',1)[-1]
 assert digest==hashlib.sha256((DATA/'images'/key).read_bytes()).hexdigest(),'Conflicting image: '+key
subprocess.run(['docker','cp',str(DATA/'images')+'/.','academia-backend:/app/private-files/'],capture_output=True,check=True)
now=datetime.datetime.now(datetime.timezone.utc).isoformat().replace('+00:00','Z');sql='START TRANSACTION;\n'
for e in new:
 row={k:e[k] for k in ['id','title','university','career','admission_year','process','description']}
 row.update(created_at=now,bulletin_group=e['id'],version=1,archived=0,processing_status='LISTO',question_count=40,processed_count=40)
 sql+=insert('bulletins',row)
 for q in e['questions']:
  row=dict(q);row.update(created_at=now,bulletin_id=e['id'],source_text=q['prompt'],solution_status='RESUELTA',model='Simulacro original: solución preparada, no clave oficial')
  sql+=insert('bulletin_questions',row)
sql+='COMMIT;\n';mysql(sql)
check=mysql('SELECT COUNT(*),SUM(question_count),SUM(processed_count) FROM bulletins WHERE id IN ('+','.join(value(e['id']) for e in exams)+');')
assert check.strip()=='105\t4200\t4200',check
print('Importación completada:',len(new),'simulacros nuevos. Total verificado:',check.strip().replace('\t',' / '));print('Respaldo:',backup)
