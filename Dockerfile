# syntax=docker/dockerfile:1

# builder
FROM eclipse-temurin:21-jdk-jammy AS builder

WORKDIR /workspace

COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle

COPY api/build.gradle ./api/build.gradle
COPY core/build.gradle ./core/build.gradle

RUN chmod +x gradlew

RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew :api:dependencies --no-daemon

COPY api/src ./api/src
COPY core/src ./core/src

RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew :api:bootJar --no-daemon


# runtime
FROM eclipse-temurin:21-jre-jammy AS runtime

RUN groupadd --gid 10001 connect \
    && useradd --uid 10001 \
        --gid connect \
        --no-create-home \
        --shell /usr/sbin/nologin \
        connect

WORKDIR /app

COPY --from=builder \
    --chown=connect:connect \
    /workspace/api/build/libs/*.jar \
    ./app.jar

USER connect

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]