package pe.jauja.academia;

import java.util.*;
import org.springframework.stereotype.Component;
import org.springframework.security.core.context.SecurityContextHolder;
import static pe.jauja.academia.Store.*;

@Component
public class Access {
    final Store db;

    public Access(Store d) {
        db = d;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> user() {
        return (Map<String, Object>) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }

    public String uid() {
        return s(user(), "id");
    }

    public String role() {
        return s(user(), "role");
    }

    public boolean director() {
        return role().equals("DIRECTOR");
    }

    public boolean student() {
        return role().equals("ALUMNO");
    }

    public void roles(String... r) {
        if (!Arrays.asList(r).contains(role()))
            deny();
    }

    public Map<String, Object> offering(String id, boolean edit) {
        var o = db.one(
                "SELECT o.*,s.cycle_id,c.closed FROM offerings o JOIN sections s ON s.id=o.section_id JOIN cycles c ON c.id=s.cycle_id WHERE o.id=?",
                id);
        if (!director()) {
            if (student()) {
                if (edit || db.find("SELECT id FROM enrollments WHERE student_id=? AND offering_id=?", uid(),
                        id) == null)
                    deny();
            } else if (!s(o, "teacher_id").equals(uid()))
                deny();
        }
        if (edit)
            check(!b(o, "closed"), "El ciclo está cerrado");
        return o;
    }

    public String scope() {
        return director() ? "1=1"
                : student() ? "o.id IN (SELECT offering_id FROM enrollments WHERE student_id='" + uid() + "')"
                        : "o.teacher_id='" + uid() + "'";
    }

    public void enrolled(String student, String offering) {
        check(db.find("SELECT id FROM enrollments WHERE student_id=? AND offering_id=? AND withdrawn_on IS NULL",
                student, offering) != null, "Alumno no matriculado");
    }

    public boolean course(String c) {
        return director() || db.find(student()
                ? "SELECT o.id FROM offerings o JOIN enrollments e ON e.offering_id=o.id WHERE o.course_id=? AND e.student_id=?"
                : "SELECT id FROM offerings WHERE course_id=? AND teacher_id=?", c, uid()) != null;
    }

    public void own(String student) {
        if (!director() && !uid().equals(student))
            deny();
    }
}
