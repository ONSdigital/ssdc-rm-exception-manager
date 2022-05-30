FROM eclipse-temurin:17-jdk-alpine

CMD ["/opt/java/openjdk/bin/java", "-jar", "/opt/ssdc-rm-exception-manager.jar"]

RUN addgroup --gid 1000 exceptionmanager && \
    adduser --system --uid 1000 exceptionmanager exceptionmanager
USER exceptionmanager

ARG JAR_FILE=ssdc-rm-exception-manager*.jar

COPY target/$JAR_FILE /opt/ssdc-rm-exception-manager.jar
