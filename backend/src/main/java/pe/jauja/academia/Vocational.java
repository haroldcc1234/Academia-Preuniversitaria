package pe.jauja.academia;

import java.util.*;

import org.springframework.web.bind.annotation.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.transaction.annotation.Transactional;
import static pe.jauja.academia.Store.*;

@RestController
@RequestMapping("/api/vocational")
public class Vocational {
    final Store db;
    final Access ac;
    @org.springframework.beans.factory.annotation.Autowired UniversityCatalog universityCatalog;
    final Map<String, Object> instrument;

    public Vocational(Store d, Access a) throws Exception {
        db = d;
        ac = a;
        instrument = db.json.readValue(new ClassPathResource("vocational.json").getInputStream(), Map.class);
    }

    public Map<String,Object> definition(){return instrument;}

    @GetMapping("/instrument")
    public Object instrument() {
        return instrument;
    }

    @GetMapping
    public Object history() {
        return db.list("SELECT * FROM vocational WHERE student_id=? ORDER BY created_at DESC", ac.uid());
    }

    @PostMapping
    public Object start() {
        ac.roles("ALUMNO");
        var active = db.find(
                "SELECT id FROM vocational WHERE student_id=? AND status='BORRADOR' ORDER BY created_at DESC LIMIT 1",
                ac.uid());
        if (active != null)
            return active;
        return m("id", db.insert("vocational", m("student_id", ac.uid(), "instrument", instrument.get("version"),
                "answers", "{}", "preferences", "{}", "status", "BORRADOR", "updated_at", now())));
    }

    Map<String, Object> owned(String id) {
        var v = db.get("vocational", id);
        if (!s(v, "student_id").equals(ac.uid()))
            deny();
        return v;
    }

    @GetMapping("/{id}")
    public Object detail(@PathVariable String id) {
        var row=owned(id);
        if(s(row,"status").equals("COMPLETO")) {
            var result=calculate(db.parse(s(row,"answers"),Map.class),db.parse(s(row,"preferences"),Map.class));
            row.put("result",db.json(result));
        }
        return row;
    }

    @PutMapping("/{id}")
    @Transactional
    public Object save(@PathVariable String id, @RequestBody Map<String, Object> x) {
        ac.roles("ALUMNO");
        owned(id);
        var v = db.one("SELECT * FROM vocational WHERE id=? FOR UPDATE", id);
        check(s(v, "status").equals("BORRADOR"), "El resultado ya fue emitido; inicia otro cuestionario");
        var answers = (Map<String, Object>) x.get("answers");
        check(answers != null, "Respuestas inválidas");
        validate(answers, false);
        var preferences = x.get("preferences") instanceof Map<?, ?> p ? p : Map.of();
        db.patch("vocational", id,
                m("answers", db.json(answers), "preferences", db.json(preferences), "updated_at", now()));
        return m("ok", true);
    }

    void validate(Map<String, Object> answers, boolean complete) {
        List<Map<String, Object>> qs = (List<Map<String, Object>>) instrument.get("questions");
        Set<String> ids = new HashSet<>();
        qs.forEach(q -> ids.add(s(q, "id")));
        check(ids.containsAll(answers.keySet()), "Pregunta desconocida");
        for (Object value : answers.values()) {
            double n = Double.parseDouble(value.toString());
            check(n >= 1 && n <= 5 && n == Math.floor(n), "Usa respuestas entre 1 y 5");
        }
        if (complete)
            check(answers.size() == qs.size(), "Responde las 48 preguntas antes de finalizar");
    }

    public Map<String, Object> calculate(Map<String, Object> answers, Map<String, Object> preferences) {
        validate(answers, true);
        List<Map<String, Object>> dims = (List<Map<String, Object>>) instrument.get("dimensions"),
                qs = (List<Map<String, Object>>) instrument.get("questions");
        Map<String, Double> scores = new LinkedHashMap<>();
        List<Map<String, Object>> profile = new ArrayList<>();
        for (var d : dims) {
            String dim = s(d, "id");
            double sum = qs.stream().filter(q -> s(q, "dimension").equals(dim))
                    .mapToDouble(q -> Double.parseDouble(answers.get(s(q, "id")).toString())).sum();
            double score = Math.round((sum - 8) / 32 * 1000.0) / 10.0;
            scores.put(dim, score);
            profile.add(m("id", dim, "name", d.get("name"), "description", d.get("description"), "score", score, "raw",
                    sum));
        }
        profile.sort(Comparator.comparingDouble((Map<String, Object> d) -> num(d, "score")).reversed());
        double top = num(profile.get(0), "score");
        List<String> tied = profile.stream().filter(d -> num(d, "score") == top).map(d -> s(d, "name")).toList();
        boolean uniform = answers.values().stream().map(Object::toString).distinct().count() == 1;
        List<Map<String, Object>> careers = new ArrayList<>();
        List<Map<String, Object>> catalogue = (List<Map<String, Object>>) instrument.get("careers");
        for (var c : catalogue) {
            List<String> codes = (List<String>) c.get("dimensions");
            double broad = .5 * scores.get(codes.get(0)) + .3 * scores.get(codes.get(1)) + .2 * scores.get(codes.get(2));
            List<String> anchors=(List<String>)c.getOrDefault("anchors",List.of());
            double specific=anchors.isEmpty()?broad:anchors.stream().mapToDouble(q->(Double.parseDouble(answers.get(q).toString())-1)*25).average().orElse(broad);
            double fit=.7*broad+.3*specific;
            var row = new LinkedHashMap<>(c);
            row.put("affinity", Math.round(fit * 10.0) / 10.0);
            row.put("reason",
                    "Relaciona tus intereses "
                            + codes.stream()
                                    .map(code -> dims.stream().filter(d -> s(d, "id").equals(code)).findFirst()
                                            .map(d -> s(d, "name")).orElse(code).toLowerCase())
                                    .reduce((a, b) -> a + ", " + b).orElse("")
                            + ". Esta afinidad es orientativa, no una probabilidad de éxito.");
            var universities=universityCatalog.recommend(List.of(row),preferences);
            row.put("universities",universities);
            careers.add(row);
        }
        careers.sort(Comparator.comparingDouble((Map<String, Object> c) -> num(c, "affinity")).reversed());
        String interpretation = uniform
                ? "Tus respuestas fueron iguales en todas las actividades. El perfil no diferencia áreas: explora experiencias y conversa con un orientador antes de priorizar una carrera."
                : tied.size() > 1
                        ? "Hay un empate entre " + String.join(", ", tied)
                                + ". Explora combinaciones de estas áreas; no existe una única carrera obligatoria."
                        : "Tu mayor interés aparece en el área " + s(profile.get(0), "name")
                                + ". Compara las opciones con tus experiencias, valores y condiciones de estudio.";
        var topCareers=new ArrayList<>(careers.subList(0,Math.min(6,careers.size())));
        var recommended=universityCatalog.recommend(topCareers,preferences);
        return m("university_recommendations",recommended,"recommendation_notice","Opciones por afinidad, oferta contrastada y ubicación. No es un ranking de calidad. Se muestran hasta cuatro públicas y cuatro privadas; no se inventan opciones para completar cupos.","profile", profile, "careers", topCareers, "interpretation",
                interpretation, "uniform", uniform, "ties", tied, "notice", instrument.get("notice"), "formula",
                "Área = (suma de 8 respuestas - 8) / 32 × 100. Afinidad = 70% del perfil de áreas (50%, 30%, 20%) + 30% de actividades específicas de la carrera. Es una regla orientativa, no un diagnóstico psicométrico.",
                "preferences", preferences, "next_steps",
                List.of("Elige dos carreras y compara sus mallas curriculares.",
                        "Realiza una actividad exploratoria de cada opción.",
                        "Conversa con estudiantes o profesionales de esas carreras.",
                        "Revisa sedes, costos, becas y el proceso de admisión vigente.",
                        "Comenta tus resultados con un orientador; puedes cambiar de interés."));
    }

    @PostMapping("/{id}/finish")
    @Transactional
    public Object finish(@PathVariable String id) {
        ac.roles("ALUMNO");
        owned(id);
        var v = db.one("SELECT * FROM vocational WHERE id=? FOR UPDATE", id);
        if (s(v, "status").equals("COMPLETO"))
            return calculate(db.parse(s(v, "answers"), Map.class), db.parse(s(v, "preferences"), Map.class));
        var result = calculate(db.parse(s(v, "answers"), Map.class), db.parse(s(v, "preferences"), Map.class));
        db.patch("vocational", id, m("result", db.json(result), "status", "COMPLETO", "updated_at", now()));
        db.audit(ac.uid(), "FINALIZAR_VOCACIONAL", id, m("instrument", v.get("instrument")));
        return result;
    }
}
