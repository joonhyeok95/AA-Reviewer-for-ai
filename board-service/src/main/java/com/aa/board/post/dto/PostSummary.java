package com.aa.board.post.dto;

import java.time.LocalDateTime;

import com.aa.board.post.domain.Post;

/**
 * 목록 조회용 요약 응답 (본문 제외). JPA 목록과 MyBatis 검색 결과 모두 이 타입으로 매핑한다.
 */
public record PostSummary(
        Long id,
        String title,
        String author,
        long viewCount,
        LocalDateTime createdAt) {

    public static PostSummary from(Post post) {
        return new PostSummary(post.getId(), post.getTitle(), post.getAuthor(),
                post.getViewCount(), post.getCreatedAt());
    }
}
