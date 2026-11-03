package pe.jauja.academia;

import java.util.*;
import java.time.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;
import static pe.jauja.academia.Store.*;

@RestController
@RequestMapping("/api/challenges")
public class Challenges {
    final Store db;
    final Access ac;
    final Knowledge knowledge;

    public Challenges(Store d, Access a, Knowledge k) {
        db = d;
        ac = a;
        knowledge = k;
    }

    @GetMapping
    public Object list() {
        ac.roles("ALUMNO");
        var rows = db.list(
                "SELECT ch.*,u.name creator_name,v.name opponent_name,c.name course_name FROM challenges ch JOIN users u ON u.id=ch.creator_id JOIN users v ON v.id=ch.opponent_id JOIN offerings o ON o.id=ch.offering_id JOIN courses c ON c.id=o.course_id WHERE ch.creator_id=? OR ch.opponent_id=? ORDER BY ch.created_at DESC",
                ac.uid(), ac.uid());
        for (var r : rows) {
            r.remove("questions");
            r.put("results", db.list("SELECT user_id,score,created_at FROM challenge_results WHERE challenge_id=?",
                    r.get("id")));
        }
        return rows;
    }

    @PostMapping
    public Object create(@RequestBody Map<String, Object> x) {
        ac.roles("ALUMNO");
        String offering = req(x, "offering_id", 36), opponent = req(x, "opponent_id", 36);
        var o = ac.offering(offering, false);
        ac.enrolled(ac.uid(), offering);
        ac.enrolled(opponent, offering);
        check(!opponent.equals(ac.uid()), "Elige otro compañero");
        check(db.find("SELECT id FROM users WHERE id=? AND role='ALUMNO' AND active=TRUE", opponent) != null,
                "Compañero no disponible");
        var qs = db.list(
                "SELECT v.*,q.id question_id FROM questions q JOIN question_versions v ON v.question_id=q.id WHERE q.course_id=? AND q.practice=TRUE AND q.archived=FALSE AND v.kind<>'DESARROLLO' AND v.version=(SELECT MAX(v2.version) FROM question_versions v2 WHERE v2.question_id=q.id)",
                o.get("course_id"));
        qs.removeIf(q -> knowledge.reserved(s(q, "question_id")));
        check(qs.size() >= 3, "Se necesitan al menos 3 preguntas objetivas de práctica no reservadas");
        Collections.shuffle(qs, new java.security.SecureRandom());
        qs = qs.subList(0, Math.min(5, qs.size()));
        String id = db.insert("challenges", m("offering_id", offering, "creator_id", ac.uid(), "opponent_id", opponent,
                "questions", db.json(qs), "expires_at", Instant.now().plusSeconds(86400).toString()));
        db.insert("notifications", m("user_id", opponent, "title", "Nuevo reto académico", "body",
                s(ac.user(), "name") + " te invitó a un reto.", "severity", "INFO", "dedup_key", "reto:" + id));
        return m("id", id);
    }

    Map<String, Object> get(String id) {
        var c = db.get("challenges", id);
        if (!s(c, "creator_id").equals(ac.uid()) && !s(c, "opponent_id").equals(ac.uid()))
            deny();
        return c;
    }

    @GetMapping("/{id}")
    public Object detail(@PathVariable String id) {
        var c = get(id);
        var result = db.find("SELECT * FROM challenge_results WHERE challenge_id=? AND user_id=?", id, ac.uid());
        List<Map<String, Object>> qs = db.parse(s(c, "questions"), List.class);
        boolean allDone = db.list("SELECT id FROM challenge_results WHERE challenge_id=?", id).size() == 2
                || Instant.parse(s(c, "expires_at")).isBefore(Instant.now());
        for (var q : qs) {
            q.put("options", db.parse(s(q, "options"), List.class));
            if (!allDone) {
                q.remove("answer");
                q.remove("explanation");
            }
        }
        c.put("questions", qs);
        return m("challenge", c, "result", result, "solutions_available", allDone);
    }

    @PostMapping("/{id}/submit")
    @Transactional
    public Object submit(@PathVariable String id, @RequestBody Map<String, Object> x) {
        ac.roles("ALUMNO");
        var c = get(id);
        db.one("SELECT id FROM challenges WHERE id=? FOR UPDATE", id);
        check(Instant.parse(s(c, "expires_at")).isAfter(Instant.now()), "Reto vencido");
        check(db.find("SELECT id FROM challenge_results WHERE challenge_id=? AND user_id=?", id, ac.uid()) == null,
                "Ya respondiste este reto");
        Map<String, Object> answers = (Map<String, Object>) x.get("answers");
        check(answers != null, "Responde el reto");
        List<Map<String, Object>> qs = db.parse(s(c, "questions"), List.class);
        int score = 0;
        for (var q : qs) {
            check(answers.containsKey(s(q, "id")), "Responde todas las preguntas");
            if (s(q, "answer").equals(s(answers, s(q, "id"))))
                score++;
        }
        db.insert("challenge_results",
                m("challenge_id", id, "user_id", ac.uid(), "answers", db.json(answers), "score", score));
        return m("score", score, "total", qs.size());
    }

    @GetMapping("/ranking/all")
    public Object ranking() {
        ac.roles("ALUMNO");

        return db.list(
                "SELECT u.id, u.name student_name, c.id course_id, " +
                        "c.name course_name, SUM(r.score) points, COUNT(*) challenges " +
                        "FROM challenge_results r " +
                        "JOIN users u ON u.id = r.user_id " +
                        "JOIN challenges ch ON ch.id = r.challenge_id " +
                        "JOIN offerings o ON o.id = ch.offering_id " +
                        "JOIN courses c ON c.id = o.course_id " +
                        "WHERE " + ac.scope() + " " +
                        "GROUP BY u.id, u.name, c.id, c.name " +
                        "ORDER BY c.name ASC, points DESC, u.name ASC");
    }
}
