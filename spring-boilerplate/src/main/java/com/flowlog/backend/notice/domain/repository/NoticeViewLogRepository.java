package com.flowlog.backend.notice.domain.repository;

import com.flowlog.backend.notice.domain.model.NoticeViewLog;
import com.flowlog.backend.notice.domain.model.NoticeViewLogId;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 공지 조회 로그 Repository.
 *
 * @author flowlog
 * @since 2026-11-05
 */
public interface NoticeViewLogRepository extends JpaRepository<NoticeViewLog, NoticeViewLogId> {
}
