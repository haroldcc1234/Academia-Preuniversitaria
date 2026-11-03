"""Simulacros originales de práctica general. No reproducen exámenes universitarios.
Generación determinista: 105 variantes, 40 preguntas, soluciones y figuras locales.
No llama a modelos ni proveedores externos. No importa ni modifica la base de datos.
"""
import json, pathlib, random, uuid, math
from fractions import Fraction
from PIL import Image, ImageDraw, ImageFont
ROOT=pathlib.Path(__file__).resolve().parents[1]
OUT=ROOT/'data/balotarios-originales'; IMG=OUT/'images';IMG.mkdir(parents=True,exist_ok=True)
CAT=json.loads((ROOT/'backend/src/main/resources/universities-peru.json').read_text(encoding='utf-8-sig'))
NS=uuid.UUID('e26f6331-2c92-4c9c-9906-48a173776412')
def uid(s):return str(uuid.uuid5(NS,s))
def fmt(x):return str(int(x)) if int(x)==x else str(x)
font=ImageFont.truetype('C:/Windows/Fonts/arial.ttf',25)
small=ImageFont.truetype('C:/Windows/Fonts/arial.ttf',21)
def figure(key,kind,a,b):
 im=Image.new('RGB',(760,360),'white');d=ImageDraw.Draw(im);ink='#183f4a';d.text((24,15),'Figura de apoyo (esquema, no a escala)',font=small,fill=ink)
 if kind=='triangle':
  d.line([(190,280),(190,90),(540,280),(190,280)],fill=ink,width=4);d.line([(190,255),(215,255),(215,280)],fill=ink,width=2)
  d.text((100,175),f'{a} cm',font=font,fill=ink);d.text((330,295),f'{b} cm',font=font,fill=ink);d.text((190,60),'C',font=font,fill=ink);d.text((160,280),'A',font=font,fill=ink);d.text((550,280),'B',font=font,fill=ink)
 elif kind=='rectangle':
  d.rectangle((170,90,570,270),outline=ink,width=4);d.text((310,285),f'{a} m',font=font,fill=ink);d.text((590,170),f'{b} m',font=font,fill=ink)
 elif kind=='bars':
  vals=[a,b,a+b];scale=180/max(vals);d.line((110,80,110,280,660,280),fill=ink,width=3)
  for i,v in enumerate(vals):
   x=170+i*170;y=280-v*scale;d.rectangle((x,y,x+90,280),fill=['#1d6c7a','#4a8a69','#b17a37'][i]);d.text((x+20,y-30),str(v),font=font,fill=ink);d.text((x+10,295),['Lunes','Martes','Miérc.'][i],font=small,fill=ink)
  d.text((120,48),'Ejercicios resueltos por día',font=small,fill=ink)
 f=IMG/key;im.save(f,format='PNG');return key
BANK={
'Química':[
('Un átomo neutro posee 11 protones. ¿Cuántos electrones tiene?', ['11','22','10','12'], 'En un átomo neutro, la carga positiva de los protones se compensa con igual número de electrones: 11. No se suman protones y neutrones para hallar electrones.'),
('Un átomo tiene número másico 23 y número atómico 11. ¿Cuántos neutrones posee?', ['12','11','23','34'], 'El número másico A suma protones y neutrones. Como Z cuenta protones, N = A − Z = 23 − 11 = 12. Confundir A con N lleva a contar también los protones.'),
('¿Qué enlace predomina en una muestra sólida de cloruro de sodio?', ['Iónico','Covalente no polar','Metálico','Puente de hidrógeno'], 'El sodio cede un electrón y el cloro lo acepta. Se forman Na⁺ y Cl⁻, unidos en una red por atracción electrostática: enlace iónico.'),
('¿Qué coeficientes mínimos balancean H₂ + O₂ → H₂O, en ese orden?', ['2, 1, 2','1, 1, 1','1, 2, 1','2, 2, 1'], 'Dos moléculas de agua contienen cuatro H y dos O. Se necesitan 2 H₂ y 1 O₂: 2 H₂ + O₂ → 2 H₂O. Cambiar subíndices alteraría las sustancias.'),
('En agua a 25 °C, una solución con pH 3 es:', ['Ácida','Neutra','Básica','Necesariamente una sal sólida'], 'A 25 °C, pH menor que 7 corresponde a una solución ácida; pH 7 es neutro y mayor que 7 básico. El valor 3 está por debajo de 7.'),
('¿Cuántos moles hay en 36 g de agua? Usa masa molar de 18 g/mol.', ['2 mol','18 mol','36 mol','0,5 mol'], 'Se divide masa entre masa molar: n = 36 g / (18 g/mol) = 2 mol. Las unidades de gramos se cancelan; no se multiplican las dos magnitudes.'),
('¿Cuál es una mezcla homogénea a escala macroscópica?', ['Sal completamente disuelta en agua','Agua con arena sedimentada','Aceite y agua separados','Granito con minerales visibles'], 'Una solución de sal presenta composición uniforme y una sola fase visible. Los otros ejemplos tienen regiones o fases distinguibles.'),
('¿Qué ocurre cuando una especie química se oxida?', ['Pierde electrones','Gana electrones','Siempre gana protones','Siempre pierde neutrones'], 'Oxidación significa pérdida de electrones; reducción significa ganancia. La regla se refiere a electrones, no a cambios en el núcleo.')],
'Biología':[
('¿Qué orgánulo realiza la mayor parte de la respiración celular aeróbica en una célula eucariota?', ['Mitocondria','Ribosoma','Lisosoma','Aparato de Golgi'], 'La mitocondria alberga el ciclo de Krebs y la cadena respiratoria, que permite producir ATP. Los ribosomas sintetizan proteínas; Golgi modifica y distribuye moléculas.'),
('¿Qué molécula almacena principalmente la información hereditaria celular?', ['ADN','ATP','Glucosa','Triglicérido'], 'El ADN contiene la secuencia de nucleótidos que guarda información genética. ATP es una molécula de transferencia energética y no el archivo hereditario celular.'),
('¿Cuál es el resultado habitual de la mitosis de una célula somática diploide?', ['Dos células con igual número de cromosomas que la inicial','Cuatro células haploides','Dos células con la mitad de cromosomas','Una célula con el doble de cromosomas'], 'Tras duplicar su ADN, la célula reparte las cromátidas en dos núcleos. Cada célula hija conserva el número cromosómico. La reducción a haploide caracteriza a la meiosis.'),
('¿Qué gas se libera en las reacciones luminosas de la fotosíntesis oxigénica?', ['Oxígeno','Nitrógeno','Metano','Monóxido de carbono'], 'La ruptura de moléculas de agua suministra electrones y protones, y libera O₂. El carbono para fabricar materia orgánica procede del CO₂.'),
('En una cadena alimentaria, los productores se caracterizan por:', ['Fabricar materia orgánica a partir de sustancias inorgánicas','Alimentarse exclusivamente de herbívoros','Descomponer únicamente plástico','Carecer de metabolismo'], 'Los productores, como plantas y algas, generan materia orgánica mediante fotosíntesis u otras rutas autotróficas. Por eso sostienen la entrada de materia orgánica a la cadena.'),
('Si A domina completamente sobre a, ¿qué proporción de aa se espera del cruce Aa × Aa?', ['1/4','1/2','3/4','1'], 'Cada progenitor aporta A o a con probabilidad 1/2. Las combinaciones son AA, Aa, aA y aa. Solo una de cuatro es aa; se trata de una proporción esperada, no obligatoria en familias pequeñas.'),
('¿Qué células transportan la mayor parte del oxígeno en la sangre humana?', ['Eritrocitos','Plaquetas','Linfocitos','Neuronas'], 'Los eritrocitos contienen hemoglobina, que une oxígeno. Las plaquetas participan en coagulación y los linfocitos en defensa inmunitaria.'),
('¿Qué proceso mueve agua a través de una membrana selectivamente permeable por un gradiente de potencial hídrico?', ['Ósmosis','Traducción','Replicación','Exocitosis'], 'La ósmosis es el movimiento neto de agua por una membrana selectiva. Replicación y traducción son procesos de información genética; exocitosis libera contenido de vesículas.')],
'Comunicación':[
('Texto: «La biblioteca amplió su horario. Desde entonces, más estudiantes asisten por la tarde». ¿Qué afirma expresamente el texto?', ['La asistencia vespertina aumentó','Todos mejoraron sus notas','La biblioteca abrió por primera vez','Las clases nocturnas se suspendieron'], 'La segunda oración indica de manera explícita que asisten más estudiantes por la tarde. Las notas, la apertura inicial y la suspensión de clases no se mencionan; no deben inferirse como hechos.'),
('Completa: «Estudió con constancia; ___, aprobó la evaluación».', ['por eso','sin embargo','aunque','en cambio'], 'La aprobación se presenta como consecuencia del estudio constante. «Por eso» expresa esa relación. «Sin embargo» y «en cambio» introducen contraste, que aquí no se plantea.'),
('¿Qué palabra es sinónimo contextual de «preciso» en «El informe contiene un dato preciso»?', ['Exacto','Abundante','Dudoso','Extenso'], 'En relación con un dato, «preciso» significa exacto o claramente determinado. No describe su cantidad ni la extensión del informe.'),
('En «Los estudiantes de Jauja prepararon el proyecto», ¿cuál es el núcleo del sujeto?', ['estudiantes','Jauja','prepararon','proyecto'], 'El sujeto es «Los estudiantes de Jauja». Su núcleo sustantivo es «estudiantes»; «de Jauja» lo modifica. «Prepararon» es el verbo del predicado.'),
('¿Cuál oración presenta concordancia correcta?', ['Las alumnas llegaron temprano.','Las alumnas llegó temprano.','La alumnas llegaron temprano.','Las alumna llegaron temprano.'], 'El artículo, el sustantivo y el verbo concuerdan en plural: las, alumnas, llegaron. Las otras opciones mezclan formas singulares y plurales.'),
('Texto: «El experimento se repitió tres veces y en cada ocasión se registró una temperatura distinta». ¿Cuál conclusión está respaldada?', ['Las mediciones no fueron idénticas','El aparato estaba necesariamente roto','La hipótesis quedó demostrada','La temperatura siempre aumentó'], 'El texto solo permite asegurar que los valores fueron distintos. No informa su orden, causas ni la hipótesis. Una conclusión debe limitarse a la evidencia disponible.'),
('¿Qué palabra lleva tilde por ser esdrújula?', ['Brújula','Café','Canción','Árbol'], 'Brújula se divide brú-ju-la y la sílaba tónica es la antepenúltima. Todas las esdrújulas llevan tilde. Café y canción son agudas; árbol es llana.'),
('¿Qué recurso aparece en «Tus ojos son dos luceros»?', ['Metáfora','Onomatopeya','Enumeración','Hipérbaton'], 'Se identifica figuradamente los ojos con luceros por una cualidad compartida, su brillo. Es una metáfora; no imita sonidos ni enumera elementos.')],
'Historia y Geografía':[
('¿Qué civilización construyó la ciudad de Machu Picchu?', ['Inca','Moche','Chavín','Nazca'], 'Machu Picchu es una ciudad del periodo inca situada en Cusco. Las culturas Moche, Chavín y Nazca corresponden a otros contextos y centros históricos.'),
('¿Qué océano limita con la costa occidental del Perú?', ['Pacífico','Atlántico','Índico','Ártico'], 'La costa peruana se encuentra en el borde oriental del océano Pacífico. El Atlántico se encuentra al este de Sudamérica, sin costa peruana sobre él.'),
('¿Qué cordillera atraviesa el territorio peruano?', ['Los Andes','Los Alpes','El Himalaya','Los Apeninos'], 'Los Andes recorren el oeste de Sudamérica e influyen en el relieve y los pisos altitudinales del Perú. Los otros sistemas montañosos están en Europa o Asia.'),
('¿En qué fecha se proclamó la independencia del Perú en Lima?', ['28 de julio de 1821','9 de diciembre de 1824','2 de mayo de 1866','8 de octubre de 1879'], 'La proclamación en Lima ocurrió el 28 de julio de 1821. La batalla de Ayacucho, decisiva para consolidar la independencia, ocurrió el 9 de diciembre de 1824; son acontecimientos diferentes.'),
('En un mapa de escala 1:100 000, ¿qué distancia real representa 1 cm?', ['1 km','100 km','10 m','100 m'], 'Un centímetro representa 100 000 cm. Al dividir entre 100 se obtienen 1 000 m, equivalentes a 1 km. Es necesario convertir unidades antes de comparar.'),
('¿Qué línea imaginaria divide la Tierra en hemisferios norte y sur?', ['Ecuador','Meridiano de Greenwich','Trópico de Capricornio','Círculo polar ártico'], 'El ecuador tiene latitud 0° y separa ambos hemisferios. Greenwich es un meridiano de longitud 0°, relacionado con la división este-oeste.'),
('¿Cuál es una fuente histórica primaria para investigar una época?', ['Una carta escrita durante los hechos estudiados','Un resumen escolar publicado siglos después','Una novela contemporánea ambientada en esa época','Una reseña actual de un documental'], 'La carta es un testimonio producido en el contexto estudiado. Debe analizarse críticamente, pero su cercanía documental la distingue de interpretaciones posteriores.'),
('¿Qué proceso describe el paso del agua líquida a vapor desde la superficie de un lago?', ['Evaporación','Condensación','Solidificación','Precipitación'], 'En la evaporación, moléculas de la superficie pasan a fase gaseosa. Condensación es el proceso inverso; precipitación es la caída de agua desde la atmósfera.')],
'Razonamiento cívico y científico':[
('Una investigación compara dos grupos para evaluar un fertilizante. ¿Qué diseño permite atribuir mejor el efecto al fertilizante?', ['Mantener iguales las demás condiciones y variar el fertilizante','Cambiar también luz, agua y tipo de planta','Elegir solo las plantas que crecieron más','Medir únicamente el grupo tratado'], 'Al controlar agua, luz y especie, se reduce el efecto de variables ajenas al tratamiento. Comparar con un grupo de control ayuda a distinguir el efecto del fertilizante.'),
('Una noticia cita cifras sin indicar su origen. ¿Cuál es la mejor acción antes de difundirla?', ['Buscar la fuente y contrastar los datos','Compartirla porque tiene muchos comentarios','Cambiar el título para que parezca fiable','Aceptar las cifras si coinciden con una opinión propia'], 'La verificación requiere rastrear el origen y comparar con evidencia independiente. La popularidad y la afinidad personal no prueban la exactitud de una afirmación.'),
('En una decisión colectiva, ¿qué conducta favorece el diálogo democrático?', ['Escuchar argumentos y respetar el derecho a disentir','Impedir que intervenga la minoría','Sustituir argumentos por insultos','Ocultar información relevante'], 'El diálogo necesita participación, razones y respeto por opiniones distintas. Desacreditar, silenciar u ocultar información deteriora la deliberación.'),
('Un estudio encuentra que dos variables aumentan juntas. ¿Qué puede afirmarse únicamente con ese dato?', ['Existe asociación, sin demostrar por sí sola causalidad','Una causa necesariamente la otra','No puede haber una tercera variable','La relación se cumple en todas las personas'], 'La covariación indica asociación. Para concluir causalidad se requiere evidencia adicional y descartar otras explicaciones, como variables de confusión o causalidad inversa.'),
('¿Qué práctica protege mejor la información de una cuenta digital?', ['Usar una contraseña única y un segundo factor de autenticación','Compartir la contraseña con el grupo de clase','Reutilizar una contraseña en todos los sitios','Publicar los códigos de recuperación'], 'Una contraseña única limita el daño de una filtración y el segundo factor agrega una comprobación. Compartir secretos o reutilizarlos aumenta la exposición.'),
('Si una persona presenta una idea ajena como propia sin reconocer la fuente, incurre en:', ['Plagio','Citación','Paráfrasis atribuida','Revisión bibliográfica correctamente referenciada'], 'El plagio consiste en apropiarse de contenido o ideas sin reconocimiento adecuado. Parafrasear también exige atribuir la fuente; cambiar palabras no elimina esa obligación.'),
('¿Qué acción reduce residuos desde el origen?', ['Usar una botella durable y recargarla','Comprar más botellas desechables','Mezclar todos los residuos','Quemar envases después de usarlos'], 'Reutilizar un recipiente evita generar nuevos envases por cada consumo. Reducir en origen previene el residuo; reciclar actúa después de generarlo.'),
('Un resultado experimental contradice la hipótesis inicial. ¿Qué corresponde hacer?', ['Revisar método y evidencia, y reformular la hipótesis si procede','Eliminar los datos contradictorios','Declarar correcta la hipótesis sin revisar','Modificar los números para obtener el resultado esperado'], 'La investigación debe conservar datos y examinar errores o explicaciones alternativas. Ajustar una hipótesis a evidencia válida es parte del método científico; alterar datos invalida la investigación.')]
}
def build(u,index):
 rng=random.Random(271026+index);qs=[];n=index+1;a=3+n%12;b=5+n%9;x=2+n%13
 def add(course,prompt,answer,explanation,alternatives=None,img=None):
  if alternatives is None:
   answer=fmt(answer);v=int(answer);alternatives=[answer,str(v+1),str(v+3),str(v+7)]
  else:answer=str(answer);alternatives=list(map(str,alternatives))
  assert len(alternatives)==len(set(alternatives))==4 and answer in alternatives,(prompt,alternatives)
  rng.shuffle(alternatives);i=len(qs);q={'id':uid(u['acronym']+':q:'+str(i)), 'source_number':i+1,'position':i,'prompt':prompt,'options':alternatives,'answer':str(alternatives.index(answer)),'explanation':explanation,'course_name':course,'images':[]}
  if img:q['images']=[figure(uid(u['acronym']+':figure:'+str(i)),*img)]
  qs.append(q)
 total=20*(10+n);pct=5*(1+n%8);ans=total*pct//100
 add('Aritmética',f'Una mochila cuesta S/ {total}. Se aplica un descuento de {pct} %. ¿Cuántos soles se descuentan?',ans,f'El descuento es {pct}/100 del precio: {total} × {pct}/100 = {ans} soles. Se pide el descuento, no el precio final, que sería {total-ans} soles.')
 add('Aritmética',f'Una imprenta produce {a*b} hojas en {a} minutos a ritmo constante. ¿Cuántas produce en {a+3} minutos?',b*(a+3),f'La tasa es {a*b}/{a} = {b} hojas/min. En {a+3} min produce {b} × {a+3} = {b*(a+3)} hojas. La proporcionalidad es directa porque el ritmo no cambia.')
 add('Aritmética',f'Las notas de cuatro prácticas son {x}, {x+2}, {x+4} y {x+6}. ¿Cuál es su media aritmética?',x+3,f'Suma las cuatro notas: {4*x+12}. Divide entre 4: ({x}+{x+2}+{x+4}+{x+6})/4 = {x+3}. No se divide entre la diferencia de los extremos.')
 add('Aritmética',f'Dos luces se encienden cada {a*2} y {a*3} segundos. Coinciden ahora. ¿En cuántos segundos volverán a coincidir por primera vez?',6*a,f'Buscamos el mínimo común múltiplo. Como {2*a}=2×{a} y {3*a}=3×{a}, el mcm es 6×{a}={6*a} segundos. El máximo común divisor no da el próximo encuentro.')
 add('Álgebra',f'Resuelve {a}x + {b} = {a*x+b}.',x,f'Resta {b} a ambos lados: {a}x={a*x}. Divide entre {a}: x={x}. Comprobación: {a}×{x}+{b}={a*x+b}.')
 add('Álgebra',f'Si f(t) = {a}t² − {b}, calcula f({x}).',a*x*x-b,f'Sustituye t={x}, eleva al cuadrado y luego multiplica: {a}×{x*x}−{b}={a*x*x-b}. Respeta el orden de las operaciones.')
 add('Álgebra',f'Dos números suman {2*x+b} y su diferencia es {b}. ¿Cuál es el mayor?',x+b,f'Sea M el mayor y m el menor. M+m={2*x+b}, M−m={b}. Al sumar: 2M={2*(x+b)}, de modo que M={x+b}. El menor es {x}.')
 add('Álgebra',f'¿Cuál es la raíz mayor de x² − {2*x+3}x + {x*(x+3)} = 0?',x+3,f'El polinomio se factoriza como (x−{x})(x−{x+3}). Las raíces son {x} y {x+3}; la mayor es {x+3}. Verifica suma {2*x+3} y producto {x*(x+3)}.')
 add('Geometría',f'El triángulo rectángulo de la figura tiene catetos {3*a} cm y {4*a} cm. ¿Cuánto mide su hipotenusa en cm?',5*a,f'Por Pitágoras, c²=({3*a})²+({4*a})²={25*a*a}. La raíz positiva es c={5*a} cm. Se suman cuadrados, no directamente longitudes.',img=('triangle',3*a,4*a))
 add('Geometría',f'Calcula el área del rectángulo mostrado, en m².',a*b,f'El área de un rectángulo es base×altura. Con base {a} m y altura {b} m: A={a}×{b}={a*b} m². El perímetro 2(base+altura) tiene otras unidades.',img=('rectangle',a,b))
 angle=20+n%40; angle2=45+n%20
 add('Geometría',f'Dos ángulos interiores de un triángulo miden {angle}° y {angle2}°. ¿Cuánto mide el tercero, en grados?',180-angle-angle2,f'En geometría euclidiana, los ángulos interiores suman 180°. El tercero mide 180−{angle}−{angle2}={180-angle-angle2}°. No se usa la suma de 360° de un cuadrilátero.')
 add('Geometría',f'Un cubo tiene arista de {a} cm. ¿Cuál es su volumen en cm³?',a**3,f'El volumen del cubo es arista³: {a}×{a}×{a}={a**3} cm³. Elevar al cuadrado daría el área de una cara, no el volumen.')
 add('Razonamiento matemático',f'Completa la sucesión aritmética: {x}, {x+a}, {x+2*a}, {x+3*a}, …',x+4*a,f'Cada término aumenta {a}. Por tanto, el siguiente es {x+3*a}+{a}={x+4*a}. Se especifica que la sucesión es aritmética para fijar la regla.')
 add('Razonamiento matemático',f'La gráfica muestra ejercicios resueltos. ¿Cuántos se resolvieron entre los tres días?',2*(a+b),f'Suma las alturas de las barras: lunes {a}, martes {b}, miércoles {a+b}. Total={a}+{b}+{a+b}={2*(a+b)}. La última barra no representa el total acumulado.',img=('bars',a,b))
 add('Razonamiento matemático',f'Hay {a} caminos de A a B y {b} de B a C. ¿Cuántas rutas de A a C pasando por B se pueden formar?',a*b,f'Por cada uno de los {a} primeros caminos se puede elegir cualquiera de los {b} segundos. Por el principio multiplicativo hay {a}×{b}={a*b} rutas.')
 add('Razonamiento matemático',f'Una urna contiene {a} bolas rojas y {b} azules. Se extrae una al azar. ¿Cuál es la probabilidad de que sea roja?',str(Fraction(a,a+b)),f'Hay {a+b} resultados equiprobables y {a} favorables. P(roja)={a}/({a}+{b})={Fraction(a,a+b)}. El denominador es el total de bolas, no solo las azules.',[str(Fraction(a,a+b)),str(Fraction(a,a+b+1)),str(Fraction(a+1,a+b)),str(Fraction(a,a+b+2))])
 add('Física',f'Un móvil recorre {a*b} m en {b} s con rapidez constante. ¿Cuál es su rapidez en m/s?',a,f'Rapidez=distancia/tiempo={a*b}/{b}={a} m/s. No confundas rapidez con aceleración, que mide el cambio de velocidad por tiempo.')
 add('Física',f'Una fuerza neta de {a*b} N actúa sobre una masa de {b} kg. ¿Cuál es la aceleración en m/s²?',a,f'La segunda ley de Newton establece F=m·a. Despejando, a=F/m={a*b}/{b}={a} m/s². Se usa fuerza neta, no una fuerza aislada si hubiera otras.')
 add('Física',f'Una resistencia de {a} Ω está sometida a {a*b} V. ¿Qué corriente la atraviesa en amperios, suponiendo comportamiento óhmico?',b,f'Ley de Ohm: V=I·R. Entonces I=V/R={a*b}/{a}={b} A. Multiplicar voltaje por resistencia no produce una corriente.')
 add('Física',f'Una fuerza constante de {a} N desplaza un objeto {b} m en la misma dirección y sentido. ¿Qué trabajo realiza en joules?',a*b,f'W=F·d·cos(0°)={a}×{b}×1={a*b} J. Si fuerza y desplazamiento fueran perpendiculares el trabajo sería cero, pero aquí son paralelos.')
 for course,bank in BANK.items():
  for prompt,options,explanation in rng.sample(bank,4):add(course,prompt,options[0],explanation,options)
 assert len(qs)==40
 return {'id':uid('bulletin:'+u['acronym']), 'title':f'{u["acronym"]} · Simulacro original de práctica general (no oficial)', 'university':u['acronym'],'career':'General','admission_year':2026,'process':'Práctica original no oficial', 'description':'Material original de la academia para demostración y práctica general. No es un examen publicado, avalado ni validado por esta universidad; no replica su estructura de admisión. Variante de un banco común con ejercicios numéricos y conceptuales, tres figuras y desarrollos preparados de antemano. No requiere llamadas a IA para rendirlo.', 'questions':qs}
all_exams=[build(u,i) for i,u in enumerate(CAT)]
(OUT/'exams.json').write_text(json.dumps(all_exams,ensure_ascii=False,indent=2),encoding='utf8')
print(f'{len(all_exams)} simulacros, {sum(len(e["questions"]) for e in all_exams)} preguntas, {len(list(IMG.iterdir()))} figuras')
