package com.flowlog.backend.global.common.model;

import jakarta.persistence.MappedSuperclass;
import org.springframework.data.domain.Persistable;

/**
 * PK 를 직접 지정하는(자연키/복합키) Entity 의 공통 부모.
 *
 * <p>Spring Data 는 PK 가 null 이 아니면 기존 데이터로 판단해 {@code save()} 시 SELECT 후 merge 한다.
 * 생성일시(createdAt) 유무로 신규 여부를 판단하게 하여, 신규 저장 시 바로 INSERT 되도록 한다.
 *
 * <p>사용 대상: 공통코드, 다국어(_LANG), 매핑 테이블 등 시퀀스를 쓰지 않는 Entity.
 *
 * @param <I> PK 타입
 * @author flowlog
 * @since 2026-11-05
 */
@MappedSuperclass
public abstract class AssignedIdEntity<I> extends BaseEntity implements Persistable<I> {

  @Override
  public boolean isNew() {
    return getCreatedAt() == null;
  }
}
