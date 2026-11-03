package pe.jauja.academia;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static pe.jauja.academia.Store.*;

class AcademicCareersTest {
    final AcademicCareers service=new AcademicCareers();
    Map<String,Object> row(String course, Object grade) { return m("course_name",course,"grade",grade,"complete",false,"entries",List.of(m("created_at","2026-10-01"))); }
    List<Map<String,Object>> options(List<Map<String,Object>> rows) {return (List<Map<String,Object>>)service.calculate(rows).get("careers");}
    @Test void differentStrengthsChangeTheOptions() {
        var engineering=options(List.of(row("Matemática",19),row("Física",19),row("Biología",8),row("Química",8),row("Comunicación",10),row("Historia",9)));
        var health=options(List.of(row("Matemática",8),row("Física",8),row("Biología",19),row("Química",19),row("Comunicación",10),row("Historia",9)));
        assertEquals("Ingeniería Civil",s(engineering.get(0),"name"));
        assertEquals("Medicina Humana",s(health.get(0),"name"));
        assertNotEquals(engineering.stream().map(c->c.get("name")).toList(),health.stream().map(c->c.get("name")).toList());
    }
    @Test void missingGradesDoNotBecomeZerosAndGeneralScienceIsNotBiology() {
        assertTrue(options(List.of()).isEmpty());
        assertTrue(options(List.of(row("Matemática",19),row("Biología",null),row("Ciencias",20))).isEmpty());
        var options=options(List.of(row("Biología",18),row("Química",16)));
        var medicine=options.stream().filter(c->s(c,"name").equals("Medicina Humana")).findFirst().orElseThrow();
        assertEquals(85L,medicine.get("coverage"));
        assertEquals(17.18,num(medicine,"score"));
        assertEquals(List.of("Comunicación"),medicine.get("missing"));
        assertFalse(options(List.of(row("Matemática",0),row("Comunicación",0))).isEmpty());
    }
    @Test void latestCourseIsUsedOnceAndTiesRemainExplicit() {
        var old=row("Matemática",2);var recent=row("Matemática",18);
        recent.put("entries",List.of(m("created_at","2026-10-02")));
        var result=service.calculate(List.of(old,recent,row("Física",18),row("Comunicación",18)));
        assertEquals(3,result.get("graded_courses"));assertEquals(true,result.get("ties"));
        for(var c:(List<Map<String,Object>>)result.get("careers"))assertEquals(18,num(c,"score"));
    }
}
