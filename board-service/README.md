# board-service

게시판 CRUD API — Spring Boot 4.0.9 / JDK 21 / Spring Cloud 2025.1.4 / H2

- 기본 CRUD·페이지 목록: **JPA** (`PostRepository`)
- 복잡한 조회(동적 조건 검색): **MyBatis** (`PostQueryMapper` + `resources/mapper/PostQueryMapper.xml`)
- Spring Cloud: Config Client + Eureka Discovery Client (`cloud` 프로필에서만 활성화)

## 실행

```bash
mvn test                      # 통합 테스트 (H2, MockMvc)
mvn spring-boot:run           # local 프로필: Config/Eureka 없이 단독 기동, :8081
# 클라우드 환경
SPRING_PROFILES_ACTIVE=cloud CONFIG_SERVER_URL=http://config:8888 EUREKA_URL=http://eureka:8761/eureka/ \
  java -jar target/board-service-0.0.1-SNAPSHOT.jar
```

H2 콘솔(local): http://localhost:8081/h2-console (JDBC URL `jdbc:h2:mem:board`, user `sa`)

## API

| Method | URL | 설명 |
|---|---|---|
| POST | `/api/posts` | 등록 (201 + Location) |
| GET | `/api/posts?page=0&size=10` | 페이지 목록 (JPA, 최신순) |
| GET | `/api/posts/{id}` | 상세 보기 (조회수 +1) |
| PUT | `/api/posts/{id}` | 수정 |
| DELETE | `/api/posts/{id}` | 삭제 (204) |
| GET | `/api/posts/search?keyword=&author=&from=&to=&page=&size=` | 동적 검색 (MyBatis) |

오류 응답은 RFC 9457 `ProblemDetail` 형식 (404, 400 + `errors` 필드).
