package pe.jauja.academia;

import java.util.*;
import java.time.*;
import org.springframework.web.bind.annotation.*;

import org.springframework.transaction.annotation.Transactional;

import static pe.jauja.academia.Store.*;

@RestController
@RequestMapping("/api")
public class Exams {
    final Store db;
    final Access ac;
    final ExamEngine engine;

    public Exams(Store d, Access a, ExamEngine e) {
        db = d;
        ac = a;
        engine = e;
    }

    @GetMapping("/exams")
    public Object exams() {
        var rows = db.list(
                "SELECT e.*,c.name course_name,s.name section_name,(SELECT COUNT(*) FROM exam_questions eq WHERE eq.exam_id=e.id) question_count FROM exams e JOIN offerings o ON o.id=e.offering_id JOIN courses c ON c.id=o.course_id JOIN sections s ON s.id=o.section_id WHERE e.archived=FALSE AND "
                        + ac.scope() + " ORDER BY e.opens_at DESC");
        if (ac.student()) {
            rows.removeIf(e -> !eligible(e));
            for (var e : rows) {
                var attempts = db.list(
                        "SELECT id,status,number,grade,deadline,submitted_at FROM attempts WHERE exam_id=? AND student_id=? ORDER BY number DESC",
                        e.get("id"), ac.uid());
                if (Instant.parse(s(e, "release_at")).isAfter(Instant.now()))
                    for (var a : attempts)
                        a.put("grade", null);
                e.put("attempts", attempts);
            }
        }
        return rows;
    }

    boolean eligible(Map<String, Object> e) {
        return (s(e, "career").isBlank() || s(e, "career").equals("General")
                || s(e, "career").equalsIgnoreCase(s(ac.user(), "career")))
                && (s(e, "university").isBlank() || s(e, "university").equalsIgnoreCase(s(ac.user(), "university")));
    }

    @PostMapping("/exams")
    @Transactional
    public Object create(@RequestBody Map<String, Object> x) {
        ac.roles("DIRECTOR", "DOCENTE");
        String offering = req(x, "offering_id", 36);
        var o = ac.offering(offering, true);
        String category = req(x, "category", 20);
        check(List.of("PRACTICA", "PARCIAL", "SIMULACRO").contains(category), "Categoría inválida");
        Instant opens = Instant.parse(req(x, "opens_at", 30)), closes = Instant.parse(req(x, "closes_at", 30)),
                release = Instant.parse(req(x, "release_at", 30));
        check(closes.isAfter(opens) && !release.isBefore(closes), "Revisa disponibilidad y fecha de resultados");
        int duration = n(x, "duration", 30), attempts = n(x, "max_attempts", 1);
        check(duration > 0 && duration <= 240 && attempts > 0 && attempts <= 10, "Duración o intentos inválidos");
        var questions = (List<Map<String, Object>>) x.get("questions");
        check(questions != null && !questions.isEmpty() && questions.size() <= 100, "Selecciona de 1 a 100 preguntas");
        double weight = x.containsKey("relative_weight") ? num(x, "relative_weight") : 1;
        check(Double.isFinite(weight) && weight > 0 && weight <= 100, "Peso relativo entre 0.01 y 100");
        String id = db.insert("exams",
                m("relative_weight", weight, "offering_id", offering, "title", req(x, "title", 200), "category",
                        category, "opens_at", opens.toString(), "closes_at", closes.toString(), "release_at",
                        release.toString(), "duration", duration, "max_attempts", attempts, "university",
                        s(x, "university"), "career", s(x, "career")));
        int pos = 0;
        Set<String> selected = new HashSet<>();
        for (var q : questions) {
            String version = req(q, "version_id", 36);
            var question = db.one(
                    "SELECT v.*,q.course_id FROM question_versions v JOIN questions q ON q.id=v.question_id WHERE v.id=?",
                    version);
            check(s(question, "course_id").equals(s(o, "course_id")), "Pregunta de otro curso");
            check(selected.add(version), "Pregunta repetida");
            double points = num(q, "points");
            check(Double.isFinite(points) && points > 0 && points <= 100, "Puntaje inválido");
            db.insert("exam_questions",
                    m("exam_id", id, "question_version_id", version, "position", pos++, "points", points));
        }
        db.audit(ac.uid(), "PUBLICAR_EXAMEN", id, m("preguntas", pos));
        return m("id", id);
    }

    @DeleteMapping("/exams/{id}")
    public Object archive(@PathVariable String id) {
        var e = db.get("exams", id);
        ac.offering(s(e, "offering_id"), true);
        check(db.find("SELECT id FROM attempts WHERE exam_id=? AND status='EN_CURSO'", id) == null,
                "Hay alumnos rindiendo la evaluación");
        db.patch("exams", id, m("archived", true));
        db.audit(ac.uid(), "ARCHIVAR_EXAMEN", id, m());
        return m("ok", true);
    }

    @PostMapping("/exams/{id}/start")
    @Transactional
    public Object start(@PathVariable String id) {
        ac.roles("ALUMNO");
        var e = db.one("SELECT * FROM exams WHERE id=? FOR UPDATE", id);
        ac.offering(s(e, "offering_id"), false);
        ac.enrolled(ac.uid(), s(e, "offering_id"));
        check(eligible(e) && !b(e, "archived"), "Simulacro fuera de tus preferencias");
        var cycle = db.one(
                "SELECT c.closed FROM cycles c JOIN sections s ON s.cycle_id=c.id JOIN offerings o ON o.section_id=s.id WHERE o.id=?",
                e.get("offering_id"));
        check(!b(cycle, "closed"), "Ciclo cerrado");
        var active = db.find("SELECT * FROM attempts WHERE exam_id=? AND student_id=? AND status='EN_CURSO'", id,
                ac.uid());
        if (active != null) {
            if (Instant.parse(s(active, "deadline")).isAfter(Instant.now()))
                return m("id", active.get("id"));
            engine.finish(s(active, "id"));
        }
        Instant now = Instant.now();
        check(!now.isBefore(Instant.parse(s(e, "opens_at"))) && now.isBefore(Instant.parse(s(e, "closes_at"))),
                "Evaluación fuera de horario");
        int count = n(db.one("SELECT COUNT(*) n FROM attempts WHERE exam_id=? AND student_id=?", id, ac.uid()), "n", 0);
        check(count < n(e, "max_attempts", 1), "Agotaste los intentos permitidos");
        Instant deadline = now.plusSeconds(n(e, "duration", 30) * 60L), close = Instant.parse(s(e, "closes_at"));
        if (deadline.isAfter(close))
            deadline = close;
        String attempt = db.insert("attempts", m("exam_id", id, "student_id", ac.uid(), "number", count + 1, "deadline",
                deadline.toString(), "status", "EN_CURSO"));
        for (var q : db.list("SELECT id FROM exam_questions WHERE exam_id=?", id))
            db.insert("responses", m("attempt_id", attempt, "exam_question_id", q.get("id"), "answer", ""));
        return m("id", attempt);
    }

    Map<String, Object> authorized(String id, boolean edit) {
        var a = db.one(
                "SELECT a.*,e.offering_id,e.title,e.release_at FROM attempts a JOIN exams e ON e.id=a.exam_id WHERE a.id=?",
                id);
        if (ac.student()) {
            if (!s(a, "student_id").equals(ac.uid()))
                deny();
            ac.offering(s(a, "offering_id"), false);
        } else
            ac.offering(s(a, "offering_id"), edit);
        return a;
    }

    @GetMapping("/attempts/{id}")
    public Object attempt(@PathVariable String id) {
        var a = authorized(id, false);
        if (s(a, "status").equals("EN_CURSO") && !Instant.parse(s(a, "deadline")).isAfter(Instant.now())) {
            engine.finish(id);
            a = authorized(id, false);
        }
        boolean released = !ac.student()
                || (!s(a, "status").equals("EN_CURSO") && !Instant.parse(s(a, "release_at")).isAfter(Instant.now()));
        var qs = db.list(
                "SELECT r.id response_id,r.answer response,r.points earned,r.feedback,eq.id exam_question_id,eq.points,eq.position,v.kind,v.prompt,v.options,v.answer correct_answer,v.explanation FROM responses r JOIN exam_questions eq ON eq.id=r.exam_question_id JOIN question_versions v ON v.id=eq.question_version_id WHERE r.attempt_id=? ORDER BY eq.position",
                id);
        for (var q : qs) {
            q.put("options", db.parse(s(q, "options"), List.class));
            if (!released) {
                q.remove("correct_answer");
                q.remove("explanation");
                q.remove("earned");
                q.remove("feedback");
            }
        }
        if (!released) {
            a.put("grade", null);
            a.put("feedback", null);
        }
        return m("attempt", a, "questions", qs, "server_time", now(), "released", released);
    }

    @PutMapping("/attempts/{id}/answers")
    @Transactional
    public Object answers(@PathVariable String id, @RequestBody Map<String, Object> x) {
        ac.roles("ALUMNO");
        authorized(id, false);
        var a = db.one("SELECT * FROM attempts WHERE id=? FOR UPDATE", id);
        check(s(a, "status").equals("EN_CURSO"), "Intento cerrado");
        check(Instant.parse(s(a, "deadline")).isAfter(Instant.now()), "El tiempo terminó");
        var rows = (List<Map<String, Object>>) x.get("answers");
        check(rows != null && rows.size() <= 100, "Respuestas inválidas");
        for (var r : rows) {
            String response = req(r, "response_id", 36);
            check(db.find("SELECT id FROM responses WHERE id=? AND attempt_id=?", response, id) != null,
                    "Respuesta ajena al intento");
            check(s(r, "answer").length() <= 20000, "Respuesta muy extensa");
            db.patch("responses", response, m("answer", s(r, "answer")));
        }
        return m("ok", true, "saved_at", now());
    }

    @PostMapping("/attempts/{id}/submit")
    public Object submit(@PathVariable String id) {
        ac.roles("ALUMNO");
        authorized(id, false);
        engine.finish(id);
        return attempt(id);
    }

    @GetMapping("/grading")
    public Object grading() {
        ac.roles("DIRECTOR", "DOCENTE");
        return db.list(
                "SELECT a.*,e.title,c.name course_name,u.name student_name FROM attempts a JOIN exams e ON e.id=a.exam_id JOIN offerings o ON o.id=e.offering_id JOIN courses c ON c.id=o.course_id JOIN users u ON u.id=a.student_id WHERE "
                        + ac.scope() + " AND a.status<>'EN_CURSO' ORDER BY a.created_at DESC");
    }

    @PostMapping("/attempts/{id}/grade")
    @Transactional
    public Object grade(@PathVariable String id, @RequestBody Map<String, Object> x) {
        ac.roles("DIRECTOR", "DOCENTE");
        authorized(id, true);
        var a = db.one("SELECT * FROM attempts WHERE id=? FOR UPDATE", id);
        check(!s(a, "status").equals("EN_CURSO"), "El examen sigue abierto");
        if (s(a, "status").equals("CALIFICADO"))
            req(x, "reason", 400);
        var rows = (List<Map<String, Object>>) x.get("rows");
        check(rows != null, "Faltan puntajes");
        for (var r : rows) {
            var q = db.one(
                    "SELECT r.id,eq.points,v.kind FROM responses r JOIN exam_questions eq ON eq.id=r.exam_question_id JOIN question_versions v ON v.id=eq.question_version_id WHERE r.id=? AND r.attempt_id=?",
                    s(r, "response_id"), id);
            check(s(q, "kind").equals("DESARROLLO"), "Las preguntas objetivas se califican automáticamente");
            double pts = Double.parseDouble(req(r, "points", 20));
            check(Double.isFinite(pts) && pts >= 0 && pts <= num(q, "points"), "Puntaje fuera del rango");
            db.patch("responses", s(r, "response_id"), m("points", pts, "feedback", s(r, "feedback")));
        }
        db.patch("attempts", id, m("feedback", s(x, "feedback")));
        engine.recalculate(id);
        db.audit(ac.uid(), "CALIFICAR_EXAMEN", id, x);
        return attempt(id);
    }
}
