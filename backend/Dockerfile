FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -B dependency:go-offline
COPY src ./src
RUN mvn -B package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Upgrade OS packages to apply security patches (e.g. brotli-libs)
# Create dedicated non-root user and group (appuser:appgroup, UID/GID 10001)
RUN apk update && apk upgrade --no-cache && \
    addgroup -S appgroup -g 10001 && \
    adduser -S appuser -u 10001 -G appgroup && \
    chown -R appuser:appgroup /app

COPY --from=build --chown=appuser:appgroup /app/target/parking-system.jar app.jar

USER appuser:appgroup

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
