package pe.jauja.academia;

import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static pe.jauja.academia.Store.*;

class BulletinAiContractTest {
    @Test
    void convertsLettersToIndicesAndRetainsDocumentOptions() throws Exception {
        var db=new Store(new org.springframework.jdbc.core.JdbcTemplate(),new com.fasterxml.jackson.databind.ObjectMapper());
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/api/chat",exchange->{
            exchange.getRequestBody().readAllBytes();
            byte[] body=db.json(m("message",m("content",db.json(m("explanation","La solución es la alternativa D.","answer_letter","D","course_name","Matemática"))))).getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200,body.length);exchange.getResponseBody().write(body);exchange.close();
        });
        server.start();
        try {
            var ai=new BulletinAi(db,new FilesService("local","target/ai-contract-files","","","",""),"http://127.0.0.1:"+server.getAddress().getPort(),"vision-model");
            var answer=ai.solve(m("source_number",1,"source_text","Un ejercicio. A) uno B) dos C) tres D) cuatro E) cinco","images","[]"),false);
            assertEquals(3,n(answer,"answer",-1));
            assertTrue(b(answer,"solvable"));
            assertEquals(List.of("uno","dos","tres","cuatro","cinco"),answer.get("options"));
        } finally {server.stop(0);}
    }
}
