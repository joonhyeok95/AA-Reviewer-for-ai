package com.aa.board.post.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.aa.board.post.dto.PostSearchCondition;
import com.aa.board.post.dto.PostSummary;

/**
 * 복잡한 조회(동적 조건 검색)는 MyBatis로 처리한다. SQL은 resources/mapper/PostQueryMapper.xml 참고.
 */
@Mapper
public interface PostQueryMapper {

    List<PostSummary> search(@Param("cond") PostSearchCondition cond,
                             @Param("offset") long offset,
                             @Param("limit") int limit);

    long count(@Param("cond") PostSearchCondition cond);
}
