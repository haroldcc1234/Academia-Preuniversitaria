package pe.jauja.academia;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static pe.jauja.academia.Store.*;

@SpringBootTest(properties={"app.ollama-url=http://127.0.0.1:1","spring.datasource.url=jdbc:h2:mem:career_access;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"})
@AutoConfigureMockMvc @ActiveProfiles("test")
class AcademicCareersAccessTest {
    @Autowired MockMvc mvc;
    @Autowired Store db;
    String login(String email,String password) throws Exception {
        var r=mvc.perform(post("/api/auth/login").contentType("application/json").content(db.json(m("email",email,"password",password)))).andExpect(status().isOk()).andReturn();
        return db.json.readTree(r.getResponse().getContentAsString()).get("token").asText();
    }
    @Test void onlyStudentOwnGradesAreAvailable() throws Exception {
        mvc.perform(get("/api/ai/careers")).andExpect(status().isUnauthorized());
        String director=login("director@academia.local","Director2026!");
        mvc.perform(get("/api/ai/careers").header("Authorization","Bearer "+director)).andExpect(status().isForbidden());
        String token=login("alumno@academia.local","Alumno2026!");
        var own=mvc.perform(get("/api/ai/careers").header("Authorization","Bearer "+token)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var spoof=mvc.perform(get("/api/ai/careers?student_id=someone-else").header("Authorization","Bearer "+token)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertEquals(own,spoof);
        assertTrue(db.json.readTree(own).has("careers"));
        mvc.perform(post("/api/ai/chat").header("Authorization","Bearer "+token)
            .contentType("application/json").content(db.json(m("question","¿Qué carreras me recomiendas según mis notas?"))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.provider").value("ORIENTACION"))
            .andExpect(jsonPath("$.sources[0].id").value("mis-carreras"))
            .andExpect(jsonPath("$.answer").isNotEmpty());
    }
}
