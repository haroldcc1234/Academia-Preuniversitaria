package pe.jauja.academia;

import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.apache.pdfbox.Loader;
import static org.junit.jupiter.api.Assertions.*;

class PdfQuestionsTest {
    @Test
    @EnabledIfSystemProperty(named="samplePdf",matches=".+")
    void sampleKeepsOneHundredQuestionsAndContinuation() throws Exception {
        try(var pdf=Loader.loadPDF(Files.readAllBytes(Path.of(System.getProperty("samplePdf"))))) {
            var qs=PdfQuestions.split(pdf);
            Files.createDirectories(Path.of("target/pdf-review"));
            Files.writeString(Path.of("target/pdf-review/questions.txt"),String.join("\n\n",qs.stream().map(q->"QUESTION "+q.number+" PIECES "+q.pieces+"\n"+q.text).toList()));
            assertEquals(100,qs.size());
            for(int i=0;i<100;i++) assertEquals(i+1,qs.get(i).number);
            assertTrue(qs.get(14).pieces.size()>1,"Pregunta 15 continúa en otra página");
            var renderer=new org.apache.pdfbox.rendering.PDFRenderer(pdf);
            for(int index:List.of(0,1,2,14,42,99)) {
                var q=qs.get(index);int pieceNumber=0;
                for(var piece:q.pieces) Files.write(Path.of("target/pdf-review/q"+q.number+"-"+(pieceNumber++)+".png"),
                    PdfQuestions.crop(renderer.renderImageWithDPI(piece.page(),144),piece,2));
            }
        }
    }
}
