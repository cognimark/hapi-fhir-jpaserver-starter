FROM docker.io/library/maven:3.9.12-eclipse-temurin-17@sha256:a0603aab698040d9c94259f379ec0487da1678560748d6c7508483034033c53d AS build-hapi
WORKDIR /tmp/hapi-fhir-jpaserver-starter

ARG OPENTELEMETRY_JAVA_AGENT_VERSION=2.24.0
RUN curl -fLSsO https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/v${OPENTELEMETRY_JAVA_AGENT_VERSION}/opentelemetry-javaagent.jar \
    && printf '%s  %s\n' '5c48cd48f9907f824214e22f14a28375c92613a94b0cf98381997e0a678220ce' opentelemetry-javaagent.jar | sha256sum --check -

# The complete custom JARs are built from the fork, never injected as loose classes.
# All non-custom HAPI dependencies are supplied by the official 8.12.1 release.
RUN git init /tmp/cognimark-hapi-core \
    && git -C /tmp/cognimark-hapi-core remote add origin https://github.com/cognimark/hapi-fhir.git \
    && git -C /tmp/cognimark-hapi-core fetch --depth 1 --filter=blob:none origin 58f3e8a121c8cbc2545fafb147dc90114af107b8 \
    && git -C /tmp/cognimark-hapi-core sparse-checkout set hapi-deployable-pom hapi-fhir-jpaserver-model hapi-fhir-storage \
    && git -C /tmp/cognimark-hapi-core checkout --detach FETCH_HEAD \
    && test "$(git -C /tmp/cognimark-hapi-core rev-parse HEAD)" = 58f3e8a121c8cbc2545fafb147dc90114af107b8 \
    && mvn -B -ntp -f /tmp/cognimark-hapi-core/hapi-fhir-jpaserver-model/pom.xml -Dtest=ResourceTableTest install \
    && mvn -B -ntp -f /tmp/cognimark-hapi-core/hapi-fhir-storage/pom.xml -Dtest=BaseStorageDaoResourceIdTest install

COPY pom.xml .
COPY server.xml .

COPY src/ /tmp/hapi-fhir-jpaserver-starter/src/
RUN mvn -B -ntp -Dtest=CognimarkStorageContractTest -DskipITs -Pboot package

FROM build-hapi AS build-distroless
RUN mkdir /app && cp /tmp/hapi-fhir-jpaserver-starter/target/ROOT.war /app/main.war

RUN cp /tmp/hapi-fhir-jpaserver-starter/target/classes/HealthCheck.class /app/HealthCheck.class


########### Use the official Tomcat image as base image for the Tomcat variant
########### it can be built using eg. `docker build --target tomcat .`
FROM docker.io/library/tomcat:10-jre21-temurin-noble AS tomcat

USER root
RUN rm -rf /usr/local/tomcat/webapps/ROOT && \
    mkdir -p /usr/local/tomcat/data/hapi/lucenefiles && \
    chown -R 65532:65532 /usr/local/tomcat/data/hapi/lucenefiles && \
    chmod 775 /usr/local/tomcat/data/hapi/lucenefiles

RUN mkdir -p /target && chown -R 65532:65532 /target
USER 65532

COPY --chown=65532:65532 catalina.properties /usr/local/tomcat/conf/catalina.properties
COPY --chown=65532:65532 server.xml /usr/local/tomcat/conf/server.xml
COPY --from=build-hapi --chown=65532:65532 /tmp/hapi-fhir-jpaserver-starter/target/ROOT.war /usr/local/tomcat/webapps/ROOT.war
COPY --from=build-hapi --chown=65532:65532 /tmp/hapi-fhir-jpaserver-starter/opentelemetry-javaagent.jar /app

########### distroless brings focus on security and runs on plain spring boot - this is the default image
FROM gcr.io/distroless/java21-debian13:nonroot@sha256:0a1f5a75661918de9c0813f287f651c3bf2d6dd752eada5f084eb0c1f14ced9e AS default
LABEL ai.cognimark.hapi.upstream-version="8.12.1" \
      ai.cognimark.hapi.core-revision="58f3e8a121c8cbc2545fafb147dc90114af107b8" \
      ai.cognimark.hapi.storage-version="8.12.1-cognimark.1" \
      ai.cognimark.hapi.resource-id-limit="512"
# 65532 is the nonroot user's uid
# used here instead of the name to allow Kubernetes to easily detect that the container
# is running as a non-root (uid != 0) user.
USER 65532:65532
WORKDIR /app

COPY --chown=nonroot:nonroot --from=build-distroless /app /app
COPY --chown=nonroot:nonroot --from=build-hapi /tmp/hapi-fhir-jpaserver-starter/opentelemetry-javaagent.jar /app

ENTRYPOINT ["java", "--class-path", "/app/main.war", "-Dloader.path=main.war!/WEB-INF/classes/,main.war!/WEB-INF/,/app/extra-classes", "org.springframework.boot.loader.PropertiesLauncher"]
