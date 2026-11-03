package pe.jauja.academia;

import java.util.*;
import java.time.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.*;
import org.springframework.mock.web.MockMultipartFile;
import com.fasterxml.jackson.databind.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static pe.jauja.academia.Store.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AcademiaTest {
    @Autowired
    MockMvc mvc;
    @Autowired
    Store db;
    @Autowired
    Vocational vocational;
    @Autowired
    Metrics metrics;
    @Autowired
    ExamEngine engine;
    String director, teacher, student, other, studentId, offering, otherOffering, course;

    String login(String mail, String pass) throws Exception {
        return tree(mvc.perform(post("/api/auth/login").contentType("application/json")
                .content(db.json(m("email", mail, "password", pass)))).andExpect(status().isOk()).andReturn())
                .get("token").asText();
    }

    JsonNode tree(MvcResult r) throws Exception {
        return db.json.readTree(r.getResponse().getContentAsString());
    }

    ResultActions call(String token, String method, String path, Object body) throws Exception {
        var b = switch (method) {
            case "POST" -> post(path);
            case "PUT" -> put(path);
            case "DELETE" -> delete(path);
            default -> get(path);
        };
        b.header("Authorization", "Bearer " + token);
        if (body != null)
            b.contentType("application/json").content(db.json(body));
        return mvc.perform(b);
    }

    @BeforeAll
    void setup() throws Exception {
        director = login("director@academia.local", "Director2026!");
        teacher = login("docente@academia.local", "Docente2026!");
        student = login("alumno@academia.local", "Alumno2026!");
        other = login("alumno2@academia.local", "Alumno2026!");
        studentId = s(db.one("SELECT id FROM users WHERE email='alumno@academia.local'"), "id");
        var o = db.one(
                "SELECT o.* FROM offerings o JOIN courses c ON c.id=o.course_id JOIN users u ON u.id=o.teacher_id WHERE c.name='Matemática' AND u.email='docente@academia.local'");
        offering = s(o, "id");
        course = s(o, "course_id");
        otherOffering = s(
                db.one("SELECT o.id FROM offerings o JOIN sections s ON s.id=o.section_id WHERE s.name='B · Tarde'"),
                "id");
    }

    @Test
    @Order(2)
    void roleBoundariesAreEnforcedByApi() throws Exception {
        call(director,"GET","/api/bulletins/ai/status",null).andExpect(status().isOk()).andExpect(jsonPath("$.key").doesNotExist());
        call(student,"GET","/api/bulletins/ai/status",null).andExpect(status().isForbidden());
        call(student,"POST","/api/bulletins/ai/check",null).andExpect(status().isForbidden());

        for (String path : List.of("/questions", "/exams", "/materials", "/ai/history", "/grading"))
            call(director,"GET","/api"+path,null).andExpect(status().isForbidden());
        for (String path : List.of("/bulletins", "/reports", "/reports/cards", "/ai/history", "/challenges"))
            call(teacher,"GET","/api"+path,null).andExpect(status().isForbidden());
        call(student,"GET","/api/questions",null).andExpect(status().isForbidden());
        call(student,"GET","/api/offerings/"+offering+"/students",null).andExpect(status().isForbidden());
        call(teacher,"GET","/api/offerings/"+offering+"/students",null).andExpect(status().isOk());
        call(teacher,"GET","/api/questions",null).andExpect(status().isOk());
        call(teacher,"GET","/api/grading",null).andExpect(status().isOk());
        call(director,"POST","/api/grades",m()).andExpect(status().isForbidden());
        call(teacher,"PUT","/api/auth/profile",m("career","Ingeniería")).andExpect(status().isForbidden());
    }

    @Test
    @Order(1)
    void authAndRbac() throws Exception {
        mvc.perform(get("/api/offerings")).andExpect(status().isUnauthorized());
        call(student, "GET", "/api/users", null).andExpect(status().isForbidden());
        call(teacher, "GET", "/api/users", null).andExpect(status().isForbidden());
        call(teacher, "GET", "/api/offerings/" + otherOffering + "/students", null).andExpect(status().isForbidden());
        call(student, "GET", "/api/offerings/" + otherOffering + "/students", null).andExpect(status().isForbidden());
        call(director, "GET", "/api/users", null).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].password").doesNotExist());
    }

    @Test
    @Order(2)
    void pagesReadWithoutServerErrors() throws Exception {
        for (String path : List.of("/catalog", "/offerings", "/sessions", "/attendance", "/grades", "/dashboard",
                "/notifications", "/materials", "/practice/questions", "/resolutions", "/bulletins", "/exams",
                "/vocational/instrument", "/vocational", "/challenges", "/challenges/ranking/all", "/ai/history",
                "/ai/recommendations"))
            call(student, "GET", "/api" + path, null).andExpect(status().isOk());
        for (String path : List.of("/audit", "/reports", "/reports?type=attendance",
                "/reports?type=teachers", "/reports?type=performance", "/reports/cards"))
            call(director, "GET", "/api" + path, null).andExpect(status().isOk());
    }

    @Test
    @Order(3)
    void cycleAlways16Weeks() throws Exception {
        var r = tree(call(director, "POST", "/api/cycles", m("name", "Ciclo de prueba", "start_date", "2027-01-04"))
                .andExpect(status().isOk()).andReturn());
        var c = db.get("cycles", r.get("id").asText());
        assertEquals("2027-04-25", c.get("end_date"));
    }

    @Test
    @Order(4)
    void gradeValidationAndCorrection() throws Exception {
        call(teacher, "POST", "/api/grades", m("offering_id", offering, "student_id", studentId, "title",
                "Prueba inválida", "category", "PARCIAL", "grade", 21)).andExpect(status().isBadRequest());
        call(student, "POST", "/api/grades", m("offering_id", offering, "student_id", studentId, "title", "Inyección",
                "category", "PARCIAL", "grade", 20)).andExpect(status().isForbidden());
        var r = tree(call(teacher, "POST", "/api/grades", m("offering_id", offering, "student_id", studentId, "title",
                "Simulacro", "category", "SIMULACRO", "grade", 16)).andExpect(status().isOk()).andReturn());
        call(teacher, "PUT", "/api/grades/" + r.get("id").asText(), m("grade", 17, "reason", "Revisión de rúbrica"))
                .andExpect(status().isOk());
        assertTrue(metrics.grades("o.id='" + offering + "'", studentId).get(0).get("complete").equals(true));
    }

    @Test
    @Order(5)
    void vocationalValidationScoresAndIsolation() throws Exception {
        String id = tree(call(student, "POST", "/api/vocational", m()).andExpect(status().isOk()).andReturn()).get("id")
                .asText();
        call(other, "GET", "/api/vocational/" + id, null).andExpect(status().isForbidden());
        call(student, "POST", "/api/vocational/" + id + "/finish", m()).andExpect(status().isBadRequest());
        Map<String, Object> a = new LinkedHashMap<>();
        for (var q : (List<Map<String, Object>>) ((Map<String, Object>) vocational.instrument()).get("questions"))
            a.put(s(q, "id"), s(q, "dimension").equals("I") ? 5 : 1);
        call(student, "PUT", "/api/vocational/" + id,
                m("answers", a, "preferences", m("national_only", true, "location", "Junín")))
                .andExpect(status().isOk());
        var result = tree(
                call(student, "POST", "/api/vocational/" + id + "/finish", m()).andExpect(status().isOk()).andReturn());
        assertEquals("I", result.get("profile").get(0).get("id").asText());
        assertEquals(100, result.get("profile").get(0).get("score").asDouble());
        assertEquals(0, result.get("profile").get(1).get("score").asDouble());
        call(student, "PUT", "/api/vocational/" + id, m("answers", a)).andExpect(status().isBadRequest());
        byte[] pdf = call(student, "GET", "/api/reports/vocational/" + id, null).andExpect(status().isOk()).andReturn()
                .getResponse().getContentAsByteArray();
        assertEquals("%PDF", new String(Arrays.copyOf(pdf, 4)));
        call(other, "GET", "/api/reports/vocational/" + id, null).andExpect(status().isForbidden());
    }

    @Test
    @Order(6)
    void vocationalUniformAndInvalid() {
        Map<String, Object> a = new LinkedHashMap<>();
        for (var q : (List<Map<String, Object>>) ((Map<String, Object>) vocational.instrument()).get("questions"))
            a.put(s(q, "id"), 3);
        var r = vocational.calculate(a, m());
        assertEquals(true, r.get("uniform"));
        assertEquals(6, ((List<?>) r.get("ties")).size());
        a.put("R1", 6);
        assertThrows(org.springframework.web.server.ResponseStatusException.class, () -> vocational.calculate(a, m()));
    }

    @Test
    @Order(7)
    void attemptsProtectAnswersEnforceTimingAndGrade() throws Exception {
        String exam = s(db.one("SELECT id FROM exams WHERE offering_id=?", offering), "id");
        String id = tree(
                call(student, "POST", "/api/exams/" + exam + "/start", m()).andExpect(status().isOk()).andReturn())
                .get("id").asText();
        var initial = tree(call(student, "GET", "/api/attempts/" + id, null).andExpect(status().isOk()).andReturn());
        assertFalse(initial.get("questions").get(0).has("correct_answer"));
        call(other, "GET", "/api/attempts/" + id, null).andExpect(status().isForbidden());
        List<Map<String, Object>> answers = new ArrayList<>();
        initial.get("questions")
                .forEach(q -> answers.add(m("response_id", q.get("response_id").asText(), "answer", "1")));
        call(student, "PUT", "/api/attempts/" + id + "/answers", m("answers", answers)).andExpect(status().isOk());
        var submitted = tree(
                call(student, "POST", "/api/attempts/" + id + "/submit", m()).andExpect(status().isOk()).andReturn());
        assertTrue(submitted.get("attempt").get("grade").isNull());
        assertEquals(20, num(db.get("attempts", id), "grade"));
        call(student, "PUT", "/api/attempts/" + id + "/answers", m("answers", answers))
                .andExpect(status().isBadRequest());
        db.patch("exams", exam, m("release_at", Instant.now().minusSeconds(10).toString()));
        call(student, "GET", "/api/attempts/" + id, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.attempt.grade").value(20));
        String second = tree(
                call(student, "POST", "/api/exams/" + exam + "/start", m()).andExpect(status().isOk()).andReturn())
                .get("id").asText();
        db.patch("attempts", second, m("deadline", Instant.now().minusSeconds(1).toString()));
        call(student, "PUT", "/api/attempts/" + second + "/answers", m("answers", List.of()))
                .andExpect(status().isBadRequest());
        call(student, "GET", "/api/attempts/" + second, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.attempt.status").value("CALIFICADO"));
        call(student, "POST", "/api/exams/" + exam + "/start", m()).andExpect(status().isBadRequest());
    }

    @Test
    @Order(8)
    void attendanceBoundary() {
        String cy = db.insert("cycles", m("name", "Asistencia test", "start_date", "2026-01-01", "end_date",
                "2026-04-22", "weights", "{\"PRACTICA\":30,\"PARCIAL\":30,\"SIMULACRO\":40}"));
        String sec = db.insert("sections", m("cycle_id", cy, "name", "Test", "capacity", 20));
        String offer = db.insert("offerings", m("course_id", course, "section_id", sec, "teacher_id",
                db.get("offerings", offering).get("teacher_id")));
        db.insert("enrollments", m("student_id", studentId, "offering_id", offer, "joined_on", "2026-01-01"));
        for (int i = 0; i < 32; i++) {
            String sess = db.insert("sessions",
                    m("offering_id", offer, "date", LocalDate.of(2026, 1, 1).plusDays(i).toString(), "starts", "08:00",
                            "ends", "09:00", "room", "Test", "topic", "Prueba", "status",
                            i < 16 ? "REALIZADA" : "PROGRAMADA"));
            if (i < 16)
                db.insert("attendance",
                        m("session_id", sess, "student_id", studentId, "state", "FALTA", "recorded_by", studentId));
        }
        var a = metrics.attendance("o.id='" + offer + "'", studentId).get(0);
        assertEquals(50, num(a, "cycle_pct"));
        assertEquals(100, num(a, "to_date_pct"));
        assertEquals(true, a.get("severe"));
    }

    @Test
    @Order(9)
    void materialVersionUploadAndArchive() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "guia.txt", "text/plain",
                "Ecuaciones con ejemplos propios".getBytes());
        var r = mvc.perform(multipart("/api/materials").file(file).param("offering_id", offering)
                .param("title", "Guía test").param("topic", "Ecuaciones").param("difficulty", "Básico")
                .header("Authorization", "Bearer " + teacher)).andExpect(status().isOk()).andReturn();
        String id = tree(r).get("id").asText(),
                version = s(db.one("SELECT id FROM material_versions WHERE material_id=?", id), "id");
        call(student, "GET", "/api/files/material/" + version, null).andExpect(status().isOk());
        mvc.perform(multipart("/api/materials").param("id", id).param("offering_id", offering)
                .param("title", "Guía revisada").param("topic", "Ecuaciones").param("difficulty", "Intermedio")
                .param("body", "Texto corregido").param("reason", "Corrección de ejemplo")
                .header("Authorization", "Bearer " + teacher)).andExpect(status().isOk());
        assertEquals(2, db.list("SELECT id FROM material_versions WHERE material_id=?", id).size());
        call(teacher, "DELETE", "/api/materials/" + id, null).andExpect(status().isOk());
        call(student, "GET", "/api/files/material/" + version, null).andExpect(status().isBadRequest());
    }

    @Test
    @Order(10)
    void exportsHaveRealFormats() throws Exception {
        byte[] pdf = call(director, "GET", "/api/reports/download?type=grades&format=pdf", null)
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        assertEquals("%PDF", new String(Arrays.copyOf(pdf, 4)));
        byte[] excel = call(director, "GET", "/api/reports/download?type=attendance&format=xlsx", null)
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        assertEquals('P', excel[0]);
        assertEquals('K', excel[1]);
        call(student, "GET", "/api/reports?type=teachers", null).andExpect(status().isForbidden());
    }

    @Test
    @Order(11)
    void chatFallbackDoesNotInventAi() throws Exception {
        call(student, "POST", "/api/ai/chat", m("question", "Explícame ecuaciones")).andExpect(status().isOk())
                .andExpect(jsonPath("$.provider").value("BUSQUEDA")).andExpect(jsonPath("$.sources[0].title").exists());
    }

    @Test
    @Order(12)
    void challengeOneSubmissionAndSolutionsHidden() throws Exception {
        String opponent = s(db.one("SELECT id FROM users WHERE email='alumno2@academia.local'"), "id");
        String id = tree(call(student, "POST", "/api/challenges", m("offering_id", offering, "opponent_id", opponent))
                .andExpect(status().isOk()).andReturn()).get("id").asText();
        var detail = tree(call(student, "GET", "/api/challenges/" + id, null).andExpect(status().isOk()).andReturn());
        assertFalse(detail.get("challenge").get("questions").get(0).has("answer"));
        Map<String, Object> answers = new LinkedHashMap<>();
        detail.get("challenge").get("questions").forEach(q -> answers.put(q.get("id").asText(), "1"));
        call(student, "POST", "/api/challenges/" + id + "/submit", m("answers", answers)).andExpect(status().isOk());
        call(student, "POST", "/api/challenges/" + id + "/submit", m("answers", answers))
                .andExpect(status().isBadRequest());
        call(other, "POST", "/api/challenges/" + id + "/submit", m("answers", answers)).andExpect(status().isOk());
        call(student, "GET", "/api/challenges/" + id, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.solutions_available").value(true));
    }

    @Test
    @Order(13)
    void weightedEvaluationsAndScopedCatalogue() throws Exception {
        String sid = s(db.one("SELECT id FROM users WHERE email='alumno6@academia.local'"), "id");
        call(teacher, "POST", "/api/grades", m("offering_id", offering, "student_id", sid, "title", "Simulacro A",
                "category", "SIMULACRO", "grade", 10, "relative_weight", 1)).andExpect(status().isOk());
        call(teacher, "POST", "/api/grades", m("offering_id", offering, "student_id", sid, "title", "Simulacro B",
                "category", "SIMULACRO", "grade", 20, "relative_weight", 3)).andExpect(status().isOk());
        var row = metrics.grades("o.id='" + offering + "'", sid).get(0);
        assertEquals(17.5, num((Map<String, Object>) row.get("averages"), "SIMULACRO"));
        var catalogue = tree(call(teacher, "GET", "/api/catalog", null).andExpect(status().isOk()).andReturn());
        for (var section : catalogue.get("sections"))
            assertNotEquals("B · Tarde", section.get("name").asText());
    }

    @Test
    @Order(15)
    void personalProgressAndNamedRanking() throws Exception {
        var rows=tree(call(student,"GET","/api/ai/progress",null).andExpect(status().isOk()).andReturn());
        assertFalse(rows.isEmpty());
        for(var row:rows) { assertEquals(studentId,row.get("student_id").asText()); assertTrue(row.has("trend")); }
        call(teacher,"GET","/api/ai/progress",null).andExpect(status().isForbidden());
        call(student,"POST","/api/ai/chat",m("question","¿Cómo van mis notas por curso?"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.answer").value(org.hamcrest.Matchers.containsString("Matemática")));
        call(student,"GET","/api/challenges/ranking/all",null).andExpect(status().isOk())
            .andExpect(jsonPath("$[0].student_name").isNotEmpty()).andExpect(jsonPath("$[0].course_name").isNotEmpty())
            .andExpect(jsonPath("$[0].points").exists());
    }

    @Test
    @Order(16)
    void bulletinConversionPublishesRunnableExamAndRollsBackInvalidKeys() throws Exception {
        var parsed=BulletinExams.parse("1. Suma 2 + 2\nA) 3 B) 4 C) 5\n2. Explica tu razonamiento");
        assertEquals(2,parsed.size()); assertEquals("MULTIPLE",s(parsed.get(0),"kind"));
        assertEquals(List.of("3","4","5"),parsed.get(0).get("options"));
        assertEquals("",s(parsed.get(0),"answer"));
        assertTrue(BulletinExams.parse("Documento sin preguntas numeradas").isEmpty());
        String id=db.insert("bulletins",m("title","Balotario prueba","university","","career","General","admission_year",2026,"process","Prueba","bulletin_group",Store.id(),"url","https://example.org/examen.pdf"));
        var body=m("title","Examen desde balotario","offering_id",offering,"duration",30,"max_attempts",1,"relative_weight",1,
            "opens_at",Instant.now().minusSeconds(60).toString(),"closes_at",Instant.now().plusSeconds(3600).toString(),
            "release_at",Instant.now().plusSeconds(7200).toString(),"reviewed",true,"imported_questions",parsed);
        call(student,"POST","/api/bulletins/"+id+"/extract",null).andExpect(status().isForbidden());
        call(director,"POST","/api/bulletins/"+id+"/extract",null).andExpect(status().isBadRequest());
        int count=db.list("SELECT id FROM questions").size();
        call(director,"POST","/api/bulletins/"+id+"/exam",body).andExpect(status().isBadRequest());
        assertEquals(count,db.list("SELECT id FROM questions").size());
        parsed.get(0).put("answer","1");
        call(director,"POST","/api/bulletins/"+id+"/exam",body).andExpect(status().isBadRequest());
        assertEquals(count,db.list("SELECT id FROM questions").size());
        parsed.get(1).put("explanation","Explica los pasos de la suma.");
        var exam=tree(call(director,"POST","/api/bulletins/"+id+"/exam",body).andExpect(status().isOk()).andReturn());
        String examId=exam.get("id").asText();
        assertEquals(2,db.list("SELECT id FROM exam_questions WHERE exam_id=?",examId).size());
        var attempt=tree(call(student,"POST","/api/exams/"+examId+"/start",null).andExpect(status().isOk()).andReturn());
        call(student,"GET","/api/attempts/"+attempt.get("id").asText(),null).andExpect(status().isOk())
            .andExpect(jsonPath("$.questions[0].options[1]").value("4"))
            .andExpect(jsonPath("$.questions[0].correct_answer").doesNotExist());
    }

    @Test
    @Order(17)
    void realPdfUploadExtractsQuestions() throws Exception {
        byte[] bytes;
        try(var document=new org.apache.pdfbox.pdmodel.PDDocument(); var output=new java.io.ByteArrayOutputStream()) {
            var page=new org.apache.pdfbox.pdmodel.PDPage(); document.addPage(page);
            try(var content=new org.apache.pdfbox.pdmodel.PDPageContentStream(document,page)) {
                content.beginText(); content.setFont(new org.apache.pdfbox.pdmodel.font.PDType1Font(org.apache.pdfbox.pdmodel.font.Standard14Fonts.FontName.HELVETICA),12);
                content.newLineAtOffset(50,700); content.showText("1. Suma 2 + 2"); content.newLineAtOffset(0,-20);
                content.showText("A) 3 B) 4 C) 5"); content.endText();
            }
            document.save(output); bytes=output.toByteArray();
        }
        var file=new MockMultipartFile("file","balotario.pdf","application/pdf",bytes);
        String id=tree(mvc.perform(multipart("/api/bulletins").file(file).header("Authorization","Bearer "+director)
            .param("title","PDF de prueba").param("university","UNI").param("career","General").param("admission_year","2026").param("process","Prueba"))
            .andExpect(status().isOk()).andReturn()).get("id").asText();
        call(director,"POST","/api/bulletins/"+id+"/extract",null).andExpect(status().isOk())
            .andExpect(jsonPath("$.questions[0].prompt").value("Suma 2 + 2"))
            .andExpect(jsonPath("$.questions[0].options[1]").value("4"))
            .andExpect(jsonPath("$.questions[0].answer").value(""));
    }

    @Test
    @Order(14)
    void administrationPreservesCycleAndCapacity() throws Exception {
        var offer = db.get("offerings", offering);
        var sec = db.get("sections", s(offer, "section_id"));
        call(director, "PUT", "/api/sections/" + s(sec, "id"),
                m("cycle_id", sec.get("cycle_id"), "name", sec.get("name"), "capacity", 1))
                .andExpect(status().isBadRequest());
        call(director, "PUT", "/api/cycles/" + s(sec, "cycle_id"),
                m("name", "Cambio inválido", "start_date", "2030-01-01")).andExpect(status().isBadRequest());
        call(teacher, "PUT", "/api/courses/" + course, m("name", "Cambio no autorizado"))
                .andExpect(status().isForbidden());
        call(director, "PUT", "/api/courses/" + course,
                m("name", "Matemática", "description", "Curso de preparación actualizado")).andExpect(status().isOk());
    }
}
