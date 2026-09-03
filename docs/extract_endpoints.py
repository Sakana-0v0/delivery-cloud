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

print(f"Found {len(controllers)} external controllers")

for path in sorted(controllers):
    rel = path.replace(base + os.sep, "")
    with open(path, "r", encoding="utf-8") as f:
        content = f.read()
    
    rm_match = re.search(r'@RequestMapping\("([^"]+)"\)', content)
    base_path = rm_match.group(1) if rm_match else ""
    
    class_match = re.search(r'public\s+class\s+(\w+)', content)
    class_name = class_match.group(1) if class_match else ""
    
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
                    method_match = re.search(r'public\s+R<([^>]+(?:\[\])?|[^>]+)>\s+(\w+)\s*\(([^)]*)\)', sig_line)
                    if method_match:
                        return_type = method_match.group(1)
                        method_name = method_match.group(2)
                        params = method_match.group(3).strip()
                        full_path = base_path + sub_path
                        endpoints.append({"method": method, "path": full_path, "return": return_type, "method_name": method_name, "params": params})
                    break
                j += 1
        i += 1
    
    if endpoints:
        print(f"\n=== {rel.split(os.sep)[0]} - {class_name} ===")
        print(f"Base: {base_path}")
        for ep in endpoints:
            print(f"  {ep['method']:<6} {ep['path']:<50} -> R<{ep['return']}>")
