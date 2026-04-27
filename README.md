# TraktNeoSync

将 Trakt 观看记录同步到 NeoDB 的 Android 应用。

## 功能

- 自动同步 Trakt 观看记录
- 一键添加到 NeoDB 书架
- 支持电影和剧集
- 简洁的底栏导航界面

## 构建

### 本地构建 (Debug)

```bash
./gradlew assembleDebug
```

APK 位于 `app/build/outputs/apk/debug/app-debug.apk`

### Release 构建

需要配置签名密钥：

1. 创建 `keystore.properties` 文件：
```properties
keyAlias=your_alias
keyPassword=your_password
storePassword=your_password
storeFile=path/to/your.keystore
```

2. 构建：
```bash
./gradlew assembleRelease
```

## 配置 API Keys

在 `local.properties` 中添加：

```properties
traktClientId=your_trakt_client_id
traktClientSecret=your_trakt_client_secret
```

## GitHub Actions 自动打包

推送代码到 main 分支会自动构建 Debug APK。要构建 Release APK：

1. 前往 Actions 页面
2. 选择 "Build APK" 工作流
3. 点击 "Run workflow"
4. 选择 build_type: release
5. 配置 GitHub Secrets:
   - `SIGNING_KEY_BASE64`: Base64 编码的 keystore 文件
   - `SIGNING_KEY_ALIAS`: 密钥别名
   - `SIGNING_KEY_PASSWORD`: 密钥密码
   - `SIGNING_STORE_PASSWORD`: 存储密码

## 技术栈

- Kotlin
- Jetpack Compose
- Hilt (依赖注入)
- Retrofit (网络请求)
- DataStore (本地存储)
- Material Design 3

## 界面

- 电影：Trakt 已观看/待看电影列表
- 剧集：Trakt 已观看/待看剧集列表  
- 同步：检查并同步到 NeoDB
- 设置：登录/登出账号

## 许可证

MIT
