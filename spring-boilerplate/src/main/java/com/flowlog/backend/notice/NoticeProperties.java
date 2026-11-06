package com.flowlog.backend.notice;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 공지사항 설정. ({@code app.notice.*})
 *
 * @param file 첨부파일 설정
 * @param mail 메일 발송 설정
 * @author flowlog
 * @since 2026-11-05
 */
@ConfigurationProperties(prefix = "app.notice")
public record NoticeProperties(File file, Mail mail) {

  /**
   * 첨부파일 설정.
   *
   * @param storagePath 저장 경로 (storage-type=local, 웹 경로 밖. 다중 인스턴스면 공유 디렉터리)
   * @param allowedExtensions 허용 확장자 (소문자)
   * @param maxFiles 공지당 최대 첨부 수
   * @param storageType 저장소 : local (디렉터리) / db (TB_FILE_STORE BLOB)
   */
  public record File(
      String storagePath,
      @DefaultValue({"pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "hwp", "hwpx", "txt",
          "csv", "zip", "png", "jpg", "jpeg", "gif"}) List<String> allowedExtensions,
      @DefaultValue("10") int maxFiles,
      @DefaultValue("local") String storageType) {
  }

  /**
   * 메일 발송 설정.
   *
   * @param schedulerEnabled 발송 스케줄러 사용 여부
   * @param pollInterval 발송 대상 확인 주기
   * @param batchSize 메일 1통의 최대 수신자 수 (BCC)
   * @param from 발신 주소
   * @param linkTemplate 공지 링크 ({id} 치환)
   * @param sendingTimeout 발송 중(SENDING) 상태가 이 시간을 넘으면 중단된 것으로 보고 재발송
   */
  public record Mail(
      @DefaultValue("true") boolean schedulerEnabled,
      @DefaultValue("60s") Duration pollInterval,
      @DefaultValue("100") int batchSize,
      String from,
      String linkTemplate,
      @DefaultValue("30m") Duration sendingTimeout) {
  }
}
