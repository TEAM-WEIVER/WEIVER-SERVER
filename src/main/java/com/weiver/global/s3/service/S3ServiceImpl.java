package com.weiver.global.s3.service;

import com.amazonaws.HttpMethod;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.GeneratePresignedUrlRequest;
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
import java.util.Date;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class S3ServiceImpl implements S3Service {

    private final AmazonS3 amazonS3;

    @Value("${spring.cloud.aws.s3.bucket.public}")
    private String publicBucket;

    @Value("${spring.cloud.aws.s3.bucket.private}")
    private String privateBucket;

    @Override
    public String publicUpload(MultipartFile file, String dirName) {
        return uploadToS3(file, dirName, publicBucket);
    }

    @Override
    public String privateUpload(MultipartFile file, String dirName) {
        return uploadToS3(file, dirName, privateBucket);
    }

    @Override
    public String getPresignedUrl(String fileUrl) {
        if (!StringUtils.hasText(fileUrl)) {
            return null;
        }

        try {
            String targetBucket = fileUrl.contains(privateBucket) ? privateBucket : publicBucket;
            String objectKey = extractObjectKey(fileUrl);

            Date expiration = new Date();
            long expTimeMillis = expiration.getTime();
            expTimeMillis += 1000 * 60 * 30; // 30분
            expiration.setTime(expTimeMillis);

            // 임시 접근 권한이 담긴 URL 생성 요청
            GeneratePresignedUrlRequest generatePresignedUrlRequest =
                    new GeneratePresignedUrlRequest(targetBucket, objectKey)
                            .withMethod(HttpMethod.GET)
                            .withExpiration(expiration);

            // 생성된 URL 반환
            URL url = amazonS3.generatePresignedUrl(generatePresignedUrlRequest);
            return url.toString();

        } catch (Exception e) {
            log.error("Presigned URL 생성 실패 (URL: {}): {}", fileUrl, e.getMessage(), e);
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "다운로드 URL 생성에 실패했습니다.");
        }
    }

    private String uploadToS3(MultipartFile file, String dirName, String targetBucket) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "업로드할 파일이 없습니다.");
        }

        String originalFilename = file.getOriginalFilename();
        String extension = StringUtils.getFilenameExtension(originalFilename);
        String uniqueFileName = dirName + "/" + UUID.randomUUID() + "." + extension;

        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentType(file.getContentType());
        metadata.setContentLength(file.getSize());

        try (InputStream inputStream = file.getInputStream()) {
            amazonS3.putObject(new PutObjectRequest(targetBucket, uniqueFileName, inputStream, metadata));
        } catch (IOException e) {
            log.error("S3 업로드 실패: {}", e.getMessage(), e);
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "파일 업로드에 실패했습니다.");
        }

        return amazonS3.getUrl(targetBucket, uniqueFileName).toString();
    }

    @Override
    public void deleteFile(String fileUrl) {
        if (!StringUtils.hasText(fileUrl)) {
            return;
        }

        try {
            String targetBucket = fileUrl.contains(privateBucket) ? privateBucket : publicBucket;

            URL url = new URL(fileUrl);
            String objectKey = url.getPath().substring(1);
            String decodedKey = URLDecoder.decode(objectKey, StandardCharsets.UTF_8);

            amazonS3.deleteObject(targetBucket, decodedKey);

        } catch (Exception e) {
            log.error("S3 파일 삭제 실패 (URL: {}): {}", fileUrl, e.getMessage(), e);
            throw new BusinessException(ErrorCode.FAIL_DELETE_FILE);
        }
    }

    private String extractObjectKey(String fileUrl) throws Exception {
        URL url = new URL(fileUrl);
        String objectKey = url.getPath().substring(1);
        return URLDecoder.decode(objectKey, StandardCharsets.UTF_8);
    }
}