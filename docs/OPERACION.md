# Operación, respaldos y alcance institucional

## Configuración

| Variable | Uso |
| --- | --- |
| DB_URL, DB_USER, DB_PASSWORD | Conexión al MySQL institucional |
| APP_DEMO | Carga inicial de ejemplos solo en base sin usuarios |
| ADMIN_EMAIL, ADMIN_PASSWORD | Primera cuenta de dirección; no cambia una cuenta existente |
| CORS_ORIGINS | Orígenes autorizados, separados por coma |
| STORAGE_DRIVER | `local` o `s3` |
| FILES_PATH | Carpeta privada para documentos; debe tener permisos de escritura del usuario de Java |
| S3_ENDPOINT, S3_BUCKET, S3_ACCESS_KEY, S3_SECRET_KEY | Almacenamiento compatible con S3/MinIO, opcional |
| OLLAMA_URL, OLLAMA_MODEL | Servicio local y modelo descargado |
| MAIL_ENABLED | Activa entrega de alertas por correo |
| SMTP_HOST, SMTP_PORT, SMTP_USER, SMTP_PASSWORD | Credenciales y servidor de correo autorizado |
| SMTP_AUTH, SMTP_TLS, MAIL_FROM | Autenticación, STARTTLS y remitente |

El Compose de inicio usa archivos privados en un volumen local. El controlador S3 está implementado para un bucket ya creado y privado, pero no despliega MinIO por defecto. Los archivos se descargan pasando siempre por la API autenticada. La aplicación no rastrea ni descarga enlaces arbitrarios del usuario.

El límite de archivo es 25 MB; la solicitud multipart admite 26 MB. Se recomienda publicar videos de mayor tamaño como enlaces a material autorizado, con transcripción. No se incluyen exámenes ni balotarios comerciales sin autorización del titular.

## Copias y recuperación

`bash scripts/backup.sh` produce un volcado SQL y un archivo comprimido de documentos en `backups/`. Guarda ambos fuera del equipo de la aplicación. Para un conjunto coherente de archivos y datos, evita escrituras durante la copia. Para producción se debe programar, cifrar y probar la restauración de esos respaldos.

Restauración de SQL en una instalación de recuperación con base vacía (bash):

```sh
docker compose stop backend frontend
docker compose exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -u root academia_jauja' < backups/database-FECHA.sql
```

Restauración de documentos al volumen existente (el backend debe poder iniciar el contenedor de mantenimiento):

```sh
docker compose run --rm -T --no-deps --entrypoint tar backend -C /app/private-files -xzf - < backups/files-FECHA.tar.gz
docker compose up -d backend frontend
```

Verifica después que un usuario de prueba puede abrir un material y descargar una boleta. Los respaldos contienen datos privados; no se deben publicar. Para almacenamiento S3 se respalda el bucket con la herramienta de ese proveedor y la base por separado.

## Operaciones académicas

- Programa todas las sesiones del ciclo antes de interpretar el porcentaje de faltas del ciclo; una programación incompleta produce un denominador incompleto.
- «Falta grave» es un indicador institucional automático desde 50%, no una expulsión. La justificación y las decisiones posteriores requieren revisión humana.
- «A la fecha» permite detectar problemas antes del final; no debe confundirse con el porcentaje del ciclo.
- Las notas de desarrollo necesitan revisión. No se emite boleta definitiva hasta completar las categorías y cerrar el ciclo.
- El cierre bloquea ediciones de ofertas, notas y asistencia. La reapertura solicita motivo y deja auditoría.
- Las bajas de usuarios/materiales son lógicas para conservar historial; no equivalen a eliminación definitiva de datos personales.
- Las ponderaciones por categoría se configuran antes de crear el ciclo. Cambiar la configuración global afecta a ciclos nuevos, no a boletas ya emitidas.

## Límites de cumplimiento y puesta en producción

Las reglas de **16 semanas**, **50%** y **0–20** implementan lo solicitado para la academia. No se presentan como una obligación legal general para todas las academias peruanas. «Boleta institucional» es un reporte de la academia, no un certificado emitido, firmado o validado por MINEDU/SIAGIE. No existe integración con esos sistemas.

La entrega incorpora control de acceso, registro de tutor/consentimiento, archivos privados y auditoría. Esto no certifica cumplimiento normativo. Antes de usar datos reales, la institución debe definir sus bases y avisos de tratamiento, autorización aplicable a menores, plazos de conservación, atención de derechos de los titulares y autorización para usar materiales; también debe verificar las obligaciones educativas y de funcionamiento que correspondan a su actividad. No se ha completado una revisión jurídica de la normativa vigente ni de la licencia de funcionamiento de una academia concreta.

No se incluyen: firma digital de certificados, facturación/pagos, integración SUNEDU/MINEDU, monitorización de exámenes por cámara, OCR, validación psicométrica, restauración en un clic, multisedes con aislamiento, MFA o recuperación autónoma por correo. No fueron sustituidos por controles ficticios. El cambio de contraseña lo hace el usuario autenticado o un director que restablece su cuenta.

Para un despliegue institucional faltan la configuración del servidor real y HTTPS, contraseñas propias, política de respaldo, revisión de dependencias, pruebas de carga, verificación SMTP/S3 si se usan y aceptación de los flujos por docentes/alumnos. Las imágenes están etiquetadas; para reproducibilidad estricta se deben fijar sus digests después de validarlas en el entorno de despliegue. El ZIP no publica el servicio en Internet.

## Solución de problemas

- **Login falla:** espera a que backend complete la migración y semilla; consulta `docker compose logs backend`. Las cuentas demo solo existen si la base se inicializó en modo demo.
- **MySQL no inicia:** revisa `docker compose logs mysql`. MySQL usa la red interna de Docker y no publica el puerto 3306 en Windows.
- **Web 502 al primer inicio:** espera a que termine Java y vuelve a cargar.
- **IA desconectada:** inicia el servicio `ollama` del grupo, descarga exactamente el modelo configurado y revisa `docker compose logs ollama`.
- **Sin balotarios:** carga uno como director y comprueba matrícula, universidad y carrera del alumno.
- **No aparecen resultados:** verifica fecha de publicación y calificaciones de desarrollo pendientes.
- **Sin correo:** revisa MAIL_ENABLED, remitente, SMTP y contador de reintentos. La notificación in-app no depende del éxito de SMTP.
- **Sin boleta:** faltan categorías, hay sesiones programadas o intentos pendientes, o el ciclo sigue abierto.
