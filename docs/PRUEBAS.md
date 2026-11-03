# Evidencia de validación — 21 de septiembre de 2026

## Resultado

**14 pruebas de integración: 0 fallos, 0 errores, 0 omitidas.** Se ejecutó el backend Spring Boot completo con MockMvc y base H2 en modo MySQL. La última ejecución duró 19,39 segundos. Resultado original en `docs/resultado-backend.txt`.

| Prueba | Lo verificado |
| --- | --- |
| authAndRbac | Sesión obligatoria, denegación por rol y por oferta, no exposición de contraseñas |
| pagesReadWithoutServerErrors | Lectura de los endpoints principales por alumno/director, incluido refuerzo recomendado |
| cycleAlways16Weeks | Cálculo exacto de fecha final de ciclo |
| gradeValidationAndCorrection | Rechazo de nota >20, alumno sin permiso de escritura, corrección y promedio completo |
| vocationalValidationScoresAndIsolation | Test incompleto rechazado, cálculo de áreas 0/100, informe congelado y PDF solo propio |
| vocationalUniformAndInvalid | Detección de respuestas uniformes/empates y rechazo de valores inválidos |
| attemptsProtectAnswersEnforceTimingAndGrade | Claves ocultas, propiedad, guardado, autocorrección, publicación, vencimiento e intentos |
| attendanceBoundary | 16 faltas de 32 sesiones = 50% y falta grave; diferencia con porcentaje a la fecha |
| materialVersionUploadAndArchive | Carga real de archivo, descarga, nueva versión y archivo lógico |
| exportsHaveRealFormats | PDF con cabecera PDF y Excel OOXML real; denegación de reporte docente al alumno |
| chatFallbackDoesNotInventAi | Fuentes recuperadas y modo BUSQUEDA explícito cuando Ollama está ausente |
| challengeOneSubmissionAndSolutionsHidden | Soluciones ocultas, entrega única y liberación tras finalizar ambos participantes |
| weightedEvaluationsAndScopedCatalogue | Pesos relativos 1:3 dan 17,5; catálogo docente sin secciones ajenas |
| administrationPreservesCycleAndCapacity | No reducir vacantes bajo matrícula, no mover ciclo con secciones, edición solo del director |

## Frontend

- `npm ci`/instalación de dependencias completada.
- `npm run check` finalizado sin errores. Comprueba nombres no definidos, referencias JSX, duplicados y código inalcanzable.
- `npm run build` finalizado: 2.205 módulos procesados; bundle JavaScript aproximadamente 697 kB antes de compresión y 197 kB gzip. Vite avisa del tamaño, sin impedir la compilación.
- No se completó una prueba visual interactiva en navegador: el navegador del entorno no pudo conectarse al servicio de vista previa. No se afirma una revisión visual de escritorio/móvil realizada.

## MySQL

Se descargó el binario oficial **MySQL Community Server 8.4.6** y se ejecutaron las migraciones V1 y V2 mediante `--initialize-insecure --init-file` en un directorio temporal de validación. MySQL creó las **30 tablas** de la aplicación y aplicó las columnas de ponderación, sin error de esquema. Esa ejecución temporal no forma parte de la instalación del usuario ni se incluye en el ZIP.

No fue posible ejecutar el servidor MySQL normal en este entorno: el sistema rechazó la apertura del socket UNIX. Por ello las pruebas HTTP transaccionales se ejecutaron con H2, no contra MySQL conectado. Crear las tablas con MySQL valida el DDL; **no equivale a probar todos los flujos JDBC contra MySQL**.

Para repetir la suite contra MySQL en un equipo que lo permita, crea una base **vacía y exclusiva de pruebas** y ejecuta (Maven con JDK 21):

```sh
mvn test -Dspring.datasource.url="jdbc:mysql://localhost:3306/academia_integracion?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC" -Dspring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver -Dspring.datasource.username=USUARIO_PRUEBAS -Dspring.datasource.password=CLAVE_PRUEBAS
```

La suite crea datos ficticios y presupone una base nueva por ejecución. No debe apuntarse a la base institucional. Repeticiones requieren otra base de pruebas vacía. Los secretos reales deberían proporcionarse mediante la configuración segura de CI, no quedar en el historial de una terminal compartida.

## Otras comprobaciones y pendientes

- `compose.yaml`: YAML válido, cinco servicios y tres volúmenes.
- Docker Compose no se ejecutó porque el entorno no proporciona Docker. El build Java 21 dentro de Docker queda por confirmar al instalar; las pruebas locales usaron Java 17 con `-Djava.version=17`.
- No se descargaron pesos ni se generaron respuestas con Ollama. Sí se probó el comportamiento sin modelo.
- SMTP real y almacenamiento S3 opcional no fueron ejercitados. La ruta de almacenamiento local sí fue probada.
- Falta aceptación de usuario con docentes y alumnos, prueba visual en dispositivos reales y prueba de carga para el volumen institucional.

Estas limitaciones están diferenciadas de lo implementado para evitar presentar compilación o pruebas con una base de apoyo como una certificación de despliegue productivo.
