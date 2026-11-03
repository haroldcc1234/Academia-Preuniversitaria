package pe.jauja.academia;

import java.util.*;
import java.time.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import static pe.jauja.academia.Store.*;

/** Explicit opt-in fixture. Never clears or replaces existing academic records. */
@Component @Order(3)
public class FullDemoSeed implements CommandLineRunner {
    final Store db;final PasswordEncoder passwords;final Vocational vocational;final boolean enabled;
    public FullDemoSeed(Store db,PasswordEncoder passwords,Vocational vocational,@Value("${app.demo-full:false}") boolean enabled){this.db=db;this.passwords=passwords;this.vocational=vocational;this.enabled=enabled;}
    @Override @Transactional
    public void run(String... args) throws Exception {
        if(!enabled || db.find("SELECT id FROM data_revisions WHERE id='full-demo-v1'")!=null)return;
        var director=db.one("SELECT id FROM users WHERE role='DIRECTOR' AND active=TRUE ORDER BY created_at LIMIT 1");
        String directorId=s(director,"id"), studentPassword=passwords.encode("Alumno2026!"),teacherPassword=passwords.encode("Docente2026!");
        var teachers=new ArrayList<String>();
        db.list("SELECT id FROM users WHERE role='DOCENTE' AND email LIKE '%@academia.local' ORDER BY email").forEach(u->teachers.add(s(u,"id")));
        String[] teacherNames={"Carmen Paredes Salas","Jorge Rivas Huamán","Elena Cárdenas Soto","Raúl Valverde Rojas","Patricia Huerta León","Miguel Aquino Ramos","Rosa Medina Castro","Fernando Salcedo Poma","Ana Villanueva Díaz","Víctor Palomino Ruiz"};
        while(teachers.size()<10){int i=teachers.size();teachers.add(db.insert("users",m("name",teacherNames[i],"email","docente.demo"+(i+1)+"@academia.test","role","DOCENTE","password",teacherPassword,"consent_at",now())));}
        var students=new ArrayList<String>();
        db.list("SELECT id FROM users WHERE role='ALUMNO' AND email LIKE '%@academia.local' ORDER BY email").forEach(u->students.add(s(u,"id")));
        String[] first={"Luciana","Sebastián","Daniela","Adrián","Mariana","Gabriel","Fernanda","Nicolás","Alejandra","Joaquín","Valentina","Rodrigo","Renata","Álvaro","Sofía","Emiliano","Paula","Leonardo","Ximena","Bruno"};
        String[] last={"Quispe","Huamán","Poma","Rojas","Torres","Mendoza","Flores","Salazar","Condori","Gutiérrez","Castro","Vargas","Soto","Ramos","Cárdenas","Medina","Aquino","León","Paredes","Palomino"};
        String[] interests={"Ingeniería de Sistemas","Medicina Humana","Derecho","Arquitectura","Educación","Contabilidad","Agronomía","Ingeniería Civil"};
        while(students.size()<80){int i=students.size();students.add(db.insert("users",m("name",first[i%20]+" "+last[(i*7)%20]+" "+last[(i/20+3)%20],"email","alumno.demo"+(i+1)+"@academia.test","role","ALUMNO","password",studentPassword,"career",interests[i%8],"university",i%3==0?"UNCP":i%3==1?"UNMSM":"UC","consent_at",now())));}
        var start=LocalDate.now(ZoneId.of("America/Lima")).minusWeeks(7).with(DayOfWeek.MONDAY);
        var cycle=db.insert("cycles",m("name","Ciclo Integral 2026-II · Demostración","start_date",start.toString(),"end_date",start.plusWeeks(16).minusDays(1).toString(),"weights",db.get("settings","main").get("weights")));
        List<String> sections=new ArrayList<>();String[] sectionNames={"Integral A · Mañana","Integral B · Mañana","Integral C · Tarde","Integral D · Tarde"};
        for(String name:sectionNames)sections.add(db.insert("sections",m("cycle_id",cycle,"name",name,"capacity",25)));
        List<Map<String,Object>> bank=db.json.readValue(new ClassPathResource("demo-questions.json").getInputStream(),List.class);
        var courses=bank.stream().map(q->s(q,"course")).distinct().toList();
        List<Map<String,Object>> allVersions=new ArrayList<>();
        for(int c=0;c<courses.size();c++){
            String name=courses.get(c),teacher=teachers.get(c);
            var existing=db.find("SELECT id FROM courses WHERE name=?",name);
            String course=existing==null?db.insert("courses",m("name",name,"description","Preparación integral para admisión universitaria")):s(existing,"id");
            List<Map<String,Object>> versions=new ArrayList<>();
            for(var item:bank.stream().filter(q->s(q,"course").equals(name)).toList()){
                String question=db.insert("questions",m("course_id",course,"author_id",teacher,"topic","Fundamentos y aplicación","difficulty","Básico","practice",true));
                String version=db.insert("question_versions",m("question_id",question,"version",1,"kind","MULTIPLE","prompt",item.get("prompt"),"options",db.json(item.get("options")),"answer",item.get("answer"),"explanation",item.get("explanation")));
                var q=db.get("question_versions",version);q.put("course_name",name);versions.add(q);allVersions.add(q);
            }
            for(int g=0;g<4;g++){
                List<String> group=students.subList(g*20,(g+1)*20);
                String offering=db.insert("offerings",m("course_id",course,"section_id",sections.get(g),"teacher_id",teacher,"progress",44,"syllabus","Semanas 1–4: fundamentos y diagnóstico. Semanas 5–8: resolución guiada. Semanas 9–12: problemas integradores. Semanas 13–16: simulacros y repaso."));
                for(String student:group)db.insert("enrollments",m("student_id",student,"offering_id",offering,"joined_on",start.toString()));
                for(int w=0;w<16;w++){
                    int hour=new int[]{8,10,14,16}[g]+(c>=6?2:0);
                    String session=db.insert("sessions",m("offering_id",offering,"date",start.plusWeeks(w).plusDays(c%6).toString(),"starts",String.format("%02d:00",hour),"ends",String.format("%02d:00",hour+2),"room","Aula "+(101+g),"topic",name+" · "+new String[]{"Diagnóstico y fundamentos","Resolución guiada","Aplicación y práctica","Evaluación y retroalimentación"}[w%4],"status",w<7?"REALIZADA":"PROGRAMADA"));
                    if(w<7)for(int j=0;j<group.size();j++){
                        int code=(j*3+w+c)%17;String state=j%13==0 && w<4?"FALTA":code==0?"JUSTIFICADO":code<3?"TARDE":"PRESENTE";
                        db.insert("attendance",m("session_id",session,"student_id",group.get(j),"state",state,"note","Registro ficticio de demostración","recorded_by",teacher));
                    }
                }
                for(int k=0;k<2;k++){
                    String mat=db.insert("materials",m("offering_id",offering,"author_id",teacher,"title",name+" · "+(k==0?"Guía de fundamentos":"Taller de aplicación"),"topic",k==0?"Conceptos básicos":"Resolución de problemas","difficulty",k==0?"Básico":"Intermedio","tags","guía,práctica,demostración"));
                    StringBuilder body=new StringBuilder("Material interno de demostración. Lee cada problema, identifica los datos y verifica tu respuesta.\n\n");
                    for(var q:versions)body.append(s(q,"prompt")).append("\n").append(s(q,"explanation")).append("\n\n");
                    db.insert("material_versions",m("material_id",mat,"version",1,"body",body.toString(),"reason","Publicación de material interno"));
                }
                for(int j=0;j<20;j++)for(int k=0;k<5;k++){
                    int grade=Math.min(20,Math.max(5,9+(j+c)%9+(j%3==0?k: j%3==1?-k/2:0)));
                    db.insert("grades",m("offering_id",offering,"student_id",group.get(j),"title","Evaluación "+(k+1)+" · "+name,"category",k<3?"PRACTICA":"PARCIAL","grade",grade,"recorded_by",teacher,"created_at",start.plusWeeks(k+1).atTime(12,0).toInstant(ZoneOffset.ofHours(-5)).toString()));
                }
                createExam(offering,name,versions,group,teacher,false,c);
                createExam(offering,name,versions,group,teacher,true,c);
                for(int j=0;j<20;j+=2){
                    var qs=versions.subList(0,3);String challenge=db.insert("challenges",m("offering_id",offering,"creator_id",group.get(j),"opponent_id",group.get(j+1),"questions",db.json(qs),"created_at",Instant.now().minusSeconds(7200).toString(),"expires_at",Instant.now().minusSeconds(3600).toString()));
                    for(int participant=0;participant<2;participant++){
                        int score=(j+participant+c)%4;Map<String,Object> answers=new LinkedHashMap<>();
                        for(int k=0;k<3;k++){var q=qs.get(k);answers.put(s(q,"id"),k<score?s(q,"answer"):String.valueOf((n(q,"answer",0)+1)%4));}
                        db.insert("challenge_results",m("challenge_id",challenge,"user_id",group.get(j+participant),"answers",db.json(answers),"score",score,"created_at",Instant.now().minusSeconds(5400).toString()));
                    }
                }
                db.insert("resolutions",m("question_id",versions.get(0).get("question_id"),"author_id",group.get(0),"body",versions.get(0).get("explanation"),"status","APROBADO","reviewer_id",teacher,"feedback","Procedimiento claro y resultado comprobado. Ejemplo de demostración."));
            }
        }
        for(int i=0;i<students.size();i++){
            db.insert("notifications",m("user_id",students.get(i),"title","Tu ciclo integral está disponible","body","Revisa las clases, guías y evaluaciones de tu sección. Datos ficticios de demostración.","severity","INFO","dedup_key","demo-full-welcome:"+students.get(i)));
            if(i<16){
                Map<String,Object> answers=new LinkedHashMap<>();String[] dims={"R","I","A","S","E","C"};String primary=dims[i%6];
                for(var q:(List<Map<String,Object>>)vocational.definition().get("questions"))answers.put(s(q,"id"),s(q,"dimension").equals(primary)?5:((i+s(q,"id").charAt(1))%3)+1);
                var prefs=m("location",i%2==0?"Junín":"Todo el Perú","prefer_public",true);var result=vocational.calculate(answers,prefs);
                db.insert("vocational",m("student_id",students.get(i),"instrument",vocational.definition().get("version"),"answers",db.json(answers),"preferences",db.json(prefs),"result",db.json(result),"status","COMPLETO","updated_at",now()));
            }
        }
        for(int b=0;b<2;b++){
            var qs=allVersions.subList(b*12,b*12+12);String bulletin=db.insert("bulletins",m("title","Práctica interna "+(b+1)+" · Demostración","university","General","career","General","admission_year",2026,"process","Práctica libre","bulletin_group",id(),"description","Ejercicios internos con claves elaboradas para la demostración. No es un examen oficial ni una conversión hecha por IA.","processing_status","LISTO","question_count",qs.size(),"processed_count",qs.size(),"uploaded_by",directorId));
            for(int j=0;j<qs.size();j++){var q=qs.get(j);db.insert("bulletin_questions",m("bulletin_id",bulletin,"position",j,"source_number",j+1,"prompt",q.get("prompt"),"options",q.get("options"),"images","[]","source_text",q.get("prompt"),"answer",q.get("answer"),"explanation",q.get("explanation"),"course_name",q.get("course_name"),"solution_status","RESUELTA","model","Clave docente de demostración"));}
        }
        db.insert("data_revisions",m("id","full-demo-v1","details","80 alumnos, 10 docentes, 4 secciones nuevas y 8 cursos. Datos ficticios añadidos sin borrar registros existentes."));
        db.audit(directorId,"CARGAR_DEMOSTRACION_COMPLETA",cycle,"Datos ficticios. Carga idempotente full-demo-v1.");
    }
    void createExam(String offering,String name,List<Map<String,Object>> versions,List<String> students,String teacher,boolean upcoming,int course){
        Instant now=Instant.now();
        String exam=db.insert("exams",m("offering_id",offering,"title",(upcoming?"Práctica disponible":"Simulacro resuelto")+" · "+name,"category","SIMULACRO","opens_at",now.minusSeconds(upcoming?3600:604800).toString(),"closes_at",now.plusSeconds(upcoming?30*86400:-518400).toString(),"release_at",now.plusSeconds(upcoming?30*86400:-518400).toString(),"duration",30,"max_attempts",2));
        List<String> eqs=new ArrayList<>();for(int k=0;k<4;k++)eqs.add(db.insert("exam_questions",m("exam_id",exam,"question_version_id",versions.get(k).get("id"),"position",k,"points",5)));
        if(upcoming)return;
        for(int j=0;j<students.size();j++){
            int correct=1+(j+course)%4;double grade=correct*5;
            String attempt=db.insert("attempts",m("exam_id",exam,"student_id",students.get(j),"number",1,"created_at",now.minusSeconds(604800).toString(),"deadline",now.minusSeconds(604800-1800).toString(),"submitted_at",now.minusSeconds(604800-1200).toString(),"status","CALIFICADO","grade",grade,"feedback","Intento ficticio de demostración. Revisa los desarrollos de las preguntas incorrectas."));
            for(int k=0;k<4;k++){var q=versions.get(k);db.insert("responses",m("attempt_id",attempt,"exam_question_id",eqs.get(k),"answer",k<correct?q.get("answer"):String.valueOf((n(q,"answer",0)+1)%4),"points",k<correct?5:0,"feedback",q.get("explanation")));}
        }
    }
}
