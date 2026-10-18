# 海屿音乐 · SeaShelf

连接自己的 [Navidrome](https://github.com/navidrome/navidrome) 音乐库的 Android 客户端。原创的深色音乐界面，支持 Android 8.0 及以上。

## 功能

- 浏览专辑、搜索歌手/专辑/歌曲，完整加载分页结果。
- 前台、后台、锁屏播放，以及耳机拔出暂停和音频焦点管理。
- 顺序、列表循环、单曲循环、随机和单曲播放；编辑队列、下一首播放。
- 记住账号对应的队列、模式和进度，重新打开后按播放继续。
- 显示 Navidrome 返回的歌词，支持逐句高亮；音乐库需有内嵌歌词或对应的 `.lrc` 文件。
- 在播放页调整手机媒体音量，支持默认关闭的轻度音量增强。
- 192 kbps MP3 流媒体、可复用的私有音频缓存，以及明确的加载/缓冲/失败状态。
- 不包含广告、会员促销或广告 SDK。

## 连接自己的音乐库

1. 在电脑安装并运行 Navidrome，配置音乐目录和 FFmpeg；创建自己的用户。
2. 手机和电脑在同一局域网时，填写电脑的局域网地址，例如 `http://192.168.1.10:4533`。`127.0.0.1` 指手机自身。
3. 使用移动网络时，填写已配置的公网 HTTPS 地址，再输入 Navidrome 用户名和密码。不要将不带 HTTPS 的 4533 端口直接映射到公网。

电脑需要持续运行、联网、不休眠。手机只安装本应用即可使用公网 HTTPS 入口；仓库本身不提供服务器、音乐资源或公共账号。可以在电脑配置 Tailscale Funnel 等 HTTPS 入口，服务可用性和传输速度也取决于该入口及电脑上行网络。

登录信息经 Android Keystore 加密后保存在应用私有目录。歌曲流地址包含临时认证信息，不应粘贴到日志或公开分享。切换服务器可使用首页“设置”。覆盖安装时保留原有登录和播放会话。

## 音量调节（0.3.2）

播放页右上角点击“音量”：

- **媒体音量**：调整手机的音乐音量，显示系统实际接受的音量；与手机音量键保持同步。系统限制或固定音量设备会如实提示。
- **音量增强**：默认关闭，可选 0–6 dB，用于录音本身较小的歌曲。增强设置会记住，切歌后仍有效；只作用于本应用的播放会话。增益过大可能失真，可以调低或关闭。

播放页的实体音量键也控制媒体音量。应用不会在打开页面时自动提高手机音量，也不会自动打开增强。音量增强依赖设备提供的音效支持；不支持时保留普通播放并提示，实际响度受手机、耳机与系统音量限制。

手机未连接时无法验证外放/耳机实际效果，也无法代替用户安装；请使用相同签名的新版 APK 覆盖升级。

0.3.2 验证：61 项单元测试全部通过，Android Lint 无错误（5 项既有警告），发布包及设备测试包均构建成功。发布签名与 0.3.1 一致。音量上限、耳机路由和实际听感尚未在手机验证。

## 缓冲与缓存（0.3.1）

播放器使用 AndroidX Media3 ExoPlayer，设置起播、再次缓冲和提前读取阈值；这些是缓冲策略，不代表手机实测起播时间。音频缓存限制为 256 MiB，空间不足时淘汰较早使用的数据。系统也可能回收缓存，因此它不是离线下载功能。

缓存按服务器、账号、歌曲和音质隔离，认证链接改变仍能复用相同歌曲的数据。默认请求 192 kbps MP3；普通网络超时和认证失败不再自动切换为更大的原始无损文件。播放音质为有损转码，电脑原音乐不变。

首次转码流没有时长时，读取错误会保留位置并显示“重试”，点击后重新连接；避免被当作直播而自动从头重播。没有索引的 MP3 定位可能是近似位置。

本机隔离测试：一首 26,554,001 字节 FLAC 转为 4,918,715 字节 MP3，服务器首段返回约 79 ms；整首解码和已完成转码的 Range 读取正常。这是电脑本机数据，**不是手机流量测速**。当前手机未通过 USB 连接，0.3.1 的安装、流量起播、连续切歌、定位和锁屏播放仍需真机验收。

0.3.1 构建验证：51 项主机单元测试全部通过，Android Lint 无错误（5 项既有警告），发布 APK 和设备测试 APK 均已构建。设备集成测试已编译，尚未在手机上执行。

## 本地构建

需要 JDK 17、Android SDK Platform 34、Build Tools 34.0.0。项目包含 Gradle 8.2.1 Wrapper，首次构建会下载依赖。

创建自己的 `local.properties`，填写 SDK 路径；该文件不提交：

```properties
sdk.dir=C:/Users/YOUR_NAME/AppData/Local/Android/Sdk
```

Windows PowerShell：

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

macOS / Linux：

```sh
chmod +x gradlew
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

调试 APK 在 `app/build/outputs/apk/debug/app-debug.apk`。它使用本机调试签名，**不能覆盖另一签名的已安装应用**。

### 发布签名

用 Android Studio 或 JDK `keytool` 创建自己的密钥库，将其保存为 `.signing/release.jks`，别名 `seashelf`。创建 `.signing/credentials.properties`：

```properties
storePassword=YOUR_KEYSTORE_PASSWORD
keyPassword=YOUR_KEY_PASSWORD
```

执行 `./gradlew :app:assembleRelease`（Windows 使用 `.\gradlew.bat`）。输出为 `app/build/outputs/apk/release/app-release.apk`。务必单独备份签名文件；以后覆盖升级必须使用相同签名。

`.signing/`、`local.properties`、APK、构建输出、音乐、数据库和 `.qa/` 私有测试数据不上传。仓库不附带作者的签名密钥、登录信息或个人服务器地址。

## 验证与辅助脚本

- `:app:testDebugUnitTest`：API 分页、队列、用户意图、会话隔离、凭据加密和缓存策略的主机单元测试。
- `:app:lintDebug`：Android 静态检查。
- `:app:connectedDebugAndroidTest`：需要 Android 设备；缓存复用与 Android 兼容性验证。
- `scripts/test-transcoding.py`：使用独立的临时 Navidrome 实例检查 FFmpeg、完整解码和 Range；不修改正式服务。
- `scripts/import_kugou_lyrics.py`：本地歌词匹配工具，默认只显示统计，`--apply` 才写入；已有 `.lrc` 不覆盖。
- `scripts/setup-remote.ps1 -CheckOnly`：检查 Windows 端 Navidrome / Tailscale。启用自启动和 Funnel 的脚本依赖现有的目录布局，先阅读脚本并调整为自己的环境。
