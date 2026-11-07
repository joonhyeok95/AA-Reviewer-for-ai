"""
하이델베르크 요리문답 (북미개혁교회 CRC 한글 번역 PDF) → 교리문답 이관 SQL.

  python -m venv .venv && .venv/Scripts/pip install pypdf      (최초 1회)
  .venv/Scripts/python -I scripts/catechism-import/import_heidelberg.py <PDF 경로> [--dry-run]

- 원본 : https://www.crcna.org/sites/default/files/Heidelberg%20Catechism%20Korean%20tr-2012.pdf
  ("연구와 교육, 그리고 검토의 목적 외에 … 서면 허락없이 복사되지 말아야 한다" - 저작권 Faith Alive)
  → 결과(본문)는 저장소에 올리지 않는다. 기본 출력 data/catechism-import.sql 은 /data/ gitignore 대상이고
    local 프로필이 optional 로 읽는다. Oracle(dev/prod)은 같은 SQL 을 직접 실행.

PDF → 교리문답 5단계
  제 n 부: 제목          → 단위 PART (기존 부 1~3 의 제목을 바꿈)
  제 n 주일              → 단위 LORDS_DAY (제1주일은 부 앞 서론이라 최상위, 제45주일은 기존 단위 ID 4 재사용 - 주보 연결 유지)
  제 n 문 : 질문         → 문답
  답 : … (빈 줄로 나뉜 문단) → 답 단락 (본문 속 각주 번호는 지움)
  1) 성경 구절; …        → 근거 성구 - 각주 번호가 나온 답 단락에 붙임
"""
import argparse
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SOURCE = 'CRC 한글 번역 (2012, 심재승 역) - crcna.org'

PART_RE = re.compile(r'^제\s*(\d+)\s*부\s*:\s*(.+)$')
DAY_RE = re.compile(r'^제\s*(\d+)\s*주일\s*$')
Q_RE = re.compile(r'^제\s*(\d+)\s*문\**\s*:\s*(.*)$')
A_RE = re.compile(r'^답\s*:\s*(.*)$')
NOTE_RE = re.compile(r'^(\d+)\)\s*(.*)$')
NOISE_RE = re.compile(r'^하이델베르그 요리문답$')
SEP_RE = re.compile(r'^_{5,}$')


def extract(pdf):
    from pypdf import PdfReader
    return '\n'.join(page.extract_text() for page in PdfReader(pdf).pages)


def clean(text):
    return re.sub(r'\s+', ' ', text).strip()


def parse(text):
    parts, days, current_part, current_day = [], [], None, None
    qa = None
    heading = []  # 구분선(____) 다음, 주일 앞의 소제목 (예: 성부 하나님) → 그 주일의 제목
    mode = None  # question / answer / notes / editorial / between
    lines = text.split('\n')
    start = next(i for i, line in enumerate(lines) if DAY_RE.match(line.strip()))
    for raw in lines[start:]:
        line = raw.strip()
        if NOISE_RE.match(line):
            continue
        if SEP_RE.match(line):
            mode, heading = 'between', []
            continue
        if m := PART_RE.match(line):
            current_part = {'no': int(m[1]), 'title': clean(m[2])}
            parts.append(current_part)
            continue
        if m := DAY_RE.match(line):
            current_day = {'no': int(m[1]), 'part': current_part['no'] if current_part else None,
                           'title': clean(' '.join(heading)) or None, 'qas': []}
            heading = []
            days.append(current_day)
            mode = None
            continue
        if m := Q_RE.match(line):
            qa = {'no': int(m[1]), 'question': [m[2]], 'paragraphs': [[]], 'notes': {}}
            current_day['qas'].append(qa)
            mode = 'question'
            continue
        if qa is None:
            continue
        if m := A_RE.match(line):
            qa['paragraphs'] = [[m[1]]] if m[1] else [[]]
            mode = 'answer'
            continue
        if line.startswith('*') and mode in ('notes', 'editorial'):
            qa.setdefault('editorial', []).append([line.lstrip('*').strip()])
            mode = 'editorial'
            continue
        if m := NOTE_RE.match(line):
            qa['notes'][int(m[1])] = [m[2]]
            qa['last_note'] = int(m[1])
            mode = 'notes'
            continue
        if mode == 'between':
            if line and not re.fullmatch(r'\d+', line):
                heading.append(line)
            continue
        if mode == 'question':
            if line:
                qa['question'].append(line)
        elif mode == 'answer':
            if not line:
                if qa['paragraphs'][-1]:
                    qa['paragraphs'].append([])
            elif re.fullmatch(r'\d+', line) and int(line) > 30:
                continue  # 쪽 번호
            else:
                qa['paragraphs'][-1].append(line)
        elif mode == 'editorial' and line:
            qa['editorial'][-1].append(line)
        elif mode == 'notes' and line and not re.fullmatch(r'\d+', line):
            qa['notes'][qa['last_note']].append(line)  # 각주 줄바꿈 이어짐
    return parts, days


def strip_markers(text, notes, expected):
    """본문에서 각주 번호(expected, expected+1, …)를 순서대로 찾아 지운다. → (본문, 찾은 번호, 다음 번호)"""
    found = []
    while expected in notes:
        # 숫자 앞뒤가 숫자·장절 기호가 아닌 위치 (예: "아니고,1", "죽으나 2 몸과")
        # "제 1 계명", "두 번째 간구" 처럼 본문의 숫자는 제외
        m = re.search(rf'(?<![\d:\-–])(?<!제 )(?<!제){expected}(?![\d:\-–장편절])(?!\s*(?:계명|간구|번))',
                      text)
        if not m:
            break
        text = clean(text[:m.start()] + ' ' + text[m.end():])
        found.append(expected)
        expected += 1
    return re.sub(r'\s+([,.:;?!”’])', r'\1', text).replace(']**', ']'), found, expected


def build_question(qa):
    """질문 (질문 속 각주 번호도 지움 - 예: 제25문 "한 분 1")"""
    return strip_markers(clean(' '.join(qa['question'])), qa['notes'], 1)


def build_answers(qa):
    """문단별 본문에서 각주 번호를 순서대로 찾아 지우고, 그 문단에 근거 성구를 붙인다.
    질문에 있던 각주는 첫 단락에 붙인다."""
    _, question_proofs, expected = build_question(qa)
    answers = []
    for paragraph in [p for p in qa['paragraphs'] if p]:
        text, proofs, expected = strip_markers(clean(' '.join(paragraph)), qa['notes'], expected)
        answers.append({'content': text, 'proofs': proofs})
    if answers:
        answers[0]['proofs'] = question_proofs + answers[0]['proofs']
    if not answers:
        answers = [{'content': '', 'proofs': []}]
    # 본문에서 못 찾은 각주는 마지막 단락에
    leftovers = [n for n in sorted(qa['notes']) if n >= expected]
    answers[-1]['proofs'] += leftovers
    for answer in answers:
        answer['proofs'] = [clean(' '.join(qa['notes'][n])) for n in answer['proofs']]
    # 편집 주 (*, **) - 문 80 처럼 판·교단 결정에 관한 설명은 마지막 단락으로
    for note in qa.get('editorial', []):
        answers.append({'content': '※ ' + clean(' '.join(note)), 'proofs': []})
    return answers


def q(value):
    if value is None:
        return 'NULL'
    if isinstance(value, int):
        return str(value)
    return "'" + str(value).replace("'", "''") + "'"


AUDIT = "'import', SYSTIMESTAMP, 'import', SYSTIMESTAMP"


def ins(table, cols, values):
    return (f"INSERT INTO {table} ({', '.join(cols)}, CREATED_BY, CREATED_AT, UPDATED_BY, UPDATED_AT) "
            f"VALUES ({', '.join(q(v) for v in values)}, {AUDIT});")


def to_sql(parts, days):
    out = ['-- 하이델베르크 요리문답 이관 - scripts/catechism-import/import_heidelberg.py 로 생성',
           f'-- 원본 : {SOURCE}. 저작권 본문이라 저장소에 올리지 않는다 (/data/ gitignore)',
           '-- 기존 교리문답(ID 1)의 문답을 모두 지우고 52주일 129문으로 바꾼다. 제45주일은 단위 ID 4 를 그대로 쓴다 (주보 연결 유지)',
           'DELETE FROM TB_CATECHISM_PROOF WHERE ANSWER_ID IN (SELECT A.ANSWER_ID FROM TB_CATECHISM_ANSWER A '
           'JOIN TB_CATECHISM_QA Q ON Q.QA_ID = A.QA_ID WHERE Q.CATECHISM_ID = 1);',
           'DELETE FROM TB_CATECHISM_ANSWER WHERE QA_ID IN (SELECT QA_ID FROM TB_CATECHISM_QA WHERE CATECHISM_ID = 1);',
           'DELETE FROM TB_CATECHISM_QA WHERE CATECHISM_ID = 1;',
           'DELETE FROM TB_CATECHISM_UNIT WHERE CATECHISM_ID = 1 AND UNIT_ID NOT IN (1, 2, 3, 4) '
           'AND UNIT_ID NOT IN (SELECT UNIT_ID FROM TB_BULLETIN_CATECHISM);',
           f"UPDATE TB_CATECHISM SET DESCRIPTION = {q('1563년. 서론(제1주일)과 3부 52주일 129문. ' + SOURCE)} "
           'WHERE CATECHISM_ID = 1;']
    for part in parts:
        out.append(f"UPDATE TB_CATECHISM_UNIT SET TITLE = {q(part['title'])} WHERE UNIT_ID = {part['no']};")
    unit_id, qa_id, answer_id, proof_id = 1001, 1001, 1001, 1001
    for day in days:
        uid = 4 if day['no'] == 45 else unit_id
        if day['no'] == 45:
            out.append(f"UPDATE TB_CATECHISM_UNIT SET PARENT_UNIT_ID = 3, UNIT_NO = 45, SORT_ORDER = 45, "
                       f"TITLE = {q(day['title'])} WHERE UNIT_ID = 4;")
        else:
            out.append(ins('TB_CATECHISM_UNIT', ['UNIT_ID', 'CATECHISM_ID', 'PARENT_UNIT_ID', 'UNIT_TYPE_CD',
                                                 'UNIT_NO', 'TITLE', 'SORT_ORDER'],
                           [uid, 1, day['part'], 'LORDS_DAY', day['no'], day['title'],
                            day['no'] if day['part'] else 0]))
            unit_id += 1
        for order, qa in enumerate(day['qas'], start=1):
            out.append(ins('TB_CATECHISM_QA', ['QA_ID', 'CATECHISM_ID', 'UNIT_ID', 'QUESTION_NO', 'QUESTION',
                                               'SORT_ORDER'],
                           [qa_id, 1, uid, qa['no'], build_question(qa)[0], order]))
            for a_order, answer in enumerate(build_answers(qa), start=1):
                out.append(ins('TB_CATECHISM_ANSWER', ['ANSWER_ID', 'QA_ID', 'SORT_ORDER', 'CONTENT'],
                               [answer_id, qa_id, a_order, answer['content']]))
                for p_order, ref in enumerate(answer['proofs'], start=1):
                    out.append(ins('TB_CATECHISM_PROOF', ['PROOF_ID', 'ANSWER_ID', 'SORT_ORDER', 'BIBLE_REF'],
                                   [proof_id, answer_id, p_order, ref[:100]]))
                    proof_id += 1
                answer_id += 1
            qa_id += 1
    for seq, start in (('SQ_CATECHISM_UNIT', unit_id), ('SQ_CATECHISM_QA', qa_id),
                       ('SQ_CATECHISM_ANSWER', answer_id), ('SQ_CATECHISM_PROOF', proof_id)):
        out.append(f'ALTER SEQUENCE {seq} RESTART START WITH {start + 1000};')
    return '\n'.join(out) + '\n'


def main():
    parser = argparse.ArgumentParser(description='하이델베르크 요리문답 PDF → 이관 SQL')
    parser.add_argument('pdf')
    parser.add_argument('--out', default=str(ROOT / 'data' / 'catechism-import.sql'))
    parser.add_argument('--dry-run', action='store_true')
    parser.add_argument('--show', type=int, nargs='*', default=[], help='확인할 문 번호')
    args = parser.parse_args()
    sys.stdout.reconfigure(encoding='utf-8')

    parts, days = parse(extract(args.pdf))
    qas = [qa for day in days for qa in day['qas']]
    print(f'부 {len(parts)} / 주일 {len(days)} / 문답 {len(qas)} / 근거 성구 {sum(len(q["notes"]) for q in qas)}')
    numbers = [qa['no'] for qa in qas]
    missing = sorted(set(range(1, 130)) - set(numbers))
    print('빠진 문 :', missing or '없음', '/ 주일 :', sorted(set(range(1, 53)) - {d['no'] for d in days}) or '없음')
    for no in args.show:
        qa = next(qa for qa in qas if qa['no'] == no)
        print(f'\n제{no}문 {clean(" ".join(qa["question"]))}')
        for answer in build_answers(qa):
            print('  답:', answer['content'])
            print('     ', answer['proofs'])
    if not args.dry_run:
        out = Path(args.out)
        out.parent.mkdir(parents=True, exist_ok=True)
        out.write_text(to_sql(parts, days), encoding='utf-8', newline='\n')
        print(f'→ {out} (앱을 재시작하면 반영)')


if __name__ == '__main__':
    main()
