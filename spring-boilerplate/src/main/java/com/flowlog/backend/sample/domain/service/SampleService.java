package com.flowlog.backend.sample.domain.service;

import com.flowlog.backend.global.aop.Logging;
import com.flowlog.backend.global.common.domain.service.dto.PageResponse;
import com.flowlog.backend.global.exception.BusinessException;
import com.flowlog.backend.global.util.LikePatterns;
import com.flowlog.backend.sample.domain.model.Sample;
import com.flowlog.backend.sample.domain.repository.SampleRepository;
import com.flowlog.backend.sample.domain.service.dto.SampleCreateRequest;
import com.flowlog.backend.sample.domain.service.dto.SampleResponse;
import com.flowlog.backend.sample.domain.service.dto.SampleSearchRequest;
import com.flowlog.backend.sample.domain.service.dto.SampleUpdateRequest;
import com.flowlog.backend.sample.exception.SampleErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 샘플 서비스.
 *
 * <ul>
 *   <li>클래스 레벨 {@code @Transactional(readOnly = true)}, 변경 메서드에만 {@code @Transactional}.</li>
 *   <li>Entity 는 Service 밖으로 반환하지 않는다. (DTO 로 변환하여 반환)</li>
 * </ul>
 *
 * @author flowlog
 * @since 2026-11-05
 */
@Logging
@Service("sampleService")
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class SampleService {

  private final SampleRepository sampleRepository;

  /**
   * 샘플 목록을 조회한다.
   *
   * @param condition 검색 조건
   * @param pageable 페이징/정렬
   * @return 페이징 결과
   */
  public PageResponse<SampleResponse> getSamples(SampleSearchRequest condition, Pageable pageable) {
    return PageResponse.from(
        sampleRepository.search(LikePatterns.contains(condition.name()), condition.useYn(),
            pageable),
        SampleResponse::from);
  }

  /**
   * 샘플을 단건 조회한다.
   *
   * @param id 샘플 ID
   * @return 샘플
   * @throws BusinessException 샘플이 없는 경우 (B1001)
   */
  public SampleResponse getSample(Long id) {
    return SampleResponse.from(findSampleById(id));
  }

  /**
   * 샘플을 등록한다.
   *
   * @param request 등록 요청
   * @return 등록된 샘플
   * @throws BusinessException 샘플명이 중복된 경우 (B1002)
   */
  @Transactional
  public SampleResponse createSample(SampleCreateRequest request) {
    if (sampleRepository.existsByName(request.name())) {
      throw new BusinessException(SampleErrorCode.SAMPLE_NAME_DUPLICATED, request.name());
    }
    Sample saved = sampleRepository.save(request.toEntity());
    return SampleResponse.from(saved);
  }

  /**
   * 샘플을 수정한다.
   *
   * @param id 샘플 ID
   * @param request 수정 요청
   * @return 수정된 샘플
   * @throws BusinessException 샘플이 없거나(B1001) 샘플명이 중복된 경우(B1002)
   */
  @Transactional
  public SampleResponse updateSample(Long id, SampleUpdateRequest request) {
    Sample sample = findSampleById(id);
    if (sampleRepository.existsByNameAndIdNot(request.name(), id)) {
      throw new BusinessException(SampleErrorCode.SAMPLE_NAME_DUPLICATED, request.name());
    }
    sample.update(request.name(), request.description(), request.useYn());
    sampleRepository.flush();
    return SampleResponse.from(sample);
  }

  /**
   * 샘플을 삭제한다.
   *
   * @param id 샘플 ID
   * @throws BusinessException 샘플이 없는 경우 (B1001)
   */
  @Transactional
  public void deleteSample(Long id) {
    sampleRepository.delete(findSampleById(id));
  }

  private Sample findSampleById(Long id) {
    return sampleRepository.findById(id)
        .orElseThrow(() -> new BusinessException(
            SampleErrorCode.SAMPLE_NOT_FOUND, String.valueOf(id)));
  }
}
