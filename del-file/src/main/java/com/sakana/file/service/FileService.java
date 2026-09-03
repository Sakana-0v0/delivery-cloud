package com.sakana.file.service;

import com.sakana.file.dto.FileUploadVO;
import org.springframework.web.multipart.MultipartFile;

public interface FileService {

    FileUploadVO upload(MultipartFile file);

    FileUploadVO getByMd5(String md5);

    void delete(String md5);
}
