$Root = Split-Path -Parent $MyInvocation.MyCommand.Path
$env:Path = "C:\Program Files\nodejs;C:\software\apache-maven-3.9.16\bin;" + $env:Path
$mvn = "C:\software\apache-maven-3.9.16\bin\mvn.cmd"
$npm = "C:\Program Files\nodejs\npm.cmd"

Write-Host "Starting backend..."
$backend = Start-Process -FilePath $mvn -ArgumentList "spring-boot:run" `
    -WorkingDirectory "$Root\backend" `
    -RedirectStandardOutput "$Root\backend\backend.log" `
    -RedirectStandardError "$Root\backend\backend-err.log" `
    -PassThru -NoNewWindow

Start-Sleep -Seconds 12
if ($backend.HasExited) {
    Write-Host "Backend failed to start. Check backend\backend-err.log"
    exit 1
}
Write-Host "Backend running (PID: $($backend.Id))"
Write-Host "Open http://localhost:5173"
Write-Host "Press Ctrl+C to stop everything."
Write-Host ""

try {
    Push-Location "$Root\frontend"
    & $npm run dev
} finally {
    Pop-Location
    Write-Host ""
    Write-Host "Stopping backend (PID: $($backend.Id))..."
    Stop-Process -Id $backend.Id -Force -ErrorAction SilentlyContinue
    Write-Host "Done."
}
