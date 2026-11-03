package pe.jauja.academia;

import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import static pe.jauja.academia.Store.*;

/** Cloud adapter used only for public bulletin content, never student records. */
@Service
public class GeminiBulletinClient {
    final Store db;
    final String key, model, base;
    final int interval;
    long nextRequest;
    final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    public GeminiBulletinClient(Store db, @Value("${app.gemini-key:}") String key,
            @Value("${app.gemini-model:gemini-3.8-flash}") String model,
            @Value("${app.gemini-url:https://generativelanguage.googleapis.com/v1beta}") String base,
            @Value("${app.gemini-interval-ms:6500}") int interval) {
        this.db=db; this.key=key.strip(); this.model=model; this.base=base.replaceAll("/$",""); this.interval=Math.max(0,interval);
    }
    void configured() {
        if(key.isBlank()) throw new ConfigurationFailure("Falta GEMINI_API_KEY. Ejecuta scripts/configurar-gemini.ps1 y reinicia el backend.");
        if(!model.matches("[a-zA-Z0-9._-]+")) throw new ConfigurationFailure("Nombre de modelo Gemini inválido.");
    }
    void ready() throws Exception {
        configured();
        var response=http.send(builder("/models/"+model).GET().build(),HttpResponse.BodyHandlers.ofString());
        validateStatus(response);
    }
    void probe() throws Exception {
        var response=chat(m("format",m("type","object","properties",m("answer",m("type","integer")),"required",List.of("answer")),"messages",List.of(m("role","user","content","Calcula 2 + 2 y devuelve answer."))));
        var result=db.parse(s((Map<?,?>)response.get("message"),"content"),Map.class);
        if(n(result,"answer",0)!=4)throw new InvalidAnswer("La prueba de generación no produjo una respuesta válida.");
    }
    HttpRequest.Builder builder(String path) {
        return HttpRequest.newBuilder(URI.create(base+path)).timeout(Duration.ofSeconds(150))
            .header("x-goog-api-key",key).header("Content-Type","application/json");
    }
    synchronized void pace() throws InterruptedException {
        long delay=nextRequest-System.currentTimeMillis();
        if(delay>0) Thread.sleep(delay);
        nextRequest=System.currentTimeMillis()+interval;
    }
    static void validateStatus(HttpResponse<String> response) {
        int code=response.statusCode();
        if(code==429 || code>=500) {
            int delay=60;
            try { delay=Integer.parseInt(response.headers().firstValue("Retry-After").orElse("60")); } catch(NumberFormatException ignored) {}
            throw new TemporaryFailure("Gemini está ocupado o alcanzó su cuota. Se conserva el avance; revisa los límites del proyecto si la espera continúa.",Math.max(10,Math.min(3600,delay)));
        }
        if(code==400 || code==401 || code==403 || code==404)
            throw new ConfigurationFailure("Gemini rechazó la configuración (HTTP "+code+"). Comprueba la clave, permisos y modelo en AI Studio.");
        if(code!=200) throw new TemporaryFailure("Gemini no está disponible (HTTP "+code+").",60);
    }
    Map<String,Object> chat(Map<String,Object> payload) throws Exception {
        configured();
        List<Object> contents=new ArrayList<>(); String system="";
        for(var message:(List<Map<String,Object>>)payload.get("messages")) {
            if(s(message,"role").equals("system")) {system=s(message,"content");continue;}
            List<Object> parts=new ArrayList<>(); parts.add(m("text",s(message,"content")));
            for(Object image:(List<?>)message.getOrDefault("images",List.of())) {
                byte[] bytes=Base64.getDecoder().decode(image.toString());
                String mime=bytes.length>2 && bytes[0]==(byte)0xff && bytes[1]==(byte)0xd8?"image/jpeg":"image/png";
                parts.add(m("inlineData",m("mimeType",mime,"data",image)));
            }
            contents.add(m("role","user","parts",parts));
        }
        var config=m("temperature",0,"maxOutputTokens",8192,"responseMimeType","application/json","responseSchema",payload.get("format"));
        // Preserve reasoning capacity for admission mathematics; cap hidden tokens on the tested 2.5 family.
        if(model.startsWith("gemini-2.5-")) config.put("thinkingConfig",m("thinkingBudget",2048));
        else if(model.startsWith("gemini-3")) config.put("thinkingConfig",m("thinkingLevel","LOW"));
        var body=m("systemInstruction",m("parts",List.of(m("text",system))),"contents",contents,"generationConfig",config);
        pace();
        var response=http.send(builder("/models/"+model+":generateContent")
            .POST(HttpRequest.BodyPublishers.ofString(db.json(body))).build(),HttpResponse.BodyHandlers.ofString());
        validateStatus(response);
        var root=db.json.readTree(response.body());
        var candidate=root.path("candidates").path(0);
        if(!candidate.path("finishReason").asText().equals("STOP"))
            throw new InvalidAnswer("Gemini no devolvió una solución completa; no se asignará una clave.");
        StringBuilder text=new StringBuilder();
        for(var part:candidate.path("content").path("parts")) if(!part.path("thought").asBoolean()) text.append(part.path("text").asText(""));
        if(text.isEmpty()) throw new InvalidAnswer("Gemini devolvió una respuesta vacía.");
        try { if(!db.json.readTree(text.toString()).isObject()) throw new Exception(); }
        catch(Exception e) {throw new InvalidAnswer("Gemini no devolvió una solución estructurada válida.");}
        return m("message",m("content",text.toString()));
    }
    static class ConfigurationFailure extends RuntimeException { ConfigurationFailure(String message){super(message);} }
    static class TemporaryFailure extends RuntimeException {
        final int seconds;
        TemporaryFailure(String message,int seconds){super(message);this.seconds=seconds;}
    }
    static class InvalidAnswer extends RuntimeException { InvalidAnswer(String message){super(message);} }
}
