# syntax=docker/dockerfile:1
# 构建：在 monorepo 根目录执行 docker compose -f docker-compose.prod.yml build
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build
COPY pom.xml .
COPY src ./src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
RUN useradd --system --uid 10001 --no-create-home srm \
    && mkdir -p /data/invoice-files \
    && chown -R srm:srm /data/invoice-files

COPY --from=build /build/target/srm-backend-*.jar /app/app.jar
USER srm
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
