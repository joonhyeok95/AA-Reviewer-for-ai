package com.aa.board.post.service;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aa.board.common.dto.PageResponse;
import com.aa.board.common.exception.PostNotFoundException;
import com.aa.board.post.domain.Post;
import com.aa.board.post.dto.PostCreateRequest;
import com.aa.board.post.dto.PostResponse;
import com.aa.board.post.dto.PostSearchCondition;
import com.aa.board.post.dto.PostSummary;
import com.aa.board.post.dto.PostUpdateRequest;
import com.aa.board.post.mapper.PostQueryMapper;
import com.aa.board.post.repository.PostRepository;

@Service
@Transactional(readOnly = true)
public class PostService {

    private final PostRepository postRepository;
    private final PostQueryMapper postQueryMapper;

    public PostService(PostRepository postRepository, PostQueryMapper postQueryMapper) {
        this.postRepository = postRepository;
        this.postQueryMapper = postQueryMapper;
    }

    @Transactional
    public PostResponse create(PostCreateRequest request) {
        Post saved = postRepository.save(new Post(request.title(), request.content(), request.author()));
        return PostResponse.from(saved);
    }

    @Transactional
    public PostResponse update(Long id, PostUpdateRequest request) {
        Post post = findPost(id);
        post.update(request.title(), request.content());
        postRepository.flush(); // updatedAt 갱신값을 응답에 반영
        return PostResponse.from(post);
    }

    @Transactional
    public void delete(Long id) {
        postRepository.delete(findPost(id));
    }

    /** 상세 조회 시 조회수를 1 증가시킨다. */
    @Transactional
    public PostResponse view(Long id) {
        Post post = findPost(id);
        post.increaseViewCount();
        return PostResponse.from(post);
    }

    /** 단순 페이지 목록: JPA */
    public PageResponse<PostSummary> list(Pageable pageable) {
        return PageResponse.of(postRepository.findAll(pageable), PostSummary::from);
    }

    /** 동적 조건 검색: MyBatis */
    public PageResponse<PostSummary> search(PostSearchCondition cond, int page, int size) {
        long total = postQueryMapper.count(cond);
        var content = postQueryMapper.search(cond, (long) page * size, size);
        return PageResponse.of(content, page, size, total);
    }

    private Post findPost(Long id) {
        return postRepository.findById(id).orElseThrow(() -> new PostNotFoundException(id));
    }
}
