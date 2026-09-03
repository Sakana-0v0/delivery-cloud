package com.sakana.file.service.impl;

import com.sakana.file.dao.entity.FileInfo;
import com.sakana.file.dao.mapper.FileInfoMapper;
import com.sakana.file.dto.FileUploadVO;
import com.sakana.file.service.FileService;
import com.sakana.file.service.Md5Service;
import com.sakana.file.service.MinioService;
import com.sakana.file.util.FileUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileServiceImpl implements FileService {

    private final FileInfoMapper fileInfoMapper;
    private final MinioService minioService;
    private final Md5Service md5Service;
    private final RedisTemplate<String, String> redisTemplate;

    private static final String REDIS_KEY_PREFIX = "file:md5:";
    private static final long REDIS_TTL_DAYS = 30;

    @Override
    @Transactional
    public FileUploadVO upload(MultipartFile file) {
        try {
            String md5 = md5Service.calculateMd5(file);
            String redisKey = REDIS_KEY_PREFIX + md5;

            String cachedUrl = redisTemplate.opsForValue().get(redisKey);
            if (cachedUrl != null) {
                log.info("File deduplication hit (Redis): md5={}", md5);
                FileInfo fileInfo = fileInfoMapper.findByMd5(md5);
                return buildVO(fileInfo, true);
            }

            FileInfo existingFile = fileInfoMapper.findByMd5(md5);
            if (existingFile != null) {
                redisTemplate.opsForValue().set(redisKey, existingFile.getFileUrl(), REDIS_TTL_DAYS, TimeUnit.DAYS);
                log.info("File deduplication hit (DB): md5={}", md5);
                return buildVO(existingFile, true);
            }

            String originalName = file.getOriginalFilename();
            String extension = FileUtil.getExtension(originalName);
            String objectName = LocalDate.now() + "/" + md5 + (extension.isEmpty() ? "" : "." + extension);
            String fileUrl = minioService.uploadFile(objectName, file);

            FileInfo fileInfo = new FileInfo();
            fileInfo.setMd5(md5);
            fileInfo.setOriginalName(originalName);
            fileInfo.setFileName(objectName);
            fileInfo.setFileUrl(fileUrl);
            fileInfo.setFileSize(file.getSize());
            fileInfo.setContentType(file.getContentType());
            fileInfo.setFileType(FileUtil.getFileType(extension));
            fileInfoMapper.insert(fileInfo);

            redisTemplate.opsForValue().set(redisKey, fileUrl, REDIS_TTL_DAYS, TimeUnit.DAYS);

            log.info("File uploaded: md5={}, url={}", md5, fileUrl);
            return buildVO(fileInfo, false);

        } catch (Exception e) {
            log.error("File upload failed", e);
            throw new RuntimeException("File upload failed: " + e.getMessage());
        }
    }

    @Override
    public FileUploadVO getByMd5(String md5) {
        FileInfo fileInfo = fileInfoMapper.findByMd5(md5);
        return fileInfo == null ? null : buildVO(fileInfo, true);
    }

    @Override
    @Transactional
    public void delete(String md5) {
        FileInfo fileInfo = fileInfoMapper.findByMd5(md5);
        if (fileInfo != null) {
            try {
                minioService.deleteFile(fileInfo.getFileName());
            } catch (Exception e) {
                log.warn("MinIO file deletion failed (may not exist): {}", fileInfo.getFileName(), e);
            }
            fileInfoMapper.deleteById(fileInfo.getId());
            redisTemplate.delete(REDIS_KEY_PREFIX + md5);
            log.info("File deleted: md5={}", md5);
        }
    }

    private FileUploadVO buildVO(FileInfo fileInfo, boolean deduplicated) {
        return FileUploadVO.builder()
                .md5(fileInfo.getMd5())
                .fileUrl(fileInfo.getFileUrl())
                .originalName(fileInfo.getOriginalName())
                .fileSize(fileInfo.getFileSize())
                .contentType(fileInfo.getContentType())
                .deduplicated(deduplicated)
                .build();
    }
}
