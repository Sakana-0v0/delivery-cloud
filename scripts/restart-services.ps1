# ============================================================
# 微服务重启脚本（按端口）
# ============================================================
# 用于应用 JacksonConfig 修复（Long -> String 防止 JS 精度丢失）

$ErrorActionPreference = "Stop"

function Restart-ServiceOnPort {
    param(
        [int]$Port,
        [string]$ServiceName,
        [string]$WorkingDir
    )
    
    Write-Host "`n===== 重启 $ServiceName (端口 $Port) =====" -ForegroundColor Cyan
    
    # 1. 查找占用端口的进程
    $conn = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue
    if (-not $conn) {
        Write-Host "  ⚠️  $ServiceName 端口 $Port 未监听，跳过" -ForegroundColor Yellow
        return
    }
    
    $procId = $conn.OwningProcess
    Write-Host "  发现进程 PID: $procId"
    
    # 2. 停止进程
    try {
        Stop-Process -Id $procId -Force -ErrorAction Stop
        Write-Host "  ✅ 已停止进程" -ForegroundColor Green
    } catch {
        Write-Host "  ❌ 停止进程失败: $($_.Exception.Message)" -ForegroundColor Red
        return
    }
    
    # 3. 等待端口释放
    Start-Sleep -Seconds 3
    $stillRunning = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue
    if ($stillRunning) {
        Write-Host "  ⚠️  端口仍被占用，等待..." -ForegroundColor Yellow
        Start-Sleep -Seconds 5
    }
    
    # 4. 启动服务
    Write-Host "  启动 $ServiceName..." -ForegroundColor Green
    Start-Process -FilePath "cmd.exe" -ArgumentList "/c", "cd /d `"$WorkingDir`" && mvn spring-boot:run" -WindowStyle Hidden
    Write-Host "  ✅ $ServiceName 启动命令已发出" -ForegroundColor Green
}

# 设置基础路径
$basePath = "E:\Idea_project\delivery-cloud"

# 按顺序重启关键服务
Restart-ServiceOnPort -Port 10001 -ServiceName "del-order" -WorkingDir "$basePath\del-order"
Restart-ServiceOnPort -Port 10007 -ServiceName "del-payment" -WorkingDir "$basePath\del-payment"
Restart-ServiceOnPort -Port 10005 -ServiceName "del-message" -WorkingDir "$basePath\del-message"
Restart-ServiceOnPort -Port 10010 -ServiceName "del-gateway" -WorkingDir "$basePath\del-gateway"

Write-Host "`n===== 重启脚本执行完成 =====" -ForegroundColor Green
Write-Host "请等待 60-90 秒让所有服务完全启动"
