$ErrorActionPreference = "Stop"
$logFile = "backend/evidence/q1c-after-retries-enabled-raw.txt"

Write-Host "=== Stage 5A: After Test (Retries Enabled) ==="
$startTime = Get-Date -Format "yyyy-MM-ddTHH:mm:ss.fffK"
Write-Host "Traffic Start Time: $startTime"

$process = Start-Process -FilePath "docker" -ArgumentList "run --rm --network da3_default -v `"D:\Smart Parking Allocation System\parking-system (2)\parking-system\backend\evidence:/scripts`" grafana/k6:latest run /scripts/failure-test.js" -NoNewWindow -RedirectStandardOutput $logFile -PassThru

Start-Sleep -Seconds 3

$stopTime = Get-Date -Format "yyyy-MM-ddTHH:mm:ss.fffK"
Write-Host "Stopping da3-backend-2-1 at: $stopTime"
docker stop da3-backend-2-1
$stopConfirm = Get-Date -Format "yyyy-MM-ddTHH:mm:ss.fffK"
Write-Host "da3-backend-2-1 stopped confirmed at: $stopConfirm"

$process.WaitForExit()
$endTime = Get-Date -Format "yyyy-MM-ddTHH:mm:ss.fffK"
Write-Host "Traffic End Time: $endTime"
Write-Host "k6 Process Exit Code: $($process.ExitCode)"

$raw = Get-Content $logFile
Set-Content -Path $logFile -Value $raw -Encoding utf8
Write-Host "=== After Test Finished Successfully ==="
