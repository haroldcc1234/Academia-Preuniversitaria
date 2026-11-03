package pe.jauja.academia;

import java.util.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.*;
import static pe.jauja.academia.Store.*;

@RestController
@RequestMapping("/api")
public class BulletinPractice {
    final Store db; final Access ac; final FilesService files; final Knowledge knowledge; final BulletinProcessor processor;
    public BulletinPractice(Store db,Access ac,FilesService files,Knowledge knowledge,BulletinProcessor processor) {
        this.db=db;this.ac=ac;this.files=files;this.knowledge=knowledge;this.processor=processor;
    }
    @GetMapping("/bulletins/ai/status")
    public Object aiStatus() {
        ac.roles("DIRECTOR");
        var ai=processor.ai;
        return m("provider",ai.providerName(),"model",ai.activeModel(),"configured",!ai.cloud() || !ai.gemini.key.isBlank(),
            "message",ai.cloud()?"Gemini procesa el contenido del balotario. Las notas y datos del alumno permanecen en el sistema.":"Procesamiento local con Ollama. Puedes activar Gemini con scripts/configurar-gemini.ps1.");
    }
    @PostMapping("/bulletins/ai/check")
    public Object checkAi() {
        ac.roles("DIRECTOR");
        try {if(processor.ai.cloud())processor.ai.gemini.probe();else processor.ai.ready();return m("ready",true,"message",processor.ai.cloud()?"Gemini generó una respuesta de prueba correctamente. El procesamiento queda sujeto a la cuota disponible.":"Modelo local disponible. La velocidad se comprobará al procesar el PDF.");}
        catch(GeminiBulletinClient.ConfigurationFailure | GeminiBulletinClient.TemporaryFailure e) {return m("ready",false,"message",e.getMessage());}
        catch(Exception e) {return m("ready",false,"message","No se pudo conectar con el proveedor. Comprueba conexión, modelo y configuración.");}
    }
    Map<String,Object> accessible(String id) {
        var b=knowledge.bulletins().stream().filter(r->s(r,"id").equals(id)).findFirst().orElse(null);
        if(b==null) deny(); return b;
    }
    @PostMapping("/bulletins/{id}/process")
    @Transactional
    public Object process(@PathVariable String id) {
        ac.roles("DIRECTOR");
        var b=db.one("SELECT * FROM bulletins WHERE id=? FOR UPDATE",id);
        check(!b(b,"archived") && !s(b,"file_key").isBlank(),"Adjunta un PDF disponible.");
        check(List.of("ARCHIVO","ERROR","ESPERANDO_IA").contains(s(b,"processing_status")),"El balotario ya se está procesando o está publicado.");
        processor.queue(id,ac.uid()); return m("queued",true);
    }
    @GetMapping("/bulletins/{id}/practice")
    public Object summary(@PathVariable String id) {
        var b=accessible(id);
        var attempts=ac.student()?db.list("SELECT id,status,created_at,submitted_at,grade,correct_count,graded_count FROM bulletin_attempts WHERE bulletin_id=? AND student_id=? ORDER BY created_at DESC",id,ac.uid()):List.of();
        return m("bulletin",b,"attempts",attempts);
    }
    @PostMapping("/bulletins/{id}/practice/start")
    @Transactional
    public Object start(@PathVariable String id) {
        ac.roles("ALUMNO"); accessible(id);
        var b=db.one("SELECT * FROM bulletins WHERE id=? FOR UPDATE",id);
        check(List.of("LISTO","LISTO_CON_OBSERVACIONES").contains(s(b,"processing_status")),"El balotario todavía se está preparando.");
        var active=db.find("SELECT id FROM bulletin_attempts WHERE bulletin_id=? AND student_id=? AND status='EN_CURSO'",id,ac.uid());
        if(active!=null) return active;
        var qs=db.list("SELECT * FROM bulletin_questions WHERE bulletin_id=? ORDER BY position",id);
        check(!qs.isEmpty(),"Este balotario no contiene preguntas.");
        qs.forEach(q->q.remove("first_solution"));
        return m("id",db.insert("bulletin_attempts",m("bulletin_id",id,"student_id",ac.uid(),"questions",db.json(qs),"answers","{}")));
    }
    Map<String,Object> own(String id,boolean lock) {
        ac.roles("ALUMNO");
        var a=db.one("SELECT * FROM bulletin_attempts WHERE id=?"+(lock?" FOR UPDATE":""),id);
        if(!s(a,"student_id").equals(ac.uid())) deny();
        return a;
    }
    @GetMapping("/bulletin-attempts/{id}")
    public Object attempt(@PathVariable String id) {
        var a=own(id,false);
        boolean done=s(a,"status").equals("COMPLETO");
        List<Map<String,Object>> qs=db.parse(s(a,"questions"),List.class);
        var answers=db.parse(s(a,"answers"),Map.class);
        for(var q:qs) {
            q.put("options",db.parse(s(q,"options"),List.class));
            List<String> keys=db.parse(s(q,"images"),List.class);
            q.put("image_count",keys.size());q.remove("images");q.remove("source_text");q.remove("first_solution");
            if(!done) { q.remove("answer");q.remove("explanation"); }
            else {
                String response=s(answers,s(q,"id"));
                q.put("result",!s(q,"solution_status").equals("RESUELTA")?"NO_CALIFICADA":response.isBlank()?"SIN_RESPONDER":response.equals(s(q,"answer"))?"CORRECTA":"INCORRECTA");
            }
        }
        a.remove("questions"); a.put("answers",answers);
        return m("attempt",a,"questions",qs,"title",db.get("bulletins",s(a,"bulletin_id")).get("title"));
    }
    @PutMapping("/bulletin-attempts/{id}/answers")
    @Transactional
    public Object save(@PathVariable String id,@RequestBody Map<String,Object> x) {
        var a=own(id,true);check(s(a,"status").equals("EN_CURSO"),"Práctica finalizada.");
        check(x.get("answers") instanceof Map<?,?>,"Respuestas inválidas.");
        Map<String,Object> answers=(Map<String,Object>)x.get("answers");
        List<Map<String,Object>> qs=db.parse(s(a,"questions"),List.class);
        check(answers.size()<=qs.size(),"Respuestas ajenas a la práctica.");
        for(var entry:answers.entrySet()) {
            var q=qs.stream().filter(r->s(r,"id").equals(entry.getKey())).findFirst().orElse(null);
            check(q!=null,"Pregunta ajena a la práctica.");
            List<?> opts=db.parse(s(q,"options"),List.class);
            check(entry.getValue() instanceof String && entry.getValue().toString().matches("[0-5]") && Integer.parseInt(entry.getValue().toString())<opts.size(),"Alternativa inválida.");
        }
        db.patch("bulletin_attempts",id,m("answers",db.json(answers)));return m("saved",true);
    }
    @PostMapping("/bulletin-attempts/{id}/submit")
    @Transactional
    public Object submit(@PathVariable String id) {
        var a=own(id,true);
        if(!s(a,"status").equals("EN_CURSO")) return attempt(id);
        List<Map<String,Object>> qs=db.parse(s(a,"questions"),List.class);
        Map<String,Object> answers=db.parse(s(a,"answers"),Map.class);
        int scored=0,correct=0;
        for(var q:qs) if(s(q,"solution_status").equals("RESUELTA")) {
            scored++;if(s(q,"answer").equals(s(answers,s(q,"id")))) correct++;
        }
        db.patch("bulletin_attempts",id,m("status","COMPLETO","submitted_at",now(),"correct_count",correct,"graded_count",scored,"grade",scored==0?null:Math.round(2000.0*correct/scored)/100.0));
        return attempt(id);
    }
    @GetMapping("/bulletin-questions/{id}/images/{index}")
    public ResponseEntity<byte[]> image(@PathVariable String id,@PathVariable int index) throws Exception {
        var q=db.get("bulletin_questions",id);
        accessible(s(q,"bulletin_id"));
        if(ac.student()) check(db.find("SELECT id FROM bulletin_attempts WHERE bulletin_id=? AND student_id=?",q.get("bulletin_id"),ac.uid())!=null,"Inicia la práctica para ver la pregunta.");
        List<String> keys=db.parse(s(q,"images"),List.class);
        check(index>=0 && index<keys.size(),"Imagen inválida.");
        return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).cacheControl(CacheControl.noStore()).body(files.read(keys.get(index)));
    }
}
