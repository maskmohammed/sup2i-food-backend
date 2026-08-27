# syntax=docker/dockerfile:1

# ---------- Stage 1: build ----------
FROM eclipse-temurin:21-jdk AS build

WORKDIR /app

# Dependency layer first so it is cached across builds unless pom.xml changes.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B dependency:go-offline

# Source layer — invalidates only the compile/package step on code changes.
COPY src ./src

RUN ./mvnw -B package -DskipTests

# ---------- Stage 2: runtime ----------
FROM eclipse-temurin:21-jre AS runtime

WORKDIR /app

COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
