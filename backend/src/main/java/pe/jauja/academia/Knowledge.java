package pe.jauja.academia;

import java.util.*;

import java.net.URI;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.*;
import org.springframework.transaction.annotation.Transactional;
import static pe.jauja.academia.Store.*;

@RestController
@RequestMapping("/api")
public class Knowledge {
    final Store db;
    final Access ac;
    final FilesService files;
    final BulletinProcessor bulletinProcessor;

    public Knowledge(Store d, Access a, FilesService f, BulletinProcessor processor) {
        db = d;
        ac = a;
        files = f;
        bulletinProcessor = processor;
    }

    @GetMapping("/materials")
    public List<Map<String, Object>> materials() {
        return db.list(
                "SELECT m.*,v.id version_id,v.version,v.body,v.url,v.file_name,v.mime,v.created_at updated_at,c.name course_name,s.name section_name,u.name author_name FROM materials m JOIN offerings o ON o.id=m.offering_id JOIN courses c ON c.id=o.course_id JOIN sections s ON s.id=o.section_id JOIN users u ON u.id=m.author_id JOIN material_versions v ON v.material_id=m.id WHERE m.archived=FALSE AND v.version=(SELECT MAX(v2.version) FROM material_versions v2 WHERE v2.material_id=m.id) AND "
                        + ac.scope() + " ORDER BY v.created_at DESC");
    }

    @GetMapping("/materials/{id}/versions")
    public Object versions(@PathVariable String id) {
        var m = db.get("materials", id);
        ac.offering(s(m, "offering_id"), false);
        check(!b(m, "archived") || !ac.student(), "Material retirado");
        return db.list(
                "SELECT id,version,body,url,file_name,reason,created_at FROM material_versions WHERE material_id=? ORDER BY version DESC",
                id);
    }

    @PostMapping(value = "/materials", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Transactional
    public Object save(@RequestParam Map<String, String> x, @RequestParam(required = false) MultipartFile file)
            throws Exception {
        String offering = req(x, "offering_id", 36);
        ac.offering(offering, true);
        String id = s(x, "id");
        int version = 1;
        if (id.isBlank())
            id = db.insert("materials", m("offering_id", offering, "author_id", ac.uid(), "title", req(x, "title", 200),
                    "topic", req(x, "topic", 150), "difficulty", req(x, "difficulty", 20), "tags", s(x, "tags")));
        else {
            var old = db.one("SELECT * FROM materials WHERE id=? FOR UPDATE", id);
            check(s(old, "offering_id").equals(offering), "Curso diferente");
            if (!ac.director() && !s(old, "author_id").equals(ac.uid()))
                deny();
            req(x, "reason", 400);
            version = n(db.one("SELECT MAX(version) n FROM material_versions WHERE material_id=?", id), "n", 0) + 1;
            db.patch("materials", id, m("title", req(x, "title", 200), "topic", req(x, "topic", 150), "difficulty",
                    req(x, "difficulty", 20), "tags", s(x, "tags")));
        }
        String body = s(x, "body"), url = s(x, "url");
        if (!url.isBlank())
            url(url);
        Map<String, Object> data = m("material_id", id, "version", version, "body", body, "url", url, "reason",
                s(x, "reason"));
        if (file != null && !file.isEmpty()) {
            check(file.getSize() <= 25 * 1024 * 1024, "Máximo 25 MB");
            String name = Objects.requireNonNullElse(file.getOriginalFilename(), "archivo"),
                    mime = Objects.requireNonNullElse(file.getContentType(), "application/octet-stream");
            check(name.toLowerCase().matches(".*\\.(pdf|docx|pptx|txt|mp4|webm|png|jpg|jpeg)$"),
                    "Formato no permitido");
            data.putAll(m("file_key", files.save(file.getBytes(), mime), "file_name", name, "mime", mime));
            if (!mime.startsWith("video/") && !mime.startsWith("image/")) {
                try {
                    String text = DocumentText.extract(name, file.getBytes());
                    body += "\n" + text.substring(0, Math.min(text.length(), 100000));
                } catch (Exception e) {
                    body += "\n[Agrega una transcripción: el archivo no contiene texto extraíble.]";
                }
                data.put("body", body);
            }
        } else if (version > 1) {
            var prev = db.one("SELECT * FROM material_versions WHERE material_id=? ORDER BY version DESC LIMIT 1", id);
            data.putAll(
                    m("file_key", prev.get("file_key"), "file_name", prev.get("file_name"), "mime", prev.get("mime")));
        }
        check(!body.isBlank() || data.get("file_key") != null || !url.isBlank(), "Agrega contenido, enlace o archivo");
        db.insert("material_versions", data);
        db.audit(ac.uid(), "PUBLICAR_MATERIAL", id, m("version", version));
        return m("id", id);
    }

    public static void url(String s) {
        URI u = URI.create(s);
        check(("http".equals(u.getScheme()) || "https".equals(u.getScheme())) && u.getHost() != null,
                "Enlace HTTP o HTTPS requerido");
    }

    @DeleteMapping("/materials/{id}")
    public Object archive(@PathVariable String id) {
        var x = db.get("materials", id);
        ac.offering(s(x, "offering_id"), true);
        if (!ac.director() && !s(x, "author_id").equals(ac.uid()))
            deny();
        db.patch("materials", id, m("archived", true));
        db.audit(ac.uid(), "ARCHIVAR_MATERIAL", id, m());
        return m("ok", true);
    }

    static ResponseEntity<byte[]> attachment(byte[] bytes, String name) {
        return ResponseEntity.ok()
                .header("Content-Disposition", ContentDisposition.attachment()
                        .filename(name, java.nio.charset.StandardCharsets.UTF_8).build().toString())
                .contentType(MediaType.APPLICATION_OCTET_STREAM).body(bytes);
    }

    @GetMapping("/files/material/{id}")
    public ResponseEntity<byte[]> file(@PathVariable String id) throws Exception {
        var v = db.one(
                "SELECT v.*,m.offering_id,m.archived FROM material_versions v JOIN materials m ON m.id=v.material_id WHERE v.id=?",
                id);
        ac.offering(s(v, "offering_id"), false);
        check(!b(v, "archived") || !ac.student(), "Material retirado");
        return attachment(files.read(s(v, "file_key")), s(v, "file_name"));
    }

    public boolean reserved(String q) {
        return db.find(
                "SELECT e.id FROM exams e JOIN exam_questions eq ON eq.exam_id=e.id JOIN question_versions v ON v.id=eq.question_version_id WHERE v.question_id=? AND e.archived=FALSE AND e.closes_at>?",
                q, now()) != null;
    }

    @GetMapping("/questions")
    public List<Map<String, Object>> questions() {
        var rows = db.list(
                "SELECT q.*,v.id version_id,v.version,v.kind,v.prompt,v.options,v.answer,v.explanation,c.name course_name FROM questions q JOIN question_versions v ON v.question_id=q.id JOIN courses c ON c.id=q.course_id WHERE q.archived=FALSE AND v.version=(SELECT MAX(v2.version) FROM question_versions v2 WHERE v2.question_id=q.id)");
        rows.removeIf(
                q -> !ac.course(s(q, "course_id")) || (ac.student() && (!b(q, "practice") || reserved(s(q, "id")))));
        for (var q : rows)
            q.put("options", db.parse(s(q, "options"), List.class));
        return rows;
    }

    @GetMapping("/practice/questions")
    public List<Map<String, Object>> practiceQuestions() {
        ac.roles("ALUMNO");
        return questions(); // The existing scope excludes private questions and active exam questions.
    }

    @PostMapping("/questions")
    @Transactional
    public Object saveQuestion(@RequestBody Map<String, Object> x) {
        ac.roles("DIRECTOR", "DOCENTE");
        String course = req(x, "course_id", 36);
        if (!ac.course(course))
            deny();
        String id = s(x, "id");
        int version = 1;
        if (id.isBlank())
            id = db.insert("questions", m("course_id", course, "author_id", ac.uid(), "topic", req(x, "topic", 150),
                    "difficulty", req(x, "difficulty", 20), "practice", b(x, "practice")));
        else {
            var old = db.one("SELECT * FROM questions WHERE id=? FOR UPDATE", id);
            if (!ac.director() && !s(old, "author_id").equals(ac.uid()))
                deny();
            check(s(old, "course_id").equals(course), "No se cambia el curso de una pregunta");
            version = n(db.one("SELECT MAX(version) n FROM question_versions WHERE question_id=?", id), "n", 0) + 1;
            db.patch("questions", id, m("topic", req(x, "topic", 150), "difficulty", req(x, "difficulty", 20),
                    "practice", b(x, "practice")));
        }
        String kind = req(x, "kind", 20);
        check(List.of("MULTIPLE", "VF", "DESARROLLO").contains(kind), "Tipo inválido");
        List<?> opts = x.get("options") instanceof List<?> l ? l : List.of();
        String answer = s(x, "answer");
        if (!kind.equals("DESARROLLO")) {
            check(opts.size() >= 2 && opts.size() <= 6, "Usa de 2 a 6 alternativas");
            int i = Integer.parseInt(answer);
            check(i >= 0 && i < opts.size(), "Respuesta fuera de las alternativas");
            if (kind.equals("VF"))
                check(opts.size() == 2, "Verdadero/falso requiere dos alternativas");
        }
        db.insert("question_versions",
                m("question_id", id, "version", version, "kind", kind, "prompt", req(x, "prompt", 10000), "options",
                        db.json(opts), "answer", answer, "explanation", s(x, "explanation")));
        db.audit(ac.uid(), "VERSIONAR_PREGUNTA", id, m("version", version));
        return m("id", id);
    }

    @GetMapping("/resolutions")
    public List<Map<String, Object>> resolutions() {
        var rows = db.list(
                "SELECT r.*,q.course_id,v.prompt,u.name author_name FROM resolutions r JOIN questions q ON q.id=r.question_id JOIN question_versions v ON v.question_id=q.id JOIN users u ON u.id=r.author_id WHERE v.version=(SELECT MAX(qv.version) FROM question_versions qv WHERE qv.question_id=q.id) ORDER BY r.created_at DESC");
        rows.removeIf(r -> !ac.course(s(r, "course_id"))
                || (ac.student() && (!s(r, "status").equals("APROBADO") && !s(r, "author_id").equals(ac.uid())))
                || (ac.student() && reserved(s(r, "question_id"))));
        return rows;
    }

    @PostMapping("/resolutions")
    public Object resolution(@RequestBody Map<String, Object> x) {
        String q = req(x, "question_id", 36);
        var question = db.get("questions", q);
        if (!ac.course(s(question, "course_id")))
            deny();
        if (ac.student())
            check(b(question, "practice") && !reserved(q), "Pregunta reservada");
        String id = db.insert("resolutions", m("question_id", q, "author_id", ac.uid(), "body", req(x, "body", 20000),
                "status", ac.student() ? "PENDIENTE" : "APROBADO"));
        db.audit(ac.uid(), "APORTAR_RESOLUCION", id, m());
        return m("id", id);
    }

    @PutMapping("/resolutions/{id}")
    @Transactional
    public Object editResolution(@PathVariable String id, @RequestBody Map<String, Object> x) {
        var r = db.one("SELECT * FROM resolutions WHERE id=? FOR UPDATE", id);
        if (!s(r, "author_id").equals(ac.uid()))
            deny();
        db.insert("resolution_history", m("resolution_id", id, "version", r.get("version"), "body", r.get("body")));
        db.patch("resolutions", id, m("body", req(x, "body", 20000), "version", n(r, "version", 1) + 1, "status",
                "PENDIENTE", "reviewer_id", null));
        db.audit(ac.uid(), "EDITAR_RESOLUCION", id, m());
        return m("ok", true);
    }

    @PostMapping("/resolutions/{id}/review")
    public Object review(@PathVariable String id, @RequestBody Map<String, Object> x) {
        ac.roles("DIRECTOR", "DOCENTE");
        var r = db.one("SELECT r.*,q.course_id FROM resolutions r JOIN questions q ON q.id=r.question_id WHERE r.id=?",
                id);
        if (!ac.course(s(r, "course_id")))
            deny();
        check(List.of("APROBADO", "OBSERVADO", "RECHAZADO").contains(s(x, "status")), "Estado inválido");
        db.patch("resolutions", id, m("status", s(x, "status"), "feedback", s(x, "feedback"), "reviewer_id", ac.uid()));
        db.audit(ac.uid(), "REVISAR_RESOLUCION", id, x);
        return m("ok", true);
    }

    @GetMapping("/bulletins")
    public List<Map<String, Object>> bulletins() {
        var rows = db.list("SELECT * FROM bulletins WHERE archived=FALSE ORDER BY admission_year DESC,version DESC");
        if (ac.student()) {
            boolean enrolled = db.find("SELECT id FROM enrollments WHERE student_id=? AND withdrawn_on IS NULL",
                    ac.uid()) != null;
            rows.removeIf(r -> !enrolled
                    || (!s(r, "career").equalsIgnoreCase(s(ac.user(), "career")) && !s(r, "career").equals("General")));
        }
        return rows;
    }

    @PutMapping("/bulletins/{id}/university")
    @Transactional
    public Object classifyBulletin(@PathVariable String id,@RequestBody Map<String,Object> body) {
        ac.roles("DIRECTOR");
        var bulletin=db.get("bulletins",id);
        check(!b(bulletin,"archived"),"El balotario está archivado.");
        String university=req(body,"university",100);
        check(university.equals("General") || db.find("SELECT id FROM universities WHERE acronym=?",university)!=null,"Selecciona una universidad del catálogo.");
        db.patch("bulletins",id,m("university",university));
        db.audit(ac.uid(),"CLASIFICAR_BALOTARIO",id,m("before",bulletin.get("university"),"after",university));
        return m("ok",true);
    }

    @PostMapping(value = "/bulletins", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Object bulletin(@RequestParam Map<String, String> x, @RequestParam(required = false) MultipartFile file)
            throws Exception {
        ac.roles("DIRECTOR");
        String key = null, name = null, url = s(x, "url");
        if (!url.isBlank())
            url(url);
        if (file != null && !file.isEmpty()) {
            check(file.getSize() <= 25 * 1024 * 1024, "Máximo 25 MB");
            check(Objects.requireNonNullElse(file.getOriginalFilename(), "").toLowerCase().endsWith(".pdf"),
                    "Sube un PDF");
            key = files.save(file.getBytes(), "application/pdf");
            name = file.getOriginalFilename();
        }
        check(key != null || !url.isBlank(), "Sube un archivo o agrega enlace");
        String id = id(), group = s(x, "bulletin_group");
        int version = 1;
        if (group.isBlank())
            group = id;
        else
            version = n(db.one("SELECT MAX(version) n FROM bulletins WHERE bulletin_group=?", group), "n", 0) + 1;
        db.insert("bulletins",
                m("id", id, "title", req(x, "title", 200), "university", req(x, "university", 100), "career",
                        req(x, "career", 100), "admission_year", Integer.parseInt(req(x, "admission_year", 4)),
                        "process", req(x, "process", 100), "file_key", key, "file_name", name, "url", url,
                        "description", s(x, "description"), "version", version, "bulletin_group", group));
        db.audit(ac.uid(), "PUBLICAR_BALOTARIO", id, m("version", version));
        if (key != null) bulletinProcessor.queue(id, ac.uid());
        return m("id", id);
    }

    @GetMapping("/files/bulletin/{id}")
    public ResponseEntity<byte[]> bulletinFile(@PathVariable String id) throws Exception {
        var b = bulletins().stream().filter(x -> s(x, "id").equals(id)).findFirst().orElse(null);
        if (b == null)
            deny();
        return attachment(files.read(s(b, "file_key")), s(b, "file_name"));
    }

    @DeleteMapping("/bulletins/{id}")
    public Object archiveBulletin(@PathVariable String id) {
        ac.roles("DIRECTOR");
        db.patch("bulletins", id, m("archived", true));
        db.audit(ac.uid(), "ARCHIVAR_BALOTARIO", id, m());
        return m("ok", true);
    }
}
