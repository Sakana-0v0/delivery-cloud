# 批量把 nacos-config/DEFAULT_GROUP/ 下的 .yml / .json 导入到 Nacos
# Nacos 2.x API: POST /nacos/v1/cs/configs (dataId, group, content, type)
# 调用方：tenantId=sakana
# 用法：pwsh -File sync-nacos-config.ps1

$nacosHost = "http://127.0.0.1:8848"
$namespace = "sakana"
$group = "DEFAULT_GROUP"
$configDir = Join-Path (Split-Path $PSScriptRoot -Parent) '..\..\nacos-config\DEFAULT_GROUP'

if (-not (Test-Path $configDir)) {
    Write-Error "config dir not found: $configDir"
    exit 1
}

$count = 0
Get-ChildItem -Path $configDir -File | Where-Object { $_.Extension -in '.yml','.yaml','.json' } | ForEach-Object {
    $dataId = $_.Name
    $type = switch ($_.Extension) { '.yml' { 'yaml' } '.yaml' { 'yaml' } '.json' { 'json' } }
    $content = Get-Content $_.FullName -Raw -Encoding UTF8
    $body = @{
        dataId = $dataId
        group = $group
        content = $content
        type = $type
        tenantId = $namespace
    }
    try {
        $resp = Invoke-RestMethod -Uri "$nacosHost/nacos/v1/cs/configs" -Method POST -Body $body -TimeoutSec 10
        Write-Host "OK: $dataId"
        $count++
    } catch {
        Write-Host "FAIL: $dataId -> $($_.Exception.Response.StatusCode) $($_.Exception.Message)"
    }
}
Write-Host "`n=== Total: $count configs synced ==="