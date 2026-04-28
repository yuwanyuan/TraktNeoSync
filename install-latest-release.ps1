# 自动下载并安装最新Release APK到MuMu模拟器
# 用法: .\install-latest-release.ps1

$ADB = "E:\moni\MuMu Player 12\nx_device\12.0\shell\adb.exe"
$Device = "127.0.0.1:16384"
$PackageName = "com.example.traktneosync"
$Repo = "yuwanyuan/TraktNeoSync"
$TempApk = "$env:TEMP\traktneosync-latest.apk"

function Test-DeviceConnected {
    Write-Host "检查MuMu模拟器连接状态..." -ForegroundColor Cyan
    $devices = & $ADB -s $Device devices 2>$null
    if ($devices -match "$Device\s+device") {
        Write-Host "模拟器已连接: $Device" -ForegroundColor Green
        return $true
    } else {
        Write-Host "模拟器未连接，请确保MuMu模拟器正在运行" -ForegroundColor Red
        return $false
    }
}

function Get-LatestReleaseInfo {
    Write-Host "获取最新Release信息..." -ForegroundColor Cyan
    try {
        $releaseJson = gh release view --repo $Repo --json tagName,assets,publishedAt 2>$null
        if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($releaseJson)) {
            Write-Host "无法获取Release信息" -ForegroundColor Red
            return $null
        }
        $releaseInfo = $releaseJson | ConvertFrom-Json
        return $releaseInfo
    } catch {
        Write-Host "获取Release信息失败: $_" -ForegroundColor Red
        return $null
    }
}

function Download-LatestApk {
    param([string]$DownloadUrl)

    Write-Host "下载最新APK..." -ForegroundColor Cyan
    Write-Host "下载地址: $DownloadUrl" -ForegroundColor Gray

    try {
        # 删除旧文件
        if (Test-Path $TempApk) {
            Remove-Item $TempApk -Force
        }

        # 使用gh命令下载
        $progressPreference = 'silentlyContinue'
        Invoke-WebRequest -Uri $DownloadUrl -OutFile $TempApk -Headers @{
            "Accept" = "application/octet-stream"
        }
        $progressPreference = 'Continue'

        if (Test-Path $TempApk) {
            $size = [math]::Round((Get-Item $TempApk).Length / 1MB, 2)
            Write-Host "下载完成: $size MB" -ForegroundColor Green
            return $true
        }
        return $false
    } catch {
        Write-Host "下载失败: $_" -ForegroundColor Red
        return $false
    }
}

function Install-Apk {
    param([string]$Path)

    if (-not (Test-Path $Path)) {
        Write-Host "APK文件不存在: $Path" -ForegroundColor Red
        return $false
    }

    Write-Host "正在安装APK..." -ForegroundColor Cyan
    Write-Host "APK大小: $([math]::Round((Get-Item $Path).Length / 1MB, 2)) MB" -ForegroundColor Gray

    # 先卸载旧版本（如果存在）
    Write-Host "卸载旧版本..." -ForegroundColor Gray
    & $ADB -s $Device uninstall $PackageName 2>$null | Out-Null

    # 安装新版本
    $installOutput = & $ADB -s $Device install -r -d $Path 2>&1
    if ($LASTEXITCODE -eq 0) {
        Write-Host "安装成功!" -ForegroundColor Green

        # 启动应用
        Write-Host "启动应用..." -ForegroundColor Cyan
        & $ADB -s $Device shell am start -n "$PackageName/.MainActivity" 2>$null | Out-Null
        Write-Host "应用已启动" -ForegroundColor Green
        return $true
    } else {
        Write-Host "安装失败: $installOutput" -ForegroundColor Red
        return $false
    }
}

# ========== 主流程 ==========

Write-Host "========================================" -ForegroundColor Blue
Write-Host "  下载并安装最新Release到MuMu模拟器" -ForegroundColor Blue
Write-Host "========================================" -ForegroundColor Blue
Write-Host ""

# 检查设备连接
if (-not (Test-DeviceConnected)) {
    exit 1
}

Write-Host ""

# 获取最新Release信息
$releaseInfo = Get-LatestReleaseInfo
if (-not $releaseInfo) {
    exit 1
}

Write-Host "最新版本: $($releaseInfo.tagName)" -ForegroundColor Green
Write-Host "发布时间: $($releaseInfo.publishedAt)" -ForegroundColor Gray
Write-Host ""

# 找到APK资源
$apkAsset = $releaseInfo.assets | Where-Object { $_.name -eq "app-release.apk" } | Select-Object -First 1
if (-not $apkAsset) {
    Write-Host "未找到APK文件" -ForegroundColor Red
    exit 1
}

# 下载APK
$downloadSuccess = Download-LatestApk -DownloadUrl $apkAsset.url
if (-not $downloadSuccess) {
    exit 1
}

Write-Host ""

# 安装APK
$installSuccess = Install-Apk -Path $TempApk

# 清理临时文件
if (Test-Path $TempApk) {
    Remove-Item $TempApk -Force
    Write-Host "清理临时文件" -ForegroundColor Gray
}

if ($installSuccess) {
    Write-Host ""
    Write-Host "========================================" -ForegroundColor Green
    Write-Host "  安装完成! 版本: $($releaseInfo.tagName)" -ForegroundColor Green
    Write-Host "========================================" -ForegroundColor Green
    exit 0
} else {
    exit 1
}
