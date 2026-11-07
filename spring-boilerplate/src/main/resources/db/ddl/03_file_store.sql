-- =====================================================================
-- 파일 저장소 (DB 저장 방식) - Oracle
--  - app.notice.file.storage-type=db 일 때 첨부 내용을 BLOB 으로 저장 (DatabaseFileStorage)
--  - 업무 메타데이터(원본 파일명·크기·확장자)는 각 업무 테이블(TB_NOTICE_FILE 등)에 두고,
--    업무 테이블의 SAVE_PATH 에 FILE_KEY 를 저장한다.
--  - 다중 인스턴스에서 추가 인프라(NFS 등) 없이 같은 파일을 공유하기 위한 방식.
-- =====================================================================

CREATE TABLE TB_FILE_STORE (
    FILE_KEY        VARCHAR2(64 CHAR)    NOT NULL,
    FILE_SIZE       NUMBER(19)      NOT NULL,
    FILE_DATA       BLOB            NOT NULL,
    CREATED_BY      VARCHAR2(50 CHAR)    NOT NULL,
    CREATED_AT      TIMESTAMP(6)    NOT NULL,
    UPDATED_BY      VARCHAR2(50 CHAR)    NOT NULL,
    UPDATED_AT      TIMESTAMP(6)    NOT NULL,
    CONSTRAINT PK_FILE_STORE PRIMARY KEY (FILE_KEY)
);
COMMENT ON TABLE  TB_FILE_STORE           IS '파일 저장소 (DB 저장 방식 - 첨부 내용)';
COMMENT ON COLUMN TB_FILE_STORE.FILE_KEY  IS '저장 키 (UUID, 업무 테이블 SAVE_PATH 에 저장)';
COMMENT ON COLUMN TB_FILE_STORE.FILE_SIZE IS '크기 (바이트)';
COMMENT ON COLUMN TB_FILE_STORE.FILE_DATA IS '파일 내용';
