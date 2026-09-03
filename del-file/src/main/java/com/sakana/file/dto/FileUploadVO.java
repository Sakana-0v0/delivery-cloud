package com.sakana.file.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileUploadVO {

    private String md5;

    private String fileUrl;

    private String originalName;

    private Long fileSize;

    private String contentType;

    private Boolean deduplicated;
}
