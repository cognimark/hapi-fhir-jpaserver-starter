package ca.uhn.fhir.jpa.starter.common;

import ca.uhn.fhir.IHapiBootOrder;
import ca.uhn.fhir.interceptor.api.Hook;
import ca.uhn.fhir.interceptor.api.IInterceptorService;
import ca.uhn.fhir.interceptor.api.Pointcut;
import ca.uhn.fhir.jpa.searchparam.SearchParameterMap;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.rest.server.exceptions.InternalErrorException;
import ca.uhn.fhir.rest.server.exceptions.UnclassifiedServerFailureException;
import ca.uhn.fhir.rest.server.util.ICachedSearchDetails;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Operator-owned acceptance receipt for each completed tenant/type backfill. */
@Component
@ConditionalOnProperty(name = "cognimark.fulltext.readiness-file")
public final class FullTextReadinessInterceptor {
    private final IInterceptorService interceptors;
    private final ObjectMapper mapper;
    private final Path receipt;

    public FullTextReadinessInterceptor(IInterceptorService interceptors, ObjectMapper mapper,
            @Value("${cognimark.fulltext.readiness-file}") String receipt) {
        this.interceptors = interceptors;
        this.mapper = mapper;
        this.receipt = Path.of(receipt);
    }

    @EventListener(ContextRefreshedEvent.class)
    @Order(IHapiBootOrder.REGISTER_INTERCEPTORS)
    public void register() {
        interceptors.registerInterceptor(this);
    }

    @Hook(Pointcut.STORAGE_PRESEARCH_REGISTERED)
    public void check(SearchParameterMap parameters, RequestDetails request, ICachedSearchDetails search) {
        if (parameters.keySet().stream().noneMatch(
                key -> key.equals("_content") || key.equals("_text")
                        || key.startsWith("_content:") || key.startsWith("_text:"))) {
            return;
        }
        try {
            var manifest = mapper.readTree(receipt.toFile());
            if (manifest.path("schema_version").asInt() != 1 || !manifest.path("ready").isObject()) {
                throw new InternalErrorException("FHIR_INDEX_STATE_INVALID");
            }
            String tenant = request == null ? null : request.getTenantId();
            var types = tenant == null ? mapper.createArrayNode() : manifest.path("ready").path(tenant);
            if (search.getResourceType() != null && types.isArray()) {
                for (var type : types) {
                    if (type.asText().equals(search.getResourceType())) {
                        return;
                    }
                }
            }
        } catch (IOException exception) {
            throw new UnclassifiedServerFailureException(503, "FHIR_INDEX_NOT_READY: index readiness is not yet verified");
        }
        throw new UnclassifiedServerFailureException(503,
                "FHIR_INDEX_NOT_READY: full-text backfill is incomplete; structured search and resource reads remain available");
    }
}
