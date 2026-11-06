package com.flowlog.backend.notice.domain.repository;

import com.flowlog.backend.notice.domain.model.NoticeMailHist;
import java.util.List;
import java.util.Set;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 공지 메일 발송 이력 Repository.
 *
 * @author flowlog
 * @since 2026-11-05
 */
public interface NoticeMailHistRepository extends JpaRepository<NoticeMailHist, Long> {

  /**
   * 공지의 메일 발송 이력.
   *
   * @param noticeId 공지ID
   * @return 이력 (발송순)
   */
  List<NoticeMailHist> findByNoticeIdOrderByIdAsc(Long noticeId);

  /**
   * 이미 발송에 성공한 수신자. (중단 후 재발송 시 제외)
   *
   * @param noticeId 공지ID
   * @return 수신자 사용자ID
   */
  @Query("""
      select distinct h.receiverId from NoticeMailHist h
       where h.noticeId = :noticeId and h.statusCd = 'SUCCESS'
      """)
  Set<String> findSentReceiverIds(@Param("noticeId") Long noticeId);
}
