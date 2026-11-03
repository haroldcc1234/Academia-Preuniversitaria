# Academia Jauja — Sistema académico con MySQL

Código fuente de una aplicación web con frontend React, backend Java/Spring Boot y MySQL 8.4. Incluye módulos de Director, Docente y Alumno; el test vocacional forma parte del sistema, guarda resultados y genera PDF. No es una maqueta: las pantallas consumen la API y los cambios se guardan en la base de datos.

Actualización: el asistente muestra notas y evolución por curso, los balotarios PDF se preparan como prácticas automáticas con Ollama, preguntas individuales y resultados inmediatos, sin programar horarios, y los retos muestran nombres, puntajes y puestos con filtro por curso. Consulta [cambios, revisión y límites](docs/CAMBIOS_Y_REVISION.md).

## Inicio rápido con Docker

Requisitos: Docker Engine con Compose v2 o Docker Desktop abierto. Internet en la primera instalación para descargar dependencias e imágenes. Se recomienda disponer de 8 GB de RAM; el modelo de IA necesita memoria adicional.

1. Extrae este ZIP y abre una terminal dentro de `academia-jauja`.
2. Copia `.env.example` a `.env` (en Windows también puedes ejecutar `iniciar.ps1`).
3. Ejecuta:

```sh
docker compose up --build -d
```

4. Abre **http://localhost:3000**. El primer inicio tarda mientras se compilan las aplicaciones. Para ver el avance:

```sh
docker compose logs -f backend
```

Las migraciones crean las tablas automáticamente. Los datos ficticios se cargan únicamente cuando la base no contiene usuarios y `APP_DEMO=true`.

| Rol | Correo de demostración | Contraseña |
| --- | --- | --- |
| Director | director@academia.local | Director2026! |
| Docente | docente@academia.local | Docente2026! |
| Alumno | alumno@academia.local | Alumno2026! |
| Segundo alumno para retos | alumno2@academia.local | Alumno2026! |

Los botones de demostración del login rellenan estas credenciales; el servidor verifica la cuenta y su contraseña. No omiten la autenticación.

Para detener y conservar datos: `docker compose stop`. Para reiniciar: `docker compose up -d`. Los volúmenes conservan MySQL y documentos. No uses `docker compose down -v` con información que quieras mantener.

## Test vocacional

Ingresa como Alumno → **Test vocacional** → inicia el cuestionario. Son 48 preguntas originales con escala de interés 1–5, distribuidas entre seis áreas. Puedes guardar y continuar, revisar respuestas, ver empates, explorar carreras, consultar universidades y descargar tu informe PDF.

- El cálculo se hace en el servidor y se guarda con la versión del instrumento.
- El informe base funciona sin conexión a un modelo de IA.
- Las universidades salen del catálogo institucional con su enlace y fecha de revisión; las pendientes se identifican explícitamente.
- El botón de explicación por IA requiere activar Ollama; no cambia los puntajes ni genera un ranking de calidad universitaria.
- Es orientación exploratoria: **no es una prueba psicométrica validada ni un diagnóstico de aptitud**. Conviene acompañarla con orientación humana.

## Activar IA local sin pago por consulta

La API utilizada es la de **Ollama**, ejecutada localmente. No exige una clave de una API comercial; necesita descargar un modelo y disponer de recursos de cómputo. No se promete una API externa ilimitada y gratuita.

```sh
docker compose up -d
docker compose exec ollama ollama pull qwen2.5:3b
docker compose exec ollama ollama pull qwen3-vl:2b
```

El asistente indicará «IA local conectada» cuando el servicio y el modelo estén disponibles. Puedes cambiar `OLLAMA_MODEL` en `.env` por otro modelo que hayas descargado y recrear el backend. Sin Ollama, se conserva la búsqueda autorizada en materiales y resoluciones, identificada como búsqueda; no simula una respuesta generativa.

El chatbot recupera contenido por tema y términos, añade fuentes y ofrece refuerzos según las notas publicadas del alumno. Solo recibe contenido que ese usuario puede consultar. No modifica calificaciones ni decide admisiones. Consulta `docs/IA_Y_VOCACION.md`.

## Funciones disponibles

- Usuarios: alta, edición, suspensión, retiro lógico y revocación de sesiones.
- Ciclos de 16 semanas, cursos, secciones, asignación docente, matrícula por curso, vacantes y retiro.
- Sesiones semanales, detección de cruces de horario, cancelación y asistencia completa por sesión.
- Asistencia: cuatro estados, historial, porcentaje del ciclo y porcentaje a la fecha, alerta grave desde 50%.
- Notas 0–20, pesos por categoría y por evaluación, correcciones auditadas, promedios, evolución y boletas institucionales.
- Materiales privados con archivos o enlaces, etiquetas, extracción de texto, versiones y archivo lógico.
- Banco reutilizable con preguntas de alternativa, verdadero/falso y desarrollo; versiones fijadas en cada examen.
- Exámenes mixtos, temporizador y disponibilidad de servidor, intentos, guardado, autocorrección, revisión manual y publicación programada.
- Resoluciones colaborativas con historial y aprobación docente.
- Balotarios por universidad/carrera/proceso/año, versiones, archivos y enlaces; simulacros creados desde Evaluaciones.
- Retos entre compañeros con preguntas aleatorias de práctica, una respuesta por participante y ranking por curso.
- Reportes filtrados de notas, asistencia, comparativos y carga docente en PDF y Excel reales.
- Alertas automáticas in-app, deduplicación, correo opcional y trazabilidad.
- Test vocacional, informe PDF, catálogo de universidades y asistente conectado al conocimiento autorizado.

## Flujo recomendado para probar

1. **Director:** revisa Gestión académica, Usuarios y Configuración. El demo ya contiene seis alumnos, dos docentes, tres cursos y un ciclo.
2. **Docente:** entra a Horario y sesiones, registra asistencia de una sesión pasada, publica un material y crea una evaluación desde el banco.
3. Para probar una evaluación, programa su apertura antes de ahora, el cierre unos minutos después y los resultados a partir del cierre. Los resultados del demo se publican posteriormente, por eso inicialmente figuran pendientes.
4. **Alumno:** consulta Biblioteca, rinde la evaluación, completa el test vocacional y crea un reto con el segundo alumno.
5. **Docente:** califica preguntas de desarrollo. **Director:** exporta los reportes.
6. Para boleta definitiva, completa categorías de notas, cierra o cancela sesiones pendientes, finaliza los intentos y cierra el ciclo. En Notas → detalle del alumno → Emitir boleta. La boleta congela una versión; las correcciones posteriores requieren reapertura y nueva emisión.

## Correo

Para probar sin enviar mensajes reales:

```sh
# Configura MAIL_ENABLED=true en .env
docker compose --profile correo up -d
```

Abre http://localhost:8025 para revisar Mailpit. En una instalación institucional configura `SMTP_HOST`, `SMTP_PORT`, `SMTP_USER`, `SMTP_PASSWORD`, `SMTP_AUTH`, `SMTP_TLS` y `MAIL_FROM` con tu servidor autorizado. La integración SMTP está implementada; no se ha enviado correo real desde este entorno.

## Ejecución manual sin Docker

Requisitos: JDK 21, Maven 3.9, Node 22 y un servidor MySQL 8.4. Crea la base y un usuario propio con permisos sobre ella, por ejemplo usando MySQL Workbench:

```sql
CREATE DATABASE academia_jauja CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'academia'@'localhost' IDENTIFIED BY 'elige-una-clave-propia';
GRANT ALL PRIVILEGES ON academia_jauja.* TO 'academia'@'localhost';
```

Configura `DB_PASSWORD` en el entorno del backend. En PowerShell: `$env:DB_PASSWORD='elige-una-clave-propia'`. En bash: `export DB_PASSWORD='elige-una-clave-propia'`. `.env` es leído por Docker Compose; Maven no lo carga automáticamente.

Primera terminal:

```sh
cd backend
mvn spring-boot:run
```

Segunda terminal:

```sh
cd frontend
npm ci
npm run dev
```

Abre http://localhost:5173. El proxy de Vite dirige `/api` a `localhost:8080`. El almacenamiento manual usa `backend/private-files`. Para usar tu Ollama nativo, conserva `OLLAMA_URL=http://localhost:11434` y descarga los modelos con `ollama pull qwen2.5:3b` y `ollama pull qwen3-vl:2b`. Para balotarios se requiere Ollama 0.12.7 o posterior y el modelo de visión configurado en `OLLAMA_BULLETIN_MODEL`.

## Instalación con datos institucionales

Usa una base nueva, `APP_DEMO=false`, un `ADMIN_EMAIL` propio y `ADMIN_PASSWORD` de al menos 10 caracteres diferente de la clave demo. Esto crea únicamente la cuenta administradora y los catálogos iniciales. Cambiar `APP_DEMO` no borra datos ya existentes ni restablece contraseñas.

El Compose entregado publica la web solo en el equipo local. Para acceso de alumnos desde otras computadoras hace falta configurar un servidor, dominio, HTTPS, credenciales y copias de seguridad. No se ha publicado un servidor de producción. La documentación explica los límites de validación y de cumplimiento institucional en `docs/OPERACION.md`.

## Pruebas

```sh
cd backend
mvn test
# En otra terminal, dentro de frontend:
npm run check
npm run build
```

Las pruebas automáticas usan H2 en modo MySQL, aislado de los datos reales. Ver `docs/PRUEBAS.md` para la evidencia, incluyendo la creación del esquema con MySQL 8.4.6 y las comprobaciones pendientes. Docker compila con Java 21. El código también se comprobó con Java 17 mediante `mvn -Djava.version=17 test`, que era la versión disponible en el entorno de construcción.

## Contenido del proyecto

| Ruta | Contenido |
| --- | --- |
| `frontend/` | React, interfaz adaptable, rutas por rol, gráficos y cliente API |
| `backend/` | API Java, servicios, seguridad, migraciones y pruebas |
| `compose.yaml` | MySQL, backend, frontend, Ollama y Mailpit opcionales |
| `docs/ARQUITECTURA.md` | Arquitectura, modelo de datos y decisiones |
| `docs/FLUJOS_E_INTERFAZ.md` | Flujos de cada rol y pantallas |
| `docs/IA_Y_VOCACION.md` | Recuperación de conocimiento y cálculo vocacional |
| `docs/OPERACION.md` | Configuración, respaldos y límites institucionales |
| `docs/API.md` | Contrato de rutas y permisos |
| `docs/PRUEBAS.md` | Validación y alcance comprobado |

El ZIP contiene fuentes y archivos de instalación. Las dependencias y los pesos del modelo se descargan al instalar; no se incluyen bases con datos personales.


Para el uso diario desde Docker Desktop, consulta [Abrir desde Docker](docs/ABRIR_EN_DOCKER.md).
