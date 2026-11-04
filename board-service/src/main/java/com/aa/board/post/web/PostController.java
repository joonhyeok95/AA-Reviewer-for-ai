package com.aa.board.post.web;

import java.net.URI;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.aa.board.common.dto.PageResponse;
import com.aa.board.post.dto.PostCreateRequest;
import com.aa.board.post.dto.PostResponse;
import com.aa.board.post.dto.PostSearchCondition;
import com.aa.board.post.dto.PostSummary;
import com.aa.board.post.dto.PostUpdateRequest;
import com.aa.board.post.service.PostService;

@Validated
@RestController
@RequestMapping("/api/posts")
public class PostController {

    private final PostService postService;

    public PostController(PostService postService) {
        this.postService = postService;
    }

    /** 등록 */
    @PostMapping
    public ResponseEntity<PostResponse> create(@Valid @RequestBody PostCreateRequest request) {
        PostResponse created = postService.create(request);
        return ResponseEntity.created(URI.create("/api/posts/" + created.id())).body(created);
    }

    /** 페이지 목록 (JPA) */
    @GetMapping
    public PageResponse<PostSummary> list(
            @PageableDefault(size = 10, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
        return postService.list(pageable);
    }

    /** 검색 (MyBatis 동적 쿼리) */
    @GetMapping("/search")
    public PageResponse<PostSummary> search(
            @ModelAttribute PostSearchCondition cond,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size) {
        return postService.search(cond, page, size);
    }

    /** 상세 보기 (조회수 증가) */
    @GetMapping("/{id}")
    public PostResponse view(@PathVariable Long id) {
        return postService.view(id);
    }

    /** 수정 */
    @PutMapping("/{id}")
    public PostResponse update(@PathVariable Long id, @Valid @RequestBody PostUpdateRequest request) {
        return postService.update(id, request);
    }

    /** 삭제 */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        postService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
