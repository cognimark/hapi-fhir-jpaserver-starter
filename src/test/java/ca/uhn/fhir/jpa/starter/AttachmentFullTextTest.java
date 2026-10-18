package ca.uhn.fhir.jpa.starter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.interceptor.api.IInterceptorService;
import ca.uhn.fhir.jpa.searchparam.fulltext.FullTextExtractionRequest;
import ca.uhn.fhir.jpa.starter.common.AttachmentFullTextInterceptor;
import ca.uhn.fhir.jpa.starter.common.AttachmentTextExtractor;
import ca.uhn.fhir.rest.server.exceptions.UnprocessableEntityException;
import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.hl7.fhir.r4.model.Binary;
import org.hl7.fhir.r4.model.DocumentReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AttachmentFullTextTest {
    private final FhirContext context = FhirContext.forR4Cached();
    private final AttachmentFullTextInterceptor interceptor =
            new AttachmentFullTextInterceptor(context, mock(IInterceptorService.class));

    @Test
    void nativeContentAndDecodedHtmlAreIndexedWithoutChangingOriginalResource() {
        var resource = new DocumentReference();
        resource.setId("DocumentReference/example");
        resource.addContent().getAttachment().setContentType("text/html")
                .setData("<html><body>Morning OFF &amp; dyskinesia</body></html>".getBytes(StandardCharsets.UTF_8));
        String before = context.newJsonParser().encodeResourceToString(resource);
        String text = interceptor.extract(new FullTextExtractionRequest(
                resource.getIdElement(), resource, "DocumentReference", () -> "native finding")).getPayload();
        assertThat(text).contains("native finding", "Morning OFF & dyskinesia").doesNotContain("<body>");
        assertThat(context.newJsonParser().encodeResourceToString(resource)).isEqualTo(before);
    }

    @Test
    void cdaTextAndClinicalAttributesAreSearchableWithoutResolvingEntities() throws Exception {
        String xml = "<?xml version=\"1.0\"?><ClinicalDocument><text>Morning wearing off</text>"
                + "<value value=\"123\" unit=\"mmHg\" displayName=\"Systolic pressure\"/></ClinicalDocument>";
        assertThat(AttachmentTextExtractor.extract(xml.getBytes(StandardCharsets.UTF_8), "application/xml"))
                .contains("Morning wearing off", "123", "mmHg", "Systolic pressure");
        String entity = "<!DOCTYPE a [<!ENTITY xxe SYSTEM \"file:///etc/passwd\">]><a>&xxe;</a>";
        assertThat(AttachmentTextExtractor.extract(entity.getBytes(StandardCharsets.UTF_8), "application/xml"))
                .doesNotContain("root:");
    }

    @Test
    void pdfAndWordDocumentsKeepExtractableText() throws Exception {
        try (var pdf = new PDDocument(); var bytes = new ByteArrayOutputStream()) {
            var page = new PDPage();
            pdf.addPage(page);
            try (var content = new PDPageContentStream(pdf, page)) {
                content.beginText();
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                content.newLineAtOffset(20, 700);
                content.showText("Morning OFF");
                content.endText();
            }
            pdf.save(bytes);
            assertThat(AttachmentTextExtractor.extract(bytes.toByteArray(), "application/pdf")).contains("Morning OFF");
        }
        var bytes = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(bytes)) {
            zip.putNextEntry(new ZipEntry("word/document.xml"));
            zip.write("<w:document><w:p><w:t>Morning OFF</w:t></w:p></w:document>".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        assertThat(AttachmentTextExtractor.extract(bytes.toByteArray(),
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document")).contains("Morning OFF");
    }

    @Test
    void binaryTextIsDecodedAndInvalidDeclaredEncodingFailsIndexing() {
        var binary = new Binary().setContentType("text/plain").setData("Morning OFF".getBytes(StandardCharsets.UTF_8));
        var request = new FullTextExtractionRequest(binary.getIdElement(), binary, "Binary", () -> "");
        assertThat(interceptor.extract(request).getPayload()).contains("Morning OFF");
        binary.setContentType("text/plain; charset=UTF-8").setData(new byte[] {(byte) 0xff});
        assertThatThrownBy(() -> interceptor.extract(request)).isInstanceOf(UnprocessableEntityException.class)
                .hasMessageContaining("FHIR_TEXT_EXTRACTION_FAILED");
    }

    @ParameterizedTest
    @ValueSource(strings = {"ISO-8859-1", "windows-1252", "UTF-16", "UTF-8"})
    void undeclaredTextEncodingIsDetectedWithoutChangingOriginalAttachment(String encoding) {
        String text = ("The document contains a nonbreaking\u00a0space between these words. "
                + "The original attachment is preserved and its complete text is searchable. ").repeat(5);
        if (encoding.equals("windows-1252")) {
            text += "The author\u2019s \u201cquoted statement\u201d is also searchable.";
        }
        var resource = new DocumentReference();
        resource.setId("DocumentReference/encoded-text");
        resource.addContent().getAttachment().setContentType("text/plain")
                .setData(text.getBytes(Charset.forName(encoding)));
        String before = context.newJsonParser().encodeResourceToString(resource);
        String payload = interceptor.extract(new FullTextExtractionRequest(
                resource.getIdElement(), resource, "DocumentReference", () -> "native text")).getPayload();
        assertThat(payload).contains(text).doesNotContain("\ufffd");
        assertThat(context.newJsonParser().encodeResourceToString(resource)).isEqualTo(before);
    }

    @Test
    void declaredCharsetIsHonoredAndJsonRequiresUtf8() throws Exception {
        byte[] bytes = "A nonbreaking\u00a0space".getBytes(StandardCharsets.ISO_8859_1);
        assertThat(AttachmentTextExtractor.extract(bytes, "text/plain; charset=ISO-8859-1"))
                .isEqualTo("A nonbreaking\u00a0space");
        assertThatThrownBy(() -> AttachmentTextExtractor.extract(bytes, "text/plain; charset=UTF-8"))
                .isInstanceOf(java.io.IOException.class);
        assertThatThrownBy(() -> AttachmentTextExtractor.extract(bytes, "application/json"))
                .isInstanceOf(java.io.IOException.class);
    }

    @Test
    void deletionUsesNativeIndexRemoval() {
        assertThat(interceptor.extract(new FullTextExtractionRequest(
                null, null, "Binary", () -> null)).isIndexNormally()).isTrue();
    }
}
