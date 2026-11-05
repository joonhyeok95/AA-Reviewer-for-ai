package com.flowlog.backend.sample.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.flowlog.backend.global.exception.BusinessException;
import com.flowlog.backend.sample.domain.model.Sample;
import com.flowlog.backend.sample.domain.repository.SampleRepository;
import com.flowlog.backend.sample.domain.service.dto.SampleCreateRequest;
import com.flowlog.backend.sample.domain.service.dto.SampleResponse;
import com.flowlog.backend.sample.exception.SampleErrorCode;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * SampleService 단위 테스트. (Mockito, given-when-then)
 *
 * @author flowlog
 * @since 2026-11-05
 */
@ExtendWith(MockitoExtension.class)
class SampleServiceTest {

  @Mock
  private SampleRepository sampleRepository;

  @InjectMocks
  private SampleService sampleService;

  @Test
  @DisplayName("단건 조회 - 존재하지 않는 ID 면 B1001 예외")
  void getSample_notFound_throwsBusinessException() {
    // given
    given(sampleRepository.findById(99L)).willReturn(Optional.empty());

    // when & then
    assertThatThrownBy(() -> sampleService.getSample(99L))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(SampleErrorCode.SAMPLE_NOT_FOUND);
  }

  @Test
  @DisplayName("등록 - 샘플명이 중복이면 B1002 예외, 저장하지 않음")
  void createSample_duplicateName_throwsBusinessException() {
    // given
    SampleCreateRequest request = new SampleCreateRequest("중복", "설명", "Y");
    given(sampleRepository.existsByName("중복")).willReturn(true);

    // when & then
    assertThatThrownBy(() -> sampleService.createSample(request))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(SampleErrorCode.SAMPLE_NAME_DUPLICATED);
    then(sampleRepository).should(never()).save(any());
  }

  @Test
  @DisplayName("등록 - 정상 요청이면 저장 후 응답 DTO 반환")
  void createSample_success() {
    // given
    SampleCreateRequest request = new SampleCreateRequest("신규", "설명", "Y");
    given(sampleRepository.existsByName("신규")).willReturn(false);
    given(sampleRepository.save(any(Sample.class))).willAnswer(inv -> inv.getArgument(0));

    // when
    SampleResponse response = sampleService.createSample(request);

    // then
    assertThat(response.name()).isEqualTo("신규");
    assertThat(response.useYn()).isEqualTo("Y");
  }
}
