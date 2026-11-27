# Image of config-server. It packages the jar that "./mvnw clean verify" already built and
# tested, so what runs in the container is what passed the quality gates:
#
#   ./mvnw clean verify
#   docker build -t config-server:0.0.1-SNAPSHOT .
#
# Where the Config Server, Eureka, Vault and the rest are comes from environment variables
# (see infra/docker-compose.yml). No secret is copied into the image.

# Splits the jar into layers: the dependencies, which rarely change, stay cached between
# builds and only the layer with the service's own classes is rebuilt and pushed.
FROM eclipse-temurin:17-jre AS layers
WORKDIR /workspace
COPY target/config-server-*.jar application.jar
RUN java -Djarmode=tools -jar application.jar extract --layers --destination extracted

FROM eclipse-temurin:17-jre
RUN useradd --system --uid 10001 --no-create-home banking
WORKDIR /app
COPY --from=layers /workspace/extracted/dependencies/ ./
COPY --from=layers /workspace/extracted/spring-boot-loader/ ./
COPY --from=layers /workspace/extracted/snapshot-dependencies/ ./
COPY --from=layers /workspace/extracted/application/ ./
USER 10001
EXPOSE 8888
# The heap follows the memory limit of the container instead of the memory of the host.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70"
ENTRYPOINT ["java", "-jar", "application.jar"]
