# 生成 Android 签名密钥

## 方法 1: 使用 Git Bash 或 WSL (推荐)

```bash
# 进入项目目录
cd TraktNeoSync

# 运行生成脚本
bash generate-keystore.sh
```

脚本会自动生成:
- `traktneosync.keystore` - 签名密钥文件
- 随机生成的安全密码
- Base64 编码（用于 GitHub Actions）

## 方法 2: Windows PowerShell 手动生成

```powershell
# 生成密钥库
$keytool = "$env:JAVA_HOME\bin\keytool.exe"
& $keytool -genkey -v `
    -keystore "traktneosync.keystore" `
    -alias "traktneosync" `
    -keyalg RSA `
    -keysize 2048 `
    -validity 10000 `
    -dname "CN=TraktNeoSync, OU=App, O=Developer, L=Unknown, ST=Unknown, C=CN"

# 转换为 Base64 (用于 GitHub)
[Convert]::ToBase64String([IO.File]::ReadAllBytes("traktneosync.keystore"))
```

## GitHub Actions 配置

在仓库 Settings -> Secrets and variables -> Actions 中添加:

| Secret 名称 | 值 |
|------------|-----|
| `SIGNING_KEY_BASE64` | keystore 文件的 Base64 编码 |
| `SIGNING_KEY_ALIAS` | 密钥别名 (如: traktneosync) |
| `SIGNING_KEY_PASSWORD` | 密钥密码 |
| `SIGNING_STORE_PASSWORD` | 密钥库密码 |

## 本地构建配置

在项目根目录创建 `local.properties`:

```properties
# API Keys
traktClientId=your_trakt_client_id
traktClientSecret=your_trakt_client_secret

# 签名配置 (可选, 仅用于本地 release 构建)
SIGNING_KEY_ALIAS=traktneosync
SIGNING_KEY_PASSWORD=your_key_password
SIGNING_STORE_PASSWORD=your_keystore_password
```

将 `traktneosync.keystore` 放在 `app/` 目录下。

## 构建 Release APK

```bash
# 本地构建
./gradlew assembleRelease

# 或通过 GitHub Actions
# 访问: https://github.com/yuwanyuan/TraktNeoSync/actions
# 选择 "Build APK" -> "Run workflow" -> 选择 release
```
