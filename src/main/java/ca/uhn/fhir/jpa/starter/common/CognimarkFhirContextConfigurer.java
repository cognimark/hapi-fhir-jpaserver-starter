package ca.uhn.fhir.jpa.starter.common;

import ca.uhn.fhir.context.FhirContext;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.stereotype.Component;

/** Preserve unmodified JSON narrative strings in the shared source repository. */
// Created by Codex
@Component
public final class CognimarkFhirContextConfigurer implements BeanPostProcessor {
    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) {
        if (bean instanceof FhirContext context) {
            context.getParserOptions().setPreserveJsonXhtmlSource(true);
        }
        return bean;
    }
}
