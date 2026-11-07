# ============================================================
# spring-boilerplate 운영 이미지 : 빌드(Maven) → 실행(JRE 17, 비루트)
#   docker build -t flowlog/backend .
#   데이터(H2 파일·첨부·백업)는 /data 볼륨 - deploy/docker-compose.yml 참고
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
 && mkdir -p /app /data && chown -R app:app /app /data
WORKDIR /app
COPY --from=build --chown=app:app /app.jar /app/app.jar
USER app
ENV TZ=Asia/Seoul \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70 -Djava.awt.headless=true -Dfile.encoding=UTF-8"
EXPOSE 9091
# Actuator 는 컨테이너 안 127.0.0.1:9191 에만 (외부 노출 없음)
HEALTHCHECK --interval=15s --timeout=5s --start-period=90s --retries=5 \
  CMD curl -fs http://127.0.0.1:9191/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
