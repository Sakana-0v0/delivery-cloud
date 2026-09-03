package com.sakana.file.dao.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("t_file_info")
public class FileInfo {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String md5;

    private String originalName;

    private String fileName;

    private String fileUrl;

    private Long fileSize;

    private String contentType;

    private String fileType;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableLogic
    private Integer isDeleted;
}
