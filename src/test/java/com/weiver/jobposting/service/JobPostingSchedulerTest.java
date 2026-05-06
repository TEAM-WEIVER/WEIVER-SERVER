package com.weiver.jobposting.service;

import com.weiver.jobposting.repository.JobPostingRepository;
import jakarta.persistence.QueryTimeoutException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;

import static org.assertj.core.api.AssertionsForClassTypes.assertThatCode;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class JobPostingSchedulerTest {

    @Mock
    private JobPostingRepository jobPostingRepository;

    @InjectMocks
    private JobPostingScheduler jobPostingScheduler;

    @Test
    @DisplayName("자정 스케줄러가 실행되면 Repository의 벌크 업데이트 메서드가 1회 호출되어야 한다.")
    void closeExpiredJobPostingsScheduler() {

        // given
        given(jobPostingRepository.closeExpiredJobPostings(any(LocalDate.class)))
                .willReturn(5);

        // when
        jobPostingScheduler.closeExpiredJobPostings();

        // then
        verify(jobPostingRepository, times(1)).closeExpiredJobPostings(any(LocalDate.class));
    }

    @Test
    @DisplayName("업데이트 대상이 0건일 경우: 예외 없이 정상적으로 로직이 종료되어야 한다.")
    void closeExpiredJobPostingsScheduler_NoDataToUpdate() {
        // given
        given(jobPostingRepository.closeExpiredJobPostings(any(LocalDate.class)))
                .willReturn(0); // 업데이트 대상 0건

        // when & then
        assertThatCode(() -> jobPostingScheduler.closeExpiredJobPostings())
                .doesNotThrowAnyException();

        verify(jobPostingRepository, times(1)).closeExpiredJobPostings(any(LocalDate.class));
    }

    @Test
    @DisplayName("DB 타임아웃 등 예외 발생: 스케줄러가 예외를 삼키지 않고 상위로 전파해야 한다.")
    void closeExpiredJobPostingsScheduler_DbException() {
        // given
        given(jobPostingRepository.closeExpiredJobPostings(any(LocalDate.class)))
                .willThrow(new QueryTimeoutException("DB Query Timeout", new RuntimeException()));

        // when & then
        assertThatThrownBy(() -> jobPostingScheduler.closeExpiredJobPostings())
                .isInstanceOf(QueryTimeoutException.class)
                .hasMessageContaining("DB Query Timeout");

        verify(jobPostingRepository, times(1)).closeExpiredJobPostings(any(LocalDate.class));
    }
}