# TraktNeoSync

将 Trakt 观看记录同步到 NeoDB 的 Android 应用。

## 功能

- **Trakt 列表浏览**：查看已观看和待看的电影、剧集
- **TMDB 中文标题**：自动获取 TMDB 中文译名替换英文标题
- **海报加载**：通过 TMDB 获取海报封面，支持长按保存到相册
- **NeoDB 同步**：一键将 Trakt 记录同步到 NeoDB 书架
- **NeoDB 评分**：在应用内直接为条目评分、写评论，支持同步到长毛象
- **搜索**：搜索 NeoDB 条目并直接标记状态或评分
- **多语言支持**：可设置首选显示语言（影响 TMDB 数据）
- **本地缓存**：Room 缓存 Trakt 列表和海报 URL，减少加载时间
- **崩溃诊断**：内置诊断界面，方便排查问题

## 界面

| 页面 | 说明 |
|------|------|
| **Trakt** | 浏览 Trakt 电影/剧集列表，支持已观看/待看切换 |
| **同步** | 检查 Trakt 与 NeoDB 的差异，一键同步 |
| **搜索** | 搜索 NeoDB 条目，支持标记状态和评分 |
| **设置** | 账号管理、TMDB API Key、首选语言、清理缓存 |

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

**TMDB API Key**（可选，用于海报和中文标题）：
- 在应用内 **设置 → TMDB API Key** 中输入
- 获取地址：https://www.themoviedb.org/settings/api

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

- **Kotlin** — 主力开发语言
- **Jetpack Compose** — UI 框架
- **Hilt** — 依赖注入
- **Retrofit + OkHttp** — 网络请求
- **Room** — 本地数据库缓存
- **DataStore** — 偏好设置持久化
- **Coil** — 图片加载
- **Material Design 3** — 设计系统

## 数据流

```
Trakt API → 列表数据 → TMDB API（中文标题+海报）→ Room 缓存 → UI
                                    ↓
NeoDB API ← 同步/评分/搜索 ← 用户操作
```

## 许可证

MIT
