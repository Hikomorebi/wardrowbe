# 安卓「我的衣橱」

原生 Kotlin/Jetpack Compose 客户端，最低 Android 12（API 31），仅简体中文。与 Web 使用同一账号、数据库、图片与手动搭配，不需要 Google Play 服务。首版联网使用，不提供离线编辑、后台同步或 AI 推荐。

## 当前访问阶段

2026-11-19 已完成域名备案并启用正式 HTTPS 服务。安装 APK 后，在登录页使用 `https://cd.hikomorebi.site` 和原来的邮箱、密码即可连接同一套衣橱；默认地址已是这个域名，无需重新构建 APK。

[电脑 SSH 私有访问](private-access.md) 保留作维护备用。18080 仍只绑定服务器回环地址，手机日常通过 HTTPS 域名访问。手机相机、系统选图、返回手势等仍需在实际设备上验收。

## 安装与更新

正式安装包为 `android/artifacts/my-wardrobe-release.apk`，旁边的 `.sha256` 文件用于校验。将 APK 复制到手机，允许当前文件管理器安装应用后打开安装。后续使用同一个签名、递增 `versionCode` 的 APK 覆盖安装，可保留服务器设置和登录状态；无需卸载。

如从自己电脑下载服务器上的安装包，在 PowerShell 运行：

```powershell
scp -P 23456 hkb@139.155.149.243:/home/hkb/Projects/wardrowbe/android/artifacts/my-wardrobe-release.apk "$HOME/Downloads/my-wardrobe-release.apk"
```

测试包的名称为“我的衣橱（测试）”、包名为 `site.hikomorebi.wardrobe.debug`，可与正式包共存，数据与登录状态互相独立。正式包名为 `site.hikomorebi.wardrobe`。

## 功能入口

- **衣橱**：搜索、分页、按类别/成色/位置/季节筛选；拍照或选图新增，查看和修改，归档、恢复及删除。每件衣物维护一张主图。
- **推荐搭配**：衣物卡片仅保留此入口，按已保存组合展示其他配套衣物。源衣物单独展示，不混入结果。
- **我的搭配**：新增、修改和删除手动组合，每个组合选择 1～20 件可用衣物。含归档衣物的已有组合仍可查看，编辑时需恢复或移除相关衣物后保存。
- **我的**：类别和位置管理、账号信息、修改密码、退出登录、服务器地址与版本。没有子类别，季节只有春夏秋冬。

照片通过系统相机或照片选择器取得；选择器不可用时使用系统文件选择器。上传前校正方向、最长边缩至 2048 像素、转为 JPEG（质量 85），去除原始 EXIF 元数据。图片缓存位于应用私有目录，退出后清理。字段保存与图片替换是两个请求，部分成功时会明确提示，保留页面供重试。

## 手机会话与接口

新增接口均位于 `/api/v1/auth/mobile`，仅在 `LOCAL_AUTH_ENABLED=true` 时启用：

| 方法与路径 | 请求 | 响应 |
| --- | --- | --- |
| `POST /login` | `email`、`password` | `access_token`、`refresh_token`、`token_type`、`expires_in`、`session_expires_at`、`user` |
| `POST /refresh` | `refresh_token` | 同上，令牌轮换 |
| `POST /logout` | `refresh_token` | 204，幂等结束对应手机会话 |

访问令牌最长 1 小时，手机会话从输入密码登录起固定有效 30 天，刷新不延长截止日期。刷新令牌只存 SHA-256 哈希，轮换后的旧令牌重放会撤销整组会话。并发刷新通过数据库行锁串行处理；客户端合并刷新请求，最多重试一次认证失败请求。刷新网络错误保留本地凭据，不盲目重试写请求。退出登录需成功联系服务器，网络失败时提示重试；不会声称已经撤销服务端会话。

手机令牌使用 Android Keystore 的 AES-GCM 密钥保护，不保存密码，不参与系统备份和设备迁移。令牌绑定服务器来源，更换地址会退出原账号。正式包只接受 HTTPS；测试包额外允许 localhost、127.0.0.1 和 Android 模拟器的 10.0.2.2 使用 HTTP。HTTP 重定向不会携带登录密码或令牌跳往其他地址。

数据库新增 `mobile_sessions` 与 `mobile_refresh_tokens`，迁移版本 `d0a6b2e4c8f1`；用户删除时级联删除。修改密码撤销该用户全部手机会话；现有 Web 登录接口和 7 天有效期保持原状。过期会话在该用户下次手机登录时清理。

## 开发与构建

工具链：JDK 17、Gradle 8.13、AGP 8.13.2、Kotlin 2.2.21、Android SDK 36。Gradle Wrapper 固定发行包 SHA-256；依赖版本固定。构建并发最多 2，Kotlin 编译器在 Gradle 进程内运行。不要在生产机同时运行多个构建。

设置 `JAVA_HOME` 和 `ANDROID_HOME`，安装 SDK 组件并接受 SDK 许可：

```bash
sdkmanager 'platforms;android-36' 'build-tools;35.0.0' 'build-tools;36.0.0' 'platform-tools'
bash android/build.sh check
```

若构建网络需代理，使用当前终端的 `GRADLE_OPTS` 设置 Java HTTP/HTTPS 代理，不把代理认证信息写进仓库。只编译测试包可运行 `bash android/build.sh debug`。连接本地模拟器或开发手机后运行 `cd android && ./gradlew connectedDebugAndroidTest`；云服务器没有 KVM 加速，实机相机、选图和厂商系统行为必须另外验收。

独立测试环境可通过电脑 SSH 转发加 `adb reverse tcp:18080 tcp:18080` 提供给测试包，此时 App 连接 `http://127.0.0.1:18080`。应使用隔离数据库和测试账号；此配置不会打开服务器公网应用端口。

### 正式签名

```bash
python3 android/create-signing-key.py
bash android/build.sh release
```

签名脚本将密钥与随机密码保存在仓库外的 `~/.local/share/wardrowbe/android-signing/`，目录权限 0700、文件 0600；本地 `android/signing.properties` 已忽略。脚本不会打印密码或覆盖现有签名身份。必须将密钥和密码另外加密备份到自己的电脑；现有数据库每日备份不包含这套 APK 签名材料。丢失签名密钥将无法覆盖更新原安装包。

不要提交签名文件、SDK 本地路径、账号凭据、测试数据或 APK。正式 APK 必须通过 `apksigner verify`，记录 SHA-256 与版本号。

## 验收清单

- 单元测试：并发续期、登录恢复、30 天截止日期、网络错误、令牌失效、地址与重定向限制、multipart 季节多选、清空字段、图片部分失败、推荐搭配筛选。
- 后端测试：手机登录与撤销、刷新轮换和重放、并发刷新、改密码、停用账号及原网页登录回归；数据库迁移在独立数据库验证。
- 组件测试：季节标签可读、类别可清空、提交期间禁止重复删除。
- 图片处理与完整流程的 AndroidTest 测试包也已提供，可在设备上验证方向校正、元数据清除，以及连接隔离 API 的原生操作流程。完整流程测试需向测试运行器传入 `isolatedApi=http://127.0.0.1:18082`，并使用独立数据库及源码内指定的专用测试账号；默认不连接任何服务器。
- 真机验收：安装与覆盖更新，登录和重启保持状态，衣物与组合完整操作，拍照/选图/取消，手机和 Web 双向刷新，深浅色、返回手势、键盘遮挡、弱网与错误提示。

开发阶段验证记录（2026-11-15）：后端完整回归 2441 项通过，客户端 JVM 单元测试 21 项通过，Debug/Release 静态检查无错误；服务器私有入口的手机登录、令牌轮换、退出撤销和原网页登录已验证，迁移前后数据数量一致，更新后的备份已在隔离数据库恢复。当前云主机无 KVM，软件模拟器的系统服务启动不稳定，AndroidTest 已编译，该轮未运行通过；真实手机系统交互验收需另行完成。

公网启用验证（2026-11-19）：正式 HTTPS 登录页和备案号展示通过外部访问验证，浏览器使用 Secure/HttpOnly 会话 Cookie，衣物图片和手动推荐搭配正常；手机接口的邮箱登录、图片上传与读取、令牌续期、固定会话截止日期、退出撤销以及匿名访问拒绝均通过 HTTPS 验证。全部使用专用测试账号，验收后已清理测试数据。真实安卓手机的登录、相机和系统选图仍需由持有人确认。
