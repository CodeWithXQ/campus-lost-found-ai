# ============================================================
# 启动后端脚本
# 功能：自动检查并清理占用 8080 端口的残留进程，然后启动后端
# 用法：在 backend 目录下执行
#       .\start-backend.ps1               （清理并启动）
#       .\start-backend.ps1 -CleanOnly    （只清理，不启动）
# 注意：若残留进程是管理员权限启动的，请用管理员身份运行本脚本
# ============================================================

param(
    [switch]$CleanOnly
)

$ErrorActionPreference = "Stop"

# 检测当前是否管理员权限
$isAdmin = ([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
if (-not $isAdmin) {
    Write-Host "提示：当前不是管理员权限，若残留进程是管理员启动的，可能无法结束。" -ForegroundColor DarkYellow
}

Write-Host ""
Write-Host "===== 第 1 步：检查 8080 端口 =====" -ForegroundColor Cyan

# 查找正在监听 8080 端口的连接
$conn = Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue

if ($conn) {
    $procIds = $conn.OwningProcess | Sort-Object -Unique
    foreach ($procId in $procIds) {
        $proc = Get-Process -Id $procId -ErrorAction SilentlyContinue
        if ($proc) {
            Write-Host "发现残留进程占用 8080：$($proc.ProcessName)  (PID $procId)" -ForegroundColor Yellow
            try {
                Stop-Process -Id $procId -Force -ErrorAction Stop
                Write-Host "已结束 PID $procId" -ForegroundColor Green
            } catch {
                Write-Host "结束 PID $procId 失败（权限不足）：该进程可能是管理员权限启动的" -ForegroundColor Red
                Write-Host "请用「管理员身份」重新打开 PowerShell 后再运行本脚本" -ForegroundColor Red
                exit 1
            }
        }
    }
} else {
    Write-Host "端口 8080 空闲，无需清理" -ForegroundColor Green
}

# 稍等片刻，再次确认端口已释放
Start-Sleep -Milliseconds 500
$conn2 = Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue

if ($conn2) {
    Write-Host "端口 8080 仍被占用，请手动处理后再试" -ForegroundColor Red
    exit 1
}

Write-Host "端口 8080 已释放" -ForegroundColor Green

if ($CleanOnly) {
    Write-Host ""
    Write-Host "清理完成（未启动后端）" -ForegroundColor Green
    exit 0
}

Write-Host ""
Write-Host "===== 第 2 步：启动后端 =====" -ForegroundColor Cyan
Write-Host "运行 mvn spring-boot:run，按 Ctrl+C 可停止" -ForegroundColor DarkGray
Write-Host ""

mvn spring-boot:run
