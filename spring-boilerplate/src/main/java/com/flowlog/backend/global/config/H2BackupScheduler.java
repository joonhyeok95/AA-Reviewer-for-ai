package com.flowlog.backend.global.config;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * H2 파일 DB 온라인 백업 (h2file 프로필). 서비스를 멈추지 않고 일관된 시점의 zip 을 만든다.
 *
 * <ul>
 *   <li>{@code BACKUP TO '{dir}/flowlog-yyyyMMdd-HHmm.zip'} - H2 가 쓰기 중에도 일관된 사본을 만든다</li>
 *   <li>{@code keep-days} 지난 백업은 삭제. EBS 스냅샷(AWS Backup)과 함께 쓰면 2중 보관</li>
 *   <li>경로는 설정값만 쓰고 사용자 입력을 받지 않는다 (작은따옴표 포함 경로는 거부)</li>
 * </ul>
 *
 * @author flowlog
 * @since 2026-11-07
 */
@Slf4j
@Component
@EnableScheduling
@ConditionalOnProperty(prefix = "app.h2.backup", name = "enabled", havingValue = "true")
public class H2BackupScheduler {

  private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmm");
  private static final String PREFIX = "flowlog-";

  private final JdbcTemplate jdbcTemplate;
  private final Path dir;
  private final int keepDays;
  private final Clock clock;

  /**
   * 생성자.
   *
   * @param jdbcTemplate JDBC
   * @param dir 백업 폴더
   * @param keepDays 보관 일수
   * @param clock 시계
   */
  public H2BackupScheduler(JdbcTemplate jdbcTemplate,
      @Value("${app.h2.backup.dir:/data/backup}") String dir,
      @Value("${app.h2.backup.keep-days:14}") int keepDays, Clock clock) {
    if (dir.contains("'")) {
      throw new IllegalArgumentException("app.h2.backup.dir 에 작은따옴표를 쓸 수 없습니다");
    }
    this.jdbcTemplate = jdbcTemplate;
    this.dir = Path.of(dir).toAbsolutePath().normalize();
    this.keepDays = keepDays;
    this.clock = clock;
  }

  /** 정해진 시각에 백업 + 오래된 백업 삭제. */
  @Scheduled(cron = "${app.h2.backup.cron:0 30 3 * * *}")
  public void backup() {
    try {
      Files.createDirectories(dir);
      Path target = dir.resolve(PREFIX + LocalDateTime.now(clock).format(STAMP) + ".zip");
      jdbcTemplate.execute("BACKUP TO '" + target.toString().replace('\\', '/') + "'");
      log.info("[H2Backup] 백업 완료 {} ({} bytes)", target, Files.size(target));
      purge();
    } catch (IOException | RuntimeException e) {
      log.error("[H2Backup] 백업 실패", e);
    }
  }

  private void purge() throws IOException {
    Instant limit = Instant.now(clock).minus(keepDays, ChronoUnit.DAYS);
    try (DirectoryStream<Path> files = Files.newDirectoryStream(dir, PREFIX + "*.zip")) {
      for (Path file : files) {
        if (Files.getLastModifiedTime(file).toInstant().isBefore(limit)) {
          Files.deleteIfExists(file);
          log.info("[H2Backup] 오래된 백업 삭제 {}", file.getFileName());
        }
      }
    }
  }
}
