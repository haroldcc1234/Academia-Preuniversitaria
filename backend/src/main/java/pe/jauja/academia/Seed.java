package pe.jauja.academia;

import java.util.*;
import java.time.*;
import org.springframework.stereotype.Component;
import org.springframework.boot.CommandLineRunner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import static pe.jauja.academia.Store.*;

@Component
@org.springframework.core.annotation.Order(1)
public class Seed implements CommandLineRunner {
    final Store db;
    final PasswordEncoder pw;
    final boolean demo;
    final String email, password;

    public Seed(Store d, PasswordEncoder p, @Value("${app.demo}") boolean demo, @Value("${app.admin-email}") String e,
            @Value("${app.admin-password}") String pass) {
        db = d;
        pw = p;
        this.demo = demo;
        email = e;
        password = pass;
    }

    String user(String name, String email, String role, String password) {
        return db.insert("users", m("name", name, "email", email, "role", role, "password", pw.encode(password),
                "career", "Ingeniería de Sistemas", "university", "UNCP", "consent_at", now()));
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (db.find("SELECT id FROM settings WHERE id='main'") == null)
            db.insert("settings", m("id", "main", "name", "Academia Jauja", "warning_pct", 30, "low_grade", 11,
                    "high_grade", 17, "weights", "{\"PRACTICA\":30,\"PARCIAL\":30,\"SIMULACRO\":40}"));
        if (db.find("SELECT id FROM universities") == null) {
            uni("Universidad Nacional del Centro del Perú", "UNCP", "Huancayo",
                    "https://uncp.edu.pe/pregrado/facultades/",
                    "Ingeniería de Sistemas|Ingeniería Civil|Ingeniería Mecánica|Arquitectura|Medicina Humana|Enfermería|Educación|Administración|Contabilidad|Economía|Ciencias de la Comunicación|Trabajo Social|Agronomía|Ingeniería Ambiental",
                    "2026-09-20");
            uni("Universidad Nacional de Ingeniería", "UNI", "Lima", "https://portal.uni.edu.pe/",
                    "Ingeniería de Sistemas|Ingeniería Civil|Ingeniería Mecánica|Ingeniería Industrial|Arquitectura|Ingeniería Ambiental",
                    "2026-09-20");
            uni("Universidad Nacional Mayor de San Marcos", "UNMSM", "Lima",
                    "https://www.unmsm.edu.pe/formacion-academica/carreras-de-pregrado",
                    "Ingeniería de Sistemas|Ingeniería Industrial|Medicina Humana|Enfermería|Educación|Administración|Contabilidad|Economía|Trabajo Social|Derecho",
                    null);
            uni("Universidad Nacional de San Antonio Abad del Cusco", "UNSAAC", "Cusco", "https://www.unsaac.edu.pe/",
                    "Ingeniería Civil|Arquitectura|Medicina Humana|Enfermería|Educación|Administración|Contabilidad|Economía|Agronomía|Derecho",
                    null);
            uni("Universidad Nacional de San Agustín de Arequipa", "UNSA", "Arequipa", "https://www.unsa.edu.pe/",
                    "Ingeniería de Sistemas|Ingeniería Civil|Ingeniería Mecánica|Ingeniería Industrial|Arquitectura|Medicina Humana|Enfermería|Educación|Administración|Contabilidad|Economía|Trabajo Social|Derecho",
                    null);
        }
        if (db.find("SELECT id FROM users") == null) {
            check(password.length() >= 10, "ADMIN_PASSWORD requiere 10 caracteres como mínimo");
            check(demo || !password.equals("Director2026!"),
                    "Configura ADMIN_PASSWORD antes de iniciar sin datos demo");
            String director = user("Dirección académica", email, "DIRECTOR", password);
            if (!demo)
                return;
            String teacher = user("Lucía Mendoza", "docente@academia.local", "DOCENTE", "Docente2026!"),
                    teacher2 = user("Marco Salazar", "docente2@academia.local", "DOCENTE", "Docente2026!");
            List<String> students = new ArrayList<>();
            String[] names = { "Valeria Quispe", "Diego Huamán", "Camila Torres", "Luis Poma", "Andrea Rojas",
                    "Mateo Flores" };
            for (int i = 0; i < names.length; i++)
                students.add(user(names[i], i == 0 ? "alumno@academia.local" : "alumno" + (i + 1) + "@academia.local",
                        "ALUMNO", "Alumno2026!"));
            LocalDate start = LocalDate.now(ZoneId.of("America/Lima")).minusWeeks(7).with(java.time.DayOfWeek.MONDAY);
            String cycle = db.insert("cycles",
                    m("name", "Ciclo de preparación · 16 semanas", "start_date", start.toString(), "end_date",
                            start.plusWeeks(16).minusDays(1).toString(), "weights",
                            db.get("settings", "main").get("weights"))),
                    section = db.insert("sections", m("cycle_id", cycle, "name", "A · Mañana", "capacity", 30)),
                    section2 = db.insert("sections", m("cycle_id", cycle, "name", "B · Tarde", "capacity", 30));
            String[] courses = { "Matemática", "Comunicación", "Ciencias" };
            for (int c = 0; c < 3; c++) {
                String course = db.insert("courses",
                        m("name", courses[c], "description", "Preparación para el ingreso universitario"));
                String offering = db.insert("offerings", m("course_id", course, "section_id", section, "teacher_id",
                        c == 2 ? teacher2 : teacher, "syllabus",
                        "Semanas 1–4: fundamentos. Semanas 5–8: aplicación. Semanas 9–12: integración. Semanas 13–16: simulacros y repaso.",
                        "progress", 45));
                for (String student : students)
                    db.insert("enrollments",
                            m("student_id", student, "offering_id", offering, "joined_on", start.toString()));
                for (int w = 0; w < 16; w++) {
                    LocalDate date = start.plusWeeks(w).plusDays(c);
                    String session = db.insert("sessions",
                            m("offering_id", offering, "date", date.toString(), "starts", "08:00", "ends", "10:00",
                                    "room", "Aula 101", "topic", "Unidad " + (w + 1), "status",
                                    w < 7 ? "REALIZADA" : "PROGRAMADA"));
                    if (w < 7)
                        for (int st = 0; st < students.size(); st++)
                            db.insert("attendance", m("session_id", session, "student_id", students.get(st), "state",
                                    st == 3 && w < 5 ? "FALTA" : w == 2 && st == 1 ? "TARDE" : "PRESENTE", "note",
                                    "Registro de demostración", "recorded_by", c == 2 ? teacher2 : teacher));
                }
                for (int st = 0; st < students.size(); st++)
                    for (String cat : List.of("PRACTICA", "PARCIAL"))
                        db.insert("grades", m("offering_id", offering, "student_id", students.get(st), "title",
                                cat.equals("PRACTICA") ? "Práctica de fundamentos" : "Evaluación de unidad", "category",
                                cat, "grade", st == 3 ? 9 : 14 + (st + c) % 5, "recorded_by", teacher));
                String material = db.insert("materials",
                        m("offering_id", offering, "author_id", c == 2 ? teacher2 : teacher, "title",
                                c == 0 ? "Ecuaciones: guía paso a paso"
                                        : c == 1 ? "Lectura crítica y comprensión" : "Método científico en la práctica",
                                "topic", c == 0 ? "Ecuaciones" : c == 1 ? "Comprensión lectora" : "Método científico",
                                "difficulty", "Básico", "tags", "fundamentos,práctica"));
                db.insert("material_versions", m("material_id", material, "version", 1, "body", c == 0
                        ? "Una ecuación expresa igualdad entre dos cantidades. Para resolver 2x + 6 = 14, resta 6 en ambos lados: 2x = 8. Divide entre 2: x = 4. Verifica reemplazando el valor. Practica con 3x + 9 = 24 y explica cada transformación."
                        : c == 1 ? "La idea principal expresa el contenido central de un texto. Distingue hechos verificables de opiniones. Para comprender un párrafo, identifica su tema, la afirmación central y la evidencia. Una inferencia debe apoyarse en indicios del texto."
                                : "El método científico organiza observación, pregunta, hipótesis, experimentación y análisis. Una hipótesis debe poder ponerse a prueba. Mantén constantes las variables de control y cambia una variable independiente. Registra resultados antes de concluir.",
                        "reason", "Publicación inicial"));
                List<String> versions = new ArrayList<>();
                for (int q = 0; q < 8; q++) {
                    String question = db.insert("questions",
                            m("course_id", course, "author_id", c == 2 ? teacher2 : teacher, "topic",
                                    c == 0 ? "Ecuaciones" : c == 1 ? "Comprensión" : "Ciencia", "difficulty",
                                    q < 4 ? "Básico" : "Intermedio", "practice", q >= 3));
                    String prompt, answer, explanation;
                    List<String> options;
                    if (c == 0) {
                        int val = q + 2;
                        prompt = "Resuelve 2x + 4 = " + (2 * val + 4) + ". ¿Cuánto vale x?";
                        options = List.of("" + (val - 1), "" + val, "" + (val + 1), "" + (val + 2));
                        answer = "1";
                        explanation = "Resta 4 en ambos lados y divide entre 2. El valor de x es " + val + ".";
                    } else if (c == 1) {
                        prompt = q % 2 == 0 ? "¿Qué caracteriza a una idea principal?"
                                : "¿Qué debe respaldar una inferencia de lectura?";
                        options = q % 2 == 0
                                ? List.of("Un detalle aislado", "El contenido central del texto", "La última palabra",
                                        "Una opinión externa")
                                : List.of("Solo una intuición", "Indicios del texto", "El número de páginas",
                                        "La portada");
                        answer = "1";
                        explanation = "La comprensión se apoya en el contenido y la evidencia del texto.";
                    } else {
                        prompt = q % 2 == 0 ? "¿Qué debe permitir una hipótesis científica?"
                                : "¿Qué variable modifica quien realiza un experimento?";
                        options = q % 2 == 0
                                ? List.of("Evitar observaciones", "Ser puesta a prueba", "Afirmar certezas sin datos",
                                        "Eliminar toda medición")
                                : List.of("La variable dependiente", "La variable independiente", "Todas a la vez",
                                        "Ninguna");
                        answer = "1";
                        explanation = "La investigación necesita hipótesis contrastables y control de variables.";
                    }
                    versions.add(db.insert("question_versions",
                            m("question_id", question, "version", 1, "kind", "MULTIPLE", "prompt", prompt, "options",
                                    db.json(options), "answer", answer, "explanation", explanation)));
                    if (q == 4)
                        db.insert("resolutions", m("question_id", question, "author_id", teacher, "body", explanation
                                + " Primero identifica los datos y explica por qué se descartan las otras opciones.",
                                "status", "APROBADO"));
                }
                String exam = db.insert("exams",
                        m("offering_id", offering, "title", "Práctica inicial · " + courses[c], "category", "PRACTICA",
                                "opens_at", Instant.now().minusSeconds(86400).toString(), "closes_at",
                                Instant.now().plusSeconds(30 * 86400).toString(), "release_at",
                                Instant.now().plusSeconds(30 * 86400).toString(), "duration", 20, "max_attempts", 2));
                for (int q = 0; q < 3; q++)
                    db.insert("exam_questions",
                            m("exam_id", exam, "question_version_id", versions.get(q), "position", q, "points", 1));
                if (c == 0)
                    db.insert("offerings", m("course_id", course, "section_id", section2, "teacher_id", teacher2,
                            "syllabus", "Fundamentos y práctica", "progress", 0));
            }
            db.insert("notifications",
                    m("user_id", students.get(0), "title", "Tu camino empieza aquí", "body",
                            "Explora tus cursos y completa el test vocacional para conocer tus intereses.", "severity",
                            "INFO", "dedup_key", "bienvenida:" + students.get(0)));
            db.audit(director, "INICIAR_DEMOSTRACION", "main",
                    "Datos ficticios para pruebas; no son matrículas reales.");
        }
    }

    void uni(String name, String acronym, String city, String url, String careers, String verified) {
        db.insert("universities", m("name", name, "acronym", acronym, "city", city, "public_university", true,
                "source_url", url, "careers", careers, "verified_on", verified, "note",
                verified == null
                        ? "Catálogo orientativo pendiente de actualización. Confirma carrera, sede y convocatoria en la fuente oficial."
                        : "Oferta académica consultada; confirma sede y convocatoria de admisión vigente. Educación agrupa especialidades."));
    }
}
