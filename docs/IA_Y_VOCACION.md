# Asistente y orientación vocacional

## IA local

Ollama se consume mediante `GET /api/tags` y `POST /api/chat`, desde el backend. El navegador no recibe credenciales de proveedores. `OLLAMA_URL` y `OLLAMA_MODEL` configuran el servicio; el Compose lo mantiene dentro de su red. Modelo inicial: `qwen2.5:3b`. Los pesos no vienen en el ZIP y no se ha ejecutado el modelo en el entorno de construcción.

«Sin pago por consulta» significa que el proceso de inferencia se ejecuta en la computadora/servidor del operador. Existen costos de hardware, electricidad y alojamiento. No depende de una promoción de créditos ni de una API comercial con cuota gratuita cambiante.

## Recuperación de conocimiento

1. Identificar usuario autenticado y alcance de matrícula/asignación.
2. Obtener las versiones vigentes de materiales accesibles y resoluciones aprobadas. El banco reservado para evaluaciones activas no se expone al alumno.
3. Normalizar texto y consulta; puntuar coincidencias de términos en título, tema y contenido.
4. Seleccionar hasta cuatro recursos; limitar cada texto a 3.500 caracteres.
5. Adjuntar los fragmentos como contexto no confiable al modelo, con la instrucción de responder solo con ese respaldo.
6. Mostrar referencias con identidad y versión. Guardar conversación por usuario y proveedor real.

Si no hay fuentes, el sistema pide precisar el tema. Si no hay modelo disponible, devuelve los recursos relacionados, identificado como **BUSQUEDA**. Se controla concurrencia de generación (dos solicitudes por instancia), tiempo máximo y ritmo por usuario. Los materiales PDF, DOCX, PPTX y TXT con texto utilizable se extraen al subir; no hay OCR para escaneos, reconocimiento de voz ni acceso automático a contenido de enlaces externos. Para videos o imágenes se debe proporcionar transcripción/texto de apoyo.

El refuerzo prioriza cursos con menores promedios publicados y propone materiales del mismo curso. La recomendación inicial es determinista, no una predicción de éxito ni de abandono. Las respuestas del modelo pueden equivocarse y el alumno debe contrastarlas con las fuentes.

## Instrumento vocacional

`backend/src/main/resources/vocational.json` contiene la versión, las 48 preguntas originales, seis dimensiones, descripciones y 16 carreras exploratorias. Las dimensiones son Realista, Investigativa, Artística, Social, Emprendedora y Convencional. Se inspira en el marco RIASEC; **no reproduce ni se presenta como el instrumento oficial O*NET**. Referencia conceptual: [O*NET Interest Profiler](https://www.onetcenter.org/IP.html).

Cada dimensión tiene ocho ítems. La persona responde del 1 (muy poco interés) al 5 (mucho interés). Fórmulas:

```text
puntaje_area = (suma_de_8_respuestas − 8) / 32 × 100
afinidad_carrera = 0.50 × area_principal
                 + 0.30 × segunda_area
                 + 0.20 × tercera_area
```

Las combinaciones de áreas por carrera son decisiones explícitas del catálogo; no coeficientes psicométricos validados. La afinidad **no es una probabilidad de éxito**. Los seis resultados de mayor afinidad son opciones para explorar. Empates y patrones uniformes se explican; no se fuerza una única carrera.

El backend rechaza preguntas desconocidas, valores fuera de 1–5, decimales y cierre con menos de 48 respuestas. Borradores se pueden modificar; después del cierre se inicia un nuevo test para cambiar respuestas. Nadie puede acceder por API al informe de otro alumno, incluido el director en la implementación actual. No se utilizan notas, sexo, salud, ingresos o ascendencia para determinar una carrera. La preferencia de ubicación afecta el orden de universidades, no los puntajes.

## Universidades

El catálogo inicial es orientativo y contiene UNCP, UNI, UNMSM, UNSAAC y UNSA. UNCP y UNI incluyen fecha de revisión de la oferta; las demás se identifican como pendientes de actualización. Director → Configuración permite corregir carreras, fuente y fecha. La lista prioriza el carácter público y, si se elige Junín, las opciones de Huancayo disponibles. «Solo nacionales» filtra por universidad pública. No equivale a una clasificación de las mejores universidades ni pretende cubrir toda la oferta peruana.

La selección de universidad parte de una correspondencia exacta entre la carrera del catálogo y las carreras almacenadas por universidad. Educación agrupa especialidades: el enlace oficial debe usarse para comprobar la especialidad, sede y convocatoria. Las fuentes iniciales incluyen:

- [Facultades UNCP](https://uncp.edu.pe/pregrado/facultades/)
- [Portal UNI](https://portal.uni.edu.pe/)
- [Carreras de pregrado UNMSM](https://www.unmsm.edu.pe/formacion-academica/carreras-de-pregrado)
- [UNSAAC](https://www.unsaac.edu.pe/)
- [UNSA](https://www.unsa.edu.pe/)

El modelo explica el resultado ya calculado y el catálogo adjunto: no debe inventar instituciones, alterar puntajes ni generar diagnósticos. La explicación se solicita voluntariamente, se guarda en el resultado y puede incluirse en el PDF. Si Ollama falla, el informe base continúa disponible.

Antes de usarlo como instrumento formal de orientación institucional hace falta una validación local por profesionales competentes. El sistema no decide matrícula, acceso educativo ni sanciones a partir del test.
