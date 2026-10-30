# One command for a measured load test: starts the monitor and k6 at the same moment.
#   powershell -ExecutionPolicy Bypass -File .\load-test\benchmark.ps1 -PeakRate 25
# Requires Docker Desktop with the compose stack and the three services running.
param([int]$PeakRate = 15)

$root = Split-Path -Parent $PSScriptRoot

# the monitor runs in the background while k6 runs here
$monitor = Start-Job -FilePath "$PSScriptRoot\monitor.ps1" -ArgumentList 12

docker run --rm -i -v "${root}\load-test:/scripts" `
    -e ORDER_URL=http://host.docker.internal:8082 `
    -e INVENTORY_URL=http://host.docker.internal:8081 `
    -e PEAK_RATE=$PeakRate -e POLL_SECONDS=1 `
    grafana/k6 run /scripts/order-flow.js

Write-Host ""
Write-Host "===== monitor samples taken during the test ====="
Wait-Job $monitor | Out-Null
Receive-Job $monitor
Remove-Job $monitor
