<#
.SYNOPSIS
  scripts/dev-up.ps1 로 띄운 앱·Gateway·화면을 종료한다. (-Infra 면 Redis·Keycloak·Oracle 컨테이너도)

.PARAMETER Infra
  Redis / Keycloak / Oracle 컨테이너도 내린다 (DB 볼륨 flowlog-oracle-data 는 유지 - 데이터 보존)
#>
param([switch]$Infra)

$root = Split-Path -Parent $PSScriptRoot
$pidFile = Join-Path $root '.run/pids.txt'

if (Test-Path $pidFile) {
  foreach ($line in Get-Content $pidFile) {
    $name, $processId = $line -split '=', 2
    # mvnw / npm 이 띄운 java·node 자식까지 함께 종료
    taskkill /PID $processId /T /F 2>$null | Out-Null
    Write-Host "[dev-down] $name (PID $processId) 종료"
  }
  Remove-Item $pidFile
} else {
  Write-Host '[dev-down] 실행 기록(.run/pids.txt)이 없습니다. 직접 띄운 서버는 각 터미널에서 종료하세요.'
}

if ($Infra) {
  docker compose -f (Join-Path $root 'infra/keycloak/docker-compose.yml') stop | Out-Null
  docker compose -f (Join-Path $root 'infra/redis/docker-compose.yml') stop | Out-Null
  docker compose -f (Join-Path $root 'infra/oracle/docker-compose.yml') stop | Out-Null
  Write-Host '[dev-down] Redis / Keycloak / Oracle 컨테이너 중지 (DB 데이터는 볼륨에 유지)'
}
