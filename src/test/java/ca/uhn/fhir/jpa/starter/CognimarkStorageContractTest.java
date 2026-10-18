package ca.uhn.fhir.jpa.starter;

import ca.uhn.fhir.jpa.model.entity.ResourceIdentifierPatientUniqueEntity;
import ca.uhn.fhir.jpa.model.entity.ResourceTable;
import jakarta.persistence.Column;
import org.apache.lucene.index.DocValues;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.SortedDocValues;
import org.apache.lucene.index.SortedSetDocValues;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.util.FixedBitSet;
import org.hibernate.search.backend.lucene.lowlevel.docvalues.impl.MultiValueMode;
import org.hibernate.search.backend.lucene.lowlevel.docvalues.impl.TextMultiValuesToSingleValuesSource;
import org.hibernate.search.backend.lucene.lowlevel.join.impl.ChildDocIds;
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
        assertVersion("hapi-fhir-base", "8.12.1-cognimark.2");
        assertVersion("hapi-fhir-jpaserver-model", "8.12.1-cognimark.1");
        assertVersion("hapi-fhir-storage", "8.12.1-cognimark.6");
        assertVersion("hapi-fhir-jpa", "8.12.1-cognimark.3");
        assertVersion("hapi-fhir-storage-batch2", "8.12.1-cognimark.3");
        assertVersion("hapi-fhir-jpaserver-base", "8.12.1-cognimark.8");
        assertVersion("hapi-fhir-jpaserver-searchparam", "8.12.1-cognimark.6");
        assertVersion("hapi-fhir-storage-batch2-jobs", "8.12.1-cognimark.6");
        assertThat(Collections.list(getClass().getClassLoader().getResources(
                "ca/uhn/fhir/jpa/model/entity/ResourceTable.class"))).hasSize(1);
        assertThat(Collections.list(getClass().getClassLoader().getResources(
                "ca/uhn/fhir/jpa/dao/BaseStorageDao.class"))).hasSize(1);
        assertThat(Collections.list(getClass().getClassLoader().getResources(
                "ca/uhn/fhir/parser/JsonParser.class"))).hasSize(1);
        assertThat(Collections.list(getClass().getClassLoader().getResources(
                "ca/uhn/fhir/jpa/sched/BaseSchedulerServiceImpl.class"))).hasSize(1);
        assertThat(Collections.list(getClass().getClassLoader().getResources(
                "ca/uhn/fhir/batch2/maintenance/WorkChunkHeartbeatService.class"))).hasSize(1);
        assertThat(Collections.list(getClass().getClassLoader().getResources(
                "ca/uhn/fhir/jpa/dao/BaseHapiFhirResourceDao.class"))).hasSize(1);
        assertThat(Collections.list(getClass().getClassLoader().getResources(
                "ca/uhn/fhir/jpa/searchparam/extractor/SearchParamExtractorService.class"))).hasSize(1);
        assertThat(Collections.list(getClass().getClassLoader().getResources(
                "ca/uhn/fhir/jpa/searchparam/extractor/ReindexBatchPrefetch.class"))).hasSize(1);
        assertThat(Collections.list(getClass().getClassLoader().getResources(
                "ca/uhn/fhir/batch2/jobs/bulkmodify/reindex/ReindexV3ModifyResourcesStep.class"))).hasSize(1);
    }

    @Test
    void packagedEntitiesAgreeOnResourceIdCapacity() throws NoSuchFieldException {
        assertThat(ResourceTable.FHIR_ID_LENGTH).isEqualTo(512);
        assertThat(ResourceTable.class.getDeclaredField("myFhirId")
                .getAnnotation(Column.class).length()).isEqualTo(512);
        assertThat(ResourceIdentifierPatientUniqueEntity.class.getDeclaredField("myFhirId")
                .getAnnotation(Column.class).length()).isEqualTo(512);
    }

    @Test
    void nestedSortCanReadMissingValuesMoreThanOnce() throws IOException {
        FixedBitSet parents = new FixedBitSet(4);
        parents.set(1);
        parents.set(3);
        var source = new TextMultiValuesToSingleValuesSource(MultiValueMode.MIN, null) {
            @Override
            protected SortedSetDocValues getSortedSetDocValues(LeafReaderContext context) {
                return DocValues.emptySortedSet();
            }

            SortedDocValues values() {
                return select(DocValues.emptySortedSet(), new ChildDocIds(parents, DocIdSetIterator.empty()));
            }
        };
        SortedDocValues values = source.values();
        assertThat(values.advanceExact(1)).isFalse();
        assertThat(values.advanceExact(1)).isFalse();
        assertThat(values.docID()).isEqualTo(1);
        assertThat(values.advanceExact(3)).isFalse();
        assertThat(values.advanceExact(3)).isFalse();
        assertThat(values.docID()).isEqualTo(3);
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
