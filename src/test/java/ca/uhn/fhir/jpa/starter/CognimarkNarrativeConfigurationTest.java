package ca.uhn.fhir.jpa.starter;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.jpa.starter.common.CognimarkFhirContextConfigurer;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

// Created by Codex
class CognimarkNarrativeConfigurationTest {
    @Test
    void everyManagedFhirContextPreservesJsonNarrativeSources() {
        try (AnnotationConfigApplicationContext application = new AnnotationConfigApplicationContext()) {
            application.register(CognimarkFhirContextConfigurer.class);
            application.registerBean("r4Context", FhirContext.class, FhirContext::forR4);
            application.registerBean("otherBean", String.class, () -> "unchanged");
            application.refresh();
            assertThat(application.getBean(FhirContext.class).getParserOptions().isPreserveJsonXhtmlSource()).isTrue();
            assertThat(application.getBean("otherBean")).isEqualTo("unchanged");
        }
    }
}
