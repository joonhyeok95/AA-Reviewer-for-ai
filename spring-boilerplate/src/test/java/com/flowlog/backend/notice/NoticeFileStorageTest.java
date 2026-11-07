package com.flowlog.backend.notice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.flowlog.backend.global.exception.BusinessException;
import com.flowlog.backend.global.storage.DatabaseFileStorage;
import com.flowlog.backend.global.storage.FileStorage;
import com.flowlog.backend.global.storage.StoredFileRepository;
import com.flowlog.backend.notice.domain.service.NoticeFileService;
import com.flowlog.backend.notice.domain.service.dto.NoticeResponses.NoticeFileResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.Transactional;

/**
 * 첨부 DB 저장소 (app.notice.file.storage-type=db) - 다중 인스턴스 공유용.
 *
 * @author flowlog
 * @since 2026-11-06
 */
@SpringBootTest(properties = "app.notice.file.storage-type=db")
@Transactional
class NoticeFileStorageTest {

  @Autowired
  private NoticeFileService noticeFileService;

  @Autowired
  private FileStorage noticeFileStorage;

  @Autowired
  private StoredFileRepository storedFileRepository;

  @Test
  @DisplayName("storage-type=db - 업로드 내용은 TB_FILE_STORE 에, 다운로드·삭제도 DB 기준")
  void databaseStorage() throws Exception {
    // given
    assertThat(noticeFileStorage).isInstanceOf(DatabaseFileStorage.class);
    byte[] content = "첨부 내용".getBytes(StandardCharsets.UTF_8);

    // when : 업로드
    List<NoticeFileResponse> saved = noticeFileService.upload(1L, List.of(
        new MockMultipartFile("files", "guide.txt", "text/plain", content)), true, "admin");
    Long fileId = saved.get(0).fileId();

    // then : DB 에 저장, 다운로드 내용 일치
    assertThat(storedFileRepository.count()).isEqualTo(1);
    NoticeFileService.Download download = noticeFileService.download(1L, fileId, false);
    assertThat(download.fileName()).isEqualTo("guide.txt");
    assertThat(download.resource().getContentAsByteArray()).isEqualTo(content);

    // when : 삭제
    noticeFileService.delete(1L, fileId);

    // then
    assertThat(storedFileRepository.count()).isZero();
    assertThatThrownBy(() -> noticeFileService.download(1L, fileId, false))
        .isInstanceOf(BusinessException.class);
  }
}
