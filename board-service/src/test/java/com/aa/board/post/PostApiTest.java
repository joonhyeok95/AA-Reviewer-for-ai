package com.aa.board.post;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.aa.board.post.domain.Post;
import com.aa.board.post.repository.PostRepository;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("게시판 API")
class PostApiTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    PostRepository postRepository;

    @BeforeEach
    void setUp() {
        postRepository.deleteAll();
    }

    private Post savePost(String title, String content, String author) {
        return postRepository.save(new Post(title, content, author));
    }

    @Nested
    @DisplayName("등록")
    class Create {

        @Test
        @DisplayName("게시글을 등록하면 201과 Location 헤더, 생성된 게시글을 반환한다")
        void create() throws Exception {
            mockMvc.perform(post("/api/posts")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"title":"첫 글","content":"안녕하세요","author":"joon"}
                                    """))
                    .andExpect(status().isCreated())
                    .andExpect(header().string("Location", endsWith("/api/posts/" + postRepository.findAll().getFirst().getId())))
                    .andExpect(jsonPath("$.id").isNumber())
                    .andExpect(jsonPath("$.title").value("첫 글"))
                    .andExpect(jsonPath("$.author").value("joon"))
                    .andExpect(jsonPath("$.viewCount").value(0))
                    .andExpect(jsonPath("$.createdAt").exists());

            assertThat(postRepository.count()).isEqualTo(1);
        }

        @Test
        @DisplayName("제목이 비어 있으면 400과 필드 오류를 반환한다")
        void createValidationFail() throws Exception {
            mockMvc.perform(post("/api/posts")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"title":"","content":"내용","author":"joon"}
                                    """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.title").value("제목은 필수입니다."));

            assertThat(postRepository.count()).isZero();
        }
    }

    @Nested
    @DisplayName("수정")
    class Update {

        @Test
        @DisplayName("게시글의 제목과 내용을 수정한다")
        void update() throws Exception {
            Post saved = savePost("원래 제목", "원래 내용", "joon");

            mockMvc.perform(put("/api/posts/{id}", saved.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"title":"바뀐 제목","content":"바뀐 내용"}
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.title").value("바뀐 제목"))
                    .andExpect(jsonPath("$.content").value("바뀐 내용"))
                    .andExpect(jsonPath("$.author").value("joon"));

            Post updated = postRepository.findById(saved.getId()).orElseThrow();
            assertThat(updated.getTitle()).isEqualTo("바뀐 제목");
            assertThat(updated.getContent()).isEqualTo("바뀐 내용");
        }

        @Test
        @DisplayName("존재하지 않는 게시글을 수정하면 404를 반환한다")
        void updateNotFound() throws Exception {
            mockMvc.perform(put("/api/posts/{id}", 9999)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"title":"제목","content":"내용"}
                                    """))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("삭제")
    class Delete {

        @Test
        @DisplayName("게시글을 삭제하면 204를 반환하고 이후 조회 시 404가 된다")
        void deletePost() throws Exception {
            Post saved = savePost("삭제할 글", "내용", "joon");

            mockMvc.perform(delete("/api/posts/{id}", saved.getId()))
                    .andExpect(status().isNoContent());

            assertThat(postRepository.existsById(saved.getId())).isFalse();
            mockMvc.perform(get("/api/posts/{id}", saved.getId()))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("페이지 목록")
    class PageList {

        @Test
        @DisplayName("15건 중 첫 페이지(10건)를 최신순으로 조회한다")
        void firstPage() throws Exception {
            for (int i = 1; i <= 15; i++) {
                savePost("글 " + i, "내용 " + i, "joon");
            }

            mockMvc.perform(get("/api/posts").param("page", "0").param("size", "10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(10))
                    .andExpect(jsonPath("$.content[0].title").value("글 15"))
                    .andExpect(jsonPath("$.content[0].content").doesNotExist())
                    .andExpect(jsonPath("$.page").value(0))
                    .andExpect(jsonPath("$.totalElements").value(15))
                    .andExpect(jsonPath("$.totalPages").value(2))
                    .andExpect(jsonPath("$.first").value(true))
                    .andExpect(jsonPath("$.last").value(false));
        }

        @Test
        @DisplayName("마지막 페이지에는 남은 5건이 조회된다")
        void lastPage() throws Exception {
            for (int i = 1; i <= 15; i++) {
                savePost("글 " + i, "내용 " + i, "joon");
            }

            mockMvc.perform(get("/api/posts").param("page", "1").param("size", "10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(5))
                    .andExpect(jsonPath("$.content[4].title").value("글 1"))
                    .andExpect(jsonPath("$.last").value(true));
        }
    }

    @Nested
    @DisplayName("상세 보기")
    class View {

        @Test
        @DisplayName("상세 조회 시 본문을 반환하고 조회수가 1씩 증가한다")
        void view() throws Exception {
            Post saved = savePost("상세 글", "상세 내용", "joon");

            mockMvc.perform(get("/api/posts/{id}", saved.getId()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.title").value("상세 글"))
                    .andExpect(jsonPath("$.content").value("상세 내용"))
                    .andExpect(jsonPath("$.viewCount").value(1));

            mockMvc.perform(get("/api/posts/{id}", saved.getId()))
                    .andExpect(jsonPath("$.viewCount").value(2));
        }

        @Test
        @DisplayName("존재하지 않는 게시글은 404 ProblemDetail을 반환한다")
        void viewNotFound() throws Exception {
            mockMvc.perform(get("/api/posts/{id}", 9999))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.detail").value("게시글을 찾을 수 없습니다. id=9999"));
        }
    }

    @Nested
    @DisplayName("검색 (MyBatis)")
    class Search {

        @Test
        @DisplayName("키워드와 작성자 조건으로 동적 검색한다")
        void searchByKeywordAndAuthor() throws Exception {
            savePost("스프링 부트 4 정리", "JPA와 MyBatis", "joon");
            savePost("스프링 클라우드", "Eureka, Config", "kim");
            savePost("자바 21", "가상 스레드와 스프링", "joon");
            savePost("오늘 점심", "김치찌개", "joon");

            mockMvc.perform(get("/api/posts/search").param("keyword", "스프링").param("author", "joon"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(2))
                    .andExpect(jsonPath("$.content[0].title").value("자바 21"))
                    .andExpect(jsonPath("$.content[1].title").value("스프링 부트 4 정리"));
        }
    }
}
