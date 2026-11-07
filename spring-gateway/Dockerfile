# ============================================================
# spring-gateway 운영 이미지 : 빌드(Maven) → 실행(JRE 17, 비루트)
#   docker build -t flowlog/gateway .
# ============================================================
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /src
COPY pom.xml ./
RUN --mount=type=cache,target=/root/.m2 mvn -q -B dependency:go-offline
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -q -B -DskipTests package \
 && cp target/*.jar /app.jar

FROM eclipse-temurin:17-jre
RUN groupadd --system --gid 10001 app && useradd --system --uid 10001 --gid app --home /app app \
 && mkdir -p /app && chown -R app:app /app
WORKDIR /app
COPY --from=build --chown=app:app /app.jar /app/app.jar
USER app
ENV TZ=Asia/Seoul \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70 -Dfile.encoding=UTF-8"
EXPOSE 9909
# Actuator 는 컨테이너 안 127.0.0.1:9910 에만
HEALTHCHECK --interval=15s --timeout=5s --start-period=60s --retries=5 \
  CMD curl -fs http://127.0.0.1:9910/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
