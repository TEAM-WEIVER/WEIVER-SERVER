package com.weiver.global.s3.service;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.weiver.global.exception.BusinessException;
import com.weiver.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class S3ServiceImpl implements S3Service {

    private final AmazonS3 amazonS3;

    // application.yml에서 설정한 버킷 이름을 가져옵니다.
    @Value("${cloud.aws.s3.bucket}")
    private String bucket;

    @Override
    public String upload(MultipartFile file, String dirName) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "업로드할 파일이 없습니다.");
        }
        String originalFilename = file.getOriginalFilename();
        String extension = StringUtils.getFilenameExtension(originalFilename);
        String uniqueFileName = dirName + "/" + UUID.randomUUID() + "." + extension;

        // 메타데이터 설정
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentType(file.getContentType());
        metadata.setContentLength(file.getSize());

        // S3에 파일 업로드
        try (InputStream inputStream = file.getInputStream()) {
            amazonS3.putObject(new PutObjectRequest(bucket, uniqueFileName, inputStream, metadata));
        } catch (IOException e) {
            log.error("S3 업로드 실패: {}", e.getMessage(), e);
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "파일 업로드에 실패했습니다.");
        }

        // 업로드된 파일의 S3 URL 반환
        return amazonS3.getUrl(bucket, uniqueFileName).toString();
    }

    @Override
    public void deleteFile(String fileUrl) {
        // 삭제 할 url 없으면 무시
        if (!StringUtils.hasText(fileUrl)) {
            return;
        }

        try {
            URL url = new URL(fileUrl);
            String objectKey = url.getPath().substring(1);

            String decodedKey = URLDecoder.decode(objectKey, StandardCharsets.UTF_8);
            amazonS3.deleteObject(bucket, decodedKey);
            log.info("S3 파일 삭제 완료: {}", decodedKey);

        } catch (Exception e) {
            log.error("S3 파일 삭제 실패 (URL: {}): {}", fileUrl, e.getMessage(), e);
            throw new BusinessException(ErrorCode.FAIL_DELETE_FILE);
        }
    }
}