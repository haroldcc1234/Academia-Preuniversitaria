package pe.jauja.academia;

import java.util.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import static pe.jauja.academia.Store.*;

@Component
@Order(2)
public class UniversityCatalog implements CommandLineRunner {
    final Store db;
    public UniversityCatalog(Store db){this.db=db;}
    @Override @Transactional
    public void run(String... args) throws Exception {
        if(db.find("SELECT id FROM data_revisions WHERE id='universities-peru-20261026'")!=null)return;
        List<Map<String,Object>> universities=db.json.readValue(new ClassPathResource("universities-peru.json").getInputStream(),List.class);
        for(var u:universities){
            var row=new LinkedHashMap<>(u);row.put("programs",db.json(u.get("programs")));
            var existing=db.find("SELECT id FROM universities WHERE acronym=? OR name=?",u.get("acronym"),u.get("name"));
            if(existing==null)db.insert("universities",row);else db.patch("universities",s(existing,"id"),row);
        }
        db.insert("data_revisions",m("id","universities-peru-20261026","details","Catálogo SUNEDU: 105 instituciones de la tabla universitaria; programas contrastados en 14 fuentes oficiales."));
    }

    public List<Map<String,Object>> recommend(List<Map<String,Object>> careers,Map<String,Object> preferences){
        var candidates=new ArrayList<Map<String,Object>>();
        String region=s(preferences,"location");
        for(var u:db.list("SELECT * FROM universities WHERE verified_on IS NOT NULL AND programs IS NOT NULL")){
            Map<String,String> programs=db.parse(s(u,"programs"),Map.class);
            List<Map<String,Object>> matches=new ArrayList<>();
            for(var c:careers) if(programs.containsKey(s(c,"name"))) matches.add(c);
            if(matches.isEmpty())continue;
            var best=matches.get(0);
            boolean local=!region.isBlank() && !region.equals("Todo el Perú") && region.equals(s(u,"region"));
            var row=new LinkedHashMap<>(u);
            row.put("matched_career",s(best,"name"));row.put("program_name",programs.get(s(best,"name")));
            row.put("affinity",best.get("affinity"));
            row.put("selection_score",num(best,"affinity")+(local?5:0));
            row.put("recommendation_reason","Ofrece "+programs.get(s(best,"name"))+", relacionada con tu afinidad de "+best.get("affinity")+"/100 por "+s(best,"name")+". "+(local?"Su ubicación principal coincide con tu región de interés. ":"Ubicación principal: "+s(u,"region")+". ")+"Confirma la sede que ofrece este programa.");
            candidates.add(row);
        }
        candidates.sort(Comparator.comparingDouble((Map<String,Object> u)->num(u,"selection_score")).reversed().thenComparing(u->s(u,"name")));
        List<Map<String,Object>> out=new ArrayList<>();
        for(boolean pub:List.of(true,false)) candidates.stream().filter(u->b(u,"public_university")==pub).limit(4).forEach(out::add);
        return out;
    }
}
