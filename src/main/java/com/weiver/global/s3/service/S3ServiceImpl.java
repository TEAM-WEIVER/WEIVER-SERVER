package com.weiver.global.s3.service;

import com.weiver.global.exception.BusinessException;
import com.weiver.global.exception.ErrorCode;
import io.awspring.cloud.s3.ObjectMetadata;
import io.awspring.cloud.s3.S3Template;
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
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class S3ServiceImpl implements S3Service {

    private final S3Template s3Template;

    @Value("${spring.cloud.aws.s3.bucket.public}")
    private String publicBucket;

    @Value("${spring.cloud.aws.s3.bucket.private}")
    private String privateBucket;

    private static final List<String> ALLOWED_EXTENSIONS = Arrays.asList(

            "jpg", "jpeg", "png", "gif", "webp", "bmp", "svg", "tif", "tiff",

            "pdf", "txt", "csv", "rtf",

            "doc", "docx", "xls", "xlsx", "ppt", "pptx",

            "hwp", "hwpx"
    );

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

            URL presignedUrl = s3Template.createSignedGetURL(targetBucket, objectKey, Duration.ofMinutes(30));

            return presignedUrl.toString();

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

        validateFileExtension(originalFilename);

        String extension = StringUtils.getFilenameExtension(originalFilename);
        String uniqueFileName = dirName + "/" + UUID.randomUUID() + "." + extension;

        ObjectMetadata metadata = ObjectMetadata.builder()
                .contentType(file.getContentType())
                .build();

        try (InputStream inputStream = file.getInputStream()) {
            URL url = s3Template.upload(targetBucket, uniqueFileName, inputStream, metadata).getURL();
            return url.toString();
        } catch (IOException e) {
            log.error("S3 업로드 실패: {}", e.getMessage(), e);
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "파일 업로드에 실패했습니다.");
        }
    }

    @Override
    public void deleteFile(String fileUrl) {
        if (!StringUtils.hasText(fileUrl)) {
            return;
        }

        try {
            String targetBucket = fileUrl.contains(privateBucket) ? privateBucket : publicBucket;
            String decodedKey = extractObjectKey(fileUrl);

            s3Template.deleteObject(targetBucket, decodedKey);

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

    private void validateFileExtension(String originalFilename) {
        if (!StringUtils.hasText(originalFilename)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "잘못된 파일 이름입니다.");
        }

        String extension = StringUtils.getFilenameExtension(originalFilename);

        if (extension == null || !ALLOWED_EXTENSIONS.contains(extension.toLowerCase())) {
            log.warn("허용되지 않은 파일 업로드 시도: {}", originalFilename);
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "지원하지 않는 파일 형식입니다.");
        }
    }
}