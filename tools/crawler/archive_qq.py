import re

path = r"E:\Idea_project\delivery-cloud\docs\WORK_ORDER_AUTH-QQ-001.md"

with open(path, "r", encoding="utf-8") as f:
    content = f.read()

# 1. 更新状态标记
old_status = "> **状态**：\u23f8\ufe0f \u98ce\u9669\u63d0\u793a \u2014 \u4f7f\u7528\u7b2c\u4e09\u65b9\u4ee3\u767b\u5f55\uff0c30 \u5929 token \u9700\u624b\u52a8\u66f4\u6362"
new_status = "> **状态\uffff\u2728 **\u5df2\u5b8c\u6210\uff082026-09-06 \u8054\u8c03\u901a\u8fc7\uff09**\n> **\u26a0\ufe0f \u98ce\u9669\u63d0\u793a**\uff1a\u4f7f\u7528\u7b2c\u4e09\u65b9\u4ee3\u767b\u5f55\uff0c30 \u5929 token \u9700\u624b\u52a8\u66f4\u6362"

if old_status in content:
    content = content.replace(old_status, new_status)
    print("Status updated")
else:
    print("Old status not found, current first lines:")
    for line in content.split(chr(10))[:12]:
        print("  >", line)
    # Try fuzzy match
    for line in content.split(chr(10))[:12]:
        if "\u72b6\u6001" in line:
            print("FOUND status line:", repr(line))

# 2. Append completion summary at end
summary = """


## \u2705 \u8054\u8c03\u5b8c\u6210\u603b\u7ed3\uff082026-09-06\uff09

| \u9636\u6bb5 | \u72b6\u6001 |
|------|------|
| \u5fc3\u6708\u4e92\u8054 API \u54cd\u5e94\u683c\u5f0f\u786e\u8ba4 | \u2705 JSON\uff08\u5b9e\u6d4b\uff09 |
| \u540e\u7aef QqAuthController \u5b9e\u73b0 | \u2705 |
| \u524d\u7aef LoginView + QQSuccessView | \u2705 |
| \u7f51\u5173 PathRoleRule \u767d\u540d\u5355 | \u2705 /api/v1/auth/qq/** |
| RestTemplate \u62c6\u5206\uff08BUG-003\uff09| \u2705 internal + external |
| \u524d\u7aef a-spin \u2192 el-spin | \u2705 |
| \u7aef\u5230\u7aef\u8054\u8c03 | \u2705 token \u5199\u5165 localStorage |


## \ud83d\udc1b \u8054\u8c03\u671f\u95f4\u4fee\u590d\u7684 Bug

| Bug | \u6839\u56e0 | \u4fee\u590d |
|-----|------|------|
| BUG-003 | RestTemplate Bean \u4e0d\u5728 del-user \u4f9d\u8d56\u4e2d | \u4e0b\u6c89\u5230 del-common |
| \u7f51\u5173 401 | /api/v1/auth/qq/** \u4e0d\u5728\u767d\u540d\u5355 | PathRoleRule \u52a0\u767d\u540d\u5355 |
| RestTemplate \u5916\u90e8 URL \u62a5\u9519 | @LoadBalanced \u628a qq.wch666.com \u5f53\u670d\u52a1\u540d | \u65b0\u589e externalRestTemplate Bean |
| \u524d\u7aef\u7ec4\u4ef6\u8b66\u544a | \u9879\u76ee\u7528 element-plus \u5199\u4e86 a-spin | \u6539\u6210 el-spin |
"""

content = content.rstrip() + chr(10) + summary

with open(path, "w", encoding="utf-8") as f:
    f.write(content)

print("\n=== Updated. New file size:", len(content), "bytes")
print("\n=== First 10 lines ===")
for line in content.split(chr(10))[:10]:
    print(line)