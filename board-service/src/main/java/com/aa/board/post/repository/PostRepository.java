package com.aa.board.post.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.aa.board.post.domain.Post;

public interface PostRepository extends JpaRepository<Post, Long> {
}
