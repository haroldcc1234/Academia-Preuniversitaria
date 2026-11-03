package pe.jauja.academia;

import java.util.*;
import org.springframework.stereotype.Service;
import static pe.jauja.academia.Store.*;

@Service
public class Metrics {
    final Store db;

    public Metrics(Store d) {
        db = d;
    }

    List<Map<String, Object>> base(String scope, String student) {
        return db.list(
                "SELECT e.*,u.name student_name,c.name course_name,s.name section_name,s.cycle_id,cy.name cycle_name,cy.closed,cy.weights FROM enrollments e JOIN users u ON u.id=e.student_id JOIN offerings o ON o.id=e.offering_id JOIN courses c ON c.id=o.course_id JOIN sections s ON s.id=o.section_id JOIN cycles cy ON cy.id=s.cycle_id WHERE "
                        + scope + (student == null ? "" : " AND e.student_id=?"),
                student == null ? new Object[] {} : new Object[] { student });
    }

    public List<Map<String, Object>> attendance(String scope, String student) {
        var rows = base(scope, student);
        for (var r : rows) {
            var sessions = db.list(
                    "SELECT cs.*,a.state FROM sessions cs LEFT JOIN attendance a ON a.session_id=cs.id AND a.student_id=? WHERE cs.offering_id=? AND cs.status<>'CANCELADA' AND cs.date>=? AND (? IS NULL OR cs.date<=?) ORDER BY cs.date",
                    r.get("student_id"), r.get("offering_id"), r.get("joined_on"), r.get("withdrawn_on"),
                    r.get("withdrawn_on"));
            long faults = sessions.stream().filter(a -> s(a, "state").equals("FALTA")).count(),
                    closed = sessions.stream().filter(a -> s(a, "status").equals("REALIZADA")).count(),
                    present = sessions.stream().filter(a -> List.of("PRESENTE", "TARDE").contains(s(a, "state")))
                            .count();
            double pct = sessions.isEmpty() ? 0 : 100.0 * faults / sessions.size();
            r.putAll(m("planned", sessions.size(), "completed", closed, "faults", faults, "present", present,
                    "cycle_pct", pct, "to_date_pct", closed == 0 ? 0 : 100.0 * faults / closed, "attendance_pct",
                    closed == 0 ? null : 100.0 * present / closed, "severe", !sessions.isEmpty() && pct >= 50,
                    "history", sessions));
        }
        return rows;
    }

    public List<Map<String, Object>> grades(String scope, String student) {
        var rows = base(scope, student);
        for (var r : rows) {
            var entries = db.list(
                    "SELECT id,title,category,grade,relative_weight,created_at,'REGISTRO' source FROM grades WHERE offering_id=? AND student_id=?",
                    r.get("offering_id"), r.get("student_id"));
            entries.addAll(db.list(
                    "SELECT e.id,e.title,e.category,e.relative_weight,MAX(a.grade) grade,MAX(a.submitted_at) created_at,'EXAMEN' source FROM exams e JOIN attempts a ON a.exam_id=e.id WHERE e.offering_id=? AND a.student_id=? AND a.status='CALIFICADO' AND e.release_at<=? GROUP BY e.id,e.title,e.category,e.relative_weight",
                    r.get("offering_id"), r.get("student_id"), now()));
            Map<String, Object> w = db.parse(s(r, "weights"), Map.class);
            Map<String, Object> avg = new LinkedHashMap<>();
            double total = 0, covered = 0;
            for (String cat : List.of("PRACTICA", "PARCIAL", "SIMULACRO")) {
                var values = entries.stream().filter(e -> cat.equals(s(e, "category"))).toList();
                double weight = Double.parseDouble(w.get(cat).toString());
                if (values.isEmpty())
                    avg.put(cat, null);
                else {
                    double a = values.stream().mapToDouble(e -> num(e, "grade") * num(e, "relative_weight")).sum()
                            / values.stream().mapToDouble(e -> num(e, "relative_weight")).sum();
                    avg.put(cat, a);
                    total += a * weight;
                    covered += weight;
                }
            }
            r.putAll(m("entries", entries, "averages", avg, "grade",
                    covered == 0 ? null : Math.round(total / covered * 100.0) / 100.0, "complete",
                    Math.abs(covered - 100) < 0.001, "covered_weight", covered));
        }
        return rows;
    }
}
