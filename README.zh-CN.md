# LumaGA

[English](README.md) | **简体中文**

NGA（bbs.nga.cn）论坛的 Android 第三方客户端，[MNGA](https://github.com/BugenZhao/MNGA) 的 Android 移植版。UI 使用 **Jetpack Compose** 构建，核心业务逻辑复用 MNGA 的 **Rust** 后端，通过 JNI 以 `liblogic.so` 方式接入。

## 功能特性

- **首页版块列表**：宫格布局（每行 3 个，图标 + 标题），收藏版块置顶，分类折叠/展开，过滤（仅收藏 / 全部）
- **帖子列表**：默认排序 / 最新回复 / 最新发布 / 热门 / 推荐等模式，下拉刷新，滑动收藏
- **帖子详情**：楼层展示、点赞 / 点踩 / 引用回复、楼中楼评论、附件与图片查看、只看作者、跳楼定位
- **登录**：内置 WebView 登录 NGA，自动读取 Cookie 授权；支持退出 / 添加账号
- **收藏**：版块收藏（可远程同步）、话题收藏、收藏夹
- **短消息 / 通知**：站内信、通知列表、未读角标
- **搜索**：版块、话题、用户全局搜索
- **浏览历史**：自动记录
- **个性化**：深浅色主题、主题色、字号、图片缩放、屏蔽内容等
- **其他**：缓存管理、`mnga://` 深链、剪贴板链接跳转、Plus 功能位

## 目录结构

```
LumaGA/
├── app/                      # Compose UI（移植自 MNGA 的 SwiftUI 界面）
│   ├── build.gradle.kts
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/bugenzhao/mnga/
│       │   ├── ui/           # 各界面（forumlist、topiclist、topicdetails、login、search…）
│       │   ├── model/        # 状态与业务模型
│       │   ├── storage/      # 本地存储（偏好、认证、收藏、屏蔽词）
│       │   └── util/         # 工具类
│       └── res/              # 资源
├── logic/                    # Android 逻辑接入模块
│   ├── build.gradle.kts
│   └── src/main/
│       ├── java/             # JNI 桥接 + 检入的 protobuf Java/Kotlin 生成代码
│       └── jniLibs/          # 预编译 liblogic.so（arm64-v8a / x86 / x86_64）
├── rust/                     # 生成 liblogic.so 的 Rust workspace（从 MNGA 引入）
│   ├── logic/                # Rust 业务逻辑 crate
│   ├── protos/               # .proto 定义
│   ├── build-jni-libs.sh     # 交叉编译 liblogic.so 到 jniLibs
│   ├── gen-kotlin-protos.sh  # 生成 Kotlin protobuf 绑定
│   └── README.md
├── gradle/                   # Gradle wrapper 与版本目录
└── .github/workflows/        # CI（ci.yml 构建 APK；rust.yml 重编译 .so 并跑测试）
```

## 构建

### 前置条件

- JDK 17+
- Android SDK（`compileSdk 36`）
- 首次构建需联网拉取依赖

### 构建 APK

```bash
# 配置本机 SDK 路径（如环境变量未设置）
echo "sdk.dir=$HOME/Library/Android/sdk" > local.properties

./gradlew assembleDebug
# 产物：app/build/outputs/apk/debug/app-debug.apk

adb install -r app/build/outputs/apk/debug/app-debug.apk   # 安装到设备
```

> `liblogic.so` 已检入 `logic/src/main/jniLibs/`，纯 Gradle 构建**不需要** NDK 或 Rust 工具链。

### 重新编译 Rust 逻辑库

修改 `rust/` 下的源码后，重新生成并提交二进制：

```bash
rust/build-jni-libs.sh      # 生成 liblogic.so（arm64-v8a / x86_64 / x86）
rust/gen-kotlin-protos.sh   # 仅当 rust/protos/ 变更时
```

两个脚本都需要 `protoc`；前者还需要 `cargo-ndk` 与 Android NDK（Android 构建会从源码编译 OpenSSL，耗时较长）。

## CI

- [ci.yml](.github/workflows/ci.yml)：每次 push / PR 构建 debug APK 并上传产物。
- [rust.yml](.github/workflows/rust.yml)：`rust/` 变更时重编译三个 ABI 的 `liblogic.so` 并运行 Rust 单元测试。

## 登录说明

登录使用内置 WebView 打开 NGA 登录页，登录成功后读取 `ngaPassportUid` / `ngaPassportCid` Cookie 完成授权。实现在 `app/src/main/java/com/bugenzhao/mnga/ui/screens/login/LoginSheet.kt`。

## 已知事项

- **夜间模式页面切换闪白**：已通过在导航栈底部铺设跟随主题的不透明背景修复（主题本身继承自 Light 主题，Window 背景为白色）。
- **返回列表页不丢状态**：导航栈为每个路由保存状态（`rememberSaveable`），返回时恢复列表数据与滚动位置。
- **列表页无封面图**：NGA 话题列表接口不返回图片数据，图片仅在帖子详情中提供。

## 致谢与许可

MNGA 未附带 LICENSE，其 README 保留所有权利，因此本移植项目（含 `rust/` 下引入的 Rust 源码）未经作者许可不可再分发。`rust/logic/sled` 保留其自身的 MIT / Apache-2.0 许可文件。

本项目仅供学习交流使用，请遵守 NGA 用户协议与相关法律法规。
