package pe.jauja.academia;

import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import static org.junit.jupiter.api.Assertions.*;
import static pe.jauja.academia.Store.*;

@EnabledIfSystemProperty(named="liveOllama",matches="true")
class BulletinAiLiveTest {
    @Test
    void solvesRealSampleWithLocalVisionModel() throws Exception {
        var db=new Store(new org.springframework.jdbc.core.JdbcTemplate(),new com.fasterxml.jackson.databind.ObjectMapper());
        var files=new FilesService("local","target/live-pdf-files","","","","");
        var ai=new BulletinAi(db,files,"http://localhost:11434",System.getProperty("liveModel","qwen3-vl:2b"));
        ai.textModel=System.getProperty("liveTextModel","qwen2.5:3b");
        ai.ready();
        var questions=PdfQuestions.extract(Files.readAllBytes(Path.of(System.getProperty("samplePdf"))),files);
        assertEquals(100,questions.size());
        List<Map<String,Object>> results=new ArrayList<>();
        Files.writeString(Path.of("target/live-pdf-results.json"),"[]");
        for(int index:List.of(1,2)) {
            var q=questions.get(index);q.put("images",db.json(q.get("images")));
            var record=m("number",q.get("source_number"),"model",ai.model,"text_model",ai.textModel);
            results.add(record);
            var first=ai.solve(q,false);record.put("first",first);
            Files.writeString(Path.of("target/live-pdf-results.json"),db.json(results));
            var second=ai.solve(q,true);
            boolean agreed=BulletinAi.agrees(first,second);
            record.put("second",second);record.put("agreed",agreed);
            Files.writeString(Path.of("target/live-pdf-results.json"),db.json(results));
            if(index==1) { assertTrue(agreed);assertEquals(3,n(first,"answer",-1)); }
            if(index==2 && agreed) assertEquals(2,n(first,"answer",-1),"La figura contiene 20 triángulos; una clave coincidente incorrecta no es válida.");
        }
    }
}

