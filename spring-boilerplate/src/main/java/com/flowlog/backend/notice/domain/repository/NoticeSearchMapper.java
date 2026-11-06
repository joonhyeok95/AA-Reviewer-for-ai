package com.flowlog.backend.notice.domain.repository;

import com.flowlog.backend.notice.domain.service.dto.NoticeSearchRow;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 공지 목록 검색 매퍼 (MyBatis). SQL : {@code resources/mapper/notice/NoticeSearchMapper.xml}
 *
 * <p>공지마다 <b>표시될 언어 버전 1건</b>을 고른다 : 사용자 언어 버전이 있으면 그것, 없으면 대표 언어 버전.
 * 검색어는 이 표시 버전의 제목/본문 텍스트(CLOB)에 적용한다. (NT-64, NT-66)
 * CLOB 대소문자 무시 검색({@code LOWER(CLOB) LIKE})이 JPQL 에서 허용되지 않아 MyBatis 로 작성했다.
 *
 * <p>JPA 와 같은 DataSource/트랜잭션을 사용한다. 같은 트랜잭션에서 JPA 로 변경한 내용은
 * flush 되기 전에는 이 쿼리에 보이지 않는다.
 *
 * @author flowlog
 * @since 2026-11-05
 */
@Mapper
public interface NoticeSearchMapper {

  /**
   * 표시 버전 목록 (게시일시 최신순, 페이징).
   *
   * @param langCd 사용자 언어
   * @param categoryCd 분류 (null 이면 전체)
   * @param keyword 검색어 LIKE 패턴 (소문자, 와일드카드 이스케이프 - null 이면 전체)
   * @param includeScheduled 예약(게시 전) 공지 포함 여부 (관리자)
   * @param now 기준 시각
   * @param offset 시작 위치
   * @param limit 건수
   * @return 표시 버전 (공지ID, 언어, 제목)
   */
  List<NoticeSearchRow> searchDisplayed(
      @Param("langCd") String langCd, @Param("categoryCd") String categoryCd,
      @Param("keyword") String keyword, @Param("includeScheduled") boolean includeScheduled,
      @Param("now") LocalDateTime now, @Param("offset") long offset, @Param("limit") int limit);

  /**
   * 표시 버전 전체 건수.
   *
   * @param langCd 사용자 언어
   * @param categoryCd 분류
   * @param keyword 검색어 LIKE 패턴
   * @param includeScheduled 예약 공지 포함 여부
   * @param now 기준 시각
   * @return 건수
   */
  long countDisplayed(
      @Param("langCd") String langCd, @Param("categoryCd") String categoryCd,
      @Param("keyword") String keyword, @Param("includeScheduled") boolean includeScheduled,
      @Param("now") LocalDateTime now);
}
