#!/bin/bash
# 로컬 검증 전용 : Caddy 내부 CA 를 신뢰하도록 truststore 를 만들고 실행 (운영 ALB 는 공인 인증서라 불필요)
# Caddy 인증서 폴더는 root 만 읽을 수 있어 root 로 준비한 뒤 앱 사용자(10001)로 실행
set -euo pipefail
ROOT=/caddy/caddy/pki/authorities/local/root.crt
for _ in $(seq 1 60); do [ -f "$ROOT" ] && break; sleep 2; done
cp "$JAVA_HOME/lib/security/cacerts" /tmp/cacerts && chmod 644 /tmp/cacerts
keytool -importcert -noprompt -alias caddy-local -file "$ROOT" -keystore /tmp/cacerts -storepass changeit >/dev/null
exec setpriv --reuid=10001 --regid=10001 --init-groups java -Djavax.net.ssl.trustStore=/tmp/cacerts -Djavax.net.ssl.trustStorePassword=changeit -jar /app/app.jar
