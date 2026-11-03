package pe.jauja.academia;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.beans.factory.annotation.Value;
import jakarta.annotation.PreDestroy;
import static pe.jauja.academia.Store.*;

@Service
public class BulletinProcessor {
    final Store db; final FilesService files; final BulletinAi ai; final boolean enabled;
    final ExecutorService worker=Executors.newSingleThreadExecutor(r->{var t=new Thread(r,"bulletin-processor");t.setDaemon(true);return t;});
    final AtomicBoolean busy=new AtomicBoolean();
    public BulletinProcessor(Store db,FilesService files,BulletinAi ai,@Value("${app.bulletin-worker:true}") boolean enabled) {
        this.db=db;this.files=files;this.ai=ai;this.enabled=enabled;
    }
    public void queue(String id,String user) {
        db.patch("bulletins",id,m("processing_status","EN_COLA","processing_error",null,"uploaded_by",user,"processing_until",null));
    }
    @Scheduled(fixedDelay=3000)
    public void poll() {
        if(!enabled || !busy.compareAndSet(false,true)) return;
        worker.submit(()->{try { next(); } finally {busy.set(false);} });
    }
    void next() {
        var job=db.find("SELECT * FROM bulletins WHERE archived=FALSE AND (processing_status='EN_COLA' OR (processing_status IN ('PROCESANDO','ESPERANDO_IA') AND processing_until<?)) ORDER BY created_at LIMIT 1",now());
        if(job==null) return;
        String id=s(job,"id"),owner=id();
        int claimed=db.update("UPDATE bulletins SET processing_status='PROCESANDO',processing_owner=?,processing_until=? WHERE id=? AND archived=FALSE AND (processing_status='EN_COLA' OR (processing_status IN ('PROCESANDO','ESPERANDO_IA') AND processing_until<?))",owner,Instant.now().plusSeconds(900).toString(),id,now());
        if(claimed==0) return;
        try { process(id,owner); }
        catch(JobCancelled e) { return; }
        catch(GeminiBulletinClient.TemporaryFailure e) {
            db.update("UPDATE bulletins SET processing_status='ESPERANDO_IA',processing_error=?,processing_until=? WHERE id=? AND processing_owner=?",e.getMessage(),Instant.now().plusSeconds(e.seconds).toString(),id,owner);
        }
        catch(InterruptedException e) { Thread.currentThread().interrupt(); }
        catch(Exception e) {
            org.slf4j.LoggerFactory.getLogger(getClass()).warn("No se completó balotario {}: {}",id,e.getMessage());
            db.update("UPDATE bulletins SET processing_status='ERROR',processing_error=?,processing_until=NULL WHERE id=? AND processing_owner=?", "No se completó el procesamiento: "+Objects.toString(e.getMessage(),"Reintenta la conversión."),id,owner);
        }
    }
    void process(String id,String owner) throws Exception {
        var bulletin=db.get("bulletins",id);
        try { ai.ready(); }
        catch(GeminiBulletinClient.ConfigurationFailure | GeminiBulletinClient.TemporaryFailure e) { throw e; }
        catch(InterruptedException e) { throw e; }
        catch(Exception e) {
            db.patch("bulletins",id,m("processing_status","ESPERANDO_IA","processing_error",ai.providerName()+": no se pudo conectar con el modelo "+ai.activeModel()+". Se reintentará automáticamente; comprueba conexión y cuota.","processing_until",Instant.now().plusSeconds(60).toString()));
            return;
        }
        var questions=db.list("SELECT * FROM bulletin_questions WHERE bulletin_id=? ORDER BY position",id);
        if(questions.isEmpty()) {
            byte[] pdf=files.read(s(bulletin,"file_key"));
            var textQuestions=PdfQuestions.extract(pdf,files);
            final var extracted=textQuestions.isEmpty()?PdfQuestions.extractVisual(pdf,files,ai,()->heartbeat(id,owner)):textQuestions;
            heartbeat(id,owner);
            check(!extracted.isEmpty(),"No se detectaron preguntas en el PDF.");
            var tm=new org.springframework.transaction.support.TransactionTemplate(transactionManager);
            tm.executeWithoutResult(tx->{
                int position=0;
                for(var q:extracted) db.insert("bulletin_questions",m("bulletin_id",id,"position",position++,"source_number",q.get("source_number"),
                    "prompt","Pregunta "+q.get("source_number"),"options","[]","images",db.json(q.get("images")),"source_text",q.get("source_text")));
                db.patch("bulletins",id,m("question_count",extracted.size(),"processing_error",null));
            });
            questions=db.list("SELECT * FROM bulletin_questions WHERE bulletin_id=? ORDER BY position",id);
        }
        for(var q:questions) {
            if(!s(q,"solution_status").equals("PENDIENTE")) continue;
            var current=db.get("bulletins",id);
            if(b(current,"archived") || !s(current,"processing_owner").equals(owner)) return;
            db.patch("bulletins",id,m("processing_until",Instant.now().plusSeconds(900).toString()));
            try {
                Map<String,Object> first;
                if(!s(q,"first_solution").isBlank()) {
                    first=db.parse(s(q,"first_solution"),Map.class);
                    if(!s(first,"model_used").equals(ai.cloud()?ai.activeModel():BulletinAi.needsVision(q)?ai.model:ai.textModel)) first=null;
                } else first=null;
                if(first==null) {
                    first=ai.solve(q,false);
                    heartbeat(id,owner);
                    db.patch("bulletin_questions",s(q,"id"),m("first_solution",db.json(first)));
                }
                var second=ai.solve(q,true);
                heartbeat(id,owner);
                boolean agreed=BulletinAi.agrees(first,second);
                db.patch("bulletin_questions",s(q,"id"),m("prompt",first.get("prompt"),"options",db.json(first.get("options")),
                    "answer",agreed?String.valueOf(n(first,"answer",-1)):null,"explanation",agreed?first.get("explanation"):
                    "La IA no obtuvo una clave consistente en las dos comprobaciones. Esta pregunta no cuenta para tu nota.",
                    "course_name",s(first,"course_name").substring(0,Math.min(150,s(first,"course_name").length())),
                    "solution_status",agreed?"RESUELTA":"SIN_CLAVE","model",first.getOrDefault("model_used",ai.model)));
            } catch(JobCancelled e) { return;
            } catch(InterruptedException e) { throw e;
            } catch(GeminiBulletinClient.ConfigurationFailure e) { throw e;
            } catch(GeminiBulletinClient.TemporaryFailure e) {
                heartbeat(id,owner);
                db.patch("bulletins",id,m("processing_status","ESPERANDO_IA","processing_error",e.getMessage(),"processing_until",Instant.now().plusSeconds(e.seconds).toString()));
                return;
            } catch(java.net.http.HttpTimeoutException e) {
                heartbeat(id,owner);
                int failures=n(q,"failure_count",0)+1;
                db.patch("bulletin_questions",s(q,"id"),m("failure_count",failures));
                if(failures<2) {
                    db.patch("bulletins",id,m("processing_status","ESPERANDO_IA","processing_error","El proveedor de IA tardó demasiado. Se reintentará la pregunta; si vuelve a exceder el tiempo, se publicará sin calificarla.","processing_until",Instant.now().plusSeconds(60).toString()));
                    return;
                }
                var options=BulletinAi.needsVision(q)?List.of():BulletinAi.originalOptions(s(q,"source_text"));
                db.patch("bulletin_questions",s(q,"id"),m("prompt",s(q,"source_text"),"options",db.json(options),"answer",null,
                    "explanation","La IA excedió el tiempo de resolución en dos intentos. Esta pregunta no cuenta para tu nota.","solution_status","SIN_CLAVE","model",ai.activeModel()));
            } catch(java.io.IOException | IllegalStateException e) {
                db.patch("bulletins",id,m("processing_status","ESPERANDO_IA","processing_error","Se interrumpió la conexión con el proveedor de IA. Se conserva el avance y se reintentará automáticamente.","processing_until",Instant.now().plusSeconds(60).toString()));
                return;
            } catch(Exception e) {
                // Keep the original question and figures available; never invent an answer after a model error.
                var options=BulletinAi.needsVision(q)?List.of():BulletinAi.originalOptions(s(q,"source_text"));
                db.patch("bulletin_questions",s(q,"id"),m("prompt",s(q,"source_text"),"options",db.json(options),
                    "answer",null,"explanation","No se pudo resolver automáticamente esta pregunta. No cuenta para tu nota.","solution_status","SIN_CLAVE","model",ai.activeModel()));
            }
            db.patch("bulletins",id,m("processed_count",n(db.one("SELECT COUNT(*) n FROM bulletin_questions WHERE bulletin_id=? AND solution_status<>'PENDIENTE'",id),"n",0)));
        }
        int unresolved=n(db.one("SELECT COUNT(*) n FROM bulletin_questions WHERE bulletin_id=? AND solution_status<>'RESUELTA'",id),"n",0);
        var current=db.get("bulletins",id);
        if(b(current,"archived") || !s(current,"processing_owner").equals(owner)) return;
        db.patch("bulletins",id,m("processing_status",unresolved==0?"LISTO":"LISTO_CON_OBSERVACIONES","processing_until",null,"processing_error",unresolved==0?null:unresolved+" preguntas sin clave consistente; se excluyen de la nota."));
        if(!s(bulletin,"uploaded_by").isBlank()) db.audit(s(bulletin,"uploaded_by"),"PUBLICAR_PRACTICA_AUTOMATICA",id,m("questions",questions.size(),"unresolved",unresolved,"model",ai.activeModel()));
    }
    @org.springframework.beans.factory.annotation.Autowired
    org.springframework.transaction.PlatformTransactionManager transactionManager;
    void heartbeat(String id,String owner) {
        int changed=db.update("UPDATE bulletins SET processing_until=? WHERE id=? AND processing_owner=? AND archived=FALSE AND processing_status='PROCESANDO'",Instant.now().plusSeconds(900).toString(),id,owner);
        if(changed!=1) throw new JobCancelled();
    }
    static class JobCancelled extends RuntimeException {}
    @PreDestroy public void close() { worker.shutdownNow(); }
}
