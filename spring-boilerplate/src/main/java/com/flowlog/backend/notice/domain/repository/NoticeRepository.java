package com.flowlog.backend.notice.domain.repository;

import com.flowlog.backend.notice.domain.model.Notice;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 공지사항 Repository.
 *
 * @author flowlog
 * @since 2026-11-05
 */
public interface NoticeRepository extends JpaRepository<Notice, Long> {

  /**
   * 현재 노출 중인 팝업 공지. (게시됨 + 팝업 기간 내)
   *
   * @param now 기준 시각
   * @param today 기준 일자
   * @return 팝업 공지 (게시일시 최신순)
   */
  @Query("""
      select n from Notice n
       where n.delYn = 'N' and n.popupYn = 'Y'
         and n.postDate <= :now
         and (n.popupStartDt is null or n.popupStartDt <= :today)
         and (n.popupEndDt is null or n.popupEndDt >= :today)
       order by n.postDate desc, n.id desc
      """)
  List<Notice> findActivePopups(@Param("now") LocalDateTime now, @Param("today") LocalDate today);

  /**
   * 메일 발송 대상 공지. (발송 대기 + 게시일시 도래)
   *
   * @param now 기준 시각
   * @return 공지 ID 목록
   */
  @Query("""
      select n.id from Notice n
       where n.delYn = 'N' and n.mailStatusCd = 'READY' and n.postDate <= :now
       order by n.postDate, n.id
      """)
  List<Long> findMailDueIds(@Param("now") LocalDateTime now);

  /**
   * 메일 상태를 원자적으로 바꾼다. (여러 인스턴스 중 1대만 발송을 선점)
   * 수정일시를 함께 기록해 발송 중(SENDING) 멈춤 판단 기준으로 쓴다.
   *
   * @param id 공지ID
   * @param from 현재 상태
   * @param to 변경 상태
   * @param now 현재 일시
   * @return 변경 건수 (1 이면 선점 성공)
   */
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query("""
      update Notice n set n.mailStatusCd = :to, n.updatedAt = :now
       where n.id = :id and n.mailStatusCd = :from
      """)
  int changeMailStatus(@Param("id") Long id, @Param("from") String from, @Param("to") String to,
      @Param("now") LocalDateTime now);

  /**
   * 발송 중(SENDING) 상태로 일정 시간 이상 멈춘 공지를 발송 대기(READY)로 되돌린다.
   * (발송하던 인스턴스가 중단된 경우. 이미 성공한 수신자는 재발송에서 제외된다)
   *
   * @param staleBefore 이 일시 이전에 선점된 발송은 중단된 것으로 본다
   * @param now 현재 일시
   * @return 되돌린 건수
   */
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query("""
      update Notice n set n.mailStatusCd = 'READY', n.updatedAt = :now
       where n.mailStatusCd = 'SENDING' and n.updatedAt < :staleBefore
      """)
  int recoverStaleSending(@Param("staleBefore") LocalDateTime staleBefore,
      @Param("now") LocalDateTime now);

  /**
   * 조회수 1 증가. (원자적 update)
   *
   * @param id 공지ID
   * @return 변경 건수
   */
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query("update Notice n set n.viewCnt = n.viewCnt + 1 where n.id = :id")
  int increaseViewCnt(@Param("id") Long id);
}
