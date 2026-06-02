$Root = Split-Path -Parent $MyInvocation.MyCommand.Path
$env:Path = "C:\Program Files\nodejs;C:\software\apache-maven-3.9.16\bin;" + $env:Path
$mvn = "C:\software\apache-maven-3.9.16\bin\mvn.cmd"
$npm = "C:\Program Files\nodejs\npm.cmd"

# ──────────────────────────────────────────────────────────────────────────
# 启动前清理:务必杀干净上一次的残留进程。
#   症结:旧后端 JVM 若还活着,会一直用它启动时加载的旧字节码服务请求——
#   你在磁盘上重编译的 Java 改动根本不会生效(表现为"修了 bug 还在")。
#   同时 H2 file 模式单连接,残留进程会锁住 epic.mv.db 导致新实例起不来。
# 三重保险:① 按命令行匹配 EpicApplication ② 按端口 8080/5173 反查占用者 ③ 释放锁等待。
# ──────────────────────────────────────────────────────────────────────────

function Stop-PortOwner($port, $label) {
    $conns = Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue
    foreach ($c in $conns) {
        $procId = $c.OwningProcess
        if ($procId -and $procId -gt 4) {   # 跳过 System/Idle
            Write-Host "  Killing $label on port $port (PID: $procId)"
            Stop-Process -Id $procId -Force -ErrorAction SilentlyContinue
        }
    }
}

Write-Host "Cleaning up stale processes..."
# ① 按命令行匹配残留后端
$stale = Get-CimInstance Win32_Process -Filter "Name = 'java.exe'" -ErrorAction SilentlyContinue |
    Where-Object { $_.CommandLine -match 'com\.epic\.engine\.EpicApplication' }
foreach ($p in $stale) {
    Write-Host "  Killing stale backend (PID: $($p.ProcessId))"
    Stop-Process -Id $p.ProcessId -Force -ErrorAction SilentlyContinue
}
# ② 按端口反查并杀掉占用者(catch-all:抓 devtools fork 子进程 / 命令行没匹配上的实例)
Stop-PortOwner 8080 "backend"
Stop-PortOwner 5173 "frontend"
Start-Sleep -Seconds 2   # 给 H2 释放文件锁 + 端口回收的时间

# ──────────────────────────────────────────────────────────────────────────
# 启动后端:用 `clean` 强制全量重编,保证跑的是最新字节码(增量编译偶尔漏掉核心类)。
# spring-boot:run 会在 clean 后自动触发编译。
# ──────────────────────────────────────────────────────────────────────────
Write-Host "Starting backend (mvn clean spring-boot:run)..."
if (Test-Path "$Root\backend\backend.log") { Remove-Item "$Root\backend\backend.log" -Force -ErrorAction SilentlyContinue }
$backend = Start-Process -FilePath $mvn -ArgumentList "clean","spring-boot:run" `
    -WorkingDirectory "$Root\backend" `
    -RedirectStandardOutput "$Root\backend\backend.log" `
    -RedirectStandardError "$Root\backend\backend-err.log" `
    -PassThru -NoNewWindow

# 轮询日志,等后端真正起来(clean+编译可能要二三十秒),而不是死等固定时长
Write-Host "Waiting for backend to start (compiling)..."
$ready = $false
for ($i = 0; $i -lt 90; $i++) {
    if ($backend.HasExited) {
        Write-Host "Backend failed to start. Check backend\backend-err.log"
        exit 1
    }
    if ((Test-Path "$Root\backend\backend.log") -and
        (Select-String -Path "$Root\backend\backend.log" -Pattern "Started EpicApplication" -Quiet -ErrorAction SilentlyContinue)) {
        $ready = $true
        break
    }
    Start-Sleep -Seconds 1
}
if (-not $ready) {
    Write-Host "Backend did not report 'Started' within timeout. Check backend\backend.log"
} else {
    Write-Host "Backend running (PID: $($backend.Id))"
    # 确认跑的是新字节码:打印 BUILD MARKER 那一行
    $marker = Select-String -Path "$Root\backend\backend.log" -Pattern "EPIC BUILD MARKER" -ErrorAction SilentlyContinue
    if ($marker) { Write-Host "  $($marker.Line.Substring($marker.Line.IndexOf('===')))" }
}

# 前端在后台启动,输出写日志而不是占用本终端(不会切进 Vite 的界面)
Write-Host "Starting frontend..."
$frontend = Start-Process -FilePath $npm -ArgumentList "run","dev" `
    -WorkingDirectory "$Root\frontend" `
    -RedirectStandardOutput "$Root\frontend\frontend.log" `
    -RedirectStandardError "$Root\frontend\frontend-err.log" `
    -PassThru -NoNewWindow

Write-Host ""
Write-Host "Backend  -> http://localhost:8080  (log: backend\backend.log)"
Write-Host "Frontend -> http://localhost:5173  (log: frontend\frontend.log)"
Write-Host "Press Ctrl+C to stop everything."
Write-Host ""

try {
    # 前台等待(可被 Ctrl+C 打断),让本终端停在启动器而非前端界面
    while (-not $backend.HasExited) { Start-Sleep -Seconds 1 }
} finally {
    Write-Host ""
    Write-Host "Stopping frontend (PID: $($frontend.Id))..."
    taskkill /PID $frontend.Id /T /F 2>$null | Out-Null
    Write-Host "Stopping backend (PID: $($backend.Id))..."
    taskkill /PID $backend.Id /T /F 2>$null | Out-Null
    # 兜底:再按端口清一遍,避免 mvn fork 的子 JVM 漏网继续占 8080
    Stop-PortOwner 8080 "backend"
    Stop-PortOwner 5173 "frontend"
    Write-Host "Done."
}
