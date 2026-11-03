package pe.jauja.academia;


import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.SimpleMailMessage;
import static pe.jauja.academia.Store.*;

@Service
public class Alerts {
    final Store db;
    final Metrics metrics;
    final JavaMailSender mail;
    final boolean enabled;
    final String from;

    public Alerts(Store d, Metrics m, JavaMailSender j, @Value("${app.mail-enabled}") boolean en,
            @Value("${app.mail-from}") String f) {
        db = d;
        metrics = m;
        mail = j;
        enabled = en;
        from = f;
    }

    public void notify(String uid, String title, String body, String severity, String key) {
        if (db.find("SELECT id FROM notifications WHERE dedup_key=?", key) == null) {
            try {
                db.insert("notifications",
                        m("user_id", uid, "title", title, "body", body, "severity", severity, "dedup_key", key));
            } catch (org.springframework.dao.DuplicateKeyException ignored) {
            }
        }
    }

    @Scheduled(initialDelay = 30000, fixedDelay = 60000)
    public void evaluate() {
        var settings = db.find("SELECT * FROM settings WHERE id='main'");
        if (settings == null)
            return;
        for (var r : metrics.attendance("1=1", null)) {
            double pct = num(r, "cycle_pct"), toDate = num(r, "to_date_pct");
            String uid = s(r, "student_id"), key = uid + ":" + s(r, "offering_id");
            if (pct >= 50)
                notify(uid, "Falta grave por inasistencia",
                        s(r, "course_name") + ": " + Math.round(pct)
                                + "% de las sesiones del ciclo. Solicita revisión o acompañamiento a la dirección.",
                        "GRAVE", "faltas-grave:" + key);
            else if (pct >= num(settings, "warning_pct"))
                notify(uid, "Alerta de inasistencia",
                        s(r, "course_name") + ": acumulaste " + Math.round(pct) + "% de faltas del ciclo.", "ALERTA",
                        "faltas-aviso:" + key);
            else if (num(r, "completed") >= 4 && toDate >= 50)
                notify(uid, "Revisa tu asistencia reciente",
                        s(r, "course_name") + ": has faltado al " + Math.round(toDate)
                                + "% de las sesiones realizadas. Todavía no equivale a falta grave del ciclo.",
                        "ALERTA", "faltas-temprana:" + key);
        }
        for (var r : metrics.grades("1=1", null)) {
            if (r.get("grade") == null)
                continue;
            double grade = num(r, "grade");
            String uid = s(r, "student_id"), key = uid + ":" + s(r, "offering_id");
            if (grade < num(settings, "low_grade"))
                notify(uid, "Refuerzo recomendado",
                        s(r, "course_name") + ": promedio provisional " + grade
                                + ". Revisa materiales y solicita orientación a tu docente.",
                        "ALERTA", "rendimiento-bajo:" + key);
            if (grade >= num(settings, "high_grade"))
                notify(uid, "¡Buen progreso!",
                        s(r, "course_name") + ": promedio " + grade
                                + ". Sigue practicando y comparte tus resoluciones.",
                        "LOGRO", "rendimiento-alto:" + key);
        }
    }

    @Scheduled(initialDelay = 45000, fixedDelay = 60000)
    public void deliver() {
        if (!enabled)
            return;
        for (var n : db.list(
                "SELECT n.*,u.email FROM notifications n JOIN users u ON u.id=n.user_id WHERE n.emailed=FALSE AND n.retries<3 LIMIT 20")) {
            try {
                var m = new SimpleMailMessage();
                m.setFrom(from);
                m.setTo(s(n, "email"));
                m.setSubject(s(n, "title"));
                m.setText(s(n, "body"));
                mail.send(m);
                db.patch("notifications", s(n, "id"), m("emailed", true));
            } catch (Exception e) {
                db.patch("notifications", s(n, "id"), m("retries", n(n, "retries", 0) + 1));
            }
        }
    }
}
