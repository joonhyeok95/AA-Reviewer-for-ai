<#
.SYNOPSIS
  로컬 전체 실행 : Redis + Keycloak + Oracle(Docker) → 앱(9091[, 9092]) → Gateway(9909) → 화면(5173)

.DESCRIPTION
  저장소 3개가 같은 폴더 아래 나란히 있어야 한다.
    develop/
      spring-boilerplate/   (이 스크립트)
      spring-gateway/
      vue-frontend/
  로그는 spring-boilerplate/.run/*.log, 종료는 scripts/dev-down.ps1.
  이미 사용 중인 포트가 있으면 시작하지 않고 어떤 프로세스인지 알려준다.

.PARAMETER Instances
  앱 인스턴스 수 (1 또는 2). 2 면 9092 에 두 번째 인스턴스 (세션·캐시 무효화 공유 확인용)

.PARAMETER NoFrontend
  화면 개발 서버를 띄우지 않는다

.PARAMETER Local
  이 PC 에서만 접속 (http://localhost:9909). 생략하면 LAN 모드(기본)로 실행한다.
  LAN 모드 : 같은 네트워크의 다른 PC/모바일에서 http://{IP}:9909 로 접속.
  로그인 화면(Keycloak)도 Gateway(9909) 경유로 바꾸므로 외부에 여는 포트는 9909 하나다.
  (Keycloak 관리 콘솔은 계속 http://localhost:7000 에서만 접속)
  방화벽 9909 인바운드 허용 규칙이 없으면 관리자 승인(UAC) 창을 띄워 추가한다 (최초 1회).

.PARAMETER SkipFirewall
  방화벽 규칙 확인·추가(관리자 승인 창)를 건너뛴다. (관리자 권한이 없거나 이미 다른 방법으로 열어 둔 경우)

.PARAMETER InMemory
  영속 DB(Docker Oracle) 대신 메모리 H2 로 실행한다. 끄면 등록한 데이터가 사라진다 (이전 동작).

.PARAMETER ResetDb
  영속 DB 볼륨(flowlog-oracle-data)을 지우고 처음부터 다시 만든다 - 초기 데이터·이관 파일(data/*-import.sql)로 새로 채움.
  등록·수정한 데이터가 모두 사라지므로 이관 스크립트를 다시 돌렸거나 DDL 이 바뀌었을 때만 사용.

.PARAMETER LanHost
  LAN 모드에서 사용할 이 PC 의 IP. 생략하면 기본 경로(인터넷 연결)의 IPv4 를 쓴다.

.EXAMPLE
  ./scripts/dev-up.ps1
  ./scripts/dev-up.ps1 -Instances 2
  ./scripts/dev-up.ps1 -Local
  ./scripts/dev-up.ps1 -ResetDb
#>
param(
  [ValidateRange(1, 2)][int]$Instances = 1,
  [switch]$NoFrontend,
  [switch]$Local,
  [string]$LanHost,
  [switch]$SkipFirewall,
  [switch]$InMemory,
  [switch]$ResetDb,
  # 이전 옵션 호환 (LAN 이 기본)
  [switch]$Lan
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$develop = Split-Path -Parent $root
$gatewayDir = Join-Path $develop 'spring-gateway'
$frontendDir = Join-Path $develop 'vue-frontend'
$runDir = Join-Path $root '.run'
$pidFile = Join-Path $runDir 'pids.txt'
New-Item -ItemType Directory -Force $runDir | Out-Null

function Write-Step([string]$message) { Write-Host "[dev-up] $message" -ForegroundColor Cyan }

function Test-PortFree([int]$port) {
  $listener = Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue |
    Select-Object -First 1
  if ($listener) {
    $proc = Get-CimInstance Win32_Process -Filter "ProcessId=$($listener.OwningProcess)"
    $cmd = if ($proc.CommandLine) { $proc.CommandLine.Substring(0, [Math]::Min(120, $proc.CommandLine.Length)) } else { '' }
    Write-Host "[dev-up] 포트 $port 사용 중 - PID $($listener.OwningProcess) $cmd" -ForegroundColor Yellow
    return $false
  }
  return $true
}

function Wait-Http([string]$url, [string]$name, [int]$timeoutSec = 180) {
  $deadline = (Get-Date).AddSeconds($timeoutSec)
  while ((Get-Date) -lt $deadline) {
    try {
      $response = Invoke-WebRequest -Uri $url -UseBasicParsing -TimeoutSec 3
      if ($response.StatusCode -lt 500) { Write-Step "$name 준비됨 ($url)"; return }
    } catch {
      if ($_.Exception.Response -and [int]$_.Exception.Response.StatusCode -lt 500) {
        Write-Step "$name 준비됨 ($url)"; return
      }
    }
    Start-Sleep -Seconds 3
  }
  throw "$name 이(가) ${timeoutSec}초 안에 뜨지 않았습니다. 로그: $runDir"
}

function Start-Background([string]$name, [string]$dir, [string]$file, [string[]]$arguments,
    [hashtable]$environment = @{}) {
  $log = Join-Path $runDir "$name.log"
  $saved = @{}
  foreach ($key in $environment.Keys) {
    $saved[$key] = [Environment]::GetEnvironmentVariable($key)
    [Environment]::SetEnvironmentVariable($key, $environment[$key])
  }
  try {
    $proc = Start-Process -FilePath $file -ArgumentList $arguments -WorkingDirectory $dir `
      -RedirectStandardOutput $log -RedirectStandardError "$log.err" -WindowStyle Hidden -PassThru
  } finally {
    foreach ($key in $saved.Keys) { [Environment]::SetEnvironmentVariable($key, $saved[$key]) }
  }
  Add-Content -Path $pidFile -Value "$name=$($proc.Id)"
  Write-Step "$name 시작 (PID $($proc.Id), 로그 .run/$name.log)"
}

function Get-LanAddress {
  $route = Get-NetRoute -DestinationPrefix '0.0.0.0/0' -ErrorAction SilentlyContinue |
    Sort-Object RouteMetric | Select-Object -First 1
  if (-not $route) { return $null }
  (Get-NetIPAddress -InterfaceIndex $route.InterfaceIndex -AddressFamily IPv4 | Select-Object -First 1).IPAddress
}

# ------------------------------------------------------------------ 사전 확인
foreach ($dir in @($gatewayDir) + $(if (-not $NoFrontend) { @($frontendDir) } else { @() })) {
  if (-not (Test-Path $dir)) { throw "저장소가 없습니다: $dir (develop 폴더에 나란히 clone)" }
}
$ports = @(9091, 9191, 9909, 9910)
if ($Instances -eq 2) { $ports += 9092, 9192 }
if (-not $NoFrontend) { $ports += 5173 }
$busy = $ports | Where-Object { -not (Test-PortFree $_) }
if ($busy) {
  throw "사용 중인 포트가 있어 시작하지 않습니다: $($busy -join ', '). 이전 실행이면 scripts/dev-down.ps1, 직접 띄운 서버면 먼저 종료하세요."
}
if (Test-Path $pidFile) { Remove-Item $pidFile }

# LAN 접속 : 공개 URL 을 http://{IP}:9909 로 맞춘다 (Keycloak 은 Gateway 경유)
$appEnv = @{}
$gatewayEnv = @{}
$publicUrl = 'http://localhost:9909'
$Lan = -not $Local
if ($Lan -and -not $LanHost) {
  $LanHost = Get-LanAddress
  if (-not $LanHost) {
    Write-Host '[dev-up] 네트워크 IP 를 찾지 못해 이 PC 전용(localhost)으로 실행합니다. (-LanHost 192.168.x.x 로 지정 가능)' -ForegroundColor Yellow
    $Lan = $false
  }
}
if ($Lan) {
  if ($LanHost -notmatch '^\d{1,3}(\.\d{1,3}){3}$') { throw "IPv4 형식이 아닙니다: $LanHost" }
  $publicUrl = "http://${LanHost}:9909"
  $subnet = [regex]::Escape(($LanHost -replace '\.\d+$', '.')) + '\d{1,3}'
  $appEnv = @{
    KEYCLOAK_ISSUER_URI = "$publicUrl/realms/flowlog"
    NOTICE_LINK_TEMPLATE = "$publicUrl/notices/{id}"
  }
  $gatewayEnv = @{
    GATEWAY_IDP_PROXY_ENABLED = 'true'
    # 같은 대역 기기의 요청에도 X-Forwarded-Host 를 붙여야 백엔드가 공개 URL 로 콜백 주소를 만든다
    GATEWAY_TRUSTED_PROXIES = '127\.0\.0\.1|0:0:0:0:0:0:0:1|::1|' + $subnet
  }
  Write-Step "LAN 모드 : $publicUrl (대역 $subnet)"
  # 다른 기기에서 9909 로 들어오는 연결 허용 (개인/도메인 네트워크만, 공용 Wi-Fi 는 열지 않음)
  $ruleName = 'Flowlog LAN 9909'
  $ruleCommand = "New-NetFirewallRule -DisplayName '$ruleName' -Direction Inbound -Protocol TCP -LocalPort 9909 -Action Allow -Profile Private,Domain"
  if (-not $SkipFirewall -and -not (Get-NetFirewallRule -DisplayName $ruleName -ErrorAction SilentlyContinue)) {
    Write-Step '방화벽 9909 허용 규칙 추가 - 관리자 승인 창(UAC)에서 "예" 를 눌러 주세요 (최초 1회)'
    try {
      Start-Process powershell.exe -Verb RunAs -Wait -WindowStyle Hidden `
        -ArgumentList '-NoProfile', '-Command', $ruleCommand
    } catch {
      Write-Host '[dev-up] 승인이 취소되었습니다.' -ForegroundColor Yellow
    }
    if (-not (Get-NetFirewallRule -DisplayName $ruleName -ErrorAction SilentlyContinue)) {
      Write-Host '[dev-up] 방화벽 규칙이 없어 다른 기기에서는 접속되지 않습니다. 관리자 PowerShell 에서 실행하세요:' -ForegroundColor Yellow
      Write-Host "  $ruleCommand" -ForegroundColor Yellow
    } else {
      Write-Step "방화벽 규칙 추가됨 ($ruleName)"
    }
  }
  $publicNet = Get-NetConnectionProfile -ErrorAction SilentlyContinue | Where-Object { $_.NetworkCategory -eq 'Public' }
  if ($publicNet) {
    Write-Host "[dev-up] '$($publicNet.InterfaceAlias -join ', ')' 가 공용 네트워크입니다. 다른 기기 접속은 개인 네트워크에서만 허용됩니다 (설정 > 네트워크 > 속성 > 개인)." -ForegroundColor Yellow
  }
}

# ------------------------------------------------------------------ 인프라
Write-Step 'Redis / Keycloak (Docker)'
docker compose -f (Join-Path $root 'infra/redis/docker-compose.yml') up -d | Out-Null
# Keycloak 공개 URL(KC_PUBLIC_URL)이 바뀌면 컨테이너를 다시 만든다 (dev 모드라 realm 재import)
$env:KC_PUBLIC_URL = if ($Lan) { $publicUrl } else { $null }
$env:PUBLIC_HOST = if ($Lan) { $LanHost } else { $null }
try {
  docker compose -f (Join-Path $root 'infra/keycloak/docker-compose.yml') up -d | Out-Null
} finally {
  Remove-Item Env:KC_PUBLIC_URL, Env:PUBLIC_HOST -ErrorAction SilentlyContinue
}
Wait-Http 'http://localhost:7000/realms/flowlog/.well-known/openid-configuration' 'Keycloak' 240

# 영속 DB : Docker Oracle + 볼륨 flowlog-oracle-data (빈 DB 일 때만 앱이 스키마·초기 데이터 생성)
if (-not $InMemory) {
  $oracleCompose = Join-Path $root 'infra/oracle/docker-compose.yml'
  if ($ResetDb) {
    Write-Step '영속 DB 초기화 (볼륨 flowlog-oracle-data 삭제)'
    docker compose -f $oracleCompose down -v | Out-Null
  }
  Write-Step 'Oracle (Docker, 볼륨 flowlog-oracle-data)'
  docker compose -f $oracleCompose up -d | Out-Null
  if ($LASTEXITCODE -ne 0) { throw 'Oracle 컨테이너를 시작하지 못했습니다 (docker compose -f infra/oracle/docker-compose.yml up)' }
  $deadline = (Get-Date).AddSeconds(300)
  while ((docker inspect -f '{{.State.Health.Status}}' flowlog-oracle 2>$null) -ne 'healthy') {
    if ((Get-Date) -gt $deadline) { throw 'Oracle 이 5분 안에 준비되지 않았습니다 (docker logs flowlog-oracle)' }
    Start-Sleep -Seconds 3
  }
  Write-Step 'Oracle 준비됨'
  $appEnv['SPRING_PROFILES_ACTIVE'] = 'local,localdb'
}

# ------------------------------------------------------------------ 앱 / Gateway / 화면
Start-Background 'app-9091' $root (Join-Path $root 'mvnw.cmd') @('-q', 'spring-boot:run') $appEnv
if ($Instances -eq 2) {
  Start-Background 'app-9092' $root (Join-Path $root 'mvnw.cmd') `
    @('-q', 'spring-boot:run', '-Dspring-boot.run.arguments="--server.port=9092 --management.server.port=9192"') $appEnv
}
Start-Background 'gateway' $gatewayDir (Join-Path $gatewayDir 'mvnw.cmd') @('-q', 'spring-boot:run') $gatewayEnv
if (-not $NoFrontend) {
  if (-not (Test-Path (Join-Path $frontendDir 'node_modules'))) {
    Write-Step '화면 의존성 설치 (npm ci, 최초 1회)'
    Push-Location $frontendDir
    try { npm.cmd ci --no-audit --no-fund | Out-Null; if ($LASTEXITCODE -ne 0) { throw 'npm ci 실패' } } finally { Pop-Location }
  }
  Start-Background 'frontend' $frontendDir 'npm.cmd' @('run', 'dev')
}

Wait-Http 'http://127.0.0.1:9191/actuator/health' '앱 9091'
if ($Instances -eq 2) { Wait-Http 'http://127.0.0.1:9192/actuator/health' '앱 9092' }
Wait-Http 'http://127.0.0.1:9910/actuator/health' 'Gateway'
if (-not $NoFrontend) { Wait-Http 'http://127.0.0.1:5173/' '화면' 120 }

Write-Host ''
Write-Host "접속 : $publicUrl   (Swagger $publicUrl/cm/swagger-ui.html)" -ForegroundColor Green
if ($Lan) { Write-Host '참고 : LAN 모드는 이 PC 에서도 위 IP 주소로 접속하세요 (localhost 와 로그인 세션이 따로 잡힘)' }
Write-Host '종료 : ./scripts/dev-down.ps1' -ForegroundColor Green
if ($InMemory) {
  Write-Host '참고 : 메모리 H2 - 종료하면 데이터가 사라진다 (인스턴스마다 DB 따로)'
} else {
  Write-Host '참고 : 영속 DB (Docker 볼륨 flowlog-oracle-data) - 재시작해도 데이터 유지. 처음부터 다시: -ResetDb'
}
