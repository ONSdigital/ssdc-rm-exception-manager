FROM openjdk:17-slim

CMD ["/usr/local/openjdk-17/bin/java", "-jar", "/opt/ssdc-rm-exception-manager.jar"]

RUN apt-get update && \
apt-get -yq install curl && \
apt-get -yq clean && \
rm -rf /var/lib/apt/lists/*

RUN groupadd --gid 999 exceptionmanager && \
    useradd --create-home --system --uid 999 --gid exceptionmanager exceptionmanager
USER exceptionmanager

ARG JAR_FILE=ssdc-rm-exception-manager*.jar
COPY target/$JAR_FILE /opt/ssdc-rm-exception-manager.jar
