package com.first.genProject.resume;

import static org.junit.jupiter.api.Assertions.*;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import com.first.genProject.exception.*;

class ResumeTextReaderTest {
    private final ResumeTextReader reader = new ResumeTextReader();
    @Test
    void readsUtf8TextAndTreatsFilenameAsMetadata() {
        var file = new MockMultipartFile("file", "C:\\temp\\resume.txt", "text/plain", "\uFEFFPython {developer}\nSQL".getBytes(StandardCharsets.UTF_8));
        var result = reader.read(file);
        assertEquals("resume.txt", result.filename());
        assertEquals("Python {developer}\nSQL", result.text());
    }
    @Test
    void readsDocxTextRunsAndParagraphs() throws Exception {
        var result = reader.read(docx("""
                <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body>
                <w:p><w:r><w:t>Py</w:t></w:r><w:r><w:t>thon SQL</w:t></w:r></w:p>
                <w:p><w:r><w:t>Docker</w:t></w:r></w:p></w:body></w:document>
                """));
        assertTrue(result.text().contains("Python SQL"));
        assertTrue(result.text().contains("Docker"));
    }
    @Test
    void rejectsPdfCorruptDocxInvalidUtf8AndOversizeBeforeAi() {
        assertThrows(InvalidInputException.class, () -> reader.read(new MockMultipartFile("file", "resume.pdf", "application/pdf", "%PDF-1.7".getBytes(StandardCharsets.UTF_8))));
        assertThrows(InvalidInputException.class, () -> reader.read(new MockMultipartFile("file", "resume.docx", "application/octet-stream", new byte[]{1,2,3})));
        assertThrows(InvalidInputException.class, () -> reader.read(new MockMultipartFile("file", "resume.txt", "text/plain", new byte[]{(byte)0xff})));
        assertThrows(InvalidInputException.class, () -> reader.read(new MockMultipartFile("file", "resume.txt", "text/plain", new byte[ResumeTextReader.MAX_BYTES+1])));
        assertThrows(InvalidInputException.class, () -> ResumeTextReader.validateText("x".repeat(24001)));
    }
    @Test
    void rejectsExternalXmlDefinitions() throws Exception {
        var file = docx("<!DOCTYPE x [<!ENTITY external SYSTEM 'file:///nonexistent-resume-test'>]><w:document xmlns:w='http://schemas.openxmlformats.org/wordprocessingml/2006/main'><w:p><w:r><w:t>&external;</w:t></w:r></w:p></w:document>");
        assertThrows(InvalidInputException.class, () -> reader.read(file));
    }
    @Test
    void evidenceMustBePresentAndContainWholeSkillName() {
        assertThrows(AiProviderException.class, () -> ResumeService.verifySkills("I use JavaScript", List.of(new ResumeSkill("Java", "I use JavaScript"))));
        assertThrows(AiProviderException.class, () -> ResumeService.verifySkills("I use SQL", List.of(new ResumeSkill("Python", "I use Python"))));
        assertThrows(AiProviderException.class, () -> ResumeService.verifySkills("SQL", List.of(new ResumeSkill("SQL", "SQL"), new ResumeSkill("sql", "SQL"))));
        assertEquals("Python", ResumeService.verifySkills("Built with Python\nand SQL", List.of(new ResumeSkill("Python", "with Python and SQL"))).getFirst().name());
        assertTrue(ResumeService.verifySkills("No technical skills.", List.of()).isEmpty());
    }
    private MockMultipartFile docx(String xml) throws Exception {
        var bytes = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(bytes)) {
            zip.putNextEntry(new ZipEntry("word/document.xml"));
            zip.write(xml.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return new MockMultipartFile("file", "resume.docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", bytes.toByteArray());
    }
}
