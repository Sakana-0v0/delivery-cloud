import os
import re

base = r"E:\Idea_project\delivery-cloud"
controllers = []

for root, dirs, files in os.walk(base):
    if "target" in root or "test" in root:
        continue
    for f in files:
        if not f.endswith("Controller.java"):
            continue
        path = os.path.join(root, f)
        if "internal" in path.lower():
            continue
        controllers.append(path)

for path in sorted(controllers):
    rel = path.replace(base + os.sep, "")
    with open(path, "r", encoding="utf-8") as f:
        content = f.read()
    
    endpoints = []
    lines = content.split("\n")
    i = 0
    while i < len(lines):
        line = lines[i]
        mapping = re.search(r'@(Get|Post|Put|Delete)Mapping(?:\("([^"]*)"\)|)', line)
        if mapping:
            method = mapping.group(1)
            sub_path = mapping.group(2) or ""
            j = i + 1
            while j < len(lines):
                sig_line = lines[j].strip()
                if sig_line.startswith("public ") and "(" in sig_line:
                    # 匹配返回类型 - 不只 R<T>，还要支持 R<Map<...>>、R<PageResp> 等
                    method_match = re.search(r'public\s+([\w<>,\s\[\].]+?)\s+(\w+)\s*\(', sig_line)
                    if method_match:
                        return_type = method_match.group(1).strip()
                        method_name = method_match.group(2)
                        full_path = sub_path
                        endpoints.append({"method": method, "path": full_path, "return": return_type, "method_name": method_name})
                    break
                j += 1
        i += 1
    
    if endpoints:
        print(f"\n=== {rel} ===")
        for ep in endpoints:
            print(f"  {ep['method']:<6} {ep['path']:<40} {ep['method_name']:<30} -> {ep['return']}")
