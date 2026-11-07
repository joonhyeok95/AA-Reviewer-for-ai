# 운영 배포 - AWS EC2 1대 + ALB (H2 파일 DB)

```
사용자 ─ HTTPS 443 ─▶ ALB (ACM 인증서, TLS 종료) ─ HTTP 80 ─▶ EC2 (Docker Compose)
                                                              gateway ─┬─▶ backend (H2 파일 DB · 첨부/사진 · 매일 백업)
                                                                       ├─▶ web (화면)
                                                                       └─▶ keycloak (로그인 화면 /realms/**) ─▶ keycloak-db
                                                              redis (세션 · Rate Limit)
```

- **DB 는 H2 파일** 하나 (`/data/db/flowlog.mv.db`, Docker 볼륨 `flowlog-app-data`). 서버는 1대만 (H2 파일은 한 프로세스만 연다)
- **HTTPS 는 ALB 에서 끝**내고 EC2 는 80(HTTP)만 받는다. 앱은 ALB 가 붙인 `X-Forwarded-Proto: https` 로 https 주소·Secure 쿠키를 만든다
- 로그인(Keycloak)도 같은 도메인의 `/realms/**` 로 Gateway 를 거친다 → **ALB·보안 그룹에 여는 포트는 443(ALB)·80(EC2) 뿐**
- 파일 : `docker-compose.yml`(운영), `.env.example`→`.env`(비밀값), `gen-secrets.sh`, `ec2-setup.sh`, `import-data.sh`, `migrate/oracle-to-h2.sh`, `verify/`(로컬 검증)

---

## 1. AWS 준비 (콘솔에서 한 번)

| 항목 | 설정 |
|---|---|
| EC2 | Amazon Linux 2023, **t3.large**(8GB) 권장 (t3.medium 4GB + 스왑도 동작하지만 빠듯), EBS gp3 30GB 이상, 퍼블릭 서브넷(또는 NAT) - 이미지 받기·자기 도메인 호출에 아웃바운드 필요 |
| IAM 역할 (EC2) | `AmazonSSMManagedInstanceCore` (SSH 없이 Session Manager 로 접속). 데이터 이전에 S3 를 쓰면 해당 버킷 읽기 권한 |
| 보안 그룹 - ALB | 인바운드 443, 80 (0.0.0.0/0) |
| 보안 그룹 - EC2 | 인바운드 **80 : ALB 보안 그룹만**. 22(SSH)는 열지 않음. 아웃바운드 전체 |
| ACM | 도메인 인증서 (EC2 와 같은 리전) |
| 대상 그룹 | HTTP 80, 인스턴스, 상태 확인 경로 `/` (200), 간격 30초 |
| ALB | 리스너 443 → 대상 그룹 (ACM 인증서), 리스너 80 → 443 리디렉션. 유휴 제한 시간 60초(기본) |
| DNS | 도메인 A(별칭) → ALB |
| 백업 | AWS Backup 으로 EC2(EBS) 매일 스냅샷, 30일 보관 (앱의 H2 일일 백업과 2중) |

> 백엔드는 **기동할 때 `https://{도메인}/realms/flowlog` 를 조회**합니다 (ALB → 이 서버 Gateway → Keycloak).
> DNS·ALB·대상 그룹이 먼저 준비돼 있어야 백엔드가 뜹니다. (그 전에는 백엔드가 재시작을 반복하다가 준비되면 정상 기동)

## 2. EC2 준비 (Session Manager 로 접속해서 한 번)

```bash
sudo dnf install -y git
git clone https://github.com/joonhyeok95/AA-Reviewer-for-ai.git flowlog && cd flowlog
sudo bash deploy/ec2-setup.sh        # Docker·Compose·Buildx, 스왑 2GB, 시간대
exit                                 # 다시 접속 (docker 그룹 적용)
```

## 3. 설정 (.env)

```bash
cd ~/flowlog/deploy
./gen-secrets.sh church.example.com  # .env 생성 + 비밀값 무작위 생성 (이미 있는 값은 유지)
cat .env                             # APP_ADMIN_PASSWORD = 앱 admin 로그인 비밀번호
```

- `.env` 는 커밋하지 않는다 (`deploy/.gitignore`). 분실 대비로 비밀번호 관리 도구나 SSM Parameter Store 에 보관
- `SESSION_ENCRYPTION_KEY` 를 바꾸면 모두 다시 로그인, `H2_PASSWORD` 는 DB 를 처음 만들 때 정해진다 (이전한 DB 는 이전 때 쓴 값과 같아야 함)
- 테스트 계정(tx.manager, emp01 …)은 비활성화된다 (`KEEP_TEST_USERS=false`). admin 만 사용

## 4. 지금 PC 의 데이터 옮기기 (선택)

지금 PC 의 영속 DB(Docker Oracle)에 입력한 주보·교리문답·생일·사진을 그대로 옮긴다. 옮기지 않으면 첫 기동 때 초기 데이터(샘플)로 시작한다.

```bash
# 이 PC (Git Bash, 저장소 루트) - H2_PASSWORD 는 EC2 의 deploy/.env 값과 같게
H2_PASSWORD='EC2 .env 의 값' deploy/migrate/oracle-to-h2.sh
#  → 테이블별 행 수를 Oracle 과 비교한 뒤 deploy/export/flowlog-data.tgz (DB + 첨부·사진)

# EC2 로 전달 (예 : 비공개 S3 버킷)
aws s3 cp deploy/export/flowlog-data.tgz s3://<버킷>/flowlog-data.tgz      # 이 PC
aws s3 cp s3://<버킷>/flowlog-data.tgz ~/flowlog-data.tgz                  # EC2

# EC2 : 볼륨에 넣기 (기존 데이터는 /data/before-import-날짜 로 보관)
cd ~/flowlog/deploy && ./import-data.sh ~/flowlog-data.tgz
```

> 개인정보(이름·생일·계좌)가 들어 있습니다. 옮긴 뒤 S3 객체와 이 PC 의 `deploy/export/` 는 지우세요.

## 5. 실행

```bash
cd ~/flowlog/deploy
docker compose up -d --build         # 첫 빌드 5~10분
docker compose ps                    # 모두 healthy / keycloak-setup 은 Exited (0)
docker compose logs -f backend       # "[DB] 기존 데이터 사용" 또는 "빈 DB - 스키마·초기 데이터 생성"
```

`https://{도메인}` → admin / `APP_ADMIN_PASSWORD` 로 로그인.

## 6. 업데이트

```bash
cd ~/flowlog && git pull && cd deploy && docker compose up -d --build
```

- 백엔드가 다시 뜨는 동안(약 30초) 접속이 끊긴다 (1대 구성)
- Redis 를 다시 만들면 세션이 지워져 다시 로그인
- DDL 이 바뀐 업데이트는 기존 H2 에 직접 `ALTER` 가 필요하다 (`docs/design` 의 변경 안내 참고)

## 7. 백업 · 복구

| 종류 | 내용 |
|---|---|
| H2 일일 백업 | 매일 03:30 `BACKUP TO` (서비스 중단 없음) → 볼륨 `/data/backup/flowlog-날짜.zip`, 14일 보관 (`H2_BACKUP_KEEP_DAYS`) |
| EBS 스냅샷 | AWS Backup 매일 (DB·첨부·Keycloak DB 전체) |

```bash
# 백업 파일을 꺼내기
docker run --rm -v flowlog-app-data:/data -v "$PWD":/out alpine cp /data/backup/flowlog-20261108-0330.zip /out/

# 복구 : 백엔드 정지 → zip 의 flowlog.mv.db 로 교체 → 시작
docker compose stop backend
docker run --rm -v flowlog-app-data:/data -v "$PWD":/in alpine sh -c \
  "cd /data/db && mv flowlog.mv.db flowlog.mv.db.bak && unzip -o /in/flowlog-20261108-0330.zip && chown 10001:10001 flowlog.mv.db"
docker compose start backend
```

## 8. 운영 관리

- **Keycloak 관리 콘솔** : EC2 의 127.0.0.1:7000 에만 열려 있다. 내 PC 에서 포트 포워딩 후 http://localhost:7000 (master / `KC_ADMIN_PASSWORD`)
  ```bash
  aws ssm start-session --target <인스턴스ID> --document-name AWS-StartPortForwardingSession \
    --parameters '{"portNumber":["7000"],"localPortNumber":["7000"]}'
  ```
- **로그** : `docker compose logs -f backend gateway` (컨테이너당 20MB × 5개까지 보관)
- **사용자 추가** : Keycloak 에서 사용자를 만들고 앱 `TB_USER.IDP_SUBJECT` 에 그 사용자 ID 를 연결해야 권한이 매핑된다 (절차 미확정 - `docs/design/02_auth_keycloak.md` 남은 과제)
- **메모리** : backend·gateway 는 컨테이너 메모리의 70%까지 힙 (`JAVA_TOOL_OPTIONS`)

## 9. 로컬에서 운영 구성 검증 (ALB 대신 Caddy)

```bash
cd deploy
docker compose --env-file verify/verify.env -f docker-compose.yml -f verify/docker-compose.verify.yml up -d --build
# https://flowlog.localtest.me (Caddy 내부 인증서. localtest.me 는 공개 DNS 가 127.0.0.1 로 응답)
cd ../vue-frontend && E2E_BASE_URL=https://flowlog.localtest.me E2E_IGNORE_HTTPS_ERRORS=1 npm run e2e
```

`verify/verify.env` 는 `verify/verify.env.example` 을 복사해 채운다 (검증용 값, 테스트 계정 유지, gitignore). 운영 `.env` 와 섞지 말 것.
