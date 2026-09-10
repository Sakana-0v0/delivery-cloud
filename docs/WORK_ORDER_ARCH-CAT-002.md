# 📋 工单 #ARCH-CAT-002：使用 LLM 重新分类历史商品

> **创建时间**：2026-09-05
> **关联工单**：#ARCH-CAT-001（分类体系重构）
> **接收方**：后端工程师
> **验收方**：Codex
> **预计工时**：1 小时（含实施 + 验证）
> **依赖**：#ARCH-CAT-001 已完成

---

## 一、问题背景

#ARCH-CAT-001 完成后，**517 条历史商品的分类是"过去错误的"**，因为：
- 旧 `CATEGORY_MAP` 把所有 Beef/Chicken/Pork/Lamb/Goat 都映射到 id=5（当时是"衡阳土菜"）
- 旧 `CATEGORY_NAMES` 只有 5 个分类
- 旧 `add_categories.sql` 因 INSERT IGNORE 没生效，导致 id=6/7 缺失

虽然现在 id=5 已重命名为"西式主菜"，但 174 条西餐混在一个分类下、其他 37 条"精品小炒"里有大量西餐——**分类语义仍然不准**。

## 二、目标

用 LLM 批量重新分类所有 517 条历史商品，按 11 个新分类体系（已就位）精确归类。

## 三、实施方案

### 3.1 新建脚本 `tools/crawler/recategorize.py`

读取所有产品，调用 LLM 给出新分类，批量更新 DB。

**核心流程**：

```
1. 从 t_product 读取所有产品 (id, name, description, area, category_id)
2. 对每条产品调用 LLM，输入中文名 + 描述 + 来源国家
3. LLM 返回新的 category_id (1-11)
4. 如果与旧 category_id 不同，标记为待更新
5. 批量 UPDATE t_product SET category_id = ?
```

### 3.2 LLM Prompt 设计

**System Prompt**：

```
你是餐饮分类专家。根据菜品信息，从以下分类中选择最合适的：

1. 招牌主菜 - 中餐重磅肉菜、家宴主菜（红烧肉、宫保鸡丁）
2. 家常小炒 - 中餐快炒、家常炒菜
3. 鲜汤靓煲 - 汤、煲类
4. 米面主食 - 米饭、面食、饼类主食
5. 西式主菜 - 牛排、烤鸡、意面（西式主菜）
6. 西式轻食 - 沙拉、三明治、汉堡
7. 甜品烘焙 - 蛋糕、布丁、饼干、烘焙
8. 风味小吃 - 小吃、配菜、早餐、杂项
9. 海鲜西餐 - 鱼、虾、贝类海鲜
10. 异国料理 - 泰国、印度、墨西哥等异国菜
11. 茶饮果饮 - 茶、咖啡、果汁、奶昔

只返回一个数字（1-11），不要其他文字。
```

**User Prompt**：

```
菜品名：{name}
菜品描述：{description前200字}
来源国家：{area}

分类 ID：
```

**响应解析**：正则 `\d+` 提取数字，校验 1-11 范围。

### 3.3 关键代码骨架

```python
def call_llm(name, description, area, config):
    """调用 Qwen 分类"""
    response = requests.post(
        f"{config['llm']['base_url']}/chat/completions",
        headers={"Authorization": f"Bearer {config['llm']['api_key']}"},
        json={
            "model": config['llm'].get('model', 'qwen-plus'),
            "messages": [
                {"role": "system", "content": SYSTEM_PROMPT},
                {"role": "user", "content": user_prompt}
            ],
            "temperature": 0,        # 分类要确定性
            "max_tokens": 5          # 只要一个数字
        },
        timeout=30
    )
    content = response.json()["choices"][0]["message"]["content"].strip()
    match = re.search(r'\d+', content)
    return int(match.group()) if match else None
```

## 四、成本预估

| 项 | 估算 |
|----|------|
| 商品数 | 517 |
| 输入 tokens/条 | ~400 |
| 输出 tokens/条 | ~5 |
| 输入总 tokens | ~207K |
| 输出总 tokens | ~3K |
| qwen-plus 成本 | **~¥0.85** |
| 并发 5 | ~3 分钟 |

## 五、运行步骤

```powershell
cd E:\Idea_project\delivery-cloud\tools\crawler
$env:PYTHONIOENCODING = "utf-8"

# 第一步：Dry-run 看效果（不会改 DB）
python recategorize.py --dry-run

# 第二步：人工确认后实际更新
python recategorize.py
```

## 六、安全措施

- ✅ Dry-run 模式：先看变化再决定
- ✅ 失败兜底：LLM 返回非数字 → 跳过该商品
- ✅ 事务提交：批量更新用单次 COMMIT
- ✅ 备份：建议执行前 dump 一份 t_product（可选）
- ✅ 不动 seed 数据：id 1-18 是业务原始数据（虽然也会被扫到，但分类大概率已对）

## 七、验收清单

- [ ] recategorize.py 实现完成
- [ ] Dry-run 跑通，显示合理的变化分布
- [ ] 实际更新后，DB 分类分布更合理
- [ ] B 端商品管理页面分类显示正确
- [ ] LLM 失败率 < 5%

## 八、签收

| 角色 | 操作 | 时间 |
|------|------|------|
| 工单创建 | Codex | 2026-09-05 |
| Python 实现 | _待填_ | |
| Dry-run 验收 | Codex | |
| 实际更新验收 | Codex | |