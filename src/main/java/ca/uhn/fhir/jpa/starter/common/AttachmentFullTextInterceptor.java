package ca.uhn.fhir.jpa.starter.common;

import ca.uhn.fhir.IHapiBootOrder;
import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.interceptor.api.Hook;
import ca.uhn.fhir.interceptor.api.IInterceptorService;
import ca.uhn.fhir.interceptor.api.Pointcut;
import ca.uhn.fhir.jpa.searchparam.fulltext.FullTextExtractionRequest;
import ca.uhn.fhir.jpa.searchparam.fulltext.FullTextExtractionResponse;
import ca.uhn.fhir.rest.server.exceptions.UnprocessableEntityException;
import java.io.IOException;
import org.hl7.fhir.r4.model.Attachment;
import org.hl7.fhir.r4.model.Binary;
import org.hl7.fhir.r4.model.DomainResource;
import org.hl7.fhir.r4.model.MarkdownType;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** One extraction path for ingestion, native reindexing, updates, and deletes. */
@Component
public final class AttachmentFullTextInterceptor {
    private final FhirContext context;
    private final IInterceptorService interceptors;

    public AttachmentFullTextInterceptor(FhirContext context, IInterceptorService interceptors) {
        this.context = context;
        this.interceptors = interceptors;
    }

    @EventListener(ContextRefreshedEvent.class)
    @Order(IHapiBootOrder.REGISTER_INTERCEPTORS)
    public void register() {
        interceptors.registerInterceptor(this);
    }

    @Hook(Pointcut.JPA_INDEX_EXTRACT_FULLTEXT_CONTENT)
    public FullTextExtractionResponse extract(FullTextExtractionRequest request) {
        if (request.isDelete()) {
            return FullTextExtractionResponse.indexNormally();
        }
        StringBuilder content = new StringBuilder();
        String nativeText = request.getDefaultString();
        if (nativeText != null) {
            content.append(nativeText);
        }
        var resource = request.getResource();
        if (resource instanceof DomainResource domain && domain.hasText()) {
            content.append(' ').append(domain.getText().getDiv().allText());
        }
        var terser = context.newTerser();
        for (MarkdownType markdown : terser.getAllPopulatedChildElementsOfType(resource, MarkdownType.class)) {
            content.append(' ').append(markdown.getValue());
        }
        try {
            for (Attachment attachment : terser.getAllPopulatedChildElementsOfType(resource, Attachment.class)) {
                if (attachment.hasData()) {
                    content.append(' ').append(AttachmentTextExtractor.extract(
                            attachment.getData(), attachment.getContentType()));
                }
            }
            if (resource instanceof Binary binary && binary.hasData()) {
                content.append(' ').append(AttachmentTextExtractor.extract(binary.getData(), binary.getContentType()));
            }
        } catch (IOException | IllegalArgumentException exception) {
            // Do not turn corrupt supported content into a successfully indexed
            // resource, or include clinical text/parser exception details in logs.
            throw new UnprocessableEntityException("FHIR_TEXT_EXTRACTION_FAILED: attachment text could not be decoded");
        }
        return FullTextExtractionResponse.indexPayload(content.toString());
    }
}
