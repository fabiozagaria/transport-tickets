# syntax=docker/dockerfile:1
FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /build
COPY pom.xml ./
COPY src/ src/
# Optional public proxy CA for managed environments; never stored in image layers.
RUN --mount=type=cache,target=/root/.m2 --mount=type=secret,id=proxy_ca \
    if [ -f /run/secrets/proxy_ca ]; then \
      cp "$JAVA_HOME/lib/security/cacerts" /tmp/build-truststore; \
      keytool -importcert -noprompt -alias session-proxy -file /run/secrets/proxy_ca \
        -keystore /tmp/build-truststore -storepass changeit; \
      export MAVEN_OPTS="$MAVEN_OPTS -Djavax.net.ssl.trustStore=/tmp/build-truststore -Djavax.net.ssl.trustStorePassword=changeit"; \
    fi; \
    if [ -n "$HTTPS_PROXY" ]; then \
      endpoint="${HTTPS_PROXY#*://}"; host="${endpoint%%:*}"; port="${endpoint##*:}"; \
      printf '<settings><proxies><proxy><id>build</id><active>true</active><protocol>http</protocol><host>%s</host><port>%s</port></proxy></proxies></settings>' "$host" "$port" > /tmp/maven-settings.xml; \
    else printf '<settings/>' > /tmp/maven-settings.xml; fi; \
    mvn -B -s /tmp/maven-settings.xml package dependency:copy-dependencies -DincludeScope=runtime \
    && rm -f /tmp/build-truststore /tmp/maven-settings.xml

FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
COPY --from=build /build/target/transport-tickets-0.1.0-SNAPSHOT.jar /app/app.jar
ENV BIND_ADDRESS=0.0.0.0
USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
