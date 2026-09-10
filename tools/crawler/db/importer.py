# -*- coding: utf-8 -*-
"""
DatabaseClient — MySQL 数据库导入器
"""

import logging
from datetime import datetime
from typing import List
import pymysql
from pymysql.cursors import DictCursor

from pipeline.base import ProductItem

logger = logging.getLogger(__name__)


class DatabaseClient:
    """数据库客户端"""
    
    def __init__(self, config: dict):
        self.config = config.get('database', {}) if config else {}
    
    def get_connection(self):
        """获取数据库连接"""
        return pymysql.connect(
            host=self.config.get('host', 'localhost'),
            port=self.config.get('port', 3306),
            user=self.config.get('user', 'root'),
            password=self.config.get('password', ''),
            database=self.config.get('database', ''),
            charset='utf8mb4',
            cursorclass=DictCursor
        )
    
    def insert_products(self, items: List[ProductItem]) -> tuple:
        """
        批量插入商品数据
        :return: (成功数, 失败数)
        """
        if not items:
            return 0, 0
        
        success = 0
        failed = 0
        
        conn = self.get_connection()
        try:
            with conn.cursor() as cursor:
                sql = """
                    INSERT INTO t_product 
                    (fid, category_id, name, cover, description, norm_price, real_price, stock, sales, status)
                    VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s)
                """
                
                for item in items:
                    try:
                        cursor.execute(sql, (
                            item.fid,
                            item.category_id,
                            item.name,
                            item.minio_cover or item.cover_url or '',
                            item.description,
                            item.norm_price,
                            item.real_price,
                            item.stock,
                            item.sales,
                            item.status
                        ))
                        success += 1
                    except Exception as e:
                        logger.warning(f"插入失败 {item.name}: {e}")
                        failed += 1
                
                conn.commit()
        finally:
            conn.close()
        
        return success, failed
    
    def generate_sql(self, items: List[ProductItem]) -> str:
        """生成 SQL 插入语句"""
        now = datetime.now().strftime('%Y-%m-%d %H:%M:%S')
        
        lines = [
            "-- 菜品数据导入 SQL",
            f"-- 生成时间: {now}",
            f"-- 记录数: {len(items)}",
            "",
            "SET NAMES utf8mb4;",
            "SET FOREIGN_KEY_CHECKS = 0;",
            "",
            "INSERT INTO t_product (fid, category_id, name, cover, description, norm_price, real_price, stock, sales, status, create_time, update_time)",
            "VALUES"
        ]
        
        values = []
        for item in items:
            cover = item.minio_cover or item.cover_url or ''
            values.append(
                f"  ('{item.fid}', {item.category_id}, '{item.name}', '{cover}', "
                f"'{item.description}', {item.norm_price}, {item.real_price}, "
                f"{item.stock}, {item.sales}, {item.status}, '{now}', '{now}')"
            )
        
        lines.append(",\n".join(values) + ";")
        lines.append("SET FOREIGN_KEY_CHECKS = 1;")
        
        return "\n".join(lines)

