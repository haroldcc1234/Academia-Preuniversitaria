FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /build

COPY pom.xml .

COPY src src

RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -ntp package -DskipTests


# ==========================================
# EJECUCIÓN
# ==========================================

FROM eclipse-temurin:21-jre

RUN groupadd --system academia \
    && useradd --system --gid academia academia \
    && mkdir -p /app/private-files \
    && chown -R academia:academia /app

WORKDIR /app

COPY --from=build /build/target/*.jar app.jar

USER academia

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
