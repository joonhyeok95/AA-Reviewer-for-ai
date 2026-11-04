package com.aa.board.post.dto;

import java.time.LocalDate;

/**
 * MyBatis 동적 검색 조건. 모든 필드는 선택값이다.
 */
public record PostSearchCondition(
        String keyword,
        String author,
        LocalDate from,
        LocalDate to) {
}
