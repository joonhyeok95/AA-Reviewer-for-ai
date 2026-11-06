package com.flowlog.backend.notice;

import com.flowlog.backend.global.storage.DatabaseFileStorage;
import com.flowlog.backend.global.storage.FileStorage;
import com.flowlog.backend.global.storage.LocalFileStorage;
import com.flowlog.backend.global.storage.StoredFileRepository;
import com.flowlog.backend.notice.domain.service.NoticeMailService;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * 공지사항 설정 + 첨부 저장소 + 메일 발송 스케줄러.
 *
 * <p>첨부 저장소 {@code app.notice.file.storage-type} : {@code local}(디렉터리, 기본) /
 * {@code db}(BLOB). 여러 인스턴스로 운영하면 db 또는 공유 디렉터리(NFS)의 local 을 쓴다.
 *
 * <p>{@code app.notice.mail.scheduler-enabled=true} 일 때 {@code poll-interval} 마다 게시일시가 도래한
 * 발송 대기 공지를 처리한다. 여러 인스턴스가 함께 돌아도 공지 단위 선점(상태 원자적 변경)으로 1번만 발송된다.
 *
 * @author flowlog
 * @since 2026-11-05
 */
@Configuration
@EnableConfigurationProperties(NoticeProperties.class)
public class NoticeConfig {

  /** 첨부 저장소: DB(BLOB). */
  public static final String STORAGE_DB = "db";

  /**
   * 공지 첨부 저장소.
   *
   * @param properties 공지 설정
   * @param storedFileRepository DB 저장 파일 Repository
   * @param clock 시계
   * @return 파일 저장소
   */
  @Bean
  public FileStorage noticeFileStorage(NoticeProperties properties,
      StoredFileRepository storedFileRepository, Clock clock) {
    return STORAGE_DB.equalsIgnoreCase(properties.file().storageType())
        ? new DatabaseFileStorage(storedFileRepository)
        : new LocalFileStorage(properties.file().storagePath(), clock);
  }

  /** 메일 발송 스케줄러. */
  @Slf4j
  @Configuration
  @EnableScheduling
  @RequiredArgsConstructor
  @ConditionalOnProperty(prefix = "app.notice.mail", name = "scheduler-enabled",
      havingValue = "true", matchIfMissing = true)
  static class NoticeMailScheduler {

    private final NoticeMailService noticeMailService;

    @Scheduled(initialDelayString = "${app.notice.mail.poll-interval:60s}",
        fixedDelayString = "${app.notice.mail.poll-interval:60s}")
    void sendDueMails() {
      try {
        int processed = noticeMailService.sendDueMails();
        if (processed > 0) {
          log.info("[NoticeMailScheduler] 발송 처리 {}건", processed);
        }
      } catch (RuntimeException e) {
        log.error("[NoticeMailScheduler] 실행 오류", e);
      }
    }
  }
}
