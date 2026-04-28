# 自动安装APK到MuMu模拟器脚本
# 用法: .\install-to-mumu.ps1 [APK路径]
# 如果没有提供APK路径，会自动下载最新的Release APK

param(
    [string]$ApkPath = ""
)

$ADB = "E:\moni\MuMu Player 12\nx_device\12.0\shell\adb.exe"
$Device = "127.0.0.1:16384"
$PackageName = "com.example.traktneosync"
$Repo = "yuwanyuan/TraktNeoSync"

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

function Get-LatestApkUrl {
    Write-Host "获取最新Release APK下载链接..." -ForegroundColor Cyan
    try {
        $releaseInfo = gh release view --repo $Repo --json tagName,assets 2>$null | ConvertFrom-Json
        if ($releaseInfo -and $releaseInfo.assets) {
            $apkAsset = $releaseInfo.assets | Where-Object { $_.name -eq "app-release.apk" } | Select-Object -First 1
            if ($apkAsset) {
                Write-Host "找到最新版本: $($releaseInfo.tagName)" -ForegroundColor Green
                return $apkAsset.url
            }
        }
        Write-Host "未找到APK资源" -ForegroundColor Red
        return $null
    } catch {
        Write-Host "获取Release信息失败: $_" -ForegroundColor Red
        return $null
    }
}

function Install-Apk {
    param([string]$Path)

    if (-not (Test-Path $Path)) {
        Write-Host "APK文件不存在: $Path" -ForegroundColor Red
        return $false
    }

    Write-Host "正在安装APK: $Path" -ForegroundColor Cyan
    Write-Host "APK大小: $([math]::Round((Get-Item $Path).Length / 1MB, 2)) MB" -ForegroundColor Gray

    # 先卸载旧版本（如果存在）
    Write-Host "检查并卸载旧版本..." -ForegroundColor Gray
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

function Install-FromLocalBuild {
    Write-Host "查找本地构建的APK..." -ForegroundColor Cyan
    $localApk = "app\build\outputs\apk\release\app-release.apk"
    if (Test-Path $localApk) {
        return Install-Apk -Path $localApk
    }

    $localApk2 = "app\build\outputs\apk\debug\app-debug.apk"
    if (Test-Path $localApk2) {
        return Install-Apk -Path $localApk2
    }

    Write-Host "未找到本地构建的APK" -ForegroundColor Red
    return $false
}

# ========== 主流程 ==========

Write-Host "========================================" -ForegroundColor Blue
Write-Host "  TraktNeoSync MuMu模拟器自动安装工具" -ForegroundColor Blue
Write-Host "========================================" -ForegroundColor Blue
Write-Host ""

# 检查设备连接
if (-not (Test-DeviceConnected)) {
    exit 1
}

Write-Host ""

# 如果提供了APK路径，直接安装
if ($ApkPath -and $ApkPath -ne "") {
    Write-Host "使用指定的APK文件" -ForegroundColor Cyan
    $result = Install-Apk -Path $ApkPath
    exit ($result ? 0 : 1)
}

# 否则尝试本地构建
Write-Host "未指定APK路径，尝试本地构建..." -ForegroundColor Cyan
$localResult = Install-FromLocalBuild
if ($localResult) {
    exit 0
}

Write-Host ""
Write-Host "本地安装失败。你可以:" -ForegroundColor Yellow
Write-Host "  1. 先运行 .\gradlew assembleRelease 构建APK" -ForegroundColor Yellow
Write-Host "  2. 或者提供APK路径: .\install-to-mumu.ps1 <apk路径>" -ForegroundColor Yellow
Write-Host "  3. 或者从GitHub Release手动下载安装" -ForegroundColor Yellow

exit 1
