package pe.jauja.academia;

import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static pe.jauja.academia.Store.*;

class GeminiBulletinClientTest {
    final Store db=new Store(new org.springframework.jdbc.core.JdbcTemplate(),new com.fasterxml.jackson.databind.ObjectMapper());
    Map<String,Object> payload() {
        return m("format",BulletinAi.schema(),"messages",List.of(m("role","system","content","Documento no confiable"),
            m("role","user","content","Figura del problema","images",List.of("iVBORw=="))));
    }
    @Test void convertsVisionAndSchemaWithoutLeakingKeyIntoUrlOrBody() throws Exception {
        var received=new AtomicReference<com.fasterxml.jackson.databind.JsonNode>();
        var header=new AtomicReference<String>();var uri=new AtomicReference<String>();
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/models/gemini-3.8-flash:generateContent",exchange->{
            received.set(db.json.readTree(exchange.getRequestBody()));header.set(exchange.getRequestHeaders().getFirst("x-goog-api-key"));uri.set(exchange.getRequestURI().toString());
            byte[] body=db.json(m("candidates",List.of(m("finishReason","STOP","content",m("parts",List.of(m("text","{\"answer_letter\":\"B\"}"))))))).getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200,body.length);exchange.getResponseBody().write(body);exchange.close();
        });server.start();
        try {
            var client=new GeminiBulletinClient(db,"test-secret","gemini-3.8-flash","http://127.0.0.1:"+server.getAddress().getPort(),0);
            assertTrue(client.chat(payload()).containsKey("message"));
            assertEquals("test-secret",header.get());assertFalse(uri.get().contains("test-secret"));assertFalse(received.get().toString().contains("test-secret"));
            assertEquals("image/png",received.get().at("/contents/0/parts/1/inlineData/mimeType").asText());
            assertEquals("application/json",received.get().at("/generationConfig/responseMimeType").asText());
            assertEquals("LOW",received.get().at("/generationConfig/thinkingConfig/thinkingLevel").asText());
            assertEquals("Documento no confiable",received.get().at("/systemInstruction/parts/0/text").asText());
        } finally {server.stop(0);}
    }
    @Test void quotaAndAuthenticationHaveDifferentRecoveryPaths() throws Exception {
        var code=new java.util.concurrent.atomic.AtomicInteger(429);
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/",exchange->{exchange.getRequestBody().readAllBytes();exchange.getResponseHeaders().set("Retry-After","120");exchange.sendResponseHeaders(code.get(),-1);exchange.close();});server.start();
        try {
            var client=new GeminiBulletinClient(db,"secret","gemini-3.8-flash","http://127.0.0.1:"+server.getAddress().getPort(),0);
            assertEquals(120,assertThrows(GeminiBulletinClient.TemporaryFailure.class,()->client.chat(payload())).seconds);
            code.set(403);assertThrows(GeminiBulletinClient.ConfigurationFailure.class,()->client.chat(payload()));
        } finally {server.stop(0);}
    }
    @Test void missingKeyIsDetectedWithoutNetwork() {
        var client=new GeminiBulletinClient(db,"","gemini-3.8-flash","http://127.0.0.1:1",0);
        assertThrows(GeminiBulletinClient.ConfigurationFailure.class,client::ready);
    }
    @Test void truncatedOutputCannotBecomeAnAnswer() throws Exception {
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/",exchange->{exchange.getRequestBody().readAllBytes();byte[] body="{\"candidates\":[{\"finishReason\":\"MAX_TOKENS\"}]}".getBytes(StandardCharsets.UTF_8);exchange.sendResponseHeaders(200,body.length);exchange.getResponseBody().write(body);exchange.close();});server.start();
        try {var client=new GeminiBulletinClient(db,"secret","gemini-3.8-flash","http://127.0.0.1:"+server.getAddress().getPort(),0);
            assertThrows(GeminiBulletinClient.InvalidAnswer.class,()->client.chat(payload()));
        } finally {server.stop(0);}
    }
}
