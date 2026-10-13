FROM eclipse-temurin:21-jdk-jammy AS build

WORKDIR /workspace

COPY .mvn .mvn
COPY mvnw pom.xml ./

RUN sed -i "s/\r$//" mvnw && chmod +x mvnw
RUN ./mvnw -B -DskipTests dependency:go-offline

COPY src src

RUN ./mvnw -B -DskipTests package && JAR_FILE=$(find target -maxdepth 1 -type f -name "*.jar" ! -name "*.original" -print -quit) && test -n "$JAR_FILE" && cp "$JAR_FILE" /workspace/app.jar

FROM eclipse-temurin:21-jre-jammy

RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*
RUN groupadd --system sup2i && useradd --system --gid sup2i --home-dir /app --create-home --shell /usr/sbin/nologin sup2i

WORKDIR /app

COPY --from=build --chown=sup2i:sup2i /workspace/app.jar /app/app.jar

USER sup2i

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=40s --retries=3 CMD curl -fsS http://localhost:8080/actuator/health/readiness || exit 1

ENTRYPOINT ["java","-XX:MaxRAMPercentage=75.0","-Djava.security.egd=file:/dev/urandom","-jar","/app/app.jar"]
