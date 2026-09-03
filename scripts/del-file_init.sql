-- ================================================
-- del-file 服务数据库初始化脚本
-- 数据库：del_file_db
-- ================================================

-- 创建数据库（如果不存在）
CREATE DATABASE IF NOT EXISTS del_file_db DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE del_file_db;

-- 创建文件信息表
CREATE TABLE IF NOT EXISTS t_file_info (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  md5 VARCHAR(64) NOT NULL COMMENT '文件MD5哈希值（用于去重）',
  original_name VARCHAR(255) COMMENT '原始文件名',
  file_name VARCHAR(255) COMMENT 'MinIO存储的对象名称',
  file_url VARCHAR(500) COMMENT '文件访问URL',
  file_size BIGINT COMMENT '文件大小（字节）',
  content_type VARCHAR(100) COMMENT '文件MIME类型',
  file_type VARCHAR(20) COMMENT '文件类型（image/video/audio/document/other）',
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  is_deleted TINYINT DEFAULT 0 COMMENT '逻辑删除标记：0-未删除，1-已删除',
  PRIMARY KEY (id),
  UNIQUE KEY uk_md5 (md5),
  INDEX idx_md5 (md5)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文件信息表';
