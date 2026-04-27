package com.weiver.applicant.service;

import com.weiver.applicant.domain.Applicant;
import com.weiver.applicant.domain.Award;
import com.weiver.applicant.domain.Education;
import com.weiver.applicant.dto.request.post.AwardDetailDTO;
import com.weiver.applicant.dto.request.post.AwardRequestDTO;
import com.weiver.applicant.dto.request.post.EducationDetailDTO;
import com.weiver.applicant.dto.request.post.EducationRequestDTO;
import com.weiver.applicant.dto.request.put.ApplicantInfoRequestDTO;
import com.weiver.applicant.repository.*;
import com.weiver.global.exception.BusinessException;
import com.weiver.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ApplicantServiceImplTest {

    @Mock private ApplicantRepository applicantRepository;
    @Mock private EducationRepository educationRepository;
    @Mock private AwardRepository awardRepository;
    @Mock private CertificateRepository certificateRepository;
    @Mock private WorkExperienceRepository workExperienceRepository;

    @InjectMocks
    private ApplicantServiceImpl applicantService;

    // 레포지토리에 전달된 '진짜 리스트 데이터'를 낚아채서 검사하기 위한 도구
    @Captor
    private ArgumentCaptor<List<Education>> educationListCaptor;

    @Captor
    private ArgumentCaptor<List<Award>> awardListCaptor;

    @Test
    @DisplayName("구직자 정보 업데이트 정상 수행")
    void updateApplicantInfo_Success() {
        // Given
        long applicantId = 1L;

        // Mock이 아닌 실제 엔티티 생성 
        Applicant realApplicant = Applicant.builder()
                .applicantId(applicantId)
                .name("홍길동")
                .phoneNumber("010-1234-1234")
                .build();

        // 실제 DTO 사용
        ApplicantInfoRequestDTO realRequestDTO = new ApplicantInfoRequestDTO(
                "https://new-photo.url", "김철수", "new@email.com", "010-5678-1234", "서울시", LocalDate.of(1999, 1, 1)
        );

        given(applicantRepository.findByApplicantId(applicantId))
                .willReturn(Optional.of(realApplicant));

        // When
        applicantService.updateApplicantInfo(applicantId, realRequestDTO, null);

        // Then
        assertThat(realApplicant.getName()).isEqualTo("김철수");
        assertThat(realApplicant.getPhoneNumber()).isEqualTo("010-5678-1234");
    }
    
    @Test
    @DisplayName("수상 정보 업데이트 정상 수행")
    void saveAward_Success() {
        // Given
        long applicantId = 1L;

        Applicant realApplicant = Applicant.builder().applicantId(applicantId).build();

        AwardDetailDTO awardDetailDTO = new AwardDetailDTO(LocalDate.of(2021, 2, 1), "대상", "한양대학교");
        AwardRequestDTO awardRequestDTO = new AwardRequestDTO(List.of(awardDetailDTO));

        given(applicantRepository.findByApplicantId(applicantId))
                .willReturn(Optional.of(realApplicant));

        // When
        applicantService.saveAwardInfo(applicantId, awardRequestDTO);

        // Then

        verify(awardRepository).saveAll(awardListCaptor.capture());
        List<Award> savedAwards = awardListCaptor.getValue();


        assertThat(savedAwards).hasSize(1);
        assertThat(savedAwards.get(0).getAwardName()).isEqualTo("대상");
        assertThat(savedAwards.get(0).getApplicant().getApplicantId()).isEqualTo(applicantId);
    }
    

    @Test
    @DisplayName("학력 정보 단건 저장 정상적으로 수행")
    void saveEducationInfo_Success() {
        // Given
        long applicantId = 1L;
        Applicant realApplicant = Applicant.builder().applicantId(applicantId).build();

        EducationDetailDTO detailDTO = new EducationDetailDTO(
                "BACHELOR", "한양대학교", "컴퓨터공학", 4.0, YearMonth.of(2020, 3), YearMonth.of(2024, 2), "GRADUATED"
        );
        EducationRequestDTO realRequestDTO = new EducationRequestDTO(List.of(detailDTO));

        given(applicantRepository.findByApplicantId(applicantId))
                .willReturn(Optional.of(realApplicant));

        // When
        applicantService.saveEducationInfo(applicantId, realRequestDTO);

        // Then
        // saveAll이 호출될 때 넘어간 진짜 파라미터(List)를 캡처(Capture)
        verify(educationRepository).saveAll(educationListCaptor.capture());

        // 캡처한 리스트를 꺼내서, DTO의 값이 Entity로 잘 변환되어 들어갔는지 확인
        List<Education> savedEducations = educationListCaptor.getValue();

        assertThat(savedEducations).hasSize(1);
        assertThat(savedEducations.get(0).getSchoolName()).isEqualTo("한양대학교");
        assertThat(savedEducations.get(0).getApplicant().getApplicantId()).isEqualTo(applicantId);
    }

    @Test
    @DisplayName("학력 정보 여러건 저장 정상적으로 수행")
    void saveEducationList_Success() {
        // Given
        long applicantId = 1L;
        Applicant realApplicant = Applicant.builder().applicantId(applicantId).build();

        EducationDetailDTO highSchoolDTO = new EducationDetailDTO(
                "HIGH_SCHOOL", "테스트고등학교", "이과", 0.0, YearMonth.of(2017, 3), YearMonth.of(2020, 2), "GRADUATED"
        );

        EducationDetailDTO universityDTO = new EducationDetailDTO(
                "BACHELOR", "한양대학교 에리카", "ICT융합학부", 4.0, YearMonth.of(2020, 3), YearMonth.of(2026, 2), "ACTIVE"
        );

        EducationRequestDTO realRequestDTO = new EducationRequestDTO(List.of(highSchoolDTO, universityDTO));

        given(applicantRepository.findByApplicantId(applicantId))
                .willReturn(Optional.of(realApplicant));

        // When
        applicantService.saveEducationInfo(applicantId, realRequestDTO);

        // Then
        // saveAll이 호출될 때 넘어간 진짜 파라미터(List)를 캡처(Capture)
        verify(educationRepository).saveAll(educationListCaptor.capture());

        // 캡처한 리스트를 꺼내서, DTO의 값이 Entity로 잘 변환되어 들어갔는지 확인
        List<Education> savedEducations = educationListCaptor.getValue();

        assertThat(savedEducations.get(0).getApplicant().getApplicantId()).isEqualTo(applicantId);

        assertThat(savedEducations).hasSize(2);
        assertThat(savedEducations.get(1).getSchoolName()).isEqualTo("한양대학교 에리카");
        assertThat(savedEducations.get(1).getApplicant().getApplicantId()).isEqualTo(applicantId);
    }

    @Test
    @DisplayName("존재하지 않는 구직자 ID로 요청 시 예외 발생")
    void getApplicant_NotFound_ThrowsException() {
        // Given
        long invalidApplicantId = 999L;

        ApplicantInfoRequestDTO emptyRequestDTO = new ApplicantInfoRequestDTO(null, null, null, null, null);

        given(applicantRepository.findByApplicantId(invalidApplicantId))
                .willReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> applicantService.updateApplicantInfo(invalidApplicantId, emptyRequestDTO,null))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.APPLICANT_NOT_FOUND);
    }
}