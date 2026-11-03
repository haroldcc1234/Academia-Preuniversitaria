# Academia: demostración completa

La instalación original está en `C:\Proyectos 2026\Academia_Jauja_MySQL\academia-jauja`.
Abre Docker Desktop, inicia el proyecto **academia-jauja** y entra a http://localhost:3000.
No necesitas ejecutar Maven ni npm. Los datos se conservan en los volúmenes de Docker.

## Cuentas de demostración

| Rol | Correo | Contraseña |
|---|---|---|
| Dirección | director@academia.local | Director2026! |
| Docencia | docente@academia.local | Docente2026! |
| Alumno | alumno@academia.local | Alumno2026! |

Los nuevos alumnos usan `alumno.demo7@academia.test` hasta `alumno.demo80@academia.test`, contraseña `Alumno2026!`. Los nuevos docentes usan `docente.demo3@academia.test` hasta `docente.demo10@academia.test`, contraseña `Docente2026!`. Son personas y registros ficticios.

## Contenido añadido

80 alumnos y 10 docentes en total, cuatro secciones nuevas de 20 alumnos, ocho cursos por sección y 16 semanas de programación. Se agregan 4480 asistencias históricas, 3200 calificaciones, 64 materiales, 64 evaluaciones (32 disponibles y 32 históricas), 640 intentos corregidos, 320 retos con 640 resultados, 32 resoluciones aprobadas y 16 perfiles vocacionales variados. Dos prácticas internas contienen 24 ejercicios con claves y desarrollos comprobados. Son material de demostración; no se presentan como exámenes oficiales ni como resultados generados por Gemini.

La carga se activa con `APP_DEMO_FULL=true` y se registra como `full-demo-v1`: reiniciar no duplica los datos. No borra registros anteriores. Las fechas se calculan respecto del primer arranque de la carga; las evaluaciones de práctica permanecen abiertas durante 30 días. El director puede ajustar fechas desde el sistema.

## Universidades y orientación

Catálogo de 105 instituciones (52 públicas y 53 privadas) de la tabla universitaria de SUNEDU consultada el 26/10/2026: https://www.sunedu.gob.pe/lista-de-universidades-licenciadas/
Las escuelas de posgrado de la tabla separada no forman parte de este catálogo.

Se contrastó una selección de programas de 14 instituciones en sus páginas oficiales. Cada ficha enlaza su fuente. No se atribuyen carreras a las demás instituciones sin contrastarlas. La búsqueda por carrera consulta solo esa selección, no toda la oferta nacional.

El test combina áreas RIASEC (70%) y actividades específicas por carrera (30%). Es orientación exploratoria, no una prueba psicométrica validada. Muestra hasta cuatro universidades públicas y cuatro privadas según carrera afín, oferta contrastada y región; no representa un ranking oficial de calidad. Los informes antiguos se recalculan al abrirlos con el método vigente sin alterar sus respuestas originales.

## Gemini y PDF

El motor de balotarios usa Gemini mediante la clave local de `.env`; no se comparte con el navegador ni se incluyen notas de alumnos en las solicitudes. El modelo configurado es `gemini-3.8-flash`, disponible en el nivel gratuito según Google: https://ai.google.dev/gemini-api/docs/pricing . La cuota y disponibilidad dependen del proyecto.

El 26/10/2026 la API rechazó generar con 2.5 Flash por no estar disponible para nuevos usuarios. Las pruebas de generación con 3.8 Flash y 3.1 Flash Lite devolvieron HTTP 503 (alta demanda). Por ello no se certifica todavía una conversión real completa con Gemini. El botón Comprobar conexión realiza ahora una generación real, no solo una consulta del nombre del modelo.

Un PDF subido por Dirección se separa en preguntas y se procesa en segundo plano, conservando figuras. Cada solución se contrasta en dos pasadas. Si el proveedor limita o interrumpe el servicio se conserva el avance y se reintenta; una clave no consistente queda sin calificación. La coincidencia de dos pasadas no garantiza infalibilidad, por lo que conviene revisar el resultado antes de usarlo en una evaluación formal.

El asistente de estudio y las notas siguen usando Ollama local. Los modelos locales se conservan en Docker.

## Respaldo

Antes de la carga se guardó un respaldo SQL y una copia de las fuentes en la carpeta de trabajo de ChatGPT (`respaldo-demo-bd-20261026` y `respaldo-demo-20261026`). No elimines los volúmenes de Docker si quieres conservar la información.

## Verificación realizada

- 32 pruebas Java aprobadas; dos pruebas opcionales de archivo/modelo externo omitidas.
- ESLint y compilación de frontend aprobados; imágenes Docker compiladas correctamente.
- MySQL con 80 alumnos, 10 docentes y 105 instituciones; backend, frontend, MySQL y Ollama activos.
- Inicio de sesión de tres roles; administración rechazada con HTTP 403 para alumnos y docentes.
- Catálogo, asistencia, notas, materiales, evaluaciones, progreso y ranking por curso consultados correctamente.
- Perfil real del alumno de demostración con 4 públicas y 4 privadas; informe PDF descargado correctamente.
- Práctica interna respondida desde la API: 12 respuestas correctas, nota 20, desarrollo disponible al finalizar y claves ocultas durante el intento.
- Interfaz recorrida en navegador: Dirección, catálogo con búsqueda sin tildes y perfil vocacional del alumno.
- Prueba real desde el backend de Gemini: no disponible por saturación/cuota; pendiente validar conversión con el proveedor operativo.
