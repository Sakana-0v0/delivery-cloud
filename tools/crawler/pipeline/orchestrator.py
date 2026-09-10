# -*- coding: utf-8 -*-
"""
Pipeline 编排器 — 构建完整的数据处理流水线
"""
import logging
from .base import Pipeline
from .scraper import ScraperProcessor
from .image_uploader import ImageUploaderProcessor
from .llm_processor import LLMProcessor

logger = logging.getLogger(__name__)


def build_pipeline(config: dict) -> Pipeline:
    """
    构建数据处理流水线
    
    Pipeline 顺序：
    1. ScraperProcessor       — 从 TheMealDB 爬取数据
    2. ImageUploaderProcessor — 上传图片到 MinIO
    3. LLMProcessor           — 调用 LLM 把菜谱改写成菜品介绍
    """
    pipeline = Pipeline()

    # Stage 1: 爬取数据（必选）
    pipeline.add(ScraperProcessor(config))

    # Stage 2: 上传图片（可选 --skip-images 跳过）
    if not config.get("skip_images", False):
        pipeline.add(ImageUploaderProcessor(config))

    # Stage 3: LLM 处理（可选 llm.enabled 开关）
    llm_cfg = config.get("llm", {})
    if llm_cfg.get("enabled", False):
        logger.info("[Orchestrator] 启用 LLM 处理 Stage")
        pipeline.add(LLMProcessor(config))
    else:
        logger.info("[Orchestrator] LLM 未启用，跳过 Stage 3")

    logger.info(f"[Orchestrator] Pipeline 构建完成: {len(pipeline.processors)} 个 Stage")
    return pipeline