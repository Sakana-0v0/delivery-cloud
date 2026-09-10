# -*- coding: utf-8 -*-
"""
LLMProcessor — 把英文菜谱改写成中文菜品介绍 + 中文菜名

支持任何 OpenAI 兼容 API（Qwen / DeepSeek / OpenAI / Ollama）
使用 ThreadPoolExecutor 并发调用，失败时保留原值兜底。
"""
import json
import logging
import re
import time
from concurrent.futures import ThreadPoolExecutor, as_completed
from dataclasses import dataclass
from typing import List, Optional

import requests

from .base import Processor, ProductItem

logger = logging.getLogger(__name__)


# ============================================================
# Qwen 价格参考（2026 阿里云百炼）
# 实际价格以 https://dashscope.aliyun.com/pricing 为准
# ============================================================
QWEN_PRICING = {
    "qwen-turbo":   {"input": 0.0008, "output": 0.002},
    "qwen-plus":    {"input": 0.004,  "output": 0.012},
    "qwen-max":     {"input": 0.04,   "output": 0.12},
}


@dataclass
class LLMResult:
    success: bool
    content: str                       # 新 description
    name: Optional[str] = None         # 新 name（None 表示保留原 name）
    input_tokens: int = 0
    output_tokens: int = 0
    cost: float = 0.0
    error: Optional[str] = None


class LLMProcessor(Processor):
    """调用大模型：英文菜谱 → 中文菜品介绍 + 中文菜名"""

    DEFAULT_PROMPT = """你是一个餐厅文案专家。请将以下英文菜品信息改写成中文。

输出要求：
1. 菜名（name）：简洁中文（2-8 字），类似菜单上的菜名风格
   - 不要带"菜品"、"招牌"等后缀
   - 不要重复品类字（如"饼干"不要带"饼"）
   - 保留原文中的文化特色（如"英式"、"泰式"等可以保留）
2. 描述（description）：100-150 字
   - 不要写烹饪步骤，只描述菜品本身
   - 突出特色、口感、来源、文化背景
   - 口语化、有吸引力，让人看了想点
3. 适合放在外卖 APP 商品详情页

英文菜名：{name}
来源：{area}
分类：{category}

英文菜谱：
{instructions}

请严格按 JSON 格式返回（只返回 JSON，不要任何解释或代码块标记）：
{{"name": "中文菜名", "description": "中文菜品介绍 100-150 字"}}"""

    def __init__(self, config: dict = None):
        super().__init__(config)
        llm_cfg = (config or {}).get("llm", {})

        self.enabled = llm_cfg.get("enabled", False)
        self.api_key = llm_cfg.get("api_key", "")
        self.base_url = llm_cfg.get(
            "base_url",
            "https://dashscope.aliyuncs.com/compatible-mode/v1"
        )
        self.model = llm_cfg.get("model", "qwen-turbo")
        self.timeout = llm_cfg.get("timeout", 30)
        self.max_workers = llm_cfg.get("max_concurrency", 5)
        self.max_tokens = llm_cfg.get("max_tokens", 300)
        self.temperature = llm_cfg.get("temperature", 0.7)
        self.prompt_template = llm_cfg.get("prompt", self.DEFAULT_PROMPT)

        if self.enabled and not self.api_key:
            logger.warning("[LLM] 启用但未配置 api_key，自动降级为禁用")
            self.enabled = False

    def process(self, items: List[ProductItem]) -> List[ProductItem]:
        if not self.enabled:
            logger.info("[LLM] 未启用，跳过处理")
            return items

        logger.info(
            f"[LLM] 开始处理 {len(items)} 项 "
            f"（模型: {self.model}, 并发: {self.max_workers}）"
        )
        start = time.time()

        results: List[Optional[LLMResult]] = [None] * len(items)
        with ThreadPoolExecutor(max_workers=self.max_workers) as executor:
            future_to_idx = {
                executor.submit(self._call_llm, item): idx
                for idx, item in enumerate(items)
            }
            for future in as_completed(future_to_idx):
                idx = future_to_idx[future]
                try:
                    results[idx] = future.result()
                except Exception as e:
                    logger.warning(f"[LLM] 异常 {items[idx].name}: {e}")
                    results[idx] = LLMResult(
                        success=False,
                        content=items[idx].description,
                        name=None,
                        error=str(e)
                    )

        # 应用结果
        success_count = 0
        failed_count = 0
        name_changed = 0
        total_cost = 0.0
        total_in = 0
        total_out = 0

        for item, result in zip(items, results):
            if result is None:
                result = LLMResult(
                    success=False,
                    content=item.description,
                    name=None,
                    error="no result"
                )
            total_in += result.input_tokens
            total_out += result.output_tokens
            total_cost += result.cost
            if result.success:
                # 应用新 description
                if result.content:
                    item.description = result.content
                # 应用新 name
                if result.name:
                    old_name = item.name
                    item.name = result.name
                    name_changed += 1
                success_count += 1
            else:
                failed_count += 1
                logger.warning(
                    f"[LLM] 失败保留原值: {item.name} ({result.error})"
                )

        elapsed = time.time() - start
        logger.info(
            f"[LLM] 完成: 成功 {success_count}, 失败 {failed_count}, "
            f"翻译名称 {name_changed} 个, "
            f"输入 {total_in} tokens, 输出 {total_out} tokens, "
            f"预估成本 ¥{total_cost:.4f}, 耗时 {elapsed:.1f}s"
        )

        return items

    def _call_llm(self, item: ProductItem) -> LLMResult:
        """调用单个商品的 LLM（同步方式）"""
        if not item.description or len(item.description.strip()) < 30:
            return LLMResult(
                success=True,
                content=item.description or "",
                name=None,
                input_tokens=0,
                output_tokens=0
            )

        prompt = self._build_prompt(item)

        try:
            response = requests.post(
                f"{self.base_url}/chat/completions",
                headers={
                    "Authorization": f"Bearer {self.api_key}",
                    "Content-Type": "application/json"
                },
                json={
                    "model": self.model,
                    "messages": [
                        {
                            "role": "system",
                            "content": "你是餐厅文案专家，擅长将英文菜谱改写成吸引人的中文菜品介绍。"
                        },
                        {"role": "user", "content": prompt}
                    ],
                    "temperature": self.temperature,
                    "max_tokens": self.max_tokens
                },
                timeout=self.timeout
            )
            response.raise_for_status()
            data = response.json()

            raw_content = data["choices"][0]["message"]["content"].strip()
            usage = data.get("usage", {})
            in_tokens = usage.get("prompt_tokens", 0)
            out_tokens = usage.get("completion_tokens", 0)

            # 解析 JSON 响应
            new_name, new_desc = self._parse_response(raw_content)

            return LLMResult(
                success=True,
                content=new_desc or raw_content,  # 兜底用原文
                name=new_name,
                input_tokens=in_tokens,
                output_tokens=out_tokens,
                cost=self._calc_cost(in_tokens, out_tokens)
            )

        except requests.HTTPError as e:
            err_msg = f"HTTP {e.response.status_code}: {e.response.text[:200]}"
            logger.warning(f"[LLM] API 错误 {item.name}: {err_msg}")
            return LLMResult(
                success=False, content=item.description, name=None,
                error=err_msg
            )
        except requests.Timeout:
            return LLMResult(
                success=False, content=item.description, name=None,
                error="请求超时"
            )
        except Exception as e:
            return LLMResult(
                success=False, content=item.description, name=None,
                error=str(e)
            )

    def _parse_response(self, raw: str) -> tuple:
        """解析 LLM 返回的 JSON，容错处理

        Returns:
            (new_name, new_description)
            任一字段解析失败返回 None（调用方用兜底）
        """
        # 去除 markdown 代码块标记
        cleaned = re.sub(r"^```(?:json)?\s*\n?", "", raw.strip())
        cleaned = re.sub(r"\n?```\s*$", "", cleaned).strip()

        try:
            parsed = json.loads(cleaned)
            new_name = (parsed.get("name") or "").strip()
            new_desc = (parsed.get("description") or "").strip()

            # 校验：name 不能是空字符串、不能等于原名（说明没翻译）
            if not new_name:
                new_name = None
            if not new_desc:
                new_desc = None

            return new_name, new_desc
        except json.JSONDecodeError:
            # JSON 解析失败：当 description 用，name 不变
            logger.debug(f"[LLM] JSON 解析失败，当纯文本处理: {raw[:100]}")
            return None, raw

    def _build_prompt(self, item: ProductItem) -> str:
        area = getattr(item, "area", None) or "其他"
        category = item.category or "其他"
        instructions = (item.description or "")[:2000]
        return self.prompt_template.format(
            name=item.name or "未知菜品",
            area=area,
            category=category,
            instructions=instructions
        )

    def _calc_cost(self, input_tokens: int, output_tokens: int) -> float:
        """估算成本（元）"""
        pricing = QWEN_PRICING.get(self.model, {"input": 0, "output": 0})
        return (
            input_tokens * pricing["input"]
            + output_tokens * pricing["output"]
        ) / 1000