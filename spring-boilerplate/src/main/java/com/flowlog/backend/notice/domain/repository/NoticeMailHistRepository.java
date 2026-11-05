package com.flowlog.backend.notice.domain.repository;

import com.flowlog.backend.notice.domain.model.NoticeMailHist;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

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
}
