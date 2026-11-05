package com.flowlog.backend.notice.domain.repository;

import com.flowlog.backend.notice.domain.model.NoticeMailTarget;
import com.flowlog.backend.notice.domain.model.NoticeMailTargetId;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 공지 메일 대상 Repository.
 *
 * @author flowlog
 * @since 2026-11-05
 */
public interface NoticeMailTargetRepository
    extends JpaRepository<NoticeMailTarget, NoticeMailTargetId> {

  /**
   * 공지의 메일 대상.
   *
   * @param noticeId 공지ID
   * @return 메일 대상
   */
  List<NoticeMailTarget> findByIdNoticeId(Long noticeId);
}
