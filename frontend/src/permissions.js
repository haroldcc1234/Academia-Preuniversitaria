export const ROLES = {
  DIRECTOR: {
    title: 'Dirección', description: 'Administración y seguimiento institucional',
    groups: [
      { title: 'Institución', links: [['/', 'Resumen institucional'], ['/organization', 'Ciclos y matrículas'], ['/users', 'Usuarios'], ['/courses', 'Cursos y docentes'], ['/schedule', 'Programación de clases']] },
      { title: 'Supervisión', links: [['/attendance', 'Seguimiento de asistencia'], ['/grades', 'Notas y boletas'], ['/reports', 'Reportes institucionales']] },
      { title: 'Administración', links: [['/bulletins', 'Publicar balotarios'], ['/settings', 'Configuración y auditoría'], ['/universities', 'Universidades del Perú']] },
    ],
    shortcuts: [['/organization', 'Ciclos y matrículas', 'Organiza secciones, cursos e inscripciones.'], ['/reports', 'Reportes institucionales', 'Revisa el rendimiento y la asistencia.'], ['/bulletins', 'Publicar balotarios', 'Sube material para la práctica de los estudiantes.']],
  },
  DOCENTE: {
    title: 'Docencia', description: 'Clases, evaluación y seguimiento de tus estudiantes',
    groups: [
      { title: 'Mis clases', links: [['/', 'Resumen docente'], ['/courses', 'Cursos asignados'], ['/schedule', 'Clases y registro de asistencia'], ['/attendance', 'Seguimiento de asistencia']] },
      { title: 'Enseñanza', links: [['/materials', 'Material de clase'], ['/questions', 'Banco de preguntas'], ['/exams', 'Evaluaciones y corrección'], ['/grades', 'Calificaciones'], ['/resolutions', 'Revisión de resoluciones'], ['/universities', 'Universidades del Perú']] },
    ],
    shortcuts: [['/schedule', 'Registrar asistencia', 'Consulta tus sesiones y registra la asistencia.'], ['/exams', 'Evaluaciones y corrección', 'Prepara evaluaciones y revisa los intentos.'], ['/materials', 'Material de clase', 'Publica recursos para los cursos a tu cargo.']],
  },
  ALUMNO: {
    title: 'Estudiante', description: 'Tus cursos, actividades y resultados',
    groups: [
      { title: 'Mi ciclo', links: [['/', 'Mi inicio'], ['/courses', 'Mis cursos'], ['/schedule', 'Mi horario'], ['/attendance', 'Mi asistencia'], ['/grades', 'Mis notas']] },
      { title: 'Aprendizaje', links: [['/materials', 'Material de mis cursos'], ['/exams', 'Mis evaluaciones'], ['/bulletins', 'Practicar balotarios'], ['/challenges', 'Retos y ranking'], ['/resolutions', 'Resoluciones compartidas']] },
      { title: 'Orientación', links: [['/chat', 'Asistente de estudio'], ['/vocational', 'Orientación vocacional'], ['/universities', 'Universidades del Perú']] },
    ],
    shortcuts: [['/exams', 'Mis evaluaciones', 'Responde tus evaluaciones y consulta los resultados.'], ['/bulletins', 'Practicar balotarios', 'Practica pregunta por pregunta, a tu ritmo.'], ['/chat', 'Asistente de estudio', 'Consulta tus dudas y tu progreso por curso.']],
  },
};
export const roleLinks = role => ROLES[role]?.groups.flatMap(group => group.links) || [];
export const canVisit = (role, path) => !!ROLES[role] && (['/profile', '/notifications'].includes(path) || roleLinks(role).some(([url]) => url === path));
