package ca.uhn.fhir.jpa.starter;

import ca.uhn.fhir.jpa.model.entity.ResourceIdentifierPatientUniqueEntity;
import ca.uhn.fhir.jpa.model.entity.ResourceTable;
import jakarta.persistence.Column;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

// Created by Codex
class CognimarkStorageContractTest {

    @Test
    void usesOneQualifiedVersionOfEachStorageLibrary() throws IOException {
        assertVersion("hapi-fhir-base", "8.12.1");
        assertVersion("hapi-fhir-jpaserver-model", "8.12.1-cognimark.1");
        assertVersion("hapi-fhir-storage", "8.12.1-cognimark.1");
        assertThat(Collections.list(getClass().getClassLoader().getResources(
                "ca/uhn/fhir/jpa/model/entity/ResourceTable.class"))).hasSize(1);
        assertThat(Collections.list(getClass().getClassLoader().getResources(
                "ca/uhn/fhir/jpa/dao/BaseStorageDao.class"))).hasSize(1);
    }

    @Test
    void packagedEntitiesAgreeOnResourceIdCapacity() throws NoSuchFieldException {
        assertThat(ResourceTable.FHIR_ID_LENGTH).isEqualTo(512);
        assertThat(ResourceTable.class.getDeclaredField("myFhirId")
                .getAnnotation(Column.class).length()).isEqualTo(512);
        assertThat(ResourceIdentifierPatientUniqueEntity.class.getDeclaredField("myFhirId")
                .getAnnotation(Column.class).length()).isEqualTo(512);
    }

    private void assertVersion(String artifactId, String version) throws IOException {
        List<URL> resources = Collections.list(getClass().getClassLoader().getResources(
                "META-INF/maven/ca.uhn.hapi.fhir/" + artifactId + "/pom.properties"));
        assertThat(resources).hasSize(1);
        Properties properties = new Properties();
        try (InputStream input = resources.get(0).openStream()) {
            properties.load(input);
        }
        assertThat(properties.getProperty("version")).isEqualTo(version);
    }
}
