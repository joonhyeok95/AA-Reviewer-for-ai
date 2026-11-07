#!/usr/bin/env bash
# =====================================================================
# 지금 PC 의 데이터(로컬 영속 DB - Docker Oracle) → 운영용 H2 파일 + 첨부·사진 묶음
#
#   사용 (Git Bash / macOS / Linux, 저장소 루트에서) :
#     deploy/migrate/oracle-to-h2.sh            # H2 비밀번호는 deploy/.env 의 H2_PASSWORD
#   결과 : deploy/export/flowlog-data.tgz  (db/flowlog.mv.db + files/)  → EC2 로 복사 후 deploy/import-data.sh
#
#  1. 운영과 같은 DDL(db/ddl)로 빈 H2 를 만들고
#  2. Oracle 테이블을 H2 linked table 로 붙여 행을 그대로 복사 (열 이름 지정, 참조 무결성은 복사 중에만 끔)
#  3. 시퀀스를 Oracle 다음 값으로 맞추고, 초기화 완료 표시(TB_DB_INIT)를 남겨 운영 기동 때 초기 데이터를 다시 넣지 않게
#  4. 테이블별 행 수를 Oracle 과 비교
#  - 결과물에는 개인정보가 있다 : deploy/export 는 gitignore, 옮긴 뒤 지울 것
# =====================================================================
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
BOOT="$ROOT/spring-boilerplate"
OUT="$ROOT/deploy/export"
ORACLE_CONTAINER="${ORACLE_CONTAINER:-flowlog-oracle}"
ORACLE_USER="${ORACLE_USER:-flowlog}"
ORACLE_PASSWORD="${ORACLE_APP_PASSWORD:-flowlog-local}"
ORACLE_URL="jdbc:oracle:thin:@//localhost:1521/FREEPDB1"

H2_PASSWORD="${H2_PASSWORD:-$(grep -E '^H2_PASSWORD=' "$ROOT/deploy/.env" 2>/dev/null | cut -d= -f2- || true)}"
if [ -z "$H2_PASSWORD" ]; then
  echo "H2_PASSWORD 가 없습니다. 먼저 deploy/gen-secrets.sh 로 deploy/.env 를 만드세요 (운영과 같은 값이어야 함)" >&2
  exit 1
fi

step() { echo "[migrate] $*"; }
winpath() { if command -v cygpath >/dev/null 2>&1; then cygpath -w "$1"; else echo "$1"; fi; }
sep() { if command -v cygpath >/dev/null 2>&1; then echo ';'; else echo ':'; fi; }
sqlplus() {
  docker exec -i "$ORACLE_CONTAINER" bash -c \
    "sqlplus -s ${ORACLE_USER}/${ORACLE_PASSWORD}@//localhost/FREEPDB1"
}

H2_JAR=$(ls "$HOME"/.m2/repository/com/h2database/h2/*/h2-*.jar | grep -v sources | sort -V | tail -1)
OJDBC_JAR=$(ls "$HOME"/.m2/repository/com/oracle/database/jdbc/ojdbc11/*/ojdbc11-*.jar | sort -V | tail -1)
[ -f "$H2_JAR" ] && [ -f "$OJDBC_JAR" ] || { echo "h2 / ojdbc11 jar 가 ~/.m2 에 없습니다 (spring-boilerplate 를 한 번 빌드)" >&2; exit 1; }
CP="$(winpath "$H2_JAR")$(sep)$(winpath "$OJDBC_JAR")"

rm -rf "$OUT/db" "$OUT/files" "$OUT/migrate.sql" "$OUT/flowlog-data.tgz"
mkdir -p "$OUT/db"

step "Oracle 테이블·열·시퀀스 조회 ($ORACLE_CONTAINER)"
COLUMNS=$(sqlplus <<'SQL'
set heading off feedback off pagesize 0 linesize 400 trimspool on
SELECT table_name || '|' || column_name FROM user_tab_columns
 WHERE table_name LIKE 'TB\_%' ESCAPE '\' AND table_name <> 'TB_DB_INIT' ORDER BY table_name, column_id;
SQL
)
SEQUENCES=$(sqlplus <<'SQL'
set heading off feedback off pagesize 0 linesize 200 trimspool on
SELECT sequence_name || '|' || last_number FROM user_sequences ORDER BY sequence_name;
SQL
)

step "H2 이전 스크립트 생성"
{
  for ddl in "$BOOT"/src/main/resources/db/ddl/*.sql; do
    echo "RUNSCRIPT FROM '$(winpath "$ddl" | sed 's|\\|/|g')' CHARSET 'UTF-8';"
  done
  echo "SET REFERENTIAL_INTEGRITY FALSE;"
  echo "$COLUMNS" | awk -F'|' 'NF==2 { gsub(/ /,"",$1); gsub(/ /,"",$2); cols[$1] = (cols[$1] ? cols[$1] "," : "") $2; order[$1]=1 }
    END { for (t in order) print t "|" cols[t] }' | sort | while IFS='|' read -r table cols; do
    [ -n "$table" ] || continue
    echo "CREATE LINKED TABLE L_${table}('oracle.jdbc.OracleDriver', '${ORACLE_URL}', '${ORACLE_USER}', '${ORACLE_PASSWORD}', '${ORACLE_USER^^}', '${table}') READONLY;"
    echo "INSERT INTO ${table} (${cols}) SELECT ${cols} FROM L_${table};"
    echo "DROP TABLE L_${table};"
  done
  echo "SET REFERENTIAL_INTEGRITY TRUE;"
  echo "$SEQUENCES" | while IFS='|' read -r seq next; do
    seq=$(echo "$seq" | tr -d ' '); next=$(echo "$next" | tr -d ' ')
    [ -n "$seq" ] && echo "ALTER SEQUENCE ${seq} RESTART WITH ${next};"
  done
  echo "CREATE TABLE TB_DB_INIT (INIT_AT TIMESTAMP(6) NOT NULL);"
  echo "INSERT INTO TB_DB_INIT (INIT_AT) VALUES (CURRENT_TIMESTAMP);"
} > "$OUT/migrate.sql"

# Oracle 컨테이너의 1521 은 이 PC 의 localhost 에 열려 있다 (infra/oracle)
step "H2 파일 생성 + 데이터 복사"
H2_URL="jdbc:h2:file:$(winpath "$OUT/db/flowlog" | sed 's|\\|/|g');MODE=Oracle;DEFAULT_NULL_ORDERING=HIGH"
java -cp "$CP" org.h2.tools.RunScript -url "$H2_URL" -user sa -password "$H2_PASSWORD" \
  -script "$(winpath "$OUT/migrate.sql")"
rm -f "$OUT/migrate.sql"   # Oracle 비밀번호가 들어 있음

step "행 수 비교 (Oracle / H2)"
ORACLE_COUNTS=$(echo "$COLUMNS" | cut -d'|' -f1 | tr -d ' ' | sort -u | grep . | while read -r t; do
  echo "SELECT '${t}|' || COUNT(*) FROM ${t};"; done | { echo "set heading off feedback off pagesize 0"; cat; } | sqlplus | tr -d ' ' | grep '|')
MISMATCH=0
while IFS='|' read -r table count; do
  h2=$(java -cp "$CP" org.h2.tools.Shell -url "$H2_URL" -user sa -password "$H2_PASSWORD" \
    -sql "SELECT COUNT(*) FROM ${table}" | sed -n 2p | tr -d ' \r')
  if [ "$h2" != "$count" ]; then echo "  다름 ${table} : Oracle ${count} / H2 ${h2}"; MISMATCH=1; fi
done <<< "$ORACLE_COUNTS"
TABLES=$(echo "$ORACLE_COUNTS" | grep -c '|')
ROWS=$(echo "$ORACLE_COUNTS" | awk -F'|' '{ s += $2 } END { print s }')
[ "$MISMATCH" = 0 ] && step "테이블 ${TABLES}개, 행 ${ROWS}건 모두 일치" || { echo "행 수가 다릅니다" >&2; exit 1; }

step "첨부·사진 파일 복사 (spring-boilerplate/data/notice-files)"
mkdir -p "$OUT/files"
if [ -d "$BOOT/data/notice-files" ]; then cp -R "$BOOT/data/notice-files/." "$OUT/files/"; fi

tar -C "$OUT" -czf "$OUT/flowlog-data.tgz" db files
step "완료 : $(winpath "$OUT/flowlog-data.tgz") ($(du -h "$OUT/flowlog-data.tgz" | cut -f1))"
step "EC2 로 복사 : scp 또는 S3 → deploy/import-data.sh flowlog-data.tgz  (개인정보 포함 - 옮긴 뒤 이 PC 사본은 지울 것)"
