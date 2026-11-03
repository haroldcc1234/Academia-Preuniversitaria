package pe.jauja.academia;

import java.util.*;
import org.springframework.stereotype.Service;
import static pe.jauja.academia.Store.*;

/** Transparent exploratory rules, not an aptitude test or an admission prediction. */
@Service
public class AcademicCareers {
    static final String NOTICE = "Orientación basada en tus notas publicadas, no una prueba de aptitud ni una garantía de ingreso. Tus notas pueden mejorar: combina estas opciones con tus intereses y tu test vocacional.";
    record Profile(String name, Map<String, Double> weights, String activity) {}
    static Profile p(String name, String a, double wa, String b, double wb, String c, double wc, String activity) {
        return new Profile(name, Map.of(a,wa,b,wb,c,wc), activity);
    }
    static final List<Profile> PROFILES = List.of(
        p("Ingeniería de Sistemas","mat",.6,"fis",.25,"com",.15,"Prueba crear un programa sencillo y explicar cómo funciona."),
        p("Ingeniería Civil","mat",.45,"fis",.45,"com",.1,"Explora cómo se calculan cargas y medidas en una construcción."),
        p("Ingeniería Mecánica","mat",.35,"fis",.55,"qui",.1,"Investiga el funcionamiento de un mecanismo cotidiano."),
        p("Ingeniería Industrial","mat",.5,"fis",.2,"com",.3,"Propón mejoras para organizar un proceso de tu academia."),
        p("Arquitectura","mat",.4,"fis",.25,"com",.35,"Dibuja un espacio y explica tus decisiones de diseño."),
        p("Medicina Humana","bio",.5,"qui",.35,"com",.15,"Explora una introducción a anatomía y conversa con un estudiante de salud."),
        p("Enfermería","bio",.45,"qui",.2,"com",.35,"Investiga actividades de promoción de la salud y cuidado de personas."),
        p("Educación","com",.5,"soc",.3,"mat",.2,"Prepara una explicación de un tema para un compañero."),
        p("Administración","mat",.35,"com",.4,"soc",.25,"Diseña un pequeño proyecto con objetivos y presupuesto."),
        p("Contabilidad","mat",.65,"com",.25,"soc",.1,"Organiza ingresos y gastos de un caso ficticio."),
        p("Economía","mat",.55,"soc",.35,"com",.1,"Compara datos de precios y explica posibles cambios."),
        p("Ciencias de la Comunicación","com",.7,"soc",.2,"mat",.1,"Escribe una noticia y verifica sus fuentes."),
        p("Trabajo Social","soc",.55,"com",.35,"bio",.1,"Investiga una necesidad de tu comunidad y cómo atenderla."),
        p("Agronomía","bio",.5,"qui",.3,"mat",.2,"Compara condiciones de crecimiento de dos plantas."),
        p("Ingeniería Ambiental","qui",.4,"bio",.35,"mat",.25,"Analiza un problema ambiental de tu localidad."),
        p("Derecho","com",.5,"soc",.45,"mat",.05,"Compara argumentos de ambas partes de un caso ficticio.")
    );
    static final Map<String,String> LABELS = Map.of("mat","Matemática", "fis","Física", "qui","Química", "bio","Biología", "com","Comunicación", "soc","Historia y ciencias sociales");

    static String area(String name) {
        String n = AiController.norm(name);
        if(n.matches(".*(matemat|aritmet|algebra|geometr|trigonom|razonamiento matem|razonamiento logico).*")) return "mat";
        if(n.contains("fisica")) return "fis";
        if(n.contains("quimica")) return "qui";
        if(n.contains("biologia")) return "bio";
        if(n.matches(".*(comunic|lengua|literatura|razonamiento verbal|comprension lectora).*")) return "com";
        if(n.matches(".*(historia|geografia|civica|ciencias sociales|economia|filosofia).*")) return "soc";
        return ""; // General 'Ciencias' must not invent a biology or physics grade.
    }

    public Map<String,Object> calculate(List<Map<String,Object>> rows) {
        Map<String,List<Map<String,Object>>> grouped = new TreeMap<>();
        List<String> unmapped = new ArrayList<>();
        // A repeated course across cycles contributes once, using its latest published assessment.
        Map<String,Map<String,Object>> latest = new TreeMap<>();
        for(var r:rows) {
            if(r.get("grade")==null || !Double.isFinite(num(r,"grade")) || num(r,"grade")<0 || num(r,"grade")>20) continue;
            String key=AiController.norm(s(r,"course_name"));
            var old=latest.get(key);
            if(old==null || lastDate(r).compareTo(lastDate(old))>0) latest.put(key,r);
        }
        for(var r:latest.values()) {
            String key=area(s(r,"course_name"));
            if(key.isEmpty()) { unmapped.add(s(r,"course_name")); continue; }
            grouped.computeIfAbsent(key,k->new ArrayList<>()).add(r);
        }
        List<Map<String,Object>> careers=new ArrayList<>();
        if(grouped.size()>=2) for(var p:PROFILES) {
            double sum=0, covered=0; int areas=0;
            List<Map<String,Object>> evidence=new ArrayList<>();
            List<String> missing=new ArrayList<>(), reinforce=new ArrayList<>();
            for(String key:new TreeSet<>(p.weights.keySet())) {
                var courses=grouped.get(key);
                if(courses==null) {missing.add(LABELS.get(key));continue;}
                areas++;
                double avg=courses.stream().mapToDouble(r->num(r,"grade")).average().orElseThrow();
                covered+=p.weights.get(key); sum+=avg*p.weights.get(key);
                for(var r:courses) {
                    evidence.add(m("course",r.get("course_name"),"grade",r.get("grade"),"complete",b(r,"complete"),"cycle",r.get("cycle_name"),"section",r.get("section_name")));
                    if(num(r,"grade")<13) reinforce.add(s(r,"course_name"));
                }
            }
            if(areas<2 || covered<.6) continue;
            double score=Math.round(sum/covered*100)/100.0;
            evidence.sort(Comparator.comparingDouble((Map<String,Object> r)->num(r,"grade")).reversed());
            String reason="Tus cursos relacionados: " + evidence.stream().map(r->s(r,"course")+" ("+r.get("grade")+"/20"+(b(r,"complete")?"":", provisional")+")").reduce((a,b)->a+", "+b).orElse("")+".";
            careers.add(m("name",p.name,"score",score,"coverage",Math.round(covered*100),"evidence",evidence,"missing",missing,"reinforce",reinforce,"reason",reason,"activity",p.activity));
        }
        careers.sort(Comparator.comparingDouble((Map<String,Object> r)->num(r,"score")).reversed().thenComparing(Comparator.comparingDouble((Map<String,Object> r)->num(r,"coverage")).reversed()).thenComparing(r->s(r,"name")));
        boolean tied=careers.size()>1 && num(careers.get(0),"score")==num(careers.get(1),"score");
        return m("careers",careers.stream().limit(4).toList(),"notice",NOTICE,"ties",tied,"graded_courses",latest.size(),"unmapped_courses",unmapped,
            "message",careers.isEmpty()?"Aún faltan notas en áreas relacionadas para recomendar carreras. Necesitamos al menos dos áreas y cubrir el 60% de los pesos de una opción.":tied?"Hay opciones empatadas; el rendimiento no permite priorizar una sola carrera.":"Opciones para explorar según tus promedios actuales.",
            "method","Promedio orientativo de 0 a 20: media por área y pesos específicos de cada carrera, normalizados entre las áreas con notas. No es porcentaje de afinidad. Se usa el registro con la evaluación publicada más reciente de cada curso. Las áreas ausentes no cuentan como cero; una cobertura parcial limita la comparación.");
    }
    static String lastDate(Map<String,Object> r) {
        var entries=(List<Map<String,Object>>)r.getOrDefault("entries",List.of());
        return entries.stream().map(e->s(e,"created_at")).max(String::compareTo).orElse("");
    }
    public String explain(Map<String,Object> result) {
        StringBuilder out=new StringBuilder(s(result,"message")).append("\n");
        for(var c:(List<Map<String,Object>>)result.get("careers")) {
            out.append("\n").append(s(c,"name")).append(" — promedio orientativo ").append(c.get("score")).append("/20; cobertura ").append(c.get("coverage")).append("%.\n").append(s(c,"reason")).append("\n");
            var missing=(List<String>)c.get("missing"); var reinforce=(List<String>)c.get("reinforce");
            if(!missing.isEmpty())out.append("Faltan notas de: ").append(String.join(", ",missing)).append(".\n");
            if(!reinforce.isEmpty())out.append("Refuerza: ").append(String.join(", ",reinforce)).append(". Una nota baja no descarta esta carrera.\n");
            out.append(s(c,"activity")).append("\n");
        }
        return out.append("\n").append(NOTICE).toString();
    }
}
