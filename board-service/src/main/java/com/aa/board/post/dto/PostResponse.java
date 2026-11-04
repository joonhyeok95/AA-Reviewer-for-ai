package com.aa.board.post.dto;

import java.time.LocalDateTime;

import com.aa.board.post.domain.Post;

public record PostResponse(
        Long id,
        String title,
        String content,
        String author,
        long viewCount,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public static PostResponse from(Post post) {
        return new PostResponse(post.getId(), post.getTitle(), post.getContent(), post.getAuthor(),
                post.getViewCount(), post.getCreatedAt(), post.getUpdatedAt());
    }
}
