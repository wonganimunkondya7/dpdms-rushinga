FROM maven:3.9.16-eclipse-temurin-21 AS build
ARG APP_MODULE
WORKDIR /workspace
COPY . .
RUN mvn -B -pl "${APP_MODULE}" -am package -DskipTests \
    && cp "/workspace/${APP_MODULE}/target/${APP_MODULE}-1.0.0.jar" /workspace/app.jar

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build --chown=10001:10001 /workspace/app.jar /app/app.jar
USER 10001:10001
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
