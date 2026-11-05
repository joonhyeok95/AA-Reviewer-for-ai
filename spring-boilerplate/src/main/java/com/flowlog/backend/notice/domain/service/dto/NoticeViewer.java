package com.flowlog.backend.notice.domain.service.dto;

/**
 * 공지 조회자 정보.
 *
 * @param userId 사용자ID (조회수 중복 판정)
 * @param langCd 사용자 언어 (설정 언어 → Accept-Language → 기본 언어)
 * @param manager 공지 관리 권한 (예약 공지 조회 가능)
 * @author flowlog
 * @since 2026-11-05
 */
public record NoticeViewer(String userId, String langCd, boolean manager) {
}
