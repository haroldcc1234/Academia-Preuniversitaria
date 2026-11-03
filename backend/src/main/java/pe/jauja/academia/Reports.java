package pe.jauja.academia;

import java.util.*;
import java.io.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.font.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import static pe.jauja.academia.Store.*;

@RestController
@RequestMapping("/api/reports")
public class Reports {
    final Store db;
    final Access ac;
    final Metrics metrics;
    @org.springframework.beans.factory.annotation.Autowired Vocational orientation;

    public Reports(Store d, Access a, Metrics m) {
        db = d;
        ac = a;
        metrics = m;
    }

    boolean match(Map<String, Object> r, Map<String, String> f) {
        return (s(f, "cycle").isBlank() || s(r, "cycle_id").equals(s(f, "cycle")))
                && (s(f, "offering").isBlank() || s(r, "offering_id").equals(s(f, "offering")))
                && (s(f, "student").isBlank() || s(r, "student_id").equals(s(f, "student")))
                && (s(f, "section").isBlank() || s(r, "section_name").equals(s(f, "section")));
    }

    public List<Map<String, Object>> rows(String type, Map<String, String> filters) {
        if (type.equals("performance") || type.equals("teachers")) ac.roles("DIRECTOR");
        List<Map<String, Object>> rows = new ArrayList<>();
        String own = ac.student() ? ac.uid() : null;
        if (ac.student() && !s(filters, "student").isBlank() && !s(filters, "student").equals(own))
            deny();
        if (type.equals("attendance")) {
            for (var r : metrics.attendance(ac.scope(), own)) {
                if (!match(r, filters))
                    continue;
                List<Map<String, Object>> history = (List<Map<String, Object>>) r.get("history");
                boolean range = !s(filters, "from").isBlank() || !s(filters, "to").isBlank();
                if (range) {
                    for (var h : history)
                        if ((s(filters, "from").isBlank() || s(h, "date").compareTo(s(filters, "from")) >= 0)
                                && (s(filters, "to").isBlank() || s(h, "date").compareTo(s(filters, "to")) <= 0))
                            rows.add(m("Alumno", r.get("student_name"), "Curso", r.get("course_name"), "Sección",
                                    r.get("section_name"), "Fecha", h.get("date"), "Estado",
                                    s(h, "state").isBlank() ? h.get("status") : h.get("state")));
                } else
                    rows.add(m("Alumno", r.get("student_name"), "Curso", r.get("course_name"), "Sección",
                            r.get("section_name"), "Sesiones ciclo", r.get("planned"), "Realizadas", r.get("completed"),
                            "Faltas", r.get("faults"), "% ciclo", Math.round(num(r, "cycle_pct") * 100) / 100.0,
                            "Estado", b(r, "severe") ? "FALTA GRAVE" : "Seguimiento"));
            }
        } else if (type.equals("teachers")) {
            ac.roles("DIRECTOR");
            for (var o : db.list(
                    "SELECT o.*,u.name teacher_name,c.name course_name,s.name section_name,s.cycle_id FROM offerings o JOIN users u ON u.id=o.teacher_id JOIN courses c ON c.id=o.course_id JOIN sections s ON s.id=o.section_id")) {
                o.put("offering_id", o.get("id"));
                if (!match(o, filters))
                    continue;
                var grades = metrics.grades("o.id='" + s(o, "id") + "'", null);
                double average = grades.stream().filter(g -> g.get("grade") != null).mapToDouble(g -> num(g, "grade"))
                        .average().orElse(0);
                var sessions = db.list("SELECT * FROM sessions WHERE offering_id=? AND status<>'CANCELADA'",
                        o.get("id"));
                double hours = 0;
                for (var s : sessions)
                    if ((s(filters, "from").isBlank() || s(s, "date").compareTo(s(filters, "from")) >= 0)
                            && (s(filters, "to").isBlank() || s(s, "date").compareTo(s(filters, "to")) <= 0))
                        hours += java.time.Duration.between(java.time.LocalTime.parse(s(s, "starts")),
                                java.time.LocalTime.parse(s(s, "ends"))).toMinutes() / 60.0;
                rows.add(m("Docente", o.get("teacher_name"), "Curso", o.get("course_name"), "Sección",
                        o.get("section_name"), "Horas programadas", hours, "Alumnos", grades.size(), "Promedio",
                        average, "Avance %", o.get("progress")));
            }
        } else {
            for (var r : metrics.grades(ac.scope(), own)) {
                if (!match(r, filters))
                    continue;
                boolean range = !s(filters, "from").isBlank() || !s(filters, "to").isBlank();
                if (range) {
                    List<Map<String, Object>> entries = (List<Map<String, Object>>) r.get("entries");
                    for (var e : entries) {
                        String date = s(e, "created_at").substring(0, 10);
                        if ((s(filters, "from").isBlank() || date.compareTo(s(filters, "from")) >= 0)
                                && (s(filters, "to").isBlank() || date.compareTo(s(filters, "to")) <= 0))
                            rows.add(m("Alumno", r.get("student_name"), "Curso", r.get("course_name"), "Sección",
                                    r.get("section_name"), "Evaluación", e.get("title"), "Categoría", e.get("category"),
                                    "Nota", e.get("grade"), "Fecha", date));
                    }
                } else
                    rows.add(m("Alumno", r.get("student_name"), "Curso", r.get("course_name"), "Sección",
                            r.get("section_name"), "Ciclo", r.get("cycle_name"), "Promedio", r.get("grade"), "Estado",
                            b(r, "complete") && b(r, "closed") ? "Final" : "Provisional", "Peso evaluado %",
                            r.get("covered_weight")));
            }
        }
        if (type.equals("performance")) {
            Map<String, List<Double>> grouped = new LinkedHashMap<>();
            for (var r : rows) {
                String section = s(r, "Sección");
                String key = r.containsKey("Promedio") ? "Promedio" : "Nota";
                if (r.get(key) != null)
                    grouped.computeIfAbsent(section, k -> new ArrayList<>()).add(num(r, key));
            }
            rows = new ArrayList<>();
            for (var e : grouped.entrySet())
                rows.add(m("Sección", e.getKey(), "Registros evaluados", e.getValue().size(), "Promedio",
                        e.getValue().stream().mapToDouble(Double::doubleValue).average().orElse(0)));
        }
        return rows;
    }

    @GetMapping
    public Object preview(@RequestParam(defaultValue = "grades") String type,
            @RequestParam Map<String, String> filters) {
        check(List.of("grades", "attendance", "teachers", "performance").contains(type), "Reporte inválido");
        return rows(type, filters);
    }

    @GetMapping("/download")
    public ResponseEntity<byte[]> export(@RequestParam(defaultValue = "grades") String type,
            @RequestParam(defaultValue = "pdf") String format, @RequestParam Map<String, String> filters)
            throws Exception {
        check(List.of("grades", "attendance", "teachers", "performance").contains(type), "Reporte inválido");
        var rows = rows(type, filters);
        db.audit(ac.uid(), "EXPORTAR_REPORTE", type, filters);
        String title = "Academia Jauja - " + type;
        return download(rows, title, format);
    }

    public static ResponseEntity<byte[]> download(List<Map<String, Object>> rows, String title, String format)
            throws Exception {
        if (format.equals("xlsx")) {
            try (var wb = new XSSFWorkbook(); var out = new ByteArrayOutputStream()) {
                var sh = wb.createSheet("Reporte");
                var bold = wb.createFont();
                bold.setBold(true);
                var style = wb.createCellStyle();
                style.setFont(bold);
                List<String> cols = rows.isEmpty() ? List.of("Sin registros") : new ArrayList<>(rows.get(0).keySet());
                var h = sh.createRow(0);
                for (int i = 0; i < cols.size(); i++) {
                    h.createCell(i).setCellValue(cols.get(i));
                    h.getCell(i).setCellStyle(style);
                }
                int n = 1;
                for (var r : rows) {
                    var row = sh.createRow(n++);
                    for (int i = 0; i < cols.size(); i++) {
                        Object value = r.get(cols.get(i));
                        if (value instanceof Number number)
                            row.createCell(i).setCellValue(number.doubleValue());
                        else
                            row.createCell(i).setCellValue(Objects.toString(value, "Pendiente"));
                    }
                }
                for (int i = 0; i < cols.size(); i++)
                    sh.setColumnWidth(i, 6000);
                sh.createFreezePane(0, 1);
                wb.write(out);
                return Knowledge.attachment(out.toByteArray(), "reporte-academia.xlsx");
            }
        }
        check(format.equals("pdf"), "Formato inválido");
        List<String> lines = new ArrayList<>();
        lines.add(title);
        lines.add("Documento institucional - emitido: " + now());
        lines.add(" ");
        for (var r : rows) {
            for (var e : r.entrySet())
                lines.add(e.getKey() + ": " + Objects.toString(e.getValue(), "Pendiente"));
            lines.add("----------------------------------------------------------");
        }
        if (rows.isEmpty())
            lines.add("Sin registros para los filtros seleccionados.");
        return Knowledge.attachment(pdf(lines), "reporte-academia.pdf");
    }

    public static byte[] pdf(List<String> lines) throws Exception {
        try (var doc = new PDDocument(); var out = new ByteArrayOutputStream()) {
            PDPageContentStream stream = null;
            float y = 0;
            int pageNo = 0;
            var font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            for (String line : lines) {
                String clean = line.replaceAll("[^\\x20-\\x7E\\u00A0-\\u00FF]", " ");
                List<String> wrapped = new ArrayList<>();
                while (clean.length() > 88) {
                    int split = clean.lastIndexOf(' ', 88);
                    if (split < 20)
                        split = 88;
                    wrapped.add(clean.substring(0, split));
                    clean = clean.substring(split).trim();
                }
                wrapped.add(clean);
                for (String part : wrapped) {
                    if (stream == null || y < 50) {
                        if (stream != null)
                            stream.close();
                        PDPage page = new PDPage();
                        doc.addPage(page);
                        stream = new PDPageContentStream(doc, page);
                        y = 740;
                        stream.beginText();
                        stream.setFont(font, 9);
                        stream.newLineAtOffset(50, 30);
                        stream.showText("Academia Jauja | " + (++pageNo));
                        stream.endText();
                    }
                    stream.beginText();
                    stream.setFont(font, 10);
                    stream.newLineAtOffset(50, y);
                    stream.showText(part);
                    stream.endText();
                    y -= 16;
                }
            }
            if (stream != null)
                stream.close();
            doc.save(out);
            return out.toByteArray();
        }
    }

    @GetMapping("/vocational/{id}")
    public ResponseEntity<byte[]> vocational(@PathVariable String id) throws Exception {
        var v = db.get("vocational", id);
        if (!s(v, "student_id").equals(ac.uid()))
            deny();
        check(s(v, "status").equals("COMPLETO"), "Completa el test");
        Map<String, Object> r = orientation.calculate(db.parse(s(v, "answers"), Map.class),db.parse(s(v, "preferences"), Map.class));
        List<String> lines = new ArrayList<>(List.of("Informe de orientación vocacional", s(ac.user(), "name"),
                "Instrumento: " + s(v, "instrument"), s(r, "notice"), " ", s(r, "interpretation"), s(r, "formula")));
        lines.add("Método actual: " + s(orientation.definition(),"version"));
        lines.add(s(r,"recommendation_notice"));
        for(var u:(List<Map<String,Object>>)r.get("university_recommendations")){lines.add((b(u,"public_university")?"Pública: ":"Privada: ")+s(u,"name")+" - "+s(u,"program_name"));lines.add(s(u,"recommendation_reason"));lines.add(s(u,"source_url"));}
        List<Map<String, Object>> profile = (List<Map<String, Object>>) r.get("profile");
        for (var d : profile)
            lines.add(s(d, "name") + ": " + s(d, "score") + "/100");
        List<Map<String, Object>> careers = (List<Map<String, Object>>) r.get("careers");
        for (var c : careers) {
            lines.add(" ");
            lines.add(s(c, "name") + " - Afinidad orientativa: " + s(c, "affinity"));
            lines.add(s(c, "description"));
            lines.add(s(c, "activity"));
            for (var u : (List<Map<String, Object>>) c.get("universities")) {
                lines.add(s(u, "name") + " | " + s(u, "city"));
                lines.add(s(u, "source_url"));
                lines.add("Verificado: " + (s(u, "verified_on").isBlank() ? "Pendiente" : s(u, "verified_on")));
            }
        }
        if (r.containsKey("ai_explanation"))
            lines.add(s(r, "ai_explanation"));
        return Knowledge.attachment(pdf(lines), "orientacion-vocacional.pdf");
    }

    @PostMapping("/cards/{cycle}/{student}")
    public Object issue(@PathVariable String cycle, @PathVariable String student) {
        ac.roles("DIRECTOR");
        var c = db.get("cycles", cycle);
        check(b(c, "closed"), "Cierra el ciclo antes de emitir boletas definitivas");
        var rows = metrics.grades("1=1", student).stream().filter(r -> s(r, "cycle_id").equals(cycle)).toList();
        check(!rows.isEmpty() && rows.stream().allMatch(r -> b(r, "complete")), "Notas incompletas");
        int version = n(
                db.one("SELECT MAX(version) n FROM report_cards WHERE cycle_id=? AND student_id=?", cycle, student),
                "n", 0) + 1;
        String id = db.insert("report_cards", m("cycle_id", cycle, "student_id", student, "version", version,
                "snapshot", db.json(rows), "issued_by", ac.uid()));
        db.audit(ac.uid(), "EMITIR_BOLETA", id, m("version", version));
        return m("id", id);
    }

    @GetMapping("/cards")
    public Object cards() {
        return ac.director() ? db.list(
                "SELECT rc.id,rc.cycle_id,rc.student_id,rc.version,rc.created_at,u.name student_name,c.name cycle_name FROM report_cards rc JOIN users u ON u.id=rc.student_id JOIN cycles c ON c.id=rc.cycle_id ORDER BY rc.created_at DESC")
                : db.list(
                        "SELECT rc.id,rc.cycle_id,rc.student_id,rc.version,rc.created_at,c.name cycle_name FROM report_cards rc JOIN cycles c ON c.id=rc.cycle_id WHERE rc.student_id=? ORDER BY rc.created_at DESC",
                        ac.uid());
    }

    @GetMapping("/cards/{id}/pdf")
    public ResponseEntity<byte[]> card(@PathVariable String id) throws Exception {
        var card = db.get("report_cards", id);
        ac.own(s(card, "student_id"));
        List<Map<String, Object>> rows = db.parse(s(card, "snapshot"), List.class);
        var lines = new ArrayList<String>(List.of("BOLETA INSTITUCIONAL DEL CICLO", "Folio: " + id,
                "Versión: " + s(card, "version"), "Emisión: " + s(card, "created_at"),
                "Alumno: " + s(db.get("users", s(card, "student_id")), "name")));
        double total = 0;
        for (var r : rows) {
            lines.add(s(r, "course_name") + ": " + s(r, "grade") + " / 20");
            total += num(r, "grade");
        }
        lines.add("Promedio del ciclo (cursos con igual peso): " + Math.round(total / rows.size() * 100.0) / 100.0);
        return Knowledge.attachment(pdf(lines), "boleta-institucional.pdf");
    }
}
