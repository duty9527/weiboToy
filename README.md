# weiboToy

weiboToy 是一个 Android 微博客户端实验项目，使用 Kotlin、Jetpack Compose 和 Gradle 构建。项目当前聚焦三类功能：

- 微博账号扫码登录，并同步登录 Cookie。
- 查看微博群聊列表、群聊消息和本地缓存消息。
- 查看最新微博信息流、搜索微博、打开微博详情，并展示评论、转发和赞。

## 技术栈

- Kotlin 2.3
- Android Gradle Plugin 9.0
- Jetpack Compose / Material 3
- OkHttp / Gson
- SQLite 本地存储

## 本地构建

需要 JDK 17 和 Android SDK。

```bash
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

release 构建默认只生成 `arm64-v8a` APK。未配置签名环境变量时，Gradle 仍可执行 release 构建，但产物不会使用 release keystore 签名。

```bash
./gradlew assembleRelease
```

## release 签名

release 签名通过环境变量传入：

```bash
export WEIBOTOY_RELEASE_STORE_FILE=/path/to/weiboToy-release.jks
export WEIBOTOY_RELEASE_STORE_PASSWORD=your_store_password
export WEIBOTOY_RELEASE_KEY_ALIAS=weiboToy
export WEIBOTOY_RELEASE_KEY_PASSWORD=your_key_password
./gradlew assembleRelease
```

本地签名文件建议放在 `signing/` 目录。该目录已加入 `.gitignore`，不要提交 keystore 和密码文件。

本仓库本机已生成：

- `signing/weiboToy-release.jks`
- `signing/weiboToy-release.jks.base64`
- `signing/release-signing.env`

当前 keystore 是 PKCS12 类型，`WEIBOTOY_RELEASE_KEY_PASSWORD` 与 `WEIBOTOY_RELEASE_STORE_PASSWORD` 使用同一个值。

## GitHub Actions 发布

仓库包含 `.github/workflows/android-release.yml`。推送 `v*` tag 后会自动执行：

- 安装 JDK 17 和 Android SDK
- 执行单元测试
- 构建签名的 arm64 release APK
- 上传 artifact
- 发布到 GitHub Release

需要在 GitHub 仓库的 `Settings -> Secrets and variables -> Actions -> New repository secret` 配置：

- `WEIBOTOY_RELEASE_KEYSTORE_BASE64`
- `WEIBOTOY_RELEASE_STORE_PASSWORD`
- `WEIBOTOY_RELEASE_KEY_ALIAS`
- `WEIBOTOY_RELEASE_KEY_PASSWORD`

这些值在本机的 `signing/` 目录里已经生成：`WEIBOTOY_RELEASE_KEYSTORE_BASE64` 对应 `signing/weiboToy-release.jks.base64` 的内容，其余三个对应 `signing/release-signing.env` 中的同名变量。

创建 tag 示例：

```bash
git tag v1.0.0
git push origin v1.0.0
```
