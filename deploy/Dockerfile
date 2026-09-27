FROM maven:3.9.16-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY . .
RUN mvn -B package -DskipTests

FROM eclipse-temurin:21-jre
ARG APP_MODULE
WORKDIR /app
COPY --from=build --chown=10001:10001 /workspace/${APP_MODULE}/target/${APP_MODULE}-1.0.0.jar /app/app.jar
USER 10001:10001
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
