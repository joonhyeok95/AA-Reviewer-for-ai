# EC2 운영 배포 런북 (처음 세팅 → 서비스 오픈 → 보안 점검)

| 항목 | 내용 |
|---|---|
| 대상 | AWS EC2 1대 + ALB, 서울 리전(ap-northeast-2) |
| 구성 | ALB 가 HTTPS 를 끝내고 EC2 는 80(HTTP)만. Docker Compose : Gateway · 백엔드(H2 파일 DB) · 화면 · Keycloak(PostgreSQL) · Redis |
| 소요 | AWS 리소스 30~40분 + EC2 세팅·첫 빌드 15~20분 |
| 원본 | 저장소 `spring-boilerplate/docs/operations/EC2_DEPLOY_RUNBOOK.md` (노션은 자동 동기화본), 파일은 저장소 루트 `deploy/` |
| 표기 | `<도메인>` 예: church.example.com, `<인스턴스ID>` 예: i-0123…, `<버킷>` 데이터 이전용 S3 버킷 |

> 이 문서에는 비밀번호·키를 적지 않는다. 비밀값은 EC2 의 `deploy/.env` 에서 만들고 비밀번호 관리 도구에 보관한다.

```
사용자 ─ HTTPS 443 ─▶ ALB (ACM 인증서) ─ HTTP 80 ─▶ EC2 (Docker Compose)
                                                     gateway ─┬─▶ backend (H2 파일 DB · 첨부/사진 · 매일 백업)
                                                              ├─▶ web (화면)
                                                              └─▶ keycloak (로그인 /realms/**) ─▶ keycloak-db
                                                     redis (세션 · Rate Limit)
```

---

## 0. 준비물 체크

- [ ] AWS 계정 (루트 계정 MFA 설정, 작업은 IAM 사용자/Identity Center 로)
- [ ] 도메인 (Route 53 또는 외부 DNS 에서 레코드를 추가할 수 있어야 함)
- [ ] 내 PC : AWS CLI v2 + Session Manager 플러그인 (EC2 콘솔 접속·포트 포워딩용), 데이터 이전 시 Git Bash
- [ ] 결정 : 인스턴스 t3.large(권장, 8GB) / t3.medium(4GB + 스왑, 빠듯)

---

## 1. AWS 리소스 만들기 (콘솔, 위에서부터 순서대로)

### 1-1. IAM 역할 `flowlog-ec2-role`
- 신뢰 엔터티 : EC2
- 정책 : `AmazonSSMManagedInstanceCore` (Session Manager 접속)
- (데이터 이전 시) 인라인 정책 - 이전용 버킷 읽기만 : `s3:GetObject` on `arn:aws:s3:::<버킷>/*`
- (선택, 모니터링) `CloudWatchAgentServerPolicy`

### 1-2. 보안 그룹 2개 (EC2 와 같은 VPC)

| 이름 | 인바운드 | 아웃바운드 |
|---|---|---|
| `sg-flowlog-alb` | HTTPS 443 · HTTP 80 ← 0.0.0.0/0 (IPv6 쓰면 ::/0 도) | 기본(전체) |
| `sg-flowlog-ec2` | **HTTP 80 ← `sg-flowlog-alb` (보안 그룹 ID 지정)** 만. 22(SSH) 열지 않음 | 기본(전체) - 이미지 받기, 자기 도메인 조회 |

### 1-3. EC2 인스턴스
| 설정 | 값 |
|---|---|
| AMI | Amazon Linux 2023 (x86_64 또는 arm64 - arm64 는 t4g.large) |
| 유형 | t3.large |
| 키 페어 | **없음** (SSH 대신 Session Manager) |
| 네트워크 | 퍼블릭 서브넷 + 퍼블릭 IP 자동 할당 (또는 프라이빗 서브넷 + NAT) / 보안 그룹 `sg-flowlog-ec2` |
| 스토리지 | gp3 30GB 이상, **암호화 켬** (기본 KMS 키) |
| 고급 → IAM 인스턴스 프로파일 | `flowlog-ec2-role` |
| 고급 → 메타데이터 | **IMDSv2 필수(Required)**, **홉 제한 1** → 컨테이너가 인스턴스 역할 자격 증명을 가져가지 못하게 |
| 고급 → 종료 방지 | 켬 |
| 태그 | `Name=flowlog`, `Backup=daily` (AWS Backup 대상 지정용) |

이미 만든 인스턴스의 메타데이터 설정 :
```bash
aws ec2 modify-instance-metadata-options --instance-id <인스턴스ID> \
  --http-tokens required --http-put-response-hop-limit 1 --http-endpoint enabled
```

### 1-4. ACM 인증서
- 리전 : EC2·ALB 와 같은 ap-northeast-2, 도메인 `<도메인>`, **DNS 검증** → 안내된 CNAME 을 DNS 에 추가 → "발급됨" 확인

### 1-5. 대상 그룹 `tg-flowlog`
| 설정 | 값 |
|---|---|
| 대상 유형 / 프로토콜 | 인스턴스 / HTTP 80 |
| 상태 확인 | 경로 `/`, 성공 코드 `200`, 정상 임계 2, 비정상 임계 3, 간격 30초, 제한 시간 5초 |
| 대상 등록 | EC2 인스턴스, 포트 80 |
| 속성 | 등록 취소 지연 30초 (1대라 길 필요 없음) |

### 1-6. ALB `alb-flowlog`
| 설정 | 값 |
|---|---|
| 체계 | 인터넷 경계, IPv4, 서브넷 2개 이상(AZ 다르게), 보안 그룹 `sg-flowlog-alb` |
| 리스너 HTTPS 443 | 기본 작업 → `tg-flowlog`, 인증서 = 1-4, **보안 정책 `ELBSecurityPolicy-TLS13-1-2-2021-06`** (TLS 1.2 이상) |
| 리스너 HTTP 80 | 기본 작업 → HTTPS 443 으로 **리디렉션(301)** |
| 속성 | **잘못된 헤더 필드 삭제 켬**, **삭제 방지 켬**, 유휴 제한 60초, (선택) 액세스 로그 → S3 |

### 1-7. DNS
- `<도메인>` A 레코드 **별칭(Alias)** → `alb-flowlog` (외부 DNS 는 CNAME → ALB DNS 이름)
- 확인 : `nslookup <도메인>` 이 응답하면 다음 단계

> 백엔드는 **기동할 때 `https://<도메인>/realms/flowlog` 를 조회**한다 (ALB → 이 서버). 1-4 ~ 1-7 이 끝난 뒤 2장을 진행한다.

### 1-8. 백업 (AWS Backup)
- 백업 계획 : 매일 1회, 보관 30일, 리소스 할당 = 태그 `Backup=daily`
- 앱도 매일 03:30 H2 백업 zip 을 만든다 (7장) → 2중 보관

---

## 2. EC2 초기 세팅 (Session Manager)

EC2 콘솔 → 인스턴스 선택 → **연결 → Session Manager → 연결**. (또는 `aws ssm start-session --target <인스턴스ID>`)

```bash
# 2-1. 저장소 받기 (/opt/flowlog)
sudo dnf install -y git
sudo mkdir -p /opt/flowlog && sudo chown "$(whoami)" /opt/flowlog
git clone https://github.com/joonhyeok95/AA-Reviewer-for-ai.git /opt/flowlog

# 2-2. 서버 준비 (Docker·Compose·Buildx, 보안 업데이트 자동, 스왑 2GB, 시간대)
sudo bash /opt/flowlog/deploy/ec2-setup.sh
exit
```

세션을 다시 연결한 뒤(docker 그룹 적용) 확인 :
```bash
docker compose version
cat /etc/docker/daemon.json                 # "no-new-privileges": true
systemctl is-active dnf-automatic.timer     # active
```

`ec2-setup.sh` 가 하는 일 : Docker 데몬 보안 기본값(`no-new-privileges`, 로그 20MB×5, `live-restore`), 보안 패치 자동 적용(`dnf-automatic`), 스왑 2GB, Asia/Seoul, `ssm-user`·`ec2-user` docker 그룹.

---

## 3. 설정 (.env - 비밀값)

```bash
cd /opt/flowlog/deploy
./gen-secrets.sh <도메인>     # .env 생성, 비어 있는 비밀값을 무작위로 채움 (권한 600)
cat .env                      # 확인 후 비밀번호 관리 도구에 보관
```

| 값 | 용도 |
|---|---|
| `DOMAIN` | 서비스 도메인 (https:// 없이) |
| `APP_ADMIN_PASSWORD` | 앱 admin **처음** 비밀번호 (첫 기동 때 한 번만 적용, 이후 Keycloak 에서 바꾼 값 유지) |
| `KC_ADMIN_PASSWORD` | Keycloak 관리 콘솔(master) 관리자 |
| `KC_DB_PASSWORD` · `BFF_CLIENT_SECRET` · `BATCH_CLIENT_SECRET` | 내부 비밀값 |
| `SESSION_ENCRYPTION_KEY` | 세션 암호화 (바꾸면 전원 재로그인) |
| `H2_PASSWORD` | H2 DB 비밀번호 (DB 를 처음 만들 때 정해짐 - 데이터 이전 시 같은 값 사용) |
| `KEEP_TEST_USERS=false` | 테스트 계정 비활성화 |

(선택) SSM Parameter Store 에 보관 : `aws ssm put-parameter --name /flowlog/env --type SecureString --value file://.env`

---

## 4. 데이터 옮기기 (선택 - 지금 PC 의 데이터를 그대로)

옮기지 않으면 첫 기동 때 초기 데이터(샘플)로 시작한다.

```bash
# 내 PC (Git Bash, 저장소 루트) - H2_PASSWORD 는 EC2 .env 의 값
H2_PASSWORD='<EC2 .env 의 H2_PASSWORD>' deploy/migrate/oracle-to-h2.sh
#   → "테이블 N개, 행 N건 모두 일치" 확인 후 deploy/export/flowlog-data.tgz
aws s3 cp deploy/export/flowlog-data.tgz s3://<버킷>/flowlog-data.tgz
```
```bash
# EC2
aws s3 cp s3://<버킷>/flowlog-data.tgz ~/flowlog-data.tgz
cd /opt/flowlog/deploy && ./import-data.sh ~/flowlog-data.tgz
rm ~/flowlog-data.tgz
```
- 이전용 버킷 : **퍼블릭 액세스 차단**, SSE 암호화, 옮긴 뒤 객체 삭제. 내 PC 의 `deploy/export/` 도 삭제 (개인정보)

---

## 5. 실행 · 오픈 확인

```bash
cd /opt/flowlog/deploy
docker compose up -d --build            # 첫 빌드 5~10분
docker compose ps                       # 모두 Up(healthy), keycloak-setup 은 Exited (0)
docker compose logs backend | grep -E "\[DB\]|Started"
```

| 확인 | 기대 |
|---|---|
| 대상 그룹 상태 | healthy |
| `https://<도메인>` → admin / `APP_ADMIN_PASSWORD` 로그인 | 대시보드 |
| `http://<도메인>` | https 로 301 |
| `https://<도메인>/cm/actuator/health` · `/cm/h2-console` · `/cm/swagger-ui.html` · `/cm/v3/api-docs` | 404 (외부 비공개) |
| `https://<도메인>/admin/` · `/realms/master/` | 앱 화면이 열림 (Keycloak 관리 콘솔·master realm 은 외부로 전달하지 않음) |
| EC2 퍼블릭 IP:80 으로 직접 접속 | 응답 없음 (보안 그룹이 ALB 만 허용) |
| 첫 로그인 후 | Keycloak 계정 화면에서 admin 비밀번호 변경 권장 |

---

## 6. 업데이트

```bash
cd /opt/flowlog && git pull && cd deploy && docker compose up -d --build
```
- 백엔드가 다시 뜨는 동안(30초~1분) 접속 끊김 (1대 구성) → 사용이 적은 시간에
- DDL 이 바뀐 업데이트는 기존 H2 에 `ALTER` 필요 (변경 안내 확인)
- Keycloak scope 목록이 같으면 후처리는 30초 안에 끝난다 (바뀌면 다시 등록, 수 분)

## 7. 백업 · 복구

| 종류 | 위치 | 주기·보관 |
|---|---|---|
| H2 온라인 백업 (서비스 중단 없음) | 볼륨 `/data/backup/flowlog-날짜.zip` | 매일 03:30, 14일 (`H2_BACKUP_KEEP_DAYS`, `H2_BACKUP_CRON`) |
| EBS 스냅샷 | AWS Backup | 매일, 30일 |

```bash
# 백업 꺼내기
docker run --rm -v flowlog-app-data:/data -v "$PWD":/out alpine cp /data/backup/flowlog-20261108-0330.zip /out/
# 복구 : 백엔드 정지 → DB 파일 교체 → 시작
docker compose stop backend
docker run --rm -v flowlog-app-data:/data -v "$PWD":/in alpine sh -c \
  "cd /data/db && mv flowlog.mv.db flowlog.mv.db.bak && unzip -o /in/flowlog-20261108-0330.zip && chown 10001:10001 flowlog.mv.db"
docker compose start backend
```
- 분기마다 한 번 **복구 연습** (백업 zip 으로 다른 폴더에 풀어 열리는지)

## 8. 일상 운영

| 작업 | 방법 |
|---|---|
| Keycloak 관리 콘솔 | EC2 의 127.0.0.1:7000 에만 열림 → 내 PC 에서 `aws ssm start-session --target <인스턴스ID> --document-name AWS-StartPortForwardingSession --parameters '{"portNumber":["7000"],"localPortNumber":["7000"]}'` 후 http://localhost:7000 |
| admin 비밀번호 초기화 | `.env` 의 `APP_ADMIN_PASSWORD` 수정 → `RESET_ADMIN_PASSWORD=true docker compose up -d keycloak-setup` |
| 로그 | `docker compose logs -f backend gateway` (컨테이너당 20MB×5) |
| 재부팅 | Docker·컨테이너 자동 시작 (`restart: unless-stopped`) |
| 사용자 추가 | Keycloak 사용자 생성 + 앱 `TB_USER.IDP_SUBJECT` 연결 필요 (절차 미확정 - 당분간 admin 사용) |

---

## 9. 보안 조치

### 9-1. 네트워크
| 조치 | 적용 위치 |
|---|---|
| 인터넷 노출은 ALB 443(80→443 리디렉션)뿐, EC2 80 은 **ALB 보안 그룹에서만** | 1-2 |
| SSH(22) 미개방, 키 페어 없음 - 접속은 Session Manager (IAM 인증 + CloudTrail 기록) | 1-3 |
| TLS 1.2 이상 보안 정책, ACM 인증서 자동 갱신 | 1-6 |
| ALB "잘못된 헤더 필드 삭제" - 비정상 헤더로 백엔드를 속이는 시도 차단 | 1-6 |
| Keycloak 관리 콘솔·master realm 은 Gateway 로 전달하지 않음 (127.0.0.1:7000, SSM 포트 포워딩만) | compose, Gateway `idp` 라우트 |
| Redis·PostgreSQL·백엔드·Actuator 는 Docker 내부망에만 (포트 미공개) | compose |
| Gateway 는 ALB(사설 대역)가 붙인 `X-Forwarded-*` 만 신뢰, RFC 7239 `Forwarded` 는 항상 제거 (https→http 오인·host 위조 방지) | Gateway |
| Rate Limit 은 ALB 가 알린 **실제 클라이언트 IP** 기준 (`RATE_LIMIT_TRUSTED_PROXY_HOPS=1`) | compose |

### 9-2. 인스턴스
| 조치 | 적용 위치 |
|---|---|
| **IMDSv2 필수 + 홉 제한 1** → 컨테이너(추가 홉)에서 인스턴스 역할 자격 증명 탈취 불가 | 1-3 |
| EBS 암호화, 스냅샷도 암호화 | 1-3, 1-8 |
| IAM 역할 최소 권한 (SSM + 이전용 버킷 읽기만), 이전 끝나면 S3 정책 제거 | 1-1 |
| OS 보안 패치 자동 적용 (`dnf-automatic`, 매일). 커널 업데이트는 월 1회 재부팅 | 2 |
| 종료 방지, ALB 삭제 방지 | 1-3, 1-6 |

### 9-3. 컨테이너
| 조치 | 적용 위치 |
|---|---|
| 백엔드·Gateway 는 비루트(uid 10001), 화면은 nginx-unprivileged | Dockerfile |
| `no-new-privileges` (setuid 로 권한 상승 불가) - 데몬 기본값 + compose | ec2-setup, compose |
| 로그 크기 제한(20MB×5) - 디스크 고갈 방지 | ec2-setup, compose |
| 이미지 버전 고정 (redis 8.10.3, keycloak 26.8.1, postgres 17), 헬스체크 | compose |

### 9-4. 애플리케이션 (이미 적용 - 상세는 [보안 점검 및 조치 리포트](../reports/SECURITY_REVIEW.md))
- prod 프로필 **기동 검사** : https issuer, Secure 쿠키, local 기본 비밀값 사용 시 기동 실패
- H2 콘솔·Swagger 비활성화, Actuator 는 컨테이너 내부 관리 포트만
- 세션은 Redis 에 AES-256-GCM 암호화 저장, 브라우저에는 SESSION 쿠키만 (토큰 미노출), CSRF
- 인가 : IdP scope ∩ DB 권한, 기본 거부
- 업로드 : 내용 시그니처 검사, 사진은 다시 그려 저장(EXIF·GPS 제거, 화소 제한), 본인 게시물만, 예약 공지 첨부 비공개
- PDF : 외부 자원 접근 차단, 동시 생성 제한

### 9-5. 계정 · 비밀값
| 조치 | 적용 위치 |
|---|---|
| 비밀값 무작위 생성, `.env` 권한 600·git 제외 | gen-secrets |
| realm 의 local client secret 을 운영 값으로 교체, 로그인 콜백은 `https://<도메인>` 만 | Keycloak 후처리 |
| 테스트 계정(tx.manager, emp01 …) 비활성화 | Keycloak 후처리 |
| **비밀번호 정책** : 12자 이상, 사용자명·이메일과 다르게, 최근 3개 재사용 금지 (`KC_PASSWORD_POLICY`) | Keycloak 후처리 |
| 로그인 무차별 대입 차단 (Keycloak brute force 보호) | realm |
| admin 비밀번호는 첫 기동 때만 설정 → 이후 직접 바꾼 값 유지 | Keycloak 후처리 |
| 비밀값 교체 : `.env` 수정 후 `docker compose up -d` (세션 키 교체는 전원 재로그인) | 3 |

### 9-6. 데이터
- H2 매일 백업 + EBS 스냅샷(암호화) 2중 보관, 복구 연습
- 데이터 이전 파일(개인정보)은 비공개·암호화 버킷 → 옮긴 뒤 즉시 삭제
- 저장소(공개)에는 개인정보·비밀값을 올리지 않는다 (`deploy/.gitignore`, `/data/`)

### 9-7. 모니터링 (권장)
| 항목 | 설정 |
|---|---|
| CloudWatch 경보 | EC2 `StatusCheckFailed`, ALB `UnHealthyHostCount ≥ 1`, `HTTPCode_ELB_5XX_Count`, (에이전트) 디스크 사용률 80% |
| AWS WAF (선택, 월 비용 추가) | ALB 에 관리형 규칙 : Core rule set, Known bad inputs, Amazon IP reputation + 속도 기반 규칙(IP 당 5분 1,000건) |
| GuardDuty (선택) | 계정 위협 탐지 |
| 결제 경보 | Budgets 월 예산 알림 |

### 9-8. 정기 점검 (월 1회)
- [ ] `git pull` 후 업데이트 (보안 수정 반영), 커널 업데이트가 있으면 재부팅
- [ ] 백업 zip·스냅샷이 매일 생기는지, 복구 연습(분기)
- [ ] 대상 그룹 healthy, 5xx 경보 이력
- [ ] Keycloak 사용자 목록 (불필요 계정 비활성화), 테스트 계정 비활성 상태
- [ ] 디스크 사용률 (`df -h`, `docker system df`), 오래된 이미지 정리 `docker image prune`

---

## 10. 문제 해결

| 증상 | 원인 · 조치 |
|---|---|
| backend 가 재시작을 반복 | `https://<도메인>/realms/flowlog` 조회 실패 - DNS·ALB·대상 그룹 확인, `docker compose logs backend` |
| ALB 502 / 대상 unhealthy | EC2 보안 그룹에 ALB 보안 그룹 80 허용 확인, `docker compose ps` 로 gateway·web 상태 |
| 로그인 후 "Invalid redirect uri" | `.env` 의 `DOMAIN` 과 접속 도메인이 같은지, `docker compose up -d keycloak-setup` 다시 실행 |
| Keycloak "HTTPS required" | ALB 를 거치지 않고 http 로 접속한 경우 - 도메인(https)으로 접속 |
| keycloak-setup 실패 → backend 안 뜸 | `docker compose logs keycloak-setup` (비밀번호 정책 위반 등), 수정 후 `docker compose up -d` |
| 디스크 부족 | `docker system prune` (사용 중 아닌 이미지), 백업 보관 일수 조정 |
