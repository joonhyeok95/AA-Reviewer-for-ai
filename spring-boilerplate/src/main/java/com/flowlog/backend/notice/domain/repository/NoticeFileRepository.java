package com.flowlog.backend.notice.domain.repository;

import com.flowlog.backend.notice.domain.model.NoticeFile;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 공지사항 첨부파일 Repository.
 *
 * @author flowlog
 * @since 2026-11-05
 */
public interface NoticeFileRepository extends JpaRepository<NoticeFile, Long> {

  /**
   * 공지의 첨부파일.
   *
   * @param noticeId 공지ID
   * @return 첨부파일 (등록순)
   */
  List<NoticeFile> findByNoticeIdOrderByIdAsc(Long noticeId);

  /**
   * 여러 공지의 첨부파일. (목록 첨부 표시)
   *
   * @param noticeIds 공지ID 목록
   * @return 첨부파일
   */
  List<NoticeFile> findByNoticeIdIn(Collection<Long> noticeIds);

  /**
   * 공지에 속한 첨부파일. (다른 공지의 파일 접근 차단)
   *
   * @param id 파일ID
   * @param noticeId 공지ID
   * @return 첨부파일
   */
  Optional<NoticeFile> findByIdAndNoticeId(Long id, Long noticeId);

  /**
   * 공지의 첨부파일 수.
   *
   * @param noticeId 공지ID
   * @return 건수
   */
  long countByNoticeId(Long noticeId);
}
