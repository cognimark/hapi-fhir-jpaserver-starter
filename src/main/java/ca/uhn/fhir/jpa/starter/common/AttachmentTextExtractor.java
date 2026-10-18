package ca.uhn.fhir.jpa.starter.common;

import com.ibm.icu.text.CharsetDetector;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.zip.ZipInputStream;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;
import org.springframework.http.MediaType;

/** Extract text locally without changing attachments or following their URLs. */
public final class AttachmentTextExtractor {
    private AttachmentTextExtractor() {}

    public static String extract(byte[] data, String contentType) throws IOException {
        if (data == null || data.length == 0 || contentType == null || contentType.isBlank()) {
            return "";
        }
        MediaType media = MediaType.parseMediaType(contentType);
        String type = (media.getType() + "/" + media.getSubtype()).toLowerCase(Locale.ROOT);
        if (type.equals("application/pdf")) {
            try (var pdf = Loader.loadPDF(data)) {
                return new PDFTextStripper().getText(pdf);
            }
        }
        if (type.equals("application/vnd.openxmlformats-officedocument.wordprocessingml.document")) {
            StringBuilder text = new StringBuilder();
            try (var zip = new ZipInputStream(new ByteArrayInputStream(data))) {
                for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                    if (!entry.isDirectory() && entry.getName().startsWith("word/")
                            && entry.getName().endsWith(".xml")) {
                        text.append(' ').append(markup(zip.readAllBytes(), true, null));
                    }
                }
            }
            return text.toString();
        }
        String charset = media.getCharset() == null ? null : media.getCharset().name();
        if (type.equals("text/html") || type.equals("application/xhtml+xml")) {
            return markup(data, false, charset);
        }
        if (type.equals("text/xml") || type.equals("application/xml") || type.endsWith("+xml")) {
            return markup(data, true, charset);
        }
        if (media.getType().equalsIgnoreCase("text") || type.equals("application/json")
                || type.endsWith("+json")) {
            return text(data, media.getCharset(), media.getType().equalsIgnoreCase("text"));
        }
        // Image/OCR and opaque binary formats have no text extraction capability.
        return "";
    }

    private static String text(byte[] data, Charset declared, boolean detectEncoding) throws IOException {
        Charset encoding = declared;
        if (encoding == null) {
            try {
                return StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(data)).toString();
            } catch (CharacterCodingException exception) {
                // Undeclared text may use a different encoding. JSON remains UTF-8,
                // and an explicit charset is never replaced by a detected one.
                if (!detectEncoding) {
                    throw new IOException("Attachment text has invalid character encoding");
                }
            }
            var match = new CharsetDetector().setText(data).detect();
            if (match == null) {
                throw new IOException("Attachment text encoding could not be determined");
            }
            encoding = Charset.forName(match.getName());
        }
        // Never replace undecodable bytes with U+FFFD or drop them from the index.
        return encoding.newDecoder().decode(ByteBuffer.wrap(data)).toString();
    }

    private static String markup(byte[] data, boolean xml, String charset) throws IOException {
        // jsoup does not resolve external entities or load linked resources.
        var document = Jsoup.parse(new ByteArrayInputStream(data), charset, "",
                xml ? Parser.xmlParser() : Parser.htmlParser());
        document.select("script,style").remove();
        StringBuilder text = new StringBuilder(document.text());
        if (xml) {
            // CDA often keeps codes, units, values, and display names in attributes.
            for (Element element : document.getAllElements()) {
                element.attributes().forEach(attribute -> text.append(' ').append(attribute.getValue()));
            }
        }
        return text.toString();
    }
}
