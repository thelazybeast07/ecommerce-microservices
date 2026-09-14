# ---- Build stage: full JDK + Maven, discarded after the build ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace

# Copy the POM first so the dependency layer is cached until pom.xml changes
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
# Tests run in CI (they need Docker for Testcontainers), not inside the image build
RUN mvn -B -q package -DskipTests

# ---- Runtime stage: JRE only, non-root user ----
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN groupadd --system app && useradd --system --gid app app

COPY --from=build /workspace/target/user-service-*.jar app.jar

USER app
EXPOSE 8081
# Size the heap from the container memory limit instead of the host's RAM
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75"
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
