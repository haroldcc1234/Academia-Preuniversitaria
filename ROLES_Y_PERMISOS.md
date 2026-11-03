# Funciones por rol

## Dirección
Administrar usuarios, ciclos, secciones, asignaciones y matrículas. Programar clases, supervisar asistencia, consultar notas y boletas, rectificar notas con motivo registrado, consultar reportes institucionales y auditoría. Subir y publicar balotarios automáticos para estudiantes.

## Docente
Consultar únicamente sus cursos y alumnos. Planificar sesiones, registrar asistencia, publicar materiales y preguntas, crear y corregir evaluaciones, registrar calificaciones y revisar resoluciones. Sin administración de usuarios, reportes institucionales ni publicación de balotarios.

## Alumno
Consultar sus cursos, horario, asistencia, notas y boletas. Acceder a materiales de sus cursos, rendir evaluaciones, practicar balotarios, participar en retos y consultar rankings, compartir resoluciones, usar el asistente de estudio y orientación vocacional. No puede consultar el padrón de compañeros ni el banco docente de preguntas.

La navegación y las rutas están limitadas por rol. El servidor también rechaza peticiones no autorizadas; se conservan los controles de curso asignado y propiedad del estudiante.

## Diseño
Menús agrupados por función, inicio y accesos rápidos distintos por rol, paleta verde sobria, tipografía del sistema y eliminación de emojis e iconos decorativos de IA. Se mantienen controles funcionales de búsqueda, descarga y edición.

## Validación de esta revisión
- Backend: 24 pruebas aprobadas; 2 opcionales omitidas (PDF externo e IA real).
- Navegación: prueba de rutas permitidas y prohibidas por rol aprobada.
- Frontend: ESLint y compilación de producción aprobados.
- No se pudo verificar visualmente con el navegador integrado: no conecta al servidor local.
- Docker Desktop terminó inesperadamente al arrancar; no se reconstruyeron los contenedores durante esta revisión.

## Aplicar en Docker
Los fuentes están actualizados en esta carpeta. Cuando Docker Desktop arranque normalmente, ejecutar una sola vez desde esta carpeta:

    docker compose up -d --build

Luego abrir http://localhost:3000. Esta operación conserva los volúmenes existentes; no ejecutar down -v. Los siguientes arranques pueden hacerse desde Docker Desktop.
