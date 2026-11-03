package pe.jauja.academia;


import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.scheduling.annotation.Scheduled;
import static pe.jauja.academia.Store.*;

@Service
public class ExamEngine {
    final Store db;
    final org.springframework.transaction.support.TransactionTemplate tx;

    public ExamEngine(Store d, org.springframework.transaction.PlatformTransactionManager tm) {
        db = d;
        tx = new org.springframework.transaction.support.TransactionTemplate(tm);
    }

    @Transactional
    public void finish(String id) {
        var a = db.one("SELECT * FROM attempts WHERE id=? FOR UPDATE", id);
        if (!s(a, "status").equals("EN_CURSO"))
            return;
        var rows = db.list(
                "SELECT r.id,r.answer response,v.answer correct_answer,v.kind,eq.points FROM responses r JOIN exam_questions eq ON eq.id=r.exam_question_id JOIN question_versions v ON v.id=eq.question_version_id WHERE r.attempt_id=?",
                id);
        for (var r : rows) {
            if (!s(r, "kind").equals("DESARROLLO"))
                db.patch("responses", s(r, "id"),
                        m("points", s(r, "response").equals(s(r, "correct_answer")) ? num(r, "points") : 0));
        }
        db.patch("attempts", id, m("submitted_at", now(), "status", "POR_CALIFICAR"));
        recalculate(id);
    }

    public void recalculate(String id) {
        var rows = db.list(
                "SELECT r.points earned,eq.points max_points FROM responses r JOIN exam_questions eq ON eq.id=r.exam_question_id WHERE r.attempt_id=?",
                id);
        boolean done = rows.stream().allMatch(r -> r.get("earned") != null);
        double max = rows.stream().mapToDouble(r -> num(r, "max_points")).sum(),
                got = rows.stream().mapToDouble(r -> num(r, "earned")).sum();
        db.patch("attempts", id, m("grade", done && max > 0 ? Math.round(20 * got / max * 100.0) / 100.0 : null,
                "status", done ? "CALIFICADO" : "POR_CALIFICAR"));
    }

    @Scheduled(fixedDelay = 30000)
    public void expire() {
        for (var a : db.list("SELECT id FROM attempts WHERE status='EN_CURSO' AND deadline<=?", now())) {
            try {
                tx.executeWithoutResult(status -> finish(s(a, "id")));
            } catch (Exception e) {
                org.slf4j.LoggerFactory.getLogger(ExamEngine.class).warn("No se cerró un intento vencido", e);
            }
        }
    }
}
