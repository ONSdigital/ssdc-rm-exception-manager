FROM eclipse-temurin:17-jre-alpine

CMD ["java", "-jar", "/opt/ssdc-rm-exception-manager.jar"]
RUN addgroup --gid 1000 exceptionmanager && \
    adduser --system --uid 1000 exceptionmanager exceptionmanager
USER exceptionmanager

COPY target/ssdc-rm-exception-manager*.jar /opt/ssdc-rm-exception-manager.jar
