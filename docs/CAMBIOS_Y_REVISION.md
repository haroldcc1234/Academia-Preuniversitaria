# Cambios y revisión — 6 de octubre de 2026

## Balotarios automáticos, sin programación de horarios

El director entra en **Admisión y balotarios → Subir PDF**, adjunta el archivo y confirma. Solo se solicita título, universidad y carrera (General permite compartirlo con todos). No se piden apertura, cierre, duración ni claves manuales. Los enlaces externos no se descargan: se adjunta el PDF descargado de la universidad.

La preparación continúa en segundo plano aunque se cierre la pantalla. El sistema separa las preguntas, conserva recortes de las figuras y alternativas, solicita a Ollama soluciones con desarrollo y realiza dos resoluciones independientes. Al terminar publica automáticamente la práctica. El alumno responde dentro de la plataforma, puede guardar y continuar, y recibe nota sobre 20, aciertos y retroalimentación al finalizar. Las claves permanecen ocultas durante el intento.

Las preguntas cuyas dos resoluciones no coincidan se publican sin clave y quedan fuera de la nota. Si no existen preguntas calificables, se muestra práctica terminada sin nota. Las claves son propuestas de IA, no claves oficiales de la universidad; dos respuestas coincidentes no garantizan exactitud. Las notas de estas prácticas libres se conservan en su historial y no alteran las evaluaciones oficiales de los cursos.

### Configuración

Se usa Apache PDFBox y Ollama local, sin pagar una API comercial. Ollama debe estar iniciado y disponer de un modelo de visión en `OLLAMA_BULLETIN_MODEL` y un modelo de texto en `OLLAMA_MODEL`. Los modelos consumen RAM y CPU/GPU; en un equipo sin GPU un balotario completo puede tardar considerablemente. El modelo del asistente también se usa para preguntas sin figuras.

La lista muestra En cola, Separando y resolviendo preguntas, Esperando conexión con IA o Listo para practicar, con el número de preguntas procesadas. Ante una interrupción de Ollama conserva el avance y reintenta. Para archivos antiguos o errores existe **Procesar automáticamente**. Subir nueva versión conserva los intentos de la versión anterior.

La migración `V3__automatic_bulletin_practice.sql` se aplica al iniciar el backend y añade tablas de preguntas e intentos, sin reemplazar los exámenes existentes. Los endpoints antiguos de conversión manual se conservan por compatibilidad; la nueva interfaz usa prácticas automáticas.

### Alcance y límites comprobados

- El PDF facilitado `EX_Adm_UNI - 2026-1_AAH.pdf` tiene 21 páginas. La prueba de extracción identifica las 100 preguntas consecutivas y conserva recortes, incluyendo una pregunta que continúa en otra columna/página. Se inspeccionaron ejemplos con figuras. No se codificó una lista de preguntas específica del documento.
- Para documentos sin encabezados textuales reconocibles se implementó detección visual mediante Ollama. Este camino todavía no tiene validación completa con un PDF escaneado real; no se garantiza todo formato universitario.
- Límite de carga: 25 MB; hasta 100 páginas y 300 preguntas. Fórmulas, dibujos, texto borroso, preguntas abiertas o claves generadas erróneamente pueden producir preguntas sin nota o respuestas incorrectas. No se ha certificado la solución de las 100 preguntas del ejemplo.
- Las pruebas de integración usan una IA simulada para comprobar carga, cola, publicación sin horarios, permisos, claves ocultas, guardado, calificación y retroalimentación. Esto comprueba el flujo, no la exactitud de Ollama.
- La prueba opcional `BulletinAiLiveTest` llama al Ollama real con preguntas del PDF. Sus resultados se guardan en `backend/target/live-pdf-results.json`. No debe confundirse con la prueba de extracción.
- El navegador confirmó el formulario de subida sin horarios y la creación del balotario en una base de prueba aislada. No se modificó una base institucional ni se desplegó producción.

## Progreso personal y ranking

El **Asistente académico** muestra cursos, promedio ponderado sobre 20, evolución cronológica y notas de evaluaciones publicadas del alumno autenticado. También responde consultas sobre su progreso con esos datos; si Ollama no está disponible, devuelve un resumen calculado.

En **Retos** se muestran puesto, nombre del alumno, curso, puntaje acumulado y cantidad de retos, con filtro por curso. Los empates comparten puesto. Se corrigió la discrepancia entre `alias` y `student_name`.

## Volver a verificar

Ejecuta `mvn test` dentro de backend y `npm run check` / `npm run build` dentro de frontend. Las pruebas usan H2 en modo MySQL, aislado de los datos reales. Para comprobar el PDF: `mvn "-DsamplePdf=C:/ruta/balotario.pdf" test`. Para la prueba de IA real añade `-Dtest=BulletinAiLiveTest -DliveOllama=true`; requiere ambos modelos descargados y puede tardar varios minutos.

La revisión funcional no sustituye una auditoría integral. Vite advierte de un paquete superior a 500 kB. La auditoría npm anterior informó vulnerabilidades moderadas en React Router, cuya actualización mayor sigue pendiente de verificación.

### Resultado de la comprobación local final

La pregunta 2 del PDF real fue resuelta correctamente por `qwen2.5:3b` en dos llamadas independientes: 288 soles, alternativa D, con operaciones completas. La prueba de la pregunta 3 con imagen usando `qwen3-vl:2b` agotó el tiempo de espera; por tanto, la prueba real completa de IA no pasó. No está validada la resolución automática de todas las figuras. Tras dos tiempos de espera en una pregunta, el procesador la deja sin nota y continúa con las restantes para evitar un bloqueo indefinido. Una desconexión del servicio conserva el avance y espera su recuperación.

Los cambios requieren reiniciar el backend y el frontend de la carpeta original. La migración se aplica al arrancar el backend. La validación de esta entrega se hizo en una base aislada; no se reinició ni se migró una base institucional.

La suite final completó 23 pruebas sin fallos (17 existentes, 4 del flujo nuevo, 1 del contrato de Ollama y 1 con el PDF de 100 preguntas). La prueba real de IA es opcional y se excluye de esta cifra; su resultado parcial y su tiempo de espera se describen arriba. El frontend pasó ESLint y compilación Vite. También pasó una prueba adicional del límite de tiempos de espera: 24 pruebas satisfactorias en total entre ambas ejecuciones.
