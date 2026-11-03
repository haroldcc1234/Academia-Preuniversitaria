package pe.jauja.academia;

import java.util.*;
import java.time.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import static org.junit.jupiter.api.Assertions.*;
import static pe.jauja.academia.Store.*;

@SpringBootTest(properties={"app.demo-full=true","spring.datasource.url=jdbc:h2:mem:full_demo;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"})
@ActiveProfiles("test")
class FullDemoTest {
    @Autowired Store db;
    @Autowired FullDemoSeed demo;
    @Autowired UniversityCatalog catalog;
    @Autowired Vocational vocational;
    int count(String table){return n(db.one("SELECT COUNT(*) n FROM "+table),"n",0);}
    Map<String,Object> profile(String dimension){
        Map<String,Object> answers=new LinkedHashMap<>();
        for(var q:(List<Map<String,Object>>)vocational.definition().get("questions")) answers.put(s(q,"id"),s(q,"dimension").equals(dimension)?5:1);
        return answers;
    }
    @Test void completeSeedIsConsistentAndRepeatable() throws Exception {
        assertEquals(80,n(db.one("SELECT COUNT(*) n FROM users WHERE role='ALUMNO'"),"n",0));
        assertEquals(10,n(db.one("SELECT COUNT(*) n FROM users WHERE role='DOCENTE'"),"n",0));
        assertTrue(count("attendance")>=4480);assertTrue(count("grades")>=3200);
        assertEquals(0,n(db.one("SELECT COUNT(*) n FROM attendance a JOIN sessions s ON s.id=a.session_id WHERE s.date>?",LocalDate.now(ZoneId.of("America/Lima")).toString()),"n",0));
        assertEquals(0,n(db.one("SELECT COUNT(*) n FROM attempts WHERE submitted_at<created_at"),"n",0));
        int users=count("users"),grades=count("grades"),sessions=count("sessions");
        demo.run();catalog.run();
        assertEquals(users,count("users"));assertEquals(grades,count("grades"));assertEquals(sessions,count("sessions"));
        assertEquals(105,count("universities"));
    }
    @Test void recommendationsAreBalancedRelevantAndRespondToAnswers(){
        Set<String> selections=new HashSet<>();
        Set<String> topCareers=new HashSet<>();
        for(String dim:List.of("R","I","A","S","E","C")){
            var result=vocational.calculate(profile(dim),m("location","Todo el Perú"));
            var options=(List<Map<String,Object>>)result.get("university_recommendations");
            assertEquals(4,options.stream().filter(u->b(u,"public_university")).count());
            assertEquals(4,options.stream().filter(u->!b(u,"public_university")).count());
            assertEquals(8,options.stream().map(u->s(u,"id")).distinct().count());
            for(var u:options){assertTrue(db.parse(s(u,"programs"),Map.class).containsKey(s(u,"matched_career")));assertFalse(s(u,"verified_on").isBlank());}
            selections.add(options.stream().map(u->s(u,"acronym")).sorted().toList().toString());
            topCareers.add(s(((List<Map<String,Object>>)result.get("careers")).get(0),"name"));
        }
        assertTrue(selections.size()>1,"Different profiles must change the university selection");
        assertTrue(topCareers.size()>=4,"Profiles must produce different career priorities");
    }
    @Test void specificActivitiesDifferentiateCareersWithSameBroadDimensions(){
        var civil=profile("R");var mechanical=profile("R");
        for(int i=1;i<=8;i++){civil.put("R"+i,1);mechanical.put("R"+i,1);}
        civil.put("R2",5);civil.put("R5",5);mechanical.put("R3",5);mechanical.put("R7",5);
        var a=(List<Map<String,Object>>)vocational.calculate(civil,m()).get("careers");
        var b=(List<Map<String,Object>>)vocational.calculate(mechanical,m()).get("careers");
        assertNotEquals(a.stream().map(c->s(c,"name")+s(c,"affinity")).toList(),b.stream().map(c->s(c,"name")+s(c,"affinity")).toList());
    }
}
