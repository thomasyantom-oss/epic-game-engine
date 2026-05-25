$Root = Split-Path -Parent $MyInvocation.MyCommand.Path
$env:Path = "C:\Program Files\nodejs;C:\software\apache-maven-3.9.16\bin;" + $env:Path

Start-Process powershell -ArgumentList "-NoExit", "-Command", `
    "`$env:Path = 'C:\Program Files\nodejs;C:\software\apache-maven-3.9.16\bin;' + `$env:Path; Set-Location '$Root\backend'; mvn spring-boot:run"

Start-Sleep -Seconds 10

Start-Process powershell -ArgumentList "-NoExit", "-Command", `
    "`$env:Path = 'C:\Program Files\nodejs;C:\software\apache-maven-3.9.16\bin;' + `$env:Path; Set-Location '$Root\frontend'; npm run dev"

Write-Host "Started. Open http://localhost:5173"
