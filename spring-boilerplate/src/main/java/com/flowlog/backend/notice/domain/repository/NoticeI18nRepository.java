package com.flowlog.backend.notice.domain.repository;

import com.flowlog.backend.notice.domain.model.NoticeI18n;
import com.flowlog.backend.notice.domain.model.NoticeI18nId;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 공지사항 언어별 내용 Repository.
 *
 * @author flowlog
 * @since 2026-11-05
 */
public interface NoticeI18nRepository extends JpaRepository<NoticeI18n, NoticeI18nId> {

  /**
   * 사용자 언어 기준 공지 목록 검색. (NT-64, NT-66)
   *
   * <p>공지마다 <b>표시될 언어 버전 1건</b>을 고른다 : 사용자 언어 버전이 있으면 그것, 없으면 대표 언어 버전.
   * 검색어는 이 표시 버전의 제목/본문(텍스트)에 적용한다. → 대표 언어로 대체되어 표시되는 공지도 검색된다.
   *
   * @param langCd 사용자 언어
   * @param categoryCd 분류 (null 이면 전체)
   * @param keyword 검색어 (소문자, LIKE 패턴 - null 이면 전체)
   * @param includeScheduled 예약(게시 전) 공지 포함 여부 (Y/N, 관리자)
   * @param now 기준 시각
   * @param pageable 페이징 (정렬은 게시일시 최신순 고정)
   * @return 표시 버전 목록
   */
  // CLOB 본문 검색(LOWER/LIKE)은 JPQL 에서 허용되지 않아 네이티브 SQL 사용 (Oracle / H2 Oracle 모드 공통)
  @Query(nativeQuery = true, value = """
        SELECT I.* FROM TB_NOTICE_I18N I, TB_NOTICE N
         WHERE N.NOTICE_ID = I.NOTICE_ID
           AND N.DEL_YN = 'N'
           AND (:includeScheduled = 'Y' OR N.POST_DATE <= :now)
           AND (:categoryCd IS NULL OR N.CATEGORY_CD = :categoryCd)
           AND (I.LANG_CD = :langCd
                OR (I.LANG_CD = N.BASE_LANG_CD
                    AND NOT EXISTS (SELECT 1 FROM TB_NOTICE_I18N X
                                     WHERE X.NOTICE_ID = N.NOTICE_ID AND X.LANG_CD = :langCd)))
           AND (:keyword IS NULL
                OR LOWER(I.TITLE) LIKE :keyword ESCAPE '\\'
                OR LOWER(I.CONTENT_TEXT) LIKE :keyword ESCAPE '\\')
         ORDER BY N.POST_DATE DESC, N.NOTICE_ID DESC
      """,
      countQuery = """
        SELECT COUNT(*) FROM TB_NOTICE_I18N I, TB_NOTICE N
         WHERE N.NOTICE_ID = I.NOTICE_ID
           AND N.DEL_YN = 'N'
           AND (:includeScheduled = 'Y' OR N.POST_DATE <= :now)
           AND (:categoryCd IS NULL OR N.CATEGORY_CD = :categoryCd)
           AND (I.LANG_CD = :langCd
                OR (I.LANG_CD = N.BASE_LANG_CD
                    AND NOT EXISTS (SELECT 1 FROM TB_NOTICE_I18N X
                                     WHERE X.NOTICE_ID = N.NOTICE_ID AND X.LANG_CD = :langCd)))
           AND (:keyword IS NULL
                OR LOWER(I.TITLE) LIKE :keyword ESCAPE '\\'
                OR LOWER(I.CONTENT_TEXT) LIKE :keyword ESCAPE '\\')
      """)
  Page<NoticeI18n> searchDisplayed(
      @Param("langCd") String langCd, @Param("categoryCd") String categoryCd,
      @Param("keyword") String keyword, @Param("includeScheduled") String includeScheduled,
      @Param("now") LocalDateTime now, Pageable pageable);

  /**
   * 공지들의 언어별 내용. (언어 배지 / 대체 표시)
   *
   * @param noticeIds 공지ID 목록
   * @return 언어별 내용
   */
  List<NoticeI18n> findByIdNoticeIdIn(Collection<Long> noticeIds);

  /**
   * 공지의 언어별 내용.
   *
   * @param noticeId 공지ID
   * @return 언어별 내용
   */
  List<NoticeI18n> findByIdNoticeId(Long noticeId);
}
