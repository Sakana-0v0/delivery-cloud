# -*- coding: utf-8 -*-
"""
Pipeline 抽象层 — 所有数据处理 Stage 的基类

设计参考 #PROD-VOTE-008 的状态机思想，
将数据处理拆成可插拔的 Processor。
"""

import logging
import uuid
from abc import ABC, abstractmethod
from typing import List, Optional
from dataclasses import dataclass, field

logger = logging.getLogger(__name__)


@dataclass
class ProductItem:
    """统一的菜品数据模型"""
    source_id: str
    name: str
    description: str
    category: str
    area: str = "Other"
    cover_url: Optional[str] = None       # TheMealDB 原 URL
    minio_cover: Optional[str] = None     # MinIO URL（Stage 2 填充）
    category_id: Optional[int] = None
    category_name: Optional[str] = None
    norm_price: float = 0.0
    real_price: float = 0.0
    stock: int = 999
    sales: int = 0
    status: int = 1
    fid: str = field(default_factory=lambda: uuid.uuid4().hex)
    extra: dict = field(default_factory=dict)


class Processor(ABC):
    """所有处理阶段的抽象基类"""
    
    def __init__(self, config: dict = None):
        self.config = config or {}
        self.name = self.__class__.__name__
    
    @abstractmethod
    def process(self, items: List[ProductItem]) -> List[ProductItem]:
        """
        处理一批商品
        :param items: 输入商品列表
        :return: 处理后的商品列表（可能更少，失败的被过滤）
        """
        pass
    
    def __repr__(self):
        return f"<{self.name}>"


class Pipeline:
    """Pipeline 编排器 — 按顺序执行所有 Processor"""
    
    def __init__(self):
        self.processors: List[Processor] = []
    
    def add(self, processor: Processor) -> 'Pipeline':
        self.processors.append(processor)
        return self
    
    def run(self, items: List[ProductItem]) -> List[ProductItem]:
        """依次执行所有 Processor"""
        for p in self.processors:
            logger.info(f"[Pipeline] 执行 {p.name}（{len(items)} 项）")
            items = p.process(items)
            logger.info(f"[Pipeline] {p.name} 完成，剩余 {len(items)} 项")
        return items
