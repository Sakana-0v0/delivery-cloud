# ============================================================
# JacksonConfig 修复验证脚本
# ============================================================
# 验证 Long ID 是否被序列化为 String

Write-Host "===== 1. 等待服务启动 =====" -ForegroundColor Cyan
Start-Sleep -Seconds 5

$env:Path = "E:\software\mysql\mysql-8.4.10-winx64\bin;" + $env:Path

# 测试端口
$ports = @(10001, 10007, 10005, 10010, 10004)
foreach ($port in $ports) {
    $tnc = Test-NetConnection -ComputerName 127.0.0.1 -Port $port -InformationLevel Quiet 2>$null
    Write-Host "  端口 $port`: $(if($tnc){'✅ 监听中'}else{'❌ 未监听'})"
}

# 等待所有服务就绪
Write-Host "`n===== 2. 等待所有服务健康 =====" -ForegroundColor Cyan
$ready = $false
for ($i = 1; $i -le 30; $i++) {
    $allOk = $true
    foreach ($port in $ports) {
        $tnc = Test-NetConnection -ComputerName 127.0.0.1 -Port $port -InformationLevel Quiet 2>$null
        if (-not $tnc) { $allOk = $false }
    }
    if ($allOk) {
        Write-Host "  ✅ 所有端口就绪 (用时 $i 秒)" -ForegroundColor Green
        $ready = $true
        break
    }
    Write-Host "  等待中... ($i/30)"
    Start-Sleep -Seconds 2
}

if (-not $ready) {
    Write-Host "`n  ⚠️  部分服务未就绪，但继续测试" -ForegroundColor Yellow
}

Write-Host "`n===== 3. 验证商品列表 ID 类型 =====" -ForegroundColor Cyan
try {
    $resp = Invoke-WebRequest "http://127.0.0.1:10010/api/v1/products?page=1&size=3" -UseBasicParsing -TimeoutSec 10
    $data = $resp.Content | ConvertFrom-Json
    Write-Host "  接口响应:"
    Write-Host $resp.Content
    
    Write-Host "`n  ID 类型检查:"
    $firstRecord = $data.data.records[0]
    $idType = $firstRecord.id.GetType().Name
    Write-Host "    ID 值: $($firstRecord.id)"
    Write-Host "    ID 类型: $idType"
    
    if ($idType -eq "String") {
        Write-Host "`n  ✅✅✅ 修复成功！ID 已被序列化为 String" -ForegroundColor Green
    } else {
        Write-Host "`n  ❌ 修复未生效，ID 仍然是 $idType 类型" -ForegroundColor Red
        Write-Host "  请检查:"
        Write-Host "    1. JacksonConfig.java 是否被 Spring 扫描到"
        Write-Host "    2. 服务是否重启到最新代码"
        Write-Host "    3. del-common 是否成功编译"
    }
} catch {
    Write-Host "  ❌ 请求失败: $($_.Exception.Message)" -ForegroundColor Red
}

Write-Host "`n===== 4. 验证订单详情（端到端测试）=====" -ForegroundColor Cyan
# 找到一个真实订单 ID
$orderId = & mysql -u root -psakana013 del_order_db -sse "SELECT id FROM t_order WHERE is_deleted=0 ORDER BY id DESC LIMIT 1;" 2>$null
Write-Host "  测试 orderId: $orderId"

if ($orderId) {
    # 用 sakana 用户登录
    try {
        $loginResp = Invoke-WebRequest "http://127.0.0.1:10010/api/v1/auth/login" -Method POST -ContentType "application/json" -Body '{"username":"sakana","password":"123456"}' -UseBasicParsing -TimeoutSec 10
        $token = ($loginResp.Content | ConvertFrom-Json).data.accessToken
        Write-Host "  ✅ 登录成功"
        
        # 查询订单详情
        $detailResp = Invoke-WebRequest "http://127.0.0.1:10010/api/v1/user/orders/$orderId" -Headers @{"Authorization"="Bearer $token"} -UseBasicParsing -TimeoutSec 10
        $detail = $detailResp.Content | ConvertFrom-Json
        Write-Host "`n  订单详情响应:"
        Write-Host $detailResp.Content
        
        if ($detail.code -eq 0) {
            Write-Host "`n  ✅ 订单详情查询成功！" -ForegroundColor Green
            Write-Host "    订单 ID: $($detail.data.id)"
            Write-Host "    ID 类型: $($detail.data.id.GetType().Name)"
        } elseif ($detail.code -eq 4001) {
            Write-Host "`n  ❌ 仍然返回 订单不存在 — 修复可能未生效" -ForegroundColor Red
        } else {
            Write-Host "`n  其他错误: code=$($detail.code), message=$($detail.message)" -ForegroundColor Yellow
        }
    } catch {
        Write-Host "  ❌ 登录失败: $($_.Exception.Message)" -ForegroundColor Red
    }
}
