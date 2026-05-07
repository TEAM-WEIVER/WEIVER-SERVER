package com.weiver.jobposting.service;

import com.weiver.jobposting.repository.JobPostingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;


@Slf4j
@Component
@RequiredArgsConstructor
public class JobPostingScheduler {

    private final JobPostingRepository jobPostingRepository;

    // 매일 밤 12시 0분 0초에 실행
    @Scheduled(cron = "0 0 0 * * *", zone = "Asia/Seoul")
    @Transactional
    public void closeExpiredJobPostings() {
        log.info("마감일이 지난 공고를 CLOSED 상태로 변경합니다.");

        LocalDate now = LocalDate.now();
        int updatedCount = jobPostingRepository.closeExpiredJobPostings(now);

        log.info("총 {}개의 공고가 CLOSED로 변경되었습니다.", updatedCount);
    }
}
