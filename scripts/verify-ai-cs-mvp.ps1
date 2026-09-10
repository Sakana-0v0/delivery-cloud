# AI-CS-001-MVP verification script (UTF-8 with BOM)
Param([string]$Gateway="http://localhost:10010",[string]$Cs="http://localhost:10011",[string]$UserToken="",[int]$TimeoutSec=30)

$ErrorActionPreference="Continue"
$passed=0;$failed=0

function Step($n,$b){Write-Host ("`n=== " + $n + " ===") -ForegroundColor Cyan;& $b}
function Pass($m){Write-Host ("PASS: " + $m) -ForegroundColor Green;$script:passed++}
function Fail($m){Write-Host ("FAIL: " + $m) -ForegroundColor Red;$script:failed++}

# === Test 1: del-cs health endpoint via gateway ===
Step "Test 1: gateway /api/v1/cs/health" {
   try {
      $r=Invoke-WebRequest "$Gateway/api/v1/cs/health" -UseBasicParsing -TimeoutSec 5 -ErrorAction Stop
      if ($r.Content -match "del-cs" -and $r.Content -match "UP") { Pass "del-cs health: " + $r.Content }
      else { Fail ("Unexpected: " + $r.Content) }
   } catch { Fail ("err: " + $_.Exception.Message) }
}

# === Test 2: SSE chat stream ===
Step "Test 2: SSE chat stream (no token)" {
   try {
      $r=Invoke-WebRequest "$Gateway/api/v1/cs/chat" -Method POST -Headers @{"Content-Type"="application/json";"Accept"="text/event-stream"} -Body '{"message":"hi"}' -UseBasicParsing -TimeoutSec $TimeoutSec -ErrorAction Stop
      $c=$r.Content
      $tokens=([regex]::Matches($c, "token")).Count
      if ($tokens -gt 0) { Pass ("SSE ok, token events: " + $tokens) }
      else { Fail ("no tokens: " + $c.Substring(0,200)) }
   } catch { Fail ("err: " + $_.Exception.Message) }
}

# === Test 3: Tool invocation (searchDishes) ===
Step "Test 3: Tool invocation in stream" {
   try {
      $r=Invoke-WebRequest "$Gateway/api/v1/cs/chat" -Method POST -Headers @{"Content-Type"="application/json";"Accept"="text/event-stream"} -Body '{"message":"recommend light soup"}' -UseBasicParsing -TimeoutSec $TimeoutSec -ErrorAction Stop
      if ($r.Content -match "searchDishes" -or $r.Content -match "tool") { Pass "tool call detected in stream" }
      else { Fail ("no tool call: " + $r.Content.Substring(0,200)) }
   } catch { Fail ("err: " + $_.Exception.Message) }
}

# === Test 4: del-cs port listening ===
Step "Test 4: del-cs port 10011 listening" {
   $p = netstat -ano | Select-String ":10011 " | Select-Object -First 1
   if ($p) { Pass "del-cs port 10011 is listening" }
   else { Fail "del-cs not listening on 10011" }
}

# === Test 5: Gateway port ===
Step "Test 5: gateway port 10010 listening" {
   $p = netstat -ano | Select-String ":10010 " | Select-Object -First 1
   if ($p) { Pass "gateway port 10010 is listening" }
   else { Fail "gateway not listening on 10010" }
}

# === Summary ===
Write-Host ""
Write-Host ("===========================")
Write-Host ("Result: " + $passed + " pass / " + $failed + " fail") -ForegroundColor $(if($failed -eq 0){"Green"}else{"Red"})
Write-Host ("===========================")
if ($failed -gt 0) { exit 1 }