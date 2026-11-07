-- =====================================================================
-- 캘린더 (생일·결혼기념일·휴일 등 일정) - Oracle
--  - 일정 1건 = 시작일~종료일 (하루 일정은 시작일 = 종료일)
--  - 매년 반복(REPEAT_YEARLY_YN='Y') : 조회 기간 안의 해마다 같은 월·일로 펼쳐서 응답
--    (2월 29일 일정은 평년에 2월 28일). 저장은 원본 1건만
--  - 연도를 모르는 생일·기념일(YEAR_KNOWN_YN='N')은 반복만 하고 나이·주년은 표시하지 않음
--  - 일정 유형은 공통코드 CAL_EVENT_TYPE (BIRTHDAY / ANNIVERSARY / HOLIDAY / ETC)
-- =====================================================================

CREATE TABLE TB_CAL_EVENT (
    EVENT_ID            NUMBER(19)      NOT NULL,
    EVENT_TYPE_CD       VARCHAR2(30 CHAR)    NOT NULL,
    TITLE               VARCHAR2(200 CHAR)   NOT NULL,
    START_DT            DATE            NOT NULL,
    END_DT              DATE            NOT NULL,
    REPEAT_YEARLY_YN    VARCHAR2(1 CHAR)     DEFAULT 'N' NOT NULL,
    YEAR_KNOWN_YN       VARCHAR2(1 CHAR)     DEFAULT 'Y' NOT NULL,
    MEMO                VARCHAR2(1000 CHAR),
    CREATED_BY          VARCHAR2(50 CHAR)    NOT NULL,
    CREATED_AT          TIMESTAMP(6)    NOT NULL,
    UPDATED_BY          VARCHAR2(50 CHAR)    NOT NULL,
    UPDATED_AT          TIMESTAMP(6)    NOT NULL,
    CONSTRAINT PK_CAL_EVENT PRIMARY KEY (EVENT_ID),
    CONSTRAINT CK_CAL_EVENT_01 CHECK (REPEAT_YEARLY_YN IN ('Y', 'N')),
    CONSTRAINT CK_CAL_EVENT_02 CHECK (END_DT >= START_DT),
    CONSTRAINT CK_CAL_EVENT_03 CHECK (YEAR_KNOWN_YN IN ('Y', 'N'))
);
-- 기간 조회 (반복 안 함 : 시작일 <= 조회 종료 AND 종료일 >= 조회 시작 / 반복 : 시작일 <= 조회 종료)
CREATE INDEX IX_CAL_EVENT_01 ON TB_CAL_EVENT (REPEAT_YEARLY_YN, START_DT, END_DT);
CREATE SEQUENCE SQ_CAL_EVENT START WITH 1 INCREMENT BY 1 NOCACHE;

COMMENT ON TABLE  TB_CAL_EVENT                  IS '캘린더 일정 (생일·결혼기념일·휴일 등)';
COMMENT ON COLUMN TB_CAL_EVENT.EVENT_ID         IS '일정 ID (SQ_CAL_EVENT)';
COMMENT ON COLUMN TB_CAL_EVENT.EVENT_TYPE_CD    IS '일정 유형 (공통코드 CAL_EVENT_TYPE)';
COMMENT ON COLUMN TB_CAL_EVENT.TITLE            IS '제목';
COMMENT ON COLUMN TB_CAL_EVENT.START_DT         IS '시작일 (매년 반복이면 최초 일자 - 생일은 출생일)';
COMMENT ON COLUMN TB_CAL_EVENT.END_DT           IS '종료일 (하루 일정은 시작일과 같음)';
COMMENT ON COLUMN TB_CAL_EVENT.REPEAT_YEARLY_YN IS '매년 반복 여부 (Y/N)';
COMMENT ON COLUMN TB_CAL_EVENT.YEAR_KNOWN_YN    IS '시작 연도를 아는지 (N = 출생·결혼 연도 모름 → 나이·주년 표시 안 함)';
COMMENT ON COLUMN TB_CAL_EVENT.MEMO             IS '메모';
COMMENT ON COLUMN TB_CAL_EVENT.CREATED_BY       IS '생성자';
COMMENT ON COLUMN TB_CAL_EVENT.CREATED_AT       IS '생성일시';
COMMENT ON COLUMN TB_CAL_EVENT.UPDATED_BY       IS '수정자';
COMMENT ON COLUMN TB_CAL_EVENT.UPDATED_AT       IS '수정일시';
