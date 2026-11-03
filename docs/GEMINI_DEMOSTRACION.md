# Gemini para la demostración

La integración se aplica solo a los balotarios. El asistente del alumno, sus notas y su historial siguen usando Ollama y la base de datos local. No se activó una cuenta de pago ni se envió el PDF a Google durante el desarrollo.

## Activar
1. En https://aistudio.google.com/apikey crea una clave en un proyecto con nivel gratuito. Revisa la cuota disponible para `gemini-3.8-flash`; la suscripción de Gemini no determina la cuota de la API.
2. Desde esta carpeta ejecuta en PowerShell:

       .\scripts\configurar-gemini.ps1

   La entrada de la clave es oculta. Se guarda en `.env`, que está excluido de Git. No compartir ese archivo. La clave no se incorpora al frontend.
3. Con Docker Desktop funcionando, ejecuta:

       docker compose up -d --build

4. Abre http://localhost:3000 e ingresa como Director. En Publicar balotarios, usa Comprobar conexión. Luego sube un PDF público de preguntas universitarias.

No hace falta instalar un SDK ni descargar modelos para que Gemini resuelva balotarios. La descarga de Ollama ya no bloquea el arranque del backend; Ollama sigue disponible para las funciones locales.

## Qué cambia
- Gemini recibe el texto y las imágenes de las preguntas que necesitan visión y responde con JSON estructurado.
- Dos resoluciones independientes deben coincidir en clave y alternativas. Esto reduce errores, pero no prueba que una respuesta sea correcta ni la convierte en clave oficial.
- La primera solución se guarda en la base de datos. Si falla la segunda consulta, se retoma sin repetir la primera.
- Ante cuota temporal o indisponibilidad, se conserva el avance y se respeta Retry-After. Si falla la clave o el modelo, aparece un error de configuración, no una clave inventada.
- Las preguntas sin solución consistente se excluyen de la nota; el resultado del alumno muestra el desarrollo de las calificables.
- Ollama mantiene el modelo en memoria durante cinco minutos en lugar de descargarlo después de cada respuesta.

## Modelo y velocidad
`gemini-3.8-flash` es el punto inicial por su nivel gratuito documentado y capacidad multimodal. El modelo se puede cambiar en GEMINI_MODEL, o mediante `configurar-gemini.ps1 -Modelo nombre-del-modelo`, tras comprobar acceso y precisión. No se configura un modelo de pago automáticamente.

GEMINI_MIN_INTERVAL_MS=6500 separa las solicitudes. Ajústalo a las cuotas reales del proyecto en AI Studio. El procesamiento sigue haciendo dos consultas por pregunta: 100 preguntas pueden requerir unas 200 solicitudes, además de la extracción visual si el PDF es escaneado. No se garantiza terminar 100 preguntas en una sola cuota gratuita ni en un tiempo específico. El proveedor puede rechazar por solicitudes, tokens o cuota diaria. No se usa concurrencia ilimitada ni se omiten comprobaciones para aparentar rapidez.

Para una demostración fiable, procesa el balotario antes de presentar. Valida primero 10–15 preguntas contra un solucionario oficial, incluyendo figuras, matemática y lectura. En la presentación muestra tanto el progreso de una carga como un balotario ya preparado. Nunca presenta respuestas simuladas como soluciones reales.

## Volver a Ollama
En `.env`, cambia BULLETIN_AI_PROVIDER=ollama y recrea el backend (`docker compose up -d backend`). No se borran preguntas ni intentos. No hay cambio automático silencioso entre proveedores.

## Validación y límites
Se prueban contra un servidor HTTP simulado: autenticación en cabecera, imágenes, esquema JSON, rechazo de respuestas truncadas, diferenciación de errores de clave y cuota, conservación de la primera solución y ocultación de claves al estudiante. Se prueban también frontend, permisos y migración del nuevo campo.
La clave ya está configurada localmente. El 26/10/2026 las pruebas reales de generación devolvieron HTTP 503 (alta demanda) con Gemini 3.8 Flash y con Flash Lite; por ello no se certifica aún calidad ni velocidad sobre un PDF real. El modelo 2.5 Flash fue rechazado para esta cuenta y se actualizó a 3.8 Flash. El botón Comprobar conexión ahora solicita una respuesta real y muestra la indisponibilidad; una prueba exitosa tampoco acredita la calidad de todo un examen.

Documentación: https://ai.google.dev/api/generate-content
Precios: https://ai.google.dev/gemini-api/docs/pricing
Cuotas: https://ai.google.dev/gemini-api/docs/rate-limits
