package pe.jauja.academia;

import java.util.*;
import java.util.regex.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;
import static pe.jauja.academia.Store.*;

@RestController
@RequestMapping("/api/bulletins")
public class BulletinExams {
    final Store db; final Access ac; final FilesService files; final Knowledge knowledge; final Exams exams;
    public BulletinExams(Store db, Access ac, FilesService files, Knowledge knowledge, Exams exams) {
        this.db=db; this.ac=ac; this.files=files; this.knowledge=knowledge; this.exams=exams;
    }

    static List<Map<String,Object>> parse(String text) {
        var out = new ArrayList<Map<String,Object>>();
        var starts = Pattern.compile("(?m)^\\s*(\\d{1,3})[.)]\\s+").matcher(text);
        var positions = new ArrayList<Integer>(); var ends = new ArrayList<Integer>();
        while (starts.find()) { positions.add(starts.start()); ends.add(starts.end()); }
        for (int i=0; i<positions.size(); i++) {
            String block=text.substring(ends.get(i), i+1<positions.size()?positions.get(i+1):text.length()).strip();
            var options = Pattern.compile("(?m)(?:^|[ \\t]+)([A-Fa-f])[.)]\\s+").matcher(block);
            var labels=new ArrayList<String>(); var opStarts=new ArrayList<Integer>(); var opEnds=new ArrayList<Integer>();
            while(options.find()) { labels.add(options.group(1).toUpperCase()); opStarts.add(options.start()); opEnds.add(options.end()); }
            boolean valid=labels.size()>=2 && labels.size()<=6;
            for(int j=0;j<labels.size();j++) valid &= labels.get(j).equals(String.valueOf((char)('A'+j)));
            String prompt=valid?block.substring(0,opStarts.get(0)).strip():block;
            if(prompt.isBlank()) continue;
            var opts=new ArrayList<String>();
            if(valid) for(int j=0;j<labels.size();j++) opts.add(block.substring(opEnds.get(j), j+1<labels.size()?opStarts.get(j+1):block.length()).strip());
            out.add(m("prompt",prompt,"kind",valid?"MULTIPLE":"DESARROLLO","options",opts,"answer","","explanation","","points",1));
        }
        return out;
    }

    @PostMapping("/{id}/extract")
    public Object extract(@PathVariable String id) throws Exception {
        ac.roles("DIRECTOR");
        var b=db.get("bulletins",id);
        check(!b(b,"archived"),"Balotario archivado");
        check(!s(b,"file_key").isBlank(),"Descarga el PDF del enlace y súbelo como archivo para convertirlo.");
        byte[] data=files.read(s(b,"file_key"));
        check(data.length<=25*1024*1024,"Máximo 25 MB");
        String text=DocumentText.extract("balotario.pdf",data);
        check(!text.isBlank(),"Este PDF es un escaneo sin texto. Aplica OCR y vuelve a subirlo, o pega la transcripción en el editor.");
        check(text.length()<=300000,"Divide el balotario en archivos más pequeños (máximo 300000 caracteres).");
        var questions=parse(text);
        check(questions.size()<=100,"Divide el balotario en exámenes de hasta 100 preguntas.");
        return m("questions",questions,"text",text,"warning","Revisa enunciados, alternativas y figuras con el original. Selecciona las claves correctas: no se inventan respuestas. Si falta contenido, edítalo antes de publicar.");
    }

    @PostMapping("/{id}/exam")
    @Transactional
    public Object publish(@PathVariable String id,@RequestBody Map<String,Object> x) {
        ac.roles("DIRECTOR");
        var b=db.get("bulletins",id);
        check(!b(b,"archived"),"Balotario archivado");
        check(b(x,"reviewed"),"Confirma la revisión del contenido y las claves antes de publicar.");
        var offering=ac.offering(req(x,"offering_id",36),true);
        check(x.get("imported_questions") instanceof List<?>,"Faltan preguntas");
        var imported=(List<Map<String,Object>>)x.get("imported_questions");
        check(!imported.isEmpty() && imported.size()<=100,"Usa de 1 a 100 preguntas");
        var selected=new ArrayList<Map<String,Object>>();
        for(var q:imported) {
            var data=new LinkedHashMap<String,Object>(q);
            data.remove("id"); data.putAll(m("course_id",offering.get("course_id"),"topic",s(b,"title").substring(0,Math.min(150,s(b,"title").length())),"difficulty","Intermedio","practice",false));
            check(s(q,"kind").equals("DESARROLLO") || s(q,"answer").matches("[0-5]"),"Selecciona la clave correcta de cada pregunta objetiva.");
            if(s(q,"kind").equals("DESARROLLO")) check(!s(q,"explanation").isBlank(),"Agrega una rúbrica para las preguntas de desarrollo.");
            check(s(q,"kind").equals("DESARROLLO") || q.get("options") instanceof List<?> opts && opts.stream().allMatch(v->v instanceof String && !((String)v).isBlank()),"Alternativas incompletas");
            var saved=(Map<String,Object>)knowledge.saveQuestion(data);
            var version=db.one("SELECT id FROM question_versions WHERE question_id=? AND version=1",saved.get("id"));
            selected.add(m("version_id",version.get("id"),"points",q.get("points")));
        }
        var payload=new LinkedHashMap<String,Object>(x);
        payload.putAll(m("category","SIMULACRO","university",b.get("university"),"career",b.get("career"),"questions",selected));
        var result=exams.create(payload);
        db.audit(ac.uid(),"CONVERTIR_BALOTARIO",id,result);
        return result;
    }
}
