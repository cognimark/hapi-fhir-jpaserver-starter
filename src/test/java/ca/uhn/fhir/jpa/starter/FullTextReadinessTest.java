package ca.uhn.fhir.jpa.starter;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ca.uhn.fhir.interceptor.api.IInterceptorService;
import ca.uhn.fhir.jpa.starter.common.FullTextReadinessInterceptor;
import ca.uhn.fhir.jpa.searchparam.SearchParameterMap;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.rest.param.StringParam;
import ca.uhn.fhir.rest.server.util.ICachedSearchDetails;
import ca.uhn.fhir.rest.server.exceptions.UnclassifiedServerFailureException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FullTextReadinessTest {
    @TempDir Path directory;

    @Test
    void unfinishedBackfillsCannotProduceMisleadingEmptySearchResults() throws Exception {
        Path receipt = directory.resolve("coverage.json");
        var gate = new FullTextReadinessInterceptor(mock(IInterceptorService.class), new ObjectMapper(), receipt.toString());
        var request = mock(RequestDetails.class);
        when(request.getTenantId()).thenReturn("cohort-source");
        var search = mock(ICachedSearchDetails.class);
        when(search.getResourceType()).thenReturn("DocumentReference");
        var parameters = new SearchParameterMap();
        parameters.add("_content", new StringParam("morning"));
        assertThatThrownBy(() -> gate.check(parameters, request, search)).isInstanceOf(UnclassifiedServerFailureException.class)
                .hasMessageContaining("FHIR_INDEX_NOT_READY");
        Files.writeString(receipt, "{\"schema_version\":1,\"ready\":{\"cohort-source\":[\"DocumentReference\"]}}");
        assertThatCode(() -> gate.check(parameters, request, search)).doesNotThrowAnyException();
        when(request.getTenantId()).thenReturn("another-source");
        assertThatThrownBy(() -> gate.check(parameters, request, search)).isInstanceOf(UnclassifiedServerFailureException.class);
        parameters.remove("_content");
        parameters.add("status", new StringParam("current"));
        assertThatCode(() -> gate.check(parameters, request, search)).doesNotThrowAnyException();
    }
}
