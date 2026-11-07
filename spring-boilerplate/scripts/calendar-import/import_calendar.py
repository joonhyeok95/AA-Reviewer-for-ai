"""
외부 캘린더(FullCalendar 이벤트 API) → 생일(캘린더) 이관 SQL 생성.

  python scripts/calendar-import/import_calendar.py                       # 기본: 2026년, data/calendar-import.sql
  python scripts/calendar-import/import_calendar.py --year 2027 --dry-run

- 원본 API : GET {url}/api/calendar/list?start=YYYY-01-01&end=(YYYY+1)-01-01
  응답 [{id, title, start(YYYY-MM-DD), color}] - 하루 단위 일정
- 결과는 개인정보(이름·생일)라 저장소에 올리지 않는다. 기본 출력 data/calendar-import.sql 은 /data/ gitignore 대상이고,
  local 프로필이 기동 시 optional 로 읽는다 (샘플 일정을 지우고 이관 일정으로 바꿈). 반영하려면 앱 재시작.
- Oracle(dev/prod)에도 같은 SQL 을 그대로 실행할 수 있다 (SQ_CAL_EVENT.NEXTVAL, DATE 리터럴).

분류 규칙 (원본에 유형이 없어 제목·색으로 판단)
  1. 제목에 '결혼기념일'            → WEDDING  매년 반복, 연도 모름
  2. 빨간색(#ff0000)                → HOLIDAY  양력 고정 공휴일은 매년 반복, 그 외(음력·대체공휴일)는 그해만.
                                       같은 제목이 연속된 날은 하나의 기간으로 합침 (설날 3일 → 1건)
  3. 제목에 '결혼식'                → ETC      그해만
  4. 제목에 '기념일'                → ETC      매년 반복, 연도 모름
  5. 그 외 (이름만 있는 파란 계열)  → BIRTHDAY 매년 반복, 연도 모름, 제목 뒤에 ' 생일'
  - 같은 제목·같은 날짜 중복은 1건만
  - SKIP 에 적은 항목은 제외 (사유를 보고서에 출력)
"""
import argparse
import json
import sys
import urllib.request
from datetime import date, timedelta
from pathlib import Path

DEFAULT_URL = 'https://yehan.dothome.co.kr'
SOURCE_NAME = 'yehan.dothome.co.kr'
ROOT = Path(__file__).resolve().parents[2]

# 양력 고정 공휴일 (매년 같은 날)
FIXED_HOLIDAYS = {'신정', '삼일절', '어린이날', '현충일', '광복절', '개천절', '한글날', '성탄절', '크리스마스'}
HOLIDAY_COLORS = {'#ff0000'}

# 제외 : (제목, 날짜) → 사유
SKIP = {
    ('설날', '2026-02-06'): '2026년 설날(2/16~18)이 따로 있어 날짜 오류로 판단 (원본에 설날이 두 번 등록됨)',
    ('설날', '2026-02-07'): '2026년 설날(2/16~18)이 따로 있어 날짜 오류로 판단 (원본에 설날이 두 번 등록됨)',
    ('설날', '2026-02-08'): '2026년 설날(2/16~18)이 따로 있어 날짜 오류로 판단 (원본에 설날이 두 번 등록됨)',
}


def fetch(url, year):
    query = f'{url}/api/calendar/list?start={year}-01-01&end={year + 1}-01-01'
    request = urllib.request.Request(query, headers={'User-Agent': 'flowlog-calendar-import'})
    with urllib.request.urlopen(request, timeout=30) as response:
        return json.loads(response.read().decode('utf-8-sig'))


def classify(item):
    """(유형, 제목, 매년 반복, 연도 앎)"""
    title = item['title'].strip()
    color = (item.get('color') or '').lower()
    if '결혼기념일' in title:
        return 'WEDDING', title, 'Y', 'N'
    if color in HOLIDAY_COLORS:
        return ('HOLIDAY', title, 'Y', 'N') if title in FIXED_HOLIDAYS else ('HOLIDAY', title, 'N', 'Y')
    if '결혼식' in title:
        return 'ETC', title, 'N', 'Y'
    if '기념일' in title:
        return 'ETC', title, 'Y', 'N'
    return 'BIRTHDAY', f'{title} 생일', 'Y', 'N'


def build(items):
    skipped, duplicates, seen, singles = [], [], set(), []
    for item in sorted(items, key=lambda x: (x['start'], x['id'])):
        key = (item['title'].strip(), item['start'])
        if key in SKIP:
            skipped.append((item, SKIP[key]))
            continue
        if key in seen:
            duplicates.append(item)
            continue
        seen.add(key)
        singles.append((item, *classify(item)))

    # 그해만 있는 휴일 : 같은 제목 연속 날짜 → 기간 1건
    events = []
    for item, typ, title, repeat, year_known in singles:
        day = date.fromisoformat(item['start'])
        last = events[-1] if events else None
        if (last and typ == 'HOLIDAY' and repeat == 'N' and last['type'] == typ
                and last['title'] == title and last['repeat'] == 'N'
                and last['end'] + timedelta(days=1) == day):
            last['end'] = day
            last['ids'].append(item['id'])
            continue
        events.append({'type': typ, 'title': title, 'start': day, 'end': day, 'repeat': repeat,
                       'year_known': year_known, 'ids': [item['id']]})
    return events, skipped, duplicates


def sql_text(value):
    return "'" + value.replace("'", "''") + "'"


def to_sql(events, url, year):
    cols = ('EVENT_ID, EVENT_TYPE_CD, TITLE, START_DT, END_DT, REPEAT_YEARLY_YN, YEAR_KNOWN_YN, MEMO, '
            'CREATED_BY, CREATED_AT, UPDATED_BY, UPDATED_AT')
    lines = [
        '-- =====================================================================',
        f'-- 캘린더 이관 ({url}, {year}년) - scripts/calendar-import/import_calendar.py 로 생성',
        '--  - 개인정보 포함 : 저장소에 올리지 않는다 (/data/ gitignore)',
        '--  - 샘플 일정을 지우고 이관 일정으로 바꾼다',
        '-- =====================================================================',
        'DELETE FROM TB_CAL_EVENT;',
    ]
    for event in events:
        memo = f"이관: {SOURCE_NAME} (원본 id {', '.join(map(str, event['ids']))})"
        lines.append(
            f"INSERT INTO TB_CAL_EVENT ({cols}) VALUES (SQ_CAL_EVENT.NEXTVAL, '{event['type']}', "
            f"{sql_text(event['title'])}, DATE '{event['start']}', DATE '{event['end']}', "
            f"'{event['repeat']}', '{event['year_known']}', {sql_text(memo)}, "
            "'import', SYSTIMESTAMP, 'import', SYSTIMESTAMP);")
    return '\n'.join(lines) + '\n'


def main():
    parser = argparse.ArgumentParser(description='외부 캘린더 → 생일(캘린더) 이관 SQL 생성')
    parser.add_argument('--url', default=DEFAULT_URL)
    parser.add_argument('--year', type=int, default=2026)
    parser.add_argument('--out', default=str(ROOT / 'data' / 'calendar-import.sql'))
    parser.add_argument('--dry-run', action='store_true', help='파일을 쓰지 않고 분류 결과만 출력')
    args = parser.parse_args()

    items = fetch(args.url, args.year)
    events, skipped, duplicates = build(items)

    print(f'원본 {len(items)}건 → 이관 {len(events)}건 (제외 {len(skipped)}, 중복 {len(duplicates)})')
    counts = {}
    for event in events:
        counts[event['type']] = counts.get(event['type'], 0) + 1
    print('  유형별 :', ', '.join(f'{k} {v}' for k, v in sorted(counts.items())))
    for event in events:
        period = event['start'].isoformat() if event['start'] == event['end'] \
            else f"{event['start']}~{event['end']}"
        print(f"  {event['type']:<8} {period:<21} 매년={event['repeat']} 연도={event['year_known']}  "
              f"{event['title']}")
    for item, reason in skipped:
        print(f"  [제외] {item['start']} {item['title']} (id {item['id']}) - {reason}")
    for item in duplicates:
        print(f"  [중복] {item['start']} {item['title']} (id {item['id']})")

    if args.dry_run:
        return
    out = Path(args.out)
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(to_sql(events, args.url, args.year), encoding='utf-8', newline='\n')
    print(f'→ {out} (앱을 재시작하면 반영)')


if __name__ == '__main__':
    sys.stdout.reconfigure(encoding='utf-8')
    main()
