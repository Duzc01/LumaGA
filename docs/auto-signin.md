# 自动签到任务记录

> 状态跟踪文件：实现 LumaGA 每日自动签到（每日签到得 N 币）。
> 更新时间：2026-08-23

## 目标

在 LumaGA 中实现**每日自动签到**：登录状态下自动调用 NGA 的签到接口（每日签到得 N 币），无需用户手动打开签到页。

## 当前状态

### 已完成

1. **调研 PureNGA 的签到机制**（`../PureNGA`，克隆自 chr233/PureNGA）
   - PureNGA 是 Xposed/LSPosed hook 模块，hook 官方恩基爱社区 App（适配 9.9.61 及之前的老版原生 App）
   - 它的"自动签到"分两层：
     - `AUTO_SIGN`：hook `HomeFragment.updateSingStatus`（`args[0]==0` 表示今日可签到 + 已登录）→ 自动打开官方 App 的签到页（`LoginWebView.show(context, 5)`），**仍需用户手动点签到**
     - `LOCAL_VIP`：hook `isVip()` 永远返回 true，骗官方 App 认为账号是付费会员，触发官方 App 的会员自动签到
   - **不可直接移植**（依赖官方 App 内部类/方法），LumaGA 需要直接调接口

2. **逆向新版官方 NGA App**（Flutter 版，vcode 90967，从 25pp 下载）
   - 原生层（classes2.dex）找到签到相关字段/资源 key：
     - `WALL_SIGNIN_INFO` / `WALL_DAY_INFO` / `WALL_TASK_INFO` / `WALL_TASK_URL` / `WALL_VIDEO_SET_TASK`
     - `CHECKIN_DETAIL` / `checkinSuccess` / `checkin_count_add` / `double_checkin_mission`
     - `HOMEPAGE_WALL_SCRAP_FLAG`、`ScrapWallResonseModel`（签到墙响应模型：`uid` / `sum` 累计天数 / `continued` 连续天数 / `last_time` / `prompt_words` / `have_common_exam`）
   - **结论**：官方 App 首页有「签到墙」（ScrapWall），每日签到即"刮墙"；**签到请求的 URL（`__lib`/`__act` 值）在 Flutter Dart 层，AOT 编译后非明文**，静态逆向无法拿到接口
   - 老版原生 9.9.61（PureNGA release 里的 origin.apk）没有每日签到（WALL/SCRAP 是后来加的）

3. **方案 B 已启动**：官方 NGA 新版 App 已安装到 OPPO 设备
   - 设备：`JBHUHMC68LCENZUG`（OPPO，Android 13）
   - APK：`/tmp/nga.apk`（用户提供的 25pp 链接，Flutter 新版），已 `adb install` 成功
   - 老版 9.9.61 原版也下载过：`/tmp/nga_9.9.61-3.3.2-lspatched.apk`（内含 `assets/lspatch/origin.apk`）

### 进行中 / 待办

- [ ] 用户在官方 NGA App 里**登录账号**（ElizabethDU 或用于测试的账号）
- [ ] **抓包**官方 App 签到接口（进入首页 → 点签到墙 → 点签到），拿到 `nuke.php`/`app_api.php` 的 URL + 参数
- [ ] 在 LumaGA 的 Rust 逻辑层实现签到请求（有登录态，参照其他 `logicCallAsync` 调用）
- [ ] 接入 UI（如用户菜单/设置入口或自动触发）+ 测试

## 问题 / 阻塞

1. **接口 URL 未知（主要阻塞）**：静态逆向拿不到 Flutter 层拼接的 `__lib`/`__act`，必须动态抓包
2. **抓包技术方案待定**：OPPO 无 root，抓 HTTPS 需要代理（Mac 上 mitmproxy/Charles）+ 安装证书；**NGA 可能有 SSL Pinning**，若 pinning 则需 frida/重打包（更复杂，可能放弃）
3. 官方 App 登录需要用户的 NGA 账号密码（用户手动操作，我无法代登）

## 下一步（恢复任务时从这里继续）

1. 让用户在 OPPO 上打开官方 NGA App 并登录
2. 搭建抓包：Mac 起 mitmproxy（端口如 8080），OPPO Wi-Fi 设代理 + 安装 mitm 证书（`adb install` CA 证书 / 系统证书方案按 Android 13 处理）
3. 若证书安装/代理被 App 拒绝（pinning），改用方案 A（用户手机抓包工具）或方案 C（盲试常见接口名，如 `nuke.php?__lib=wall&__act=scrap` 之类）
