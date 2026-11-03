package pe.jauja.academia;

import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.*;
import org.springframework.mock.web.MockMultipartFile;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static pe.jauja.academia.Store.*;

@SpringBootTest(properties="spring.datasource.url=jdbc:h2:mem:bulletin_practice;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BulletinPracticeTest {
    @Autowired MockMvc mvc;
    @Autowired Store db;
    @Autowired BulletinProcessor processor;
    @MockitoBean BulletinAi ai;
    String login(String mail,String password) throws Exception {
        return db.json.readTree(mvc.perform(post("/api/auth/login").contentType("application/json").content(db.json(m("email",mail,"password",password))))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("token").asText();
    }
    String id(ResultActions result) throws Exception {return db.json.readTree(result.andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("id").asText();}
    ResultActions call(String token,String method,String path,Object data) throws Exception {
        var request=switch(method){case "POST"->post(path);case "PUT"->put(path);default->get(path);};
        request.header("Authorization","Bearer "+token);
        if(data!=null) request.contentType("application/json").content(db.json(data));
        return mvc.perform(request);
    }
    @Test void directorCanClassifyAndStudentsCanExploreOtherUniversities() throws Exception {
        String director=login("director@academia.local","Director2026!"),student=login("alumno@academia.local","Alumno2026!");
        String bulletin=db.insert("bulletins",m("title","Clasificación de prueba","university","General","career","General","admission_year",2026,"process","Prueba","bulletin_group",Store.id()));
        call(student,"PUT","/api/bulletins/"+bulletin+"/university",m("university","UNI")).andExpect(status().isForbidden());
        call(director,"PUT","/api/bulletins/"+bulletin+"/university",m("university","NO_EXISTE")).andExpect(status().isBadRequest());
        call(director,"PUT","/api/bulletins/"+bulletin+"/university",m("university","UNI")).andExpect(status().isOk());
        assertEquals("UNI",s(db.get("bulletins",bulletin),"university"));
        var rows=db.json.readTree(call(student,"GET","/api/bulletins",null).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertTrue(rows.toString().contains(bulletin));
        assertNotNull(db.find("SELECT id FROM audit WHERE entity_id=? AND action='CLASIFICAR_BALOTARIO'",bulletin));
    }
    byte[] pdf() throws Exception {
        try(var doc=new org.apache.pdfbox.pdmodel.PDDocument();var out=new java.io.ByteArrayOutputStream()) {
            var page=new org.apache.pdfbox.pdmodel.PDPage();doc.addPage(page);
            try(var c=new org.apache.pdfbox.pdmodel.PDPageContentStream(doc,page)) {
                c.beginText();c.setFont(new org.apache.pdfbox.pdmodel.font.PDType1Font(org.apache.pdfbox.pdmodel.font.Standard14Fonts.FontName.HELVETICA),12);
                c.newLineAtOffset(40,700);c.showText("1. Suma 2 + 2");c.newLineAtOffset(0,-20);c.showText("A) 3 B) 4 C) 5");
                c.newLineAtOffset(0,-100);c.showText("2. Pregunta sin clave consistente");c.newLineAtOffset(0,-20);c.showText("A) Uno B) Dos C) Tres");c.endText();
            }
            doc.save(out);return out.toByteArray();
        }
    }
    @Test
    void uploadProcessesAndPublishesWithoutScheduleThenGradesWithFeedback() throws Exception {
        String director=login("director@academia.local","Director2026!"), student=login("alumno@academia.local","Alumno2026!"),other=login("alumno2@academia.local","Alumno2026!");
        when(ai.solve(anyMap(),anyBoolean())).thenAnswer(invocation->{
            Map<String,Object> q=invocation.getArgument(0);boolean second=invocation.getArgument(1);
            return m("prompt","Pregunta de prueba","options",List.of("3","4","5"),"answer",n(q,"source_number",0)==1?1:second?2:0,"explanation","2 + 2 = 4 porque se suman dos unidades a otras dos.","solvable",true,"course_name","Matemática");
        });
        var file=new MockMultipartFile("file","practica.pdf","application/pdf",pdf());
        String bulletin=id(mvc.perform(multipart("/api/bulletins").file(file).header("Authorization","Bearer "+director).param("title","Práctica automática")
            .param("university","General").param("career","General").param("admission_year","2026").param("process","Práctica libre")));
        assertEquals("EN_COLA",s(db.get("bulletins",bulletin),"processing_status"));
        processor.next();
        assertEquals("LISTO_CON_OBSERVACIONES",s(db.get("bulletins",bulletin),"processing_status"));
        assertEquals(2,n(db.get("bulletins",bulletin),"processed_count",0));
        verify(ai,times(4)).solve(anyMap(),anyBoolean());
        String attempt=id(call(student,"POST","/api/bulletins/"+bulletin+"/practice/start",null));
        assertEquals(attempt,id(call(student,"POST","/api/bulletins/"+bulletin+"/practice/start",null)));
        call(other,"GET","/api/bulletin-attempts/"+attempt,null).andExpect(status().isForbidden());
        call(student,"GET","/api/bulletin-attempts/"+attempt,null).andExpect(status().isOk())
            .andExpect(jsonPath("$.questions[0].first_solution").doesNotExist()).andExpect(jsonPath("$.questions[0].answer").doesNotExist()).andExpect(jsonPath("$.questions[0].explanation").doesNotExist());
        var qs=db.list("SELECT * FROM bulletin_questions WHERE bulletin_id=? ORDER BY position",bulletin);
        call(student,"PUT","/api/bulletin-attempts/"+attempt+"/answers",m("answers",m(s(qs.get(0),"id"),"1"))).andExpect(status().isOk());
        call(student,"POST","/api/bulletin-attempts/"+attempt+"/submit",null).andExpect(status().isOk())
            .andExpect(jsonPath("$.attempt.grade").value(20.0)).andExpect(jsonPath("$.attempt.graded_count").value(1))
            .andExpect(jsonPath("$.questions[0].explanation").value(org.hamcrest.Matchers.containsString("2 + 2")))
            .andExpect(jsonPath("$.questions[1].result").value("NO_CALIFICADA"));
        call(student,"PUT","/api/bulletin-attempts/"+attempt+"/answers",m("answers",m())).andExpect(status().isBadRequest());
        call(student,"POST","/api/bulletin-attempts/"+attempt+"/submit",null).andExpect(status().isOk()).andExpect(jsonPath("$.attempt.grade").value(20.0));
        call(student,"GET","/api/bulletin-questions/"+qs.get(0).get("id")+"/images/0",null).andExpect(status().isOk()).andExpect(content().contentType("image/png"));
        call(other,"GET","/api/bulletin-questions/"+qs.get(0).get("id")+"/images/0",null).andExpect(status().isBadRequest());
        call(student,"POST","/api/bulletins/"+bulletin+"/process",null).andExpect(status().isForbidden());
    }

    @Test
    void resumesSecondPassAfterQuotaWithoutRepeatingFirstSolution() throws Exception {
        String director=login("director@academia.local","Director2026!");
        when(ai.cloud()).thenReturn(true);when(ai.activeModel()).thenReturn("test-gemini");
        var attempts=new java.util.concurrent.atomic.AtomicInteger();
        when(ai.solve(anyMap(),anyBoolean())).thenAnswer(invocation->{
            boolean second=invocation.getArgument(1);
            if(second && attempts.getAndIncrement()==0) throw new GeminiBulletinClient.TemporaryFailure("Cuota temporal",60);
            return m("prompt","Suma 2 + 2","options",List.of("3","4","5"),"answer",1,"explanation","2+2=4", "solvable",true,"course_name","Matemática","model_used","test-gemini");
        });
        String bulletin=id(mvc.perform(multipart("/api/bulletins").file(new MockMultipartFile("file","cuota.pdf","application/pdf",pdf()))
            .header("Authorization","Bearer "+director).param("title","Retomar por cuota").param("university","General").param("career","General").param("admission_year","2026").param("process","Práctica libre")));
        processor.next();
        assertEquals("ESPERANDO_IA",s(db.get("bulletins",bulletin),"processing_status"));
        var q=db.list("SELECT * FROM bulletin_questions WHERE bulletin_id=? ORDER BY position",bulletin).get(0);
        assertFalse(s(q,"first_solution").isBlank());assertEquals("PENDIENTE",s(q,"solution_status"));
        db.patch("bulletins",bulletin,m("processing_until",java.time.Instant.now().minusSeconds(60).toString()));
        processor.next();
        assertEquals("LISTO",s(db.get("bulletins",bulletin),"processing_status"));
        verify(ai,times(2)).solve(anyMap(),eq(false));
        verify(ai,times(3)).solve(anyMap(),eq(true));
    }

    @Test
    void identicalIndexWithChangedOptionsIsNotAValidKey() {
        assertFalse(BulletinAi.agrees(m("answer",1,"solvable",true,"options",List.of("3","4")),m("answer",1,"solvable",true,"options",List.of("4","3"))));
    }
    @Test
    void repeatedTimeoutDoesNotBlockTheWholeBulletin() throws Exception {
        String director=login("director@academia.local","Director2026!");
        when(ai.solve(anyMap(),anyBoolean())).thenThrow(new java.net.http.HttpTimeoutException("timeout"));
        String bulletin=id(mvc.perform(multipart("/api/bulletins").file(new MockMultipartFile("file","lento.pdf","application/pdf",pdf()))
            .header("Authorization","Bearer "+director).param("title","Práctica lenta").param("university","General").param("career","General").param("admission_year","2026").param("process","Práctica libre")));
        for(int i=0;i<3;i++) {
            processor.next();
            if(!s(db.get("bulletins",bulletin),"processing_status").startsWith("LISTO"))
                db.patch("bulletins",bulletin,m("processing_until",java.time.Instant.now().minusSeconds(60).toString()));
        }
        assertEquals("LISTO_CON_OBSERVACIONES",s(db.get("bulletins",bulletin),"processing_status"));
        assertEquals(2,n(db.get("bulletins",bulletin),"processed_count",0));
        assertTrue(db.list("SELECT * FROM bulletin_questions WHERE bulletin_id=?",bulletin).stream().allMatch(q->s(q,"solution_status").equals("SIN_CLAVE") && n(q,"failure_count",0)==2));
    }
    @Test
    void textQuestionsDoNotLoadTheVisionModelUnnecessarily() {
        assertFalse(BulletinAi.needsVision(m("source_text","Suma 2 + 2. A) 3 B) 4 C) 5 D) 6 E) 7")));
        assertTrue(BulletinAi.needsVision(m("source_text","Cuenta los triángulos de la figura. A) 3 B) 4 C) 5 D) 6 E) 7")));
    }
    @Test
    void preservesOriginalTextAlternativesAndRejectsBrokenOrder() {
        assertEquals(List.of("274","278","280","288","311"),BulletinAi.originalOptions("Un enunciado. A) 274 B) 278 C) 280 D) 288 E) 311\n"));
        assertTrue(BulletinAi.originalOptions("A) uno C) tres B) dos").isEmpty());
    }
}
