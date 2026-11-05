package com.flowlog.backend.notice.domain.repository;

import com.flowlog.backend.notice.domain.model.NoticeI18n;
import com.flowlog.backend.notice.domain.model.NoticeI18nId;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 공지사항 언어별 내용 Repository.
 *
 * <p>목록 검색(표시 언어 선택 + CLOB 본문 검색)은 MyBatis {@link NoticeSearchMapper}.
 *
 * @author flowlog
 * @since 2026-11-05
 */
public interface NoticeI18nRepository extends JpaRepository<NoticeI18n, NoticeI18nId> {

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
