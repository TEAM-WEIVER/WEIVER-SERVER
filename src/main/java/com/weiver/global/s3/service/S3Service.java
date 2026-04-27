package com.weiver.global.s3.service;

import org.springframework.web.multipart.MultipartFile;

public interface S3Service {
    String upload(MultipartFile file, String dirName);
    void deleteFile(String fileUrl);
}
