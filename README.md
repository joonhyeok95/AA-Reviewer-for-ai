# AA-Reviewer-for-ai

Flowlog 업무 시스템 모노레포. 아래 3개 프로젝트를 커밋 이력을 유지한 채 한 저장소로 합쳤다.

| 폴더 | 역할 | 포트 |
|---|---|---|
| [spring-boilerplate](spring-boilerplate) | 백엔드 API (Spring Boot 4.1, Java 17). 인증·인가(Keycloak OIDC BFF / SAML2 / Bearer), 권한·메뉴·공통코드 관리, 다국어 공지사항(게시판) | 9091 (관리 9191) |
| [spring-gateway](spring-gateway) | API Gateway (Spring Cloud Gateway). `/cm/**` → 백엔드, `/**` → 화면, Rate Limit | 9909 |
| [vue-frontend](vue-frontend) | 화면 (Vue 3 + TypeScript) | 5173 (개발 서버, Gateway 경유 접속) |

## 한 번에 실행

```powershell
./spring-boilerplate/scripts/dev-up.ps1     # Redis·Keycloak + 백엔드 + Gateway + 화면
./spring-boilerplate/scripts/dev-down.ps1   # 종료 (-Infra : 컨테이너도 중지)
```
```bash
./spring-boilerplate/scripts/dev-up.sh
./spring-boilerplate/scripts/dev-down.sh
```

접속: http://localhost:9909 · 기본은 LAN 모드라 같은 네트워크의 다른 기기에서 `http://{이 PC IP}:9909` 로 접속 (이 PC 전용은 `-Local` / `--local`) · 자세한 내용은 [spring-boilerplate/README.md](spring-boilerplate/README.md)

## 운영 배포 (AWS EC2 1대 + ALB)

[deploy/README.md](deploy/README.md) - ALB 가 HTTPS 를 끝내고 EC2 는 80(HTTP)만, H2 파일 DB, Docker Compose
(Gateway · 백엔드 · 화면 · Keycloak(PostgreSQL) · Redis). 지금 PC 데이터 이전(`deploy/migrate/oracle-to-h2.sh`),
매일 백업, 로컬 운영 구성 검증(`deploy/verify`, Caddy 가 ALB 역할) 포함.

## 문서

- [개발 가이드](spring-boilerplate/docs/DEVELOPMENT_GUIDE.md)
- [전체 ERD](spring-boilerplate/docs/design/00_erd_overview.md) · [권한/메뉴/공통코드](spring-boilerplate/docs/design/01_auth_menu_code.md) · [인증](spring-boilerplate/docs/design/02_auth_keycloak.md) · [공지사항](spring-boilerplate/docs/design/03_notice.md)
- [테스트 결과](spring-boilerplate/docs/reports/TEST_REPORT.md) · [보안 점검](spring-boilerplate/docs/reports/SECURITY_REVIEW.md)
- [화면 개발 가이드](vue-frontend/docs/DEVELOPMENT_GUIDE.md)

## CI (`.github/workflows`)

| 워크플로 | 대상 | 내용 |
|---|---|---|
| `backend-ci.yml` | `spring-boilerplate/**` | 빌드·테스트, Oracle 스키마, 인증 E2E |
| `gateway-ci.yml` | `spring-gateway/**` | 빌드·테스트 (Testcontainers Redis) |
| `notion-sync.yml` | `spring-boilerplate/docs/**` | 문서 → 노션 동기화 (Secret `NOTION_TOKEN` 필요, 없으면 건너뜀) |

> realm import 파일·local 설정의 계정 비밀번호와 client secret 은 **로컬 개발 전용** 값이다. prod 프로필은 이 값으로 기동하면 실패한다 (`SecurityStartupValidator`).
