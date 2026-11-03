# Biblioteca de simulacros originales

## Contenido y límites

105 simulacros originales, uno por cada universidad del catálogo, con 40 preguntas cada uno: 4.200 preguntas y 315 figuras PNG propias. Cada simulacro tiene preguntas de aritmética, álgebra, geometría, razonamiento matemático, física, química, biología, comunicación, historia/geografía y razonamiento cívico/científico.

Son variantes de un banco común de práctica general. No son exámenes oficiales, no reproducen un examen universitario y no afirman ajustarse a la estructura, dificultad o especialidad de cada proceso de admisión. Incluidas las universidades de música y teología, sirven para probar el flujo general del sistema, no para simular sus pruebas especializadas.

Las soluciones se prepararon en esta tarea; no se presentan como claves oficiales ni como revisión docente. Los ejercicios numéricos tienen valores variables y las preguntas conceptuales se seleccionan y ordenan desde un banco común. Las figuras incluyen triángulo, rectángulo y gráfico de barras. Rendir y calificar estos simulacros no requiere Gemini ni Ollama.

## Investigación web

fuentes-revisadas.json registra la revisión de los 105 portales y los enlaces encontrados: 85 instituciones con enlaces de admisión y 36 con documentos PDF candidatos. Es un inventario de descubrimiento, no una validación de que todos esos documentos sean exámenes o tengan autorización para reproducción integral. La ausencia de un enlace no demuestra que una universidad no publique exámenes.

También se localizaron modelos de la PUCP en https://files.pucp.education/homepucp/uploads/sites/2/2020/04/21175955/Modelo-de-prueba-de-admision-PUCP-2016.pdf y solucionarios de UNI en https://www.trilce.edu.pe/academia/solucionarios-uni . Se mantienen como referencias externas; no se ha copiado su contenido íntegro al banco.

## Archivos y carga

- data/balotarios-originales/exams.json: enunciados, alternativas, claves y explicaciones. Archivo privado del proyecto, nunca alojarlo en frontend/public.
- data/balotarios-originales/images: figuras originales en PNG con nombres UUID.
- scripts/generar-simulacros-originales.py: generación reproducible, Python y Pillow; no consulta servicios externos.
- scripts/importar-simulacros-originales.py --validate: comprueba cobertura, alternativas, claves y figuras sin modificar datos.
- scripts/importar-simulacros-originales.py: respalda MySQL, comprueba conflictos de imágenes, copia archivos privados e inserta solo UUID nuevos en una transacción. Una segunda ejecución no duplica materiales ni altera intentos existentes.

Los materiales importados quedan en LISTO. No pasan por la cola de procesamiento del PDF. Esto evita el consumo para esta biblioteca preparada, pero no cambia la velocidad ni garantiza la exactitud del procesamiento de futuros PDF.

## Comprobaciones

Cobertura 105/105; 40 preguntas por simulacro; 315 figuras; alternativas únicas y claves válidas; 1.890 resultados numéricos contrastados de nuevo a partir de los enunciados; revisión visual de las figuras. Las 210 preguntas restantes de cálculo usan datos de figuras y sus soluciones se construyen a partir de esos mismos valores.

La interfaz muestra procedencia no oficial, texto del enunciado, resultados por curso y desarrollo. El servidor conserva las soluciones fuera de la respuesta al alumno hasta finalizar el intento.

## Verificación en Docker — 28/10/2026
Importación completada: 105 simulacros LISTO, 4.200 preguntas, 315 figuras. Se comprobó la repetición del importador sin duplicados. Dos intentos de la cuenta alumno@academia.local: UNI con 1 acierto, 1 error y 38 omitidas = 0,5/20; UNCP con 40 aciertos = 20/20. Se verificaron figuras autenticadas, claves ocultas durante el intento, soluciones disponibles al finalizar y rechazo de cambios posteriores. Comprobación visual realizada en el navegador. Se conservaron los datos anteriores y un respaldo SQL previo a la carga.

