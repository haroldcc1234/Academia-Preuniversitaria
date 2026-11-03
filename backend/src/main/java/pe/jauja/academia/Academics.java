package pe.jauja.academia;

import java.util.*;
import java.time.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.crypto.password.PasswordEncoder;
import static pe.jauja.academia.Store.*;

@RestController
@RequestMapping("/api")
public class Academics {
    final Store db;
    final Access ac;
    final PasswordEncoder pw;
    final Metrics metrics;

    public Academics(Store d, Access a, PasswordEncoder p, Metrics m) {
        db = d;
        ac = a;
        pw = p;
        metrics = m;
    }

    @GetMapping("/catalog")
    public Object catalog() {
        String scope = ac.scope();
        return m("cycles", ac.director() ? db.list("SELECT * FROM cycles ORDER BY start_date DESC")
                : db.list(
                        "SELECT DISTINCT cy.* FROM cycles cy JOIN sections s ON s.cycle_id=cy.id JOIN offerings o ON o.section_id=s.id WHERE "
                                + scope + " ORDER BY cy.start_date DESC"),
                "sections",
                db.list("SELECT DISTINCT s.*,cy.name cycle_name FROM sections s JOIN cycles cy ON cy.id=s.cycle_id "
                        + (ac.director() ? "" : "JOIN offerings o ON o.section_id=s.id WHERE " + scope)),
                "courses",
                ac.director() ? db.list("SELECT * FROM courses")
                        : db.list("SELECT DISTINCT c.* FROM courses c JOIN offerings o ON o.course_id=c.id WHERE "
                                + scope),
                "universities", db.list("SELECT * FROM universities ORDER BY public_university DESC,name"), "settings",
                db.get("settings", "main"));
    }

    @GetMapping("/offerings")
    public Object offerings() {
        return db.list(
                "SELECT o.*,c.name course_name,s.name section_name,s.cycle_id,cy.name cycle_name,cy.closed,u.name teacher_name FROM offerings o JOIN courses c ON c.id=o.course_id JOIN sections s ON s.id=o.section_id JOIN cycles cy ON cy.id=s.cycle_id JOIN users u ON u.id=o.teacher_id WHERE "
                        + ac.scope() + " ORDER BY c.name");
    }

    @GetMapping("/users")
    public Object users() {
        ac.roles("DIRECTOR");
        return db.list(
                "SELECT id,name,email,role,active,career,university,birth_date,guardian,consent_at FROM users ORDER BY role,name");
    }

    @PostMapping("/users")
    public Object user(@RequestBody Map<String, Object> x) {
        ac.roles("DIRECTOR");
        String role = req(x, "role", 16), password = req(x, "password", 100),
                email = req(x, "email", 190).toLowerCase();
        check(List.of("DIRECTOR", "DOCENTE", "ALUMNO").contains(role), "Rol inválido");
        check(password.length() >= 10, "Contraseña mínima de 10 caracteres");
        check(email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$"), "Correo inválido");
        String id = db.insert("users",
                m("name", req(x, "name", 150), "email", email, "role", role, "password", pw.encode(password), "career",
                        s(x, "career"), "university", s(x, "university"), "birth_date", s(x, "birth_date"), "guardian",
                        s(x, "guardian"), "consent_at", b(x, "consent") ? now() : null));
        db.audit(ac.uid(), "CREAR_USUARIO", id, m("role", role));
        return m("id", id);
    }

    @PutMapping("/users/{id}")
    @Transactional
    public Object editUser(@PathVariable String id, @RequestBody Map<String, Object> x) {
        ac.roles("DIRECTOR");
        check(!id.equals(ac.uid()) || b(x, "active"), "No suspendas tu propia cuenta");
        db.patch("users", id, m("name", req(x, "name", 150), "active", b(x, "active"), "career", s(x, "career"),
                "university", s(x, "university"), "guardian", s(x, "guardian")));
        if (!s(x, "password").isBlank()) {
            check(s(x, "password").length() >= 10, "Contraseña mínima de 10 caracteres");
            db.patch("users", id, m("password", pw.encode(s(x, "password"))));
            db.update("DELETE FROM auth_tokens WHERE user_id=?", id);
        }
        if (!b(x, "active"))
            db.update("DELETE FROM auth_tokens WHERE user_id=?", id);
        db.audit(ac.uid(), "EDITAR_USUARIO", id, m("active", b(x, "active")));
        return m("ok", true);
    }

    @DeleteMapping("/users/{id}")
    public Object removeUser(@PathVariable String id) {
        ac.roles("DIRECTOR");
        check(!id.equals(ac.uid()), "No puedes retirar tu cuenta");
        db.patch("users", id, m("active", false));
        db.update("DELETE FROM auth_tokens WHERE user_id=?", id);
        db.audit(ac.uid(), "RETIRAR_USUARIO", id, "Eliminación lógica; historial preservado");
        return m("ok", true);
    }

    @PostMapping("/cycles")
    public Object cycle(@RequestBody Map<String, Object> x) {
        ac.roles("DIRECTOR");
        var start = LocalDate.parse(req(x, "start_date", 10));
        String id = db.insert("cycles", m("name", req(x, "name", 100), "start_date", start.toString(), "end_date",
                start.plusWeeks(16).minusDays(1).toString(), "weights", db.get("settings", "main").get("weights")));
        db.audit(ac.uid(), "CREAR_CICLO", id, x);
        return m("id", id);
    }

    @PostMapping("/cycles/{id}/close")
    public Object close(@PathVariable String id) {
        ac.roles("DIRECTOR");
        check(db.find(
                "SELECT a.id FROM attempts a JOIN exams e ON e.id=a.exam_id JOIN offerings o ON o.id=e.offering_id JOIN sections s ON s.id=o.section_id WHERE s.cycle_id=? AND a.status<>'CALIFICADO'",
                id) == null, "Existen evaluaciones pendientes");
        check(db.find(
                "SELECT cs.id FROM sessions cs JOIN offerings o ON o.id=cs.offering_id JOIN sections s ON s.id=o.section_id WHERE s.cycle_id=? AND cs.status='PROGRAMADA'",
                id) == null, "Cierra o cancela las sesiones pendientes");
        check(metrics.grades("1=1", null).stream().filter(r -> s(r, "cycle_id").equals(id))
                .allMatch(r -> b(r, "complete")), "Faltan categorías de calificación");
        db.patch("cycles", id, m("closed", true));
        db.audit(ac.uid(), "CERRAR_CICLO", id, m());
        return m("ok", true);
    }

    @PostMapping("/cycles/{id}/reopen")
    public Object reopen(@PathVariable String id, @RequestBody Map<String, Object> x) {
        ac.roles("DIRECTOR");
        String reason = req(x, "reason", 400);
        db.patch("cycles", id, m("closed", false));
        db.audit(ac.uid(), "REABRIR_CICLO", id, reason);
        return m("ok", true);
    }

    @PostMapping("/sections")
    public Object section(@RequestBody Map<String, Object> x) {
        ac.roles("DIRECTOR");
        int cap = n(x, "capacity", 30);
        check(cap > 0 && cap <= 200, "Capacidad de 1 a 200");
        String id = db.insert("sections",
                m("cycle_id", req(x, "cycle_id", 36), "name", req(x, "name", 100), "capacity", cap));
        db.audit(ac.uid(), "CREAR_SECCION", id, x);
        return m("id", id);
    }

    @PostMapping("/courses")
    public Object course(@RequestBody Map<String, Object> x) {
        ac.roles("DIRECTOR");
        String id = db.insert("courses", m("name", req(x, "name", 100), "description", s(x, "description")));
        db.audit(ac.uid(), "CREAR_CURSO", id, x);
        return m("id", id);
    }

    @PostMapping("/offerings")
    public Object offering(@RequestBody Map<String, Object> x) {
        ac.roles("DIRECTOR");
        check(db.find("SELECT id FROM users WHERE id=? AND role='DOCENTE' AND active=TRUE", s(x, "teacher_id")) != null,
                "Selecciona un docente activo");
        String id = db.insert("offerings", m("course_id", req(x, "course_id", 36), "section_id",
                req(x, "section_id", 36), "teacher_id", s(x, "teacher_id"), "syllabus", s(x, "syllabus")));
        db.audit(ac.uid(), "ASIGNAR_CURSO", id, x);
        return m("id", id);
    }

    @PutMapping("/offerings/{id}")
    @Transactional
    public Object editOffer(@PathVariable String id, @RequestBody Map<String, Object> x) {
        ac.offering(id, true);
        int progress = n(x, "progress", 0);
        check(progress >= 0 && progress <= 100, "Avance entre 0 y 100");
        db.patch("offerings", id, m("syllabus", s(x, "syllabus"), "progress", progress));
        if (ac.director() && !s(x, "teacher_id").isBlank()) {
            check(db.find("SELECT id FROM users WHERE id=? AND role='DOCENTE' AND active=TRUE",
                    s(x, "teacher_id")) != null, "Docente inválido");
            db.patch("offerings", id, m("teacher_id", s(x, "teacher_id")));
        }
        db.audit(ac.uid(), "ACTUALIZAR_CURSO", id, x);
        return m("ok", true);
    }

    @PostMapping("/enrollments")
    @Transactional
    public Object enroll(@RequestBody Map<String, Object> x) {
        ac.roles("DIRECTOR");
        String offering = req(x, "offering_id", 36), student = req(x, "student_id", 36);
        var o = ac.offering(offering, true);
        check(db.find("SELECT id FROM users WHERE id=? AND role='ALUMNO' AND active=TRUE", student) != null,
                "Alumno inválido");
        var section = db.one("SELECT * FROM sections WHERE id=? FOR UPDATE", o.get("section_id"));
        var count = db.one(
                "SELECT COUNT(DISTINCT e.student_id) total FROM enrollments e JOIN offerings o ON o.id=e.offering_id WHERE o.section_id=? AND e.withdrawn_on IS NULL",
                o.get("section_id"));
        boolean existing = db.find(
                "SELECT e.id FROM enrollments e JOIN offerings o ON o.id=e.offering_id WHERE o.section_id=? AND e.student_id=?",
                o.get("section_id"), student) != null;
        check(existing || num(count, "total") < num(section, "capacity"), "Sección completa");
        var cy = db.get("cycles", s(o, "cycle_id"));
        String date = s(x, "joined_on").isBlank() ? s(cy, "start_date") : LocalDate.parse(s(x, "joined_on")).toString();
        check(date.compareTo(s(cy, "start_date")) >= 0 && date.compareTo(s(cy, "end_date")) <= 0,
                "Fecha fuera del ciclo");
        String id = db.insert("enrollments", m("offering_id", offering, "student_id", student, "joined_on", date));
        db.audit(ac.uid(), "MATRICULAR", id, x);
        return m("id", id);
    }

    @DeleteMapping("/enrollments/{id}")
    public Object withdraw(@PathVariable String id, @RequestParam String date) {
        ac.roles("DIRECTOR");
        var e = db.get("enrollments", id);
        ac.offering(s(e, "offering_id"), true);
        check(LocalDate.parse(date).toString().compareTo(s(e, "joined_on")) >= 0, "Fecha inválida");
        db.patch("enrollments", id, m("withdrawn_on", date));
        db.audit(ac.uid(), "RETIRAR_MATRICULA", id, date);
        return m("ok", true);
    }

    @GetMapping("/offerings/{id}/students")
    public Object students(@PathVariable String id) {
        ac.roles("DIRECTOR", "DOCENTE");
        ac.offering(id, false);
        return db.list(
                "SELECT u.id,u.name,e.id enrollment_id,e.joined_on,e.withdrawn_on FROM users u JOIN enrollments e ON e.student_id=u.id WHERE e.offering_id=? ORDER BY u.name",
                id);
    }

    @GetMapping("/sessions")
    public Object sessions() {
        return db.list(
                "SELECT cs.*,c.name course_name,s.name section_name,s.cycle_id FROM sessions cs JOIN offerings o ON o.id=cs.offering_id JOIN courses c ON c.id=o.course_id JOIN sections s ON s.id=o.section_id WHERE "
                        + ac.scope() + " ORDER BY cs.date DESC,cs.starts");
    }

    @PostMapping("/sessions")
    @Transactional
    public Object schedule(@RequestBody Map<String, Object> x) {
        ac.roles("DIRECTOR", "DOCENTE");
        String offering = req(x, "offering_id", 36);
        var o = ac.offering(offering, true);
        var cy = db.get("cycles", s(o, "cycle_id"));
        LocalDate date = LocalDate.parse(req(x, "date", 10));
        LocalTime start = LocalTime.parse(req(x, "starts", 5)), end = LocalTime.parse(req(x, "ends", 5));
        check(end.isAfter(start), "La hora final debe ser posterior");
        int repeat = n(x, "repeat", 1);
        check(repeat > 0 && repeat <= 16, "De 1 a 16 repeticiones semanales");
        String room = req(x, "room", 80);
        List<String> ids = new ArrayList<>();
        db.one("SELECT * FROM cycles WHERE id=? FOR UPDATE", cy.get("id"));
        for (int i = 0; i < repeat; i++) {
            String day = date.plusWeeks(i).toString();
            check(day.compareTo(s(cy, "start_date")) >= 0 && day.compareTo(s(cy, "end_date")) <= 0,
                    "Sesión fuera de las 16 semanas");
            check(db.find(
                    "SELECT cs.id FROM sessions cs JOIN offerings o ON o.id=cs.offering_id WHERE cs.date=? AND cs.status<>'CANCELADA' AND cs.starts<? AND cs.ends>? AND (o.teacher_id=? OR o.section_id=? OR cs.room=?)",
                    day, end.toString(), start.toString(), o.get("teacher_id"), o.get("section_id"), room) == null,
                    "Cruce de docente, sección o aula el " + day);
            ids.add(db.insert("sessions", m("offering_id", offering, "date", day, "starts", start.toString(), "ends",
                    end.toString(), "room", room, "topic", req(x, "topic", 200))));
        }
        db.audit(ac.uid(), "PROGRAMAR_SESIONES", offering, x);
        return m("ids", ids);
    }

    @PutMapping("/sessions/{id}/cancel")
    @Transactional
    public Object cancel(@PathVariable String id, @RequestBody Map<String, Object> x) {
        var cs = db.get("sessions", id);
        ac.offering(s(cs, "offering_id"), true);
        check(!s(cs, "status").equals("REALIZADA"), "La sesión tiene asistencia cerrada");
        db.patch("sessions", id, m("status", "CANCELADA"));
        db.audit(ac.uid(), "CANCELAR_SESION", id, req(x, "reason", 400));
        return m("ok", true);
    }

    @GetMapping("/sessions/{id}/attendance")
    public Object attendance(@PathVariable String id) {
        var cs = db.get("sessions", id);
        ac.offering(s(cs, "offering_id"), false);
        var rows = db.list(
                "SELECT u.id,u.name,a.state,a.note FROM users u JOIN enrollments e ON e.student_id=u.id LEFT JOIN attendance a ON a.student_id=u.id AND a.session_id=? WHERE e.offering_id=? AND e.joined_on<=? AND (e.withdrawn_on IS NULL OR e.withdrawn_on>=?) ORDER BY u.name",
                id, cs.get("offering_id"), cs.get("date"), cs.get("date"));
        if (ac.student())
            rows.removeIf(r -> !s(r, "id").equals(ac.uid()));
        return rows;
    }

    @PostMapping("/sessions/{id}/attendance")
    @Transactional
    public Object saveAttendance(@PathVariable String id, @RequestBody Map<String, Object> x) {
        var cs = db.one("SELECT * FROM sessions WHERE id=? FOR UPDATE", id);
        ac.offering(s(cs, "offering_id"), true);
        check(!s(cs, "status").equals("CANCELADA"), "Sesión cancelada");
        check(s(cs, "date").compareTo(LocalDate.now(ZoneId.of("America/Lima")).toString()) <= 0,
                "No registres asistencia futura");
        if (s(cs, "status").equals("REALIZADA"))
            req(x, "reason", 400);
        var rows = (List<Map<String, Object>>) x.get("rows");
        check(rows != null, "Faltan registros");
        Set<String> expected = new HashSet<>();
        for (var e : db.list(
                "SELECT student_id FROM enrollments WHERE offering_id=? AND joined_on<=? AND (withdrawn_on IS NULL OR withdrawn_on>=?)",
                cs.get("offering_id"), cs.get("date"), cs.get("date")))
            expected.add(s(e, "student_id"));
        Set<String> actual = new HashSet<>();
        for (var r : rows) {
            String student = s(r, "id"), state = s(r, "state");
            check(expected.contains(student) && actual.add(student), "Alumno inválido o repetido");
            check(List.of("PRESENTE", "TARDE", "FALTA", "JUSTIFICADO").contains(state),
                    "Registra un estado por alumno");
            var old = db.find("SELECT id FROM attendance WHERE session_id=? AND student_id=?", id, student);
            var data = m("session_id", id, "student_id", student, "state", state, "note", s(r, "note"), "recorded_by",
                    ac.uid());
            if (old == null)
                db.insert("attendance", data);
            else
                db.patch("attendance", s(old, "id"), data);
        }
        check(actual.equals(expected), "Completa la asistencia antes de cerrar");
        db.patch("sessions", id, m("status", "REALIZADA"));
        db.audit(ac.uid(), "REGISTRAR_ASISTENCIA", id, x);
        return m("ok", true);
    }

    @GetMapping("/attendance")
    public Object attendanceSummary() {
        return metrics.attendance(ac.scope(), ac.student() ? ac.uid() : null);
    }

    @GetMapping("/grades")
    public Object grades() {
        return metrics.grades(ac.scope(), ac.student() ? ac.uid() : null);
    }

    @PostMapping("/grades")
    public Object grade(@RequestBody Map<String, Object> x) {
        String offering = req(x, "offering_id", 36), student = req(x, "student_id", 36);
        ac.offering(offering, true);
        ac.enrolled(student, offering);
        double g = Double.parseDouble(req(x, "grade", 20));
        check(Double.isFinite(g) && g >= 0 && g <= 20, "Nota entre 0 y 20");
        String cat = req(x, "category", 20);
        check(List.of("PRACTICA", "PARCIAL", "SIMULACRO").contains(cat), "Categoría inválida");
        double weight = x.containsKey("relative_weight") ? num(x, "relative_weight") : 1;
        check(Double.isFinite(weight) && weight > 0 && weight <= 100, "Peso relativo entre 0.01 y 100");
        String id = db.insert("grades", m("relative_weight", weight, "offering_id", offering, "student_id", student,
                "title", req(x, "title", 150), "category", cat, "grade", g, "recorded_by", ac.uid()));
        db.audit(ac.uid(), "REGISTRAR_NOTA", id, x);
        return m("id", id);
    }

    @PutMapping("/grades/{id}")
    public Object correct(@PathVariable String id, @RequestBody Map<String, Object> x) {
        var old = db.get("grades", id);
        ac.offering(s(old, "offering_id"), true);
        double g = Double.parseDouble(req(x, "grade", 20));
        check(Double.isFinite(g) && g >= 0 && g <= 20, "Nota inválida");
        String reason = req(x, "reason", 400);
        db.patch("grades", id, m("grade", g));
        db.audit(ac.uid(), "RECTIFICAR_NOTA", id, m("antes", old.get("grade"), "despues", g, "motivo", reason));
        return m("ok", true);
    }

    @GetMapping("/dashboard")
    public Object dashboard() {
        return m("attendance", metrics.attendance(ac.scope(), ac.student() ? ac.uid() : null), "grades",
                metrics.grades(ac.scope(), ac.student() ? ac.uid() : null), "notifications", notifications(),
                "students",
                ac.director()
                        ? db.one("SELECT COUNT(*) total FROM users WHERE role='ALUMNO' AND active=TRUE").get("total")
                        : 0,
                "teachers",
                ac.director()
                        ? db.one("SELECT COUNT(*) total FROM users WHERE role='DOCENTE' AND active=TRUE").get("total")
                        : 0);
    }

    @GetMapping("/notifications")
    public Object notifications() {
        return db.list("SELECT * FROM notifications WHERE user_id=? ORDER BY created_at DESC LIMIT 100", ac.uid());
    }

    @PutMapping("/notifications/{id}")
    public Object read(@PathVariable String id) {
        db.update("UPDATE notifications SET read_at=? WHERE id=? AND user_id=?", now(), id, ac.uid());
        return m("ok", true);
    }

    @PutMapping("/settings")
    public Object settings(@RequestBody Map<String, Object> x) {
        ac.roles("DIRECTOR");
        int warning = n(x, "warning_pct", 30);
        double low = num(x, "low_grade"), high = num(x, "high_grade");
        check(warning > 0 && warning < 50, "Umbral preventivo de 1 a 49%");
        check(low >= 0 && high <= 20 && low < high, "Umbrales académicos inválidos");
        var w = (Map<String, Object>) x.get("weights");
        check(w != null && w.keySet().equals(Set.of("PRACTICA", "PARCIAL", "SIMULACRO")), "Ponderaciones inválidas");
        double sum = 0;
        for (var v : w.values()) {
            double n = Double.parseDouble(v.toString());
            check(Double.isFinite(n) && n >= 0 && n <= 100, "Peso inválido");
            sum += n;
        }
        check(Math.abs(sum - 100) < 0.001, "Los pesos deben sumar 100%");
        db.patch("settings", "main", m("name", req(x, "name", 150), "warning_pct", warning, "low_grade", low,
                "high_grade", high, "weights", db.json(w)));
        db.audit(ac.uid(), "CONFIGURAR", "main", x);
        return m("ok", true);
    }

    @GetMapping("/audit")
    public Object audit() {
        ac.roles("DIRECTOR");
        return db.list(
                "SELECT a.*,u.name actor FROM audit a LEFT JOIN users u ON u.id=a.user_id ORDER BY created_at DESC LIMIT 200");
    }

    @PutMapping("/universities/{id}")
    public Object university(@PathVariable String id, @RequestBody Map<String, Object> x) {
        ac.roles("DIRECTOR");
        check(s(x, "source_url").startsWith("https://"), "Enlace HTTPS requerido");
        db.patch("universities", id, m("careers", req(x, "careers", 3000), "source_url", s(x, "source_url"),
                "verified_on", LocalDate.parse(req(x, "verified_on", 10)).toString(), "note", s(x, "note")));
        db.audit(ac.uid(), "ACTUALIZAR_UNIVERSIDAD", id, x);
        return m("ok", true);
    }

    @PutMapping("/courses/{id}")
    public Object editCourse(@PathVariable String id, @RequestBody Map<String, Object> x) {
        ac.roles("DIRECTOR");
        db.get("courses", id);
        db.patch("courses", id, m("name", req(x, "name", 100), "description", s(x, "description")));
        db.audit(ac.uid(), "EDITAR_CURSO", id, x);
        return m("ok", true);
    }

    @PutMapping("/sections/{id}")
    @Transactional
    public Object editSection(@PathVariable String id, @RequestBody Map<String, Object> x) {
        ac.roles("DIRECTOR");
        var old = db.one("SELECT * FROM sections WHERE id=? FOR UPDATE", id);
        check(s(old, "cycle_id").equals(s(x, "cycle_id")), "Una sección existente conserva su ciclo");
        int capacity = n(x, "capacity", 30);
        check(capacity > 0 && capacity <= 200, "Capacidad de 1 a 200");
        var count = db.one(
                "SELECT COUNT(DISTINCT e.student_id) total FROM enrollments e JOIN offerings o ON o.id=e.offering_id WHERE o.section_id=? AND e.withdrawn_on IS NULL",
                id);
        check(capacity >= num(count, "total"), "La capacidad no puede ser menor que la matrícula activa");
        db.patch("sections", id, m("name", req(x, "name", 100), "capacity", capacity));
        db.audit(ac.uid(), "EDITAR_SECCION", id, x);
        return m("ok", true);
    }

    @PutMapping("/cycles/{id}")
    @Transactional
    public Object editCycle(@PathVariable String id, @RequestBody Map<String, Object> x) {
        ac.roles("DIRECTOR");
        var old = db.one("SELECT * FROM cycles WHERE id=? FOR UPDATE", id);
        check(!b(old, "closed"), "Reabre el ciclo antes de modificarlo");
        String start = LocalDate.parse(req(x, "start_date", 10)).toString();
        check(start.equals(s(old, "start_date")) || db.find("SELECT id FROM sections WHERE cycle_id=?", id) == null,
                "El ciclo tiene secciones: conserva la fecha de inicio");
        db.patch("cycles", id, m("name", req(x, "name", 100), "start_date", start, "end_date",
                LocalDate.parse(start).plusWeeks(16).minusDays(1).toString()));
        db.audit(ac.uid(), "EDITAR_CICLO", id, x);
        return m("ok", true);
    }
}
