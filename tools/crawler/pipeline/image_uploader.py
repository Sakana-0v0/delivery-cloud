# -*- coding: utf-8 -*-
"""
ImageUploaderProcessor — 下载图片并上传到 MinIO
"""

import logging
import time
import io
from typing import List
import requests
from minio import Minio
from minio.error import S3Error

from .base import Processor, ProductItem

logger = logging.getLogger(__name__)


class ImageUploaderProcessor(Processor):
    """上传图片到 MinIO 的 Processor"""
    
    def __init__(self, config: dict = None):
        super().__init__(config)
        self.minio_config = config.get('minio', {}) if config else {}
        self.skip = config.get('skip_images', False) if config else False
        
        if not self.skip:
            self.client = Minio(
                self.minio_config.get('endpoint', '127.0.0.1:9000'),
                access_key=self.minio_config.get('access_key', 'admin'),
                secret_key=self.minio_config.get('secret_key', ''),
                secure=self.minio_config.get('secure', False)
            )
            self.bucket = self.minio_config.get('bucket', 'product-picture')
    
    def process(self, items: List[ProductItem]) -> List[ProductItem]:
        """上传图片并填充 minio_cover 字段"""
        if self.skip:
            logger.info("[跳过] 图片上传已禁用")
            for item in items:
                item.minio_cover = item.cover_url  # 降级使用原 URL
            return items
        
        logger.info(f"[开始] 上传 {len(items)} 张图片到 MinIO...")
        
        success = 0
        failed = 0
        
        for item in items:
            if not item.cover_url:
                item.minio_cover = ''
                continue
            
            url = self._upload_image(item.cover_url, item.source_id)
            item.minio_cover = url
            
            if url:
                success += 1
            else:
                failed += 1
                item.minio_cover = item.cover_url  # 降级使用原 URL
            
            time.sleep(0.2)  # 防封
        
        logger.info(f"[完成] 图片上传: 成功 {success}, 失败 {failed}")
        return items
    
    def _upload_image(self, image_url: str, source_id: str) -> str:
        """下载图片并上传到 MinIO"""
        try:
            # 下载图片
            resp = requests.get(image_url, timeout=30, stream=True)
            resp.raise_for_status()
            image_data = resp.content
            
            # 确保 bucket 存在
            if not self.client.bucket_exists(self.bucket):
                self.client.make_bucket(self.bucket)
            
            # 上传
            object_name = f"{source_id}.jpg"
            self.client.put_object(
                self.bucket, object_name,
                io.BytesIO(image_data), len(image_data),
                content_type='image/jpeg'
            )
            
            # 生成 Presigned URL（7天有效期）
            from datetime import timedelta
            url = self.client.presigned_get_object(
                self.bucket, object_name,
                expires=timedelta(days=7)
            )
            
            return url
            
        except Exception as e:
            logger.warning(f"图片上传失败 {image_url}: {e}")
            return ''
