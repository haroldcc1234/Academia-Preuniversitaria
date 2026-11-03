# Orientación académica según notas

Implementado el 30/10/2026. GET /api/ai/careers consulta exclusivamente al alumno autenticado mediante Metrics.grades, que usa registros y exámenes calificados con resultados publicados. No usa respuestas ni notas privadas de otros alumnos.

AcademicCareers contiene 16 perfiles orientativos con pesos por área. Agrupa cursos normalizando acentos y toma una sola matrícula por nombre de curso: la que contiene la evaluación publicada más reciente. Los promedios provisionales conservan su indicador. Los cursos generales o desconocidos no se asignan a especialidades inventadas.

Requiere dos áreas relacionadas y al menos 60% del peso previsto. Calcula la media de cursos por área, aplica los pesos de la carrera y normaliza por el peso observado. Ordena por promedio, cobertura y nombre; devuelve hasta cuatro opciones. Cero es una nota válida; ausencia de notas no equivale a cero. Muestra áreas faltantes, empates en el primer lugar y cursos por reforzar (menos de 13/20).

Estas reglas son una propuesta de la aplicación, no un instrumento validado, un ranking de universidades ni una predicción de éxito. No modifican el test de intereses, las notas ni la carrera elegida. Las sugerencias aparecen al abrir el asistente o actualizar recomendaciones. El chat recupera esos mismos resultados para que Ollama los explique; si falla, entrega la explicación preparada, identificada como orientación según notas.

Validación: AcademicCareersTest (perfiles diferentes, ausencia de datos, cero, cobertura, matrícula repetida, empate); AcademicCareersAccessTest (autenticación, rol, ámbito propio, respuesta sin Ollama). Frontend: ESLint y compilación Vite. No requiere migraciones de base de datos.
