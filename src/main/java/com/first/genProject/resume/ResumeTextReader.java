package com.first.genProject.resume;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.zip.ZipInputStream;
import javax.xml.stream.*;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import com.first.genProject.exception.InvalidInputException;

/** Bounded UTF-8 / DOCX reading without writing uploaded files to disk. No OCR. */
@Component
public class ResumeTextReader {
    public static final int MAX_BYTES = 1_048_576;
    public static final int MAX_TEXT = 24_000;
    private static final String WORD = "http://schemas.openxmlformats.org/wordprocessingml/2006/main";
    public record ReadResume(String filename, String text) { }

    public ReadResume read(MultipartFile file) {
        if (file == null || file.isEmpty() || file.getSize() > MAX_BYTES)
            throw invalid("Upload a nonempty DOCX, TXT, or Markdown resume up to 1 MiB, or paste its text.");
        String filename = file.getOriginalFilename();
        if (filename == null) filename = "";
        filename = filename.replace('\\', '/');
        filename = filename.substring(filename.lastIndexOf('/') + 1);
        if (filename.isBlank() || filename.length() > 255 || filename.chars().anyMatch(Character::isISOControl))
            throw invalid("Use a readable filename of at most 255 characters.");
        String lower = filename.toLowerCase(Locale.ROOT);
        try (var input = file.getInputStream()) {
            byte[] bytes = input.readNBytes(MAX_BYTES + 1);
            if (bytes.length > MAX_BYTES) throw invalid("Resume exceeds 1 MiB.");
            String text;
            if (lower.endsWith(".docx")) text = docx(bytes);
            else if (lower.endsWith(".txt") || lower.endsWith(".md"))
                text = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString();
            else throw invalid("PDF, scanned images, and legacy DOC are not supported yet. Upload DOCX/TXT/MD or paste the resume text.");
            return new ReadResume(filename, validateText(text));
        } catch (IOException | XMLStreamException exception) {
            throw invalid("Could not read the resume. Use an unencrypted DOCX, UTF-8 text file, or paste its text.");
        }
    }

    public static String validateText(String text) {
        if (text == null) throw invalid("Resume text is required.");
        if (text.startsWith("\uFEFF")) text = text.substring(1);
        text = text.strip();
        if (text.isBlank() || text.length() > MAX_TEXT || text.chars().anyMatch(c -> Character.isISOControl(c)
                && c != '\n' && c != '\r' && c != '\t'))
            throw invalid("Resume must contain readable text between 1 and 24000 characters.");
        return text;
    }

    private String docx(byte[] bytes) throws IOException, XMLStreamException {
        byte[] document = null;
        int total = 0, entries = 0;
        try (var zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                if (++entries > 1000) throw invalid("DOCX contains too many archive entries.");
                byte[] content = zip.readNBytes(2_097_153);
                total += content.length;
                if (content.length > 2_097_152 || total > 8_388_608)
                    throw invalid("DOCX expanded content is too large. Paste the resume text instead.");
                if (entry.getName().equals("word/document.xml")) {
                    if (document != null) throw invalid("DOCX contains duplicate document content.");
                    document = content;
                }
            }
        }
        if (document == null) throw invalid("This file is not a readable DOCX document.");
        var factory = XMLInputFactory.newFactory();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factory.setProperty("javax.xml.stream.isSupportingExternalEntities", false);
        factory.setXMLResolver((publicId, systemId, base, namespace) -> { throw new XMLStreamException("External XML is disabled"); });
        var reader = factory.createXMLStreamReader(new ByteArrayInputStream(document));
        var text = new StringBuilder();
        boolean inText = false;
        try {
            while (reader.hasNext()) {
                int event = reader.next();
                if (event == XMLStreamConstants.DTD) throw invalid("DOCX external document definitions are not supported.");
                if (event == XMLStreamConstants.START_ELEMENT && WORD.equals(reader.getNamespaceURI())) {
                    if (reader.getLocalName().equals("t")) inText = true;
                    if (reader.getLocalName().equals("tab")) text.append(' ');
                    if (reader.getLocalName().equals("br")) text.append('\n');
                }
                if ((event == XMLStreamConstants.CHARACTERS || event == XMLStreamConstants.CDATA) && inText)
                    text.append(reader.getText());
                if (event == XMLStreamConstants.END_ELEMENT && WORD.equals(reader.getNamespaceURI())) {
                    if (reader.getLocalName().equals("t")) inText = false;
                    if (reader.getLocalName().equals("p")) text.append('\n');
                }
                if (text.length() > MAX_TEXT) throw invalid("Extracted resume text exceeds 24000 characters.");
            }
        } finally { reader.close(); }
        return text.toString();
    }
    private static InvalidInputException invalid(String message) { return new InvalidInputException(message); }
}
