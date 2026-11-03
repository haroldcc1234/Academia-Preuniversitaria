package pe.jauja.academia;

import java.io.*;
import java.nio.charset.StandardCharsets;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xslf.usermodel.*;

public class DocumentText {
    public static String extract(String name, byte[] data) throws Exception {
        String n = name.toLowerCase();
        if (n.endsWith(".pdf")) {
            try (var doc = Loader.loadPDF(data)) {
                return new PDFTextStripper().getText(doc);
            }
        }
        if (n.endsWith(".docx")) {
            try (var doc = new XWPFDocument(new ByteArrayInputStream(data))) {
                return String.join("\n", doc.getParagraphs().stream().map(p -> p.getText()).toList());
            }
        }
        if (n.endsWith(".pptx")) {
            try (var slides = new XMLSlideShow(new ByteArrayInputStream(data))) {
                StringBuilder text = new StringBuilder();
                for (var slide : slides.getSlides())
                    for (var shape : slide.getShapes())
                        if (shape instanceof XSLFTextShape t)
                            text.append(t.getText()).append('\n');
                return text.toString();
            }
        }
        return n.endsWith(".txt") ? new String(data, StandardCharsets.UTF_8) : "";
    }
}
