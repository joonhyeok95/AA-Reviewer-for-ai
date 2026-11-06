<#
.SYNOPSIS
  로컬 전체 실행 : Redis + Keycloak(Docker) → 앱(9091[, 9092]) → Gateway(9909) → 화면(5173)

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

.EXAMPLE
  ./scripts/dev-up.ps1
  ./scripts/dev-up.ps1 -Instances 2
#>
param(
  [ValidateRange(1, 2)][int]$Instances = 1,
  [switch]$NoFrontend
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

# ------------------------------------------------------------------ 인프라
Write-Step 'Redis / Keycloak (Docker)'
docker compose -f (Join-Path $root 'infra/redis/docker-compose.yml') up -d | Out-Null
docker compose -f (Join-Path $root 'infra/keycloak/docker-compose.yml') up -d | Out-Null
Wait-Http 'http://localhost:7000/realms/flowlog/.well-known/openid-configuration' 'Keycloak' 240

# ------------------------------------------------------------------ 앱 / Gateway / 화면
Start-Background 'app-9091' $root (Join-Path $root 'mvnw.cmd') @('-q', 'spring-boot:run')
if ($Instances -eq 2) {
  Start-Background 'app-9092' $root (Join-Path $root 'mvnw.cmd') `
    @('-q', 'spring-boot:run', '-Dspring-boot.run.arguments="--server.port=9092 --management.server.port=9192"')
}
Start-Background 'gateway' $gatewayDir (Join-Path $gatewayDir 'mvnw.cmd') @('-q', 'spring-boot:run')
if (-not $NoFrontend) {
  Start-Background 'frontend' $frontendDir 'npm.cmd' @('run', 'dev')
}

Wait-Http 'http://127.0.0.1:9191/actuator/health' '앱 9091'
if ($Instances -eq 2) { Wait-Http 'http://127.0.0.1:9192/actuator/health' '앱 9092' }
Wait-Http 'http://127.0.0.1:9910/actuator/health' 'Gateway'
if (-not $NoFrontend) { Wait-Http 'http://127.0.0.1:5173/' '화면' 120 }

Write-Host ''
Write-Host '접속 : http://localhost:9909   (Swagger http://localhost:9909/cm/swagger-ui.html)' -ForegroundColor Green
Write-Host '종료 : ./scripts/dev-down.ps1' -ForegroundColor Green
Write-Host '참고 : local 프로필은 인스턴스마다 H2 메모리 DB 를 따로 쓴다 (2대 실행 시 데이터는 공유되지 않음)'
