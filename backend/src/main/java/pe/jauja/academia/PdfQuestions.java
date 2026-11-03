package pe.jauja.academia;

import java.awt.image.BufferedImage;
import java.io.*;
import java.util.*;
import java.util.regex.*;
import javax.imageio.ImageIO;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.*;
import static pe.jauja.academia.Store.*;

/** Layout-aware splitting. Question crops preserve diagrams and continuation across columns/pages. */
public class PdfQuestions {
    static final Pattern HEADING = Pattern.compile("(?iu)^\\s*(?:PREGUNTA\\s*(?:N[.º°o\\s]*|NÚMERO\\s*)?|(?:\\d{1,3})[.)]\\s+)");
    static final Pattern NUMBER = Pattern.compile("\\d{1,3}");
    public record Piece(int page, float x, float y, float width, float height) {}
    public static class Question {
        int number;
        StringBuilder text = new StringBuilder();
        List<Piece> pieces = new ArrayList<>();
        Question(int number) { this.number=number; }
    }
    record Glyph(String text, float x, float y, float width, float height) {}
    record Line(String text, float x, float y, float height) {}
    static class Layout extends PDFTextStripper {
        List<Glyph> glyphs = new ArrayList<>();
        Layout() throws IOException { super(); }
        @Override protected void processTextPosition(TextPosition t) {
            if(t.getDir()!=0) return; // rotated chart labels have a different coordinate system
            glyphs.add(new Glyph(t.getUnicode(),t.getXDirAdj(),t.getYDirAdj(),t.getWidthDirAdj(),t.getHeightDir()));
        }
        List<Line> lines(float left, float right) {
            var selected=glyphs.stream().filter(g->g.x>=left && g.x<right).sorted(Comparator.comparingDouble(Glyph::y)).toList();
            List<List<Glyph>> groups=new ArrayList<>();
            for(var g:selected) {
                if(groups.isEmpty() || Math.abs(groups.get(groups.size()-1).get(0).y-g.y)>2.4) groups.add(new ArrayList<>());
                groups.get(groups.size()-1).add(g);
            }
            List<Line> out=new ArrayList<>();
            for(var group:groups) {
                group.sort(Comparator.comparingDouble(Glyph::x));
                var text=new StringBuilder(); float last=-1;
                for(var g:group) { if(last>=0 && g.x-last>1.8) text.append(' '); text.append(g.text); last=g.x+g.width; }
                var first=group.get(0);
                out.add(new Line(text.toString(),first.x,first.y,(float)group.stream().mapToDouble(Glyph::height).max().orElse(10)));
            }
            return out;
        }
    }
    static int marker(Line line) {
        if(!HEADING.matcher(line.text).find()) return -1;
        var number=NUMBER.matcher(line.text);
        return number.find()?Integer.parseInt(number.group()):-1;
    }
    static boolean chrome(Line line, float height) {
        String t=line.text.strip();
        return line.y<32 || line.y>height-28 || t.matches("\\d{1,3}")
            || t.matches("(?iu).*(Lumbreras Editores|Academia CÉSAR VALLEJO|UNI 20\\d\\d-[12]).*");
    }
    public static List<Question> split(PDDocument doc) throws IOException {
        check(doc.getNumberOfPages()<=100,"El PDF supera 100 páginas. Divide el material.");
        List<Layout> layouts=new ArrayList<>(); boolean columns=false;
        for(int p=0;p<doc.getNumberOfPages();p++) {
            var layout=new Layout(); layout.setStartPage(p+1); layout.setEndPage(p+1); layout.getText(doc); layouts.add(layout);
            float width=doc.getPage(p).getCropBox().getWidth();
            var right=layout.lines(width/2,width);
            if(right.stream().anyMatch(l->marker(l)>0)) columns=true;
        }
        boolean explicit=layouts.stream().anyMatch(l->l.lines(0,10000).stream().anyMatch(a->a.text.strip().toUpperCase().startsWith("PREGUNTA")));
        List<Question> questions=new ArrayList<>(); Question current=null;
        for(int p=0;p<layouts.size();p++) {
            if(layouts.get(p).lines(0,10000).stream().anyMatch(l->l.text.strip().matches("(?iu)(SOLUCIONARIO|CLAVES(?: DE RESPUESTA)?|RESPUESTAS|SOLUCIONES)"))) break;
            var page=doc.getPage(p); float width=page.getCropBox().getWidth(), height=page.getCropBox().getHeight();
            for(int col=0;col<(columns?2:1);col++) {
                float left=col==0?0:width/2, right=columns && col==0?width/2:width;
                var lines=layouts.get(p).lines(left,right).stream().filter(l->!chrome(l,height)).toList();
                var anchors=lines.stream().filter(l->marker(l)>0 && (!explicit || l.text.strip().toUpperCase().startsWith("PREGUNTA"))).toList();
                if(anchors.isEmpty()) {
                    // Preserve continuation only on text pages within the question body; skip image covers/adverts.
                    if(current!=null && !lines.isEmpty() && p<layouts.size()-1) add(current,p,left,38,right-left,height-70,lines);
                    continue;
                }
                float firstY=anchors.get(0).y-anchors.get(0).height-4;
                if(current!=null && firstY>55) {
                    var prefix=lines.stream().filter(l->l.y<firstY).toList();
                    if(prefix.stream().mapToInt(l->l.text.length()).sum()>35) add(current,p,left,38,right-left,firstY-38,prefix);
                }
                for(int i=0;i<anchors.size();i++) {
                    var a=anchors.get(i); int n=marker(a);
                    float top=Math.max(0,a.y-a.height-4);
                    float bottom=i+1<anchors.size()?anchors.get(i+1).y-anchors.get(i+1).height-5:height-32;
                    current=new Question(n); questions.add(current);
                    add(current,p,left,top,right-left,bottom-top,lines.stream().filter(l->l.y>=top && l.y<bottom).toList());
                }
            }
        }
        // Numbered list items are not question boundaries when explicit PREGUNTA headings exist.
        check(questions.size()<=300,"Se detectaron más de 300 preguntas. Divide el material.");
        // A gap can mean an image-only page or a missed heading: use the visual fallback, not a partial publication.
        if(explicit) for(int i=1;i<questions.size();i++) if(questions.get(i).number!=questions.get(i-1).number+1) return List.of();
        return questions;
    }
    static void add(Question q,int page,float x,float y,float width,float height,List<Line> lines) {
        if(height<5) return;
        q.pieces.add(new Piece(page,x,y,width,height));
        for(var line:lines) q.text.append(line.text).append('\n');
    }
    static byte[] crop(BufferedImage page, Piece piece, float scale) throws IOException {
        int x=Math.max(0,(int)(piece.x*scale)), y=Math.max(0,(int)(piece.y*scale));
        int w=Math.min(page.getWidth()-x,Math.max(1,(int)(piece.width*scale)));
        int h=Math.min(page.getHeight()-y,Math.max(1,(int)(piece.height*scale)));
        var out=new ByteArrayOutputStream(); ImageIO.write(page.getSubimage(x,y,w,h),"png",out); return out.toByteArray();
    }
    public static List<Map<String,Object>> extractVisual(byte[] pdf,FilesService files,BulletinAi ai,Runnable heartbeat) throws Exception {
        try(var doc=Loader.loadPDF(pdf)) {
            check(doc.getNumberOfPages()<=100,"Máximo 100 páginas.");
            var renderer=new PDFRenderer(doc);List<Map<String,Object>> out=new ArrayList<>();
            for(int p=0;p<doc.getNumberOfPages();p++) {
                heartbeat.run();
                var image=renderer.renderImageWithDPI(p,144);var bytes=new ByteArrayOutputStream();ImageIO.write(image,"png",bytes);
                for(var q:ai.scan(bytes.toByteArray())) {
                    double left=num(q,"left"),top=num(q,"top"),right=num(q,"right"),bottom=num(q,"bottom");
                    check(Double.isFinite(left+top+right+bottom) && left>=0 && top>=0 && right<=1 && bottom<=1 && right>left && bottom>top,"Región visual inválida. Reintenta el procesamiento.");
                    check(n(q,"number",0)>0 && !s(q,"text").isBlank(),"Pregunta visual incompleta.");
                    var piece=new Piece(p,(float)(left*image.getWidth()),(float)(top*image.getHeight()),(float)((right-left)*image.getWidth()),(float)((bottom-top)*image.getHeight()));
                    var key=files.save(crop(image,piece,1),"image/png");
                    out.add(m("source_number",q.get("number"),"source_text",q.get("text"),"images",List.of(key)));
                    check(out.size()<=300,"Máximo 300 preguntas por balotario.");
                }
            }
            heartbeat.run();
            return out;
        }
    }
    public static List<Map<String,Object>> extract(byte[] pdf, FilesService files) throws IOException {
        try(var doc=Loader.loadPDF(pdf)) {
            var questions=split(doc); var renderer=new PDFRenderer(doc);
            List<Map<String,Object>> out=new ArrayList<>(); int cachedPage=-1; BufferedImage image=null;
            for(var q:questions) {
                List<String> keys=new ArrayList<>();
                for(var piece:q.pieces) {
                    if(cachedPage!=piece.page) { image=renderer.renderImageWithDPI(piece.page,144); cachedPage=piece.page; }
                    keys.add(files.save(crop(image,piece,2),"image/png"));
                }
                out.add(m("source_number",q.number,"source_text",q.text.toString(),"images",keys));
            }
            return out;
        }
    }
}
