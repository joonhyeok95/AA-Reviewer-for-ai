package com.aa.board.post.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PostUpdateRequest(
        @NotBlank(message = "제목은 필수입니다.") @Size(max = 200) String title,
        @NotBlank(message = "내용은 필수입니다.") String content) {
}
