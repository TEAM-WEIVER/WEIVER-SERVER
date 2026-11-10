package com.weiver.global.s3.service;

import com.weiver.global.exception.BusinessException;
import com.weiver.global.exception.ErrorCode;
import com.weiver.global.s3.dto.S3ObjectLocation;
import io.awspring.cloud.s3.ObjectMetadata;
import io.awspring.cloud.s3.S3Resource;
import io.awspring.cloud.s3.S3Template;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class S3ServiceTest {

    private static final String PUBLIC_BUCKET = "test-public-bucket";
    private static final String PRIVATE_BUCKET = "test-private-bucket";
    private static final String KEY = "interview-tts/session-1/3.wav";
    private static final byte[] WAV = "RIFF....WAVEfmt fake".getBytes();

    @Mock private S3Template s3Template;
    @Mock private S3Resource s3Resource;

    private S3Service s3Service;

    @BeforeEach
    void setUp() {
        s3Service = new S3Service(s3Template, PUBLIC_BUCKET, PRIVATE_BUCKET);
    }

    private void givenUploadReturnsUrl() throws IOException {
        given(s3Template.upload(anyString(), anyString(), any(InputStream.class), any(ObjectMetadata.class)))
                .willReturn(s3Resource);
        given(s3Resource.getURL()).willReturn(new URL("https://" + PRIVATE_BUCKET + ".s3.ap-northeast-2.amazonaws.com/" + KEY));
    }

    private void assertBadRequest(Runnable call) {
        assertThatThrownBy(call::run)
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.BAD_REQUEST);
        then(s3Template).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("바이트 업로드는 private 버킷의 지정한 키에 audio/wav로 저장하고 객체 URL을 돌려준다")
    void privateUploadBytes_success() throws IOException {
        // given
        givenUploadReturnsUrl();

        // when
        String url = s3Service.privateUploadBytes(WAV, KEY, "audio/wav");

        // then
        assertThat(url).isEqualTo("https://" + PRIVATE_BUCKET + ".s3.ap-northeast-2.amazonaws.com/" + KEY);

        ArgumentCaptor<InputStream> streamCaptor = ArgumentCaptor.forClass(InputStream.class);
        ArgumentCaptor<ObjectMetadata> metadataCaptor = ArgumentCaptor.forClass(ObjectMetadata.class);
        then(s3Template).should().upload(eq(PRIVATE_BUCKET), eq(KEY), streamCaptor.capture(), metadataCaptor.capture());
        assertThat(streamCaptor.getValue().readAllBytes()).isEqualTo(WAV);
        assertThat(metadataCaptor.getValue().getContentType()).isEqualTo("audio/wav");
        then(s3Template).shouldHaveNoMoreInteractions();
    }

    @Test
    @DisplayName("업로드한 객체 URL로 기존 30분 Presigned URL을 만들 수 있다")
    void privateUploadBytes_thenPresignedUrl() throws IOException {
        // given
        givenUploadReturnsUrl();
        given(s3Template.createSignedGetURL(PRIVATE_BUCKET, KEY, Duration.ofMinutes(30)))
                .willReturn(new URL("https://signed.example.com/" + KEY + "?X-Amz-Signature=abc"));

        // when
        String uploadedUrl = s3Service.privateUploadBytes(WAV, KEY, "audio/wav");
        String presigned = s3Service.getPresignedUrl(uploadedUrl);

        // then
        assertThat(presigned).contains("X-Amz-Signature");
    }

    @Test
    @DisplayName("빈 내용이면 S3 호출 없이 BAD_REQUEST 예외가 발생한다")
    void privateUploadBytes_emptyContent() {
        assertBadRequest(() -> s3Service.privateUploadBytes(new byte[0], KEY, "audio/wav"));
        assertBadRequest(() -> s3Service.privateUploadBytes(null, KEY, "audio/wav"));
    }

    @Test
    @DisplayName("허용되지 않은 확장자면 S3 호출 없이 BAD_REQUEST 예외가 발생한다")
    void privateUploadBytes_disallowedExtension() {
        assertBadRequest(() -> s3Service.privateUploadBytes(WAV, "interview-tts/session-1/3.exe", "audio/wav"));
        assertBadRequest(() -> s3Service.privateUploadBytes(WAV, "interview-tts/session-1/noextension", "audio/wav"));
    }

    @Test
    @DisplayName("키가 비었거나 절대경로·상위경로를 포함하면 S3 호출 없이 BAD_REQUEST 예외가 발생한다")
    void privateUploadBytes_invalidKey() {
        assertBadRequest(() -> s3Service.privateUploadBytes(WAV, " ", "audio/wav"));
        assertBadRequest(() -> s3Service.privateUploadBytes(WAV, "/interview-tts/3.wav", "audio/wav"));
        assertBadRequest(() -> s3Service.privateUploadBytes(WAV, "interview-tts/../secret/3.wav", "audio/wav"));
    }

    @Test
    @DisplayName("Content-Type이 비어 있으면 S3 호출 없이 BAD_REQUEST 예외가 발생한다")
    void privateUploadBytes_blankContentType() {
        assertBadRequest(() -> s3Service.privateUploadBytes(WAV, KEY, " "));
    }

    @Test
    @DisplayName("S3 업로드가 실패하면 INTERNAL_SERVER_ERROR 예외로 변환된다")
    void privateUploadBytes_uploadFails() {
        // given
        given(s3Template.upload(anyString(), anyString(), any(InputStream.class), any(ObjectMetadata.class)))
                .willThrow(new IllegalStateException("s3 down"));

        // when & then
        assertThatThrownBy(() -> s3Service.privateUploadBytes(WAV, KEY, "audio/wav"))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.INTERNAL_SERVER_ERROR);
    }

    @Test
    @DisplayName("wav 파일도 기존 MultipartFile 업로드 화이트리스트를 통과한다")
    void privateUpload_wavAllowed() throws IOException {
        // given
        givenUploadReturnsUrl();
        MockMultipartFile file = new MockMultipartFile("file", "answer.wav", "audio/wav", WAV);

        // when
        String url = s3Service.privateUpload(file, "interview-tts");

        // then
        assertThat(url).isNotBlank();
    }

    @Test
    @DisplayName("기존 동작 유지: 허용되지 않은 확장자의 MultipartFile은 거절된다")
    void privateUpload_disallowedExtensionStillRejected() {
        MockMultipartFile file = new MockMultipartFile("file", "malware.exe", "application/octet-stream", WAV);

        assertBadRequest(() -> s3Service.privateUpload(file, "interview-tts"));
    }

    @Test
    @DisplayName("private 버킷 객체 URL을 private 버킷과 객체 키로 분해한다")
    void resolveObjectLocation_privateBucket() {
        // when
        var location = s3Service.resolveObjectLocation(
                "https://" + PRIVATE_BUCKET + ".s3.ap-northeast-2.amazonaws.com/portfolios/3f2a-e1.pdf");

        // then
        assertThat(location).contains(new S3ObjectLocation(PRIVATE_BUCKET, "portfolios/3f2a-e1.pdf"));
        then(s3Template).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("private 버킷 이름이 없는 URL은 기존 메서드처럼 public 버킷으로 판별한다")
    void resolveObjectLocation_publicBucket() {
        // when
        var location = s3Service.resolveObjectLocation(
                "https://" + PUBLIC_BUCKET + ".s3.ap-northeast-2.amazonaws.com/profile/photo.png");

        // then
        assertThat(location).contains(new S3ObjectLocation(PUBLIC_BUCKET, "profile/photo.png"));
    }

    @Test
    @DisplayName("URL 인코딩된 한글 객체 키는 디코딩한 키로 돌려준다")
    void resolveObjectLocation_decodesEncodedKoreanKey() {
        // given: "portfolios/포트폴리오 최종.pdf"
        String url = "https://" + PRIVATE_BUCKET + ".s3.ap-northeast-2.amazonaws.com/"
                + "portfolios/%ED%8F%AC%ED%8A%B8%ED%8F%B4%EB%A6%AC%EC%98%A4%20%EC%B5%9C%EC%A2%85.pdf";

        // when
        var location = s3Service.resolveObjectLocation(url);

        // then
        assertThat(location).contains(new S3ObjectLocation(PRIVATE_BUCKET, "portfolios/포트폴리오 최종.pdf"));
    }

    @Test
    @DisplayName("비어 있거나 해석할 수 없는 URL, 객체 키가 없는 URL이면 빈 값을 돌려주고 S3를 호출하지 않는다")
    void resolveObjectLocation_invalidUrl_returnsEmpty() {
        assertThat(s3Service.resolveObjectLocation(null)).isEmpty();
        assertThat(s3Service.resolveObjectLocation(" ")).isEmpty();
        assertThat(s3Service.resolveObjectLocation("portfolios/relative-key.pdf")).isEmpty();
        assertThat(s3Service.resolveObjectLocation("https://exa mple.com/a b.pdf")).isEmpty();
        assertThat(s3Service.resolveObjectLocation("https://" + PRIVATE_BUCKET + ".s3.ap-northeast-2.amazonaws.com/")).isEmpty();
        then(s3Template).shouldHaveNoInteractions();
    }
}
