# API HTTP

Prefijo general `/api`. Todos los endpoints requieren `Authorization: Bearer TOKEN`, excepto `/api/health` y `/api/auth/login`. Respuestas JSON salvo archivos. Los errores incluyen `error`; 400 para reglas incumplidas, 401 para sesión inválida, 403 para acceso denegado, 404 para recurso inexistente y 409 para conflicto de integridad.

El servidor valida rol, propiedad y asignación antes de entregar contenido. No se deben usar los UUID como mecanismos de autorización. Los endpoints multipart usan campos de formulario y archivo opcional `file`.

| Controlador | Método | Ruta |
| --- | --- | --- |
| Academics | PUT | `/api/courses/{id}` |
| Academics | PUT | `/api/sections/{id}` |
| Academics | PUT | `/api/cycles/{id}` |
| Academics | GET | `/api/catalog` |
| Academics | GET | `/api/offerings` |
| Academics | GET | `/api/users` |
| Academics | POST | `/api/users` |
| Academics | PUT | `/api/users/{id}` |
| Academics | DELETE | `/api/users/{id}` |
| Academics | POST | `/api/cycles` |
| Academics | POST | `/api/cycles/{id}/close` |
| Academics | POST | `/api/cycles/{id}/reopen` |
| Academics | POST | `/api/sections` |
| Academics | POST | `/api/courses` |
| Academics | POST | `/api/offerings` |
| Academics | PUT | `/api/offerings/{id}` |
| Academics | POST | `/api/enrollments` |
| Academics | DELETE | `/api/enrollments/{id}` |
| Academics | GET | `/api/offerings/{id}/students` |
| Academics | GET | `/api/sessions` |
| Academics | POST | `/api/sessions` |
| Academics | PUT | `/api/sessions/{id}/cancel` |
| Academics | GET | `/api/sessions/{id}/attendance` |
| Academics | POST | `/api/sessions/{id}/attendance` |
| Academics | GET | `/api/attendance` |
| Academics | GET | `/api/grades` |
| Academics | POST | `/api/grades` |
| Academics | PUT | `/api/grades/{id}` |
| Academics | GET | `/api/dashboard` |
| Academics | GET | `/api/notifications` |
| Academics | PUT | `/api/notifications/{id}` |
| Academics | PUT | `/api/settings` |
| Academics | GET | `/api/audit` |
| Academics | PUT | `/api/universities/{id}` |
| AiController | GET | `/api/ai/status` |
| AiController | GET | `/api/ai/recommendations` |
| AiController | GET | `/api/ai/history` |
| AiController | POST | `/api/ai/chat` |
| AiController | POST | `/api/ai/vocational/{id}` |
| AuthController | GET | `/api/health` |
| AuthController | POST | `/api/auth/login` |
| AuthController | GET | `/api/auth/me` |
| AuthController | POST | `/api/auth/logout` |
| AuthController | PUT | `/api/auth/profile` |
| Challenges | GET | `/api/challenges` |
| Challenges | POST | `/api/challenges` |
| Challenges | GET | `/api/challenges/{id}` |
| Challenges | POST | `/api/challenges/{id}/submit` |
| Challenges | GET | `/api/challenges/ranking/all` |
| Exams | GET | `/api/exams` |
| Exams | POST | `/api/exams` |
| Exams | DELETE | `/api/exams/{id}` |
| Exams | POST | `/api/exams/{id}/start` |
| Exams | GET | `/api/attempts/{id}` |
| Exams | PUT | `/api/attempts/{id}/answers` |
| Exams | POST | `/api/attempts/{id}/submit` |
| Exams | GET | `/api/grading` |
| Exams | POST | `/api/attempts/{id}/grade` |
| Knowledge | GET | `/api/materials` |
| Knowledge | GET | `/api/materials/{id}/versions` |
| Knowledge | POST | `/api` |
| Knowledge | DELETE | `/api/materials/{id}` |
| Knowledge | GET | `/api/files/material/{id}` |
| Knowledge | GET | `/api/questions` |
| Knowledge | POST | `/api/questions` |
| Knowledge | GET | `/api/resolutions` |
| Knowledge | POST | `/api/resolutions` |
| Knowledge | PUT | `/api/resolutions/{id}` |
| Knowledge | POST | `/api/resolutions/{id}/review` |
| Knowledge | GET | `/api/bulletins` |
| Knowledge | POST | `/api` |
| Knowledge | GET | `/api/files/bulletin/{id}` |
| Knowledge | DELETE | `/api/bulletins/{id}` |
| Reports | GET | `/api/reports` |
| Reports | GET | `/api/reports/download` |
| Reports | GET | `/api/reports/vocational/{id}` |
| Reports | POST | `/api/reports/cards/{cycle}/{student}` |
| Reports | GET | `/api/reports/cards` |
| Reports | GET | `/api/reports/cards/{id}/pdf` |
| Vocational | GET | `/api/vocational/instrument` |
| Vocational | GET | `/api/vocational` |
| Vocational | POST | `/api/vocational` |
| Vocational | GET | `/api/vocational/{id}` |
| Vocational | PUT | `/api/vocational/{id}` |
| Vocational | POST | `/api/vocational/{id}/finish` |

## Contratos principales

### Ingreso

```json
{"email":"alumno@academia.local","password":"Alumno2026!"}
```

`POST /api/auth/login` devuelve `token` y `user`. El token expira en ocho horas. Logout lo revoca. La contraseña nunca forma parte de la respuesta.

### Asistencia

`POST /api/sessions/{id}/attendance`: `rows` debe contener exactamente a los alumnos matriculados en la fecha de la sesión. Cada fila usa `id`, `state` y `note`. Correcciones de sesiones realizadas exigen `reason`.

### Evaluación

`POST /api/exams`: `offering_id`, `title`, `category`, `relative_weight`, `opens_at`, `closes_at`, `release_at`, `duration`, `max_attempts` y `questions: [{version_id, points}]`. Instantes en ISO-8601. Un simulacro puede restringirse mediante `university` y `career`.

`POST /api/exams/{id}/start` devuelve identificador de intento; `GET /api/attempts/{id}` devuelve preguntas autorizadas y hora del servidor. El autoguardado usa `PUT /api/attempts/{id}/answers` con `answers: [{response_id,answer}]`. Alternativas usan índice como cadena (`"0"`, `"1"`, etc.); desarrollo usa texto.

### Vocación

`POST /api/vocational` crea o reanuda borrador. `PUT /api/vocational/{id}` recibe `answers` (mapa de ID de pregunta a número entero 1–5) y `preferences`. `POST /api/vocational/{id}/finish` exige 48 respuestas y calcula el resultado. `POST /api/ai/vocational/{id}` solicita explicación opcional. `GET /api/reports/vocational/{id}` descarga PDF propio.

### Reportes

`GET /api/reports` previsualiza; `/download` exporta. Parámetros: `type=grades|attendance|performance|teachers`, `format=pdf|xlsx`, `cycle`, `offering`, `section` (nombre), `student` (UUID), `from` y `to`. Las fechas de rango usan `AAAA-MM-DD`. Un alumno nunca puede ampliar su alcance pasando parámetros. El reporte de docentes solo es del director.

### Archivos y versiones

Materiales y balotarios usan multipart en sus rutas POST. Para nueva versión del material envía `id` y `reason` junto con el contenido. Los archivos se descargan desde `/api/files/material/{version}` o `/api/files/bulletin/{id}`, con token. Los nombres originales no se usan como rutas físicas.

## Permisos resumidos

| Operación | Director | Docente | Alumno |
| --- | --- | --- | --- |
| Gestionar usuarios/ciclos/matrículas | Sí | No | No |
| Leer catálogo académico | Global | Asignado | Matriculado |
| Registrar asistencia/notas | Global | Asignado | No |
| Publicar material y preguntas | Global | Asignado | No |
| Crear resoluciones | Sí | Curso propio | Curso propio |
| Aprobar resoluciones | Global | Curso propio | No |
| Rendir exámenes y retos | No | No | Propios |
| Ver test vocacional | No | No | Propio |
| Exportar reportes | Global | Asignado | Propios |
| Emitir boleta definitiva | Sí | No | No |

Los contratos exactos, validaciones y ejemplos ejecutables se encuentran en los controladores y en `AcademiaTest.java`.

## Práctica automática de balotarios

- `POST /api/bulletins` (multipart, DIRECTOR): guardar PDF y encolar preparación automática. La interfaz solo pide archivo, título, universidad y carrera; completa año y proceso de práctica libre.
- `GET /api/bulletins`: incluye `processing_status`, `question_count`, `processed_count` y `processing_error`.
- `POST /api/bulletins/{id}/process` (DIRECTOR): reintentar archivo antiguo, error o espera de IA.
- `GET /api/bulletins/{id}/practice`: información e intentos del alumno autenticado.
- `POST /api/bulletins/{id}/practice/start` (ALUMNO): reanudar intento o crear otro, sin horarios.
- `GET /api/bulletin-attempts/{id}` (propietario): preguntas, imágenes y respuestas; las claves y explicaciones solo aparecen al finalizar.
- `PUT /api/bulletin-attempts/{id}/answers`: `{ "answers": { "id-pregunta": "0" } }`; índices de alternativas representados como cadenas desde cero.
- `POST /api/bulletin-attempts/{id}/submit`: finalización idempotente con resultados inmediatos.
- `GET /api/bulletin-questions/{id}/images/{index}`: recorte autorizado de pregunta; el alumno debe tener un intento iniciado.

No requiere seleccionar una oferta de curso ni crear un examen programado. Las claves provienen de IA y las preguntas sin clave consistente se excluyen del denominador de calificación.
