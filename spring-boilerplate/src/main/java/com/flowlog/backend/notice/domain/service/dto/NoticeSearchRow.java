package com.flowlog.backend.notice.domain.service.dto;

/**
 * 공지 목록 검색 결과 행 (MyBatis 매핑) - 공지별로 표시될 언어 버전.
 *
 * @param noticeId 공지ID
 * @param langCd 표시 언어 (사용자 언어 버전 또는 대표 언어 버전)
 * @param title 제목
 * @author flowlog
 * @since 2026-11-05
 */
public record NoticeSearchRow(Long noticeId, String langCd, String title) {
}
