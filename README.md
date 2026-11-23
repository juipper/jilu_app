# 考勤记录（jilu_app）

纯个人自用的极简考勤 App：只需要回答一件事——**今天该上班的，上了没有；今天该休息的，加班了没有。**

设计决策见 [SPEC.md](./SPEC.md)。本文件只讲怎么构建和怎么用。

## 0. 功能一览

- 月历记考勤：工作日「上班了 / 没上班」，休息日「加班了 / 没加班」，点一下、再点撤销。
- 工作日可配置（周规则）+ 单日覆盖（法定节假日、调休补班）。
- 本月统计：出勤 / 应出勤 / 加班 / 未记录天数。
- 上下班两条提醒（仅安卓版；只在工作日且当天未记录时提醒）。
- 背景可换：默认 / 5 套预设渐变配色 / 相册自定义图片（自动压缩 + 遮罩，可选「完整显示」或「铺满裁切」）。
- 本地存储 + JSON 备份导出 / 导入（安卓与桌面同一份备份格式，可互相导入导出）。
- 另有 Windows / macOS 桌面版：左侧月历 + 右侧当日面板，方向键选日期、空格标记、Esc 关闭设置。

---

## 1. 环境要求

| 组件 | 版本 |
|---|---|
| JDK | 21（Temurin 21.0.13 验证通过） |
| Gradle | 8.13（仓库已带 wrapper，不用另装） |
| Android SDK | platform 35 + build-tools 35.0.0 |

把 SDK 路径写进 `local.properties`，例如 `sdk.dir=C\:\\Users\\you\\AppData\\Local\\Android\\Sdk`。该文件被 `.gitignore` 排除，不进仓库。

## 2. 构建

```bash
./gradlew assembleDebug        # macOS / Linux / Git Bash
gradlew.bat assembleDebug      # Windows cmd / PowerShell
```

产物：`app/build/outputs/apk/debug/app-debug.apk`

单元测试（领域层，纯 JVM，不需要设备）：

```bash
./gradlew testDebugUnitTest
```

## 3. 桌面版（Windows / macOS）

桌面版是独立的 Compose Desktop 模块，和安卓共用 `core` 里的日型解析与备份编解码，彼此不共享界面代码。

开发时直接运行：

```bash
./gradlew :desktop:run
```

打包安装包（只能在对应系统上打对应的包）：

```bash
./gradlew :desktop:packageMsi    # Windows，需要安装 WiX Toolset 3.x
./gradlew :desktop:packageDmg    # macOS
```

产物在 `desktop/build/compose/binaries/main/msi` 或 `.../dmg`。

不想装 WiX 也可以先出免安装目录（里面带 jre，双击 `jilu_app.exe` / `jilu_app.app` 即可运行）：

```bash
./gradlew :desktop:createDistributable
```

两种包都**没有签名**：Windows 首次运行会弹 SmartScreen「未知发布者」，点「更多信息 → 仍要运行」；macOS 需要右键 → 打开，或执行 `xattr -d com.apple.quarantine /Applications/jilu_app.app`。

桌面版数据存在 `%APPDATA%\jilu_app\data.json`（Windows）或 `~/Library/Application Support/jilu_app/data.json`（macOS），背景图是同目录下的 `background.img`。

## 4. 装到手机

手机开「开发者选项 → USB 调试」，连上电脑后：

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

或者把 `app-debug.apk` 拷到手机里直接点安装（需要允许「安装未知来源应用」）。

首次启动会申请通知权限，用于「今天还没记录」的提醒。

## 5. 目录结构

```
core/src/main/kotlin/com/jilu/attendance/   # 安卓与桌面共用：日型解析、月度统计、备份编解码
desktop/src/main/kotlin/com/jilu/attendance/desktop/   # 桌面版界面：月历、当日面板、设置、背景
app/src/main/java/com/jilu/attendance/
├── AppContainer.kt         # 手写依赖装配（db / dao / 设置 / 备份）
├── data/                   # Room 实体、DAO、数据库、DataStore 设置、背景图存储
├── domain/                 # 纯函数：日型解析、月度统计（有单元测试）
├── backup/                 # JSON 导出 / 导入
├── reminder/               # 闹钟调度、通知、开机重排
└── ui/                     # Compose：月历、当日面板、设置、背景图层
app/src/test/java/com/jilu/attendance/domain/   # 领域层单测
```

## 6. 已知边界

- **精确闹钟**：Android 12+ 需要「闹钟与提醒」权限，未授予时降级为近似提醒（可能偏差几分钟），不会崩。
- **国产 ROM 后台限制**：小米 / 华为 / OPPO 等可能杀后台导致提醒延迟，需在系统设置里给本应用「自启动 / 后台运行」白名单。
- **导入是整体替换**：不是合并，导入前会弹确认并显示将被删除的条数。
- **未来日期**只能改日型（预排节假日 / 补班），不能打考勤标记。
- 修改工作日集合后，**历史记录的颜色和统计不变**（记录里存了当时的日型快照）。
- **背景图**只保存在本机私有目录（`files/background.jpg`，长边压缩到 1600px），不进 JSON 备份；换机导入备份后背景会回到默认，需要重新选图。
- 背景图会盖一层 55% 白色遮罩，所以照片再花也不会影响日期数字的可读性；如果觉得太淡/太重，改 `ui/BackgroundLayer.kt` 里的 alpha 即可。
- **图片适配**两种方式：`完整显示`（默认，整张图都在，比例不符的留白用同一张图的模糊版铺底）与 `铺满裁切`（等比放大铺满屏幕，裁掉边角）。切换在设置页「背景 → 图片」下。
- **桌面版不提醒**：只做记录、统计、备份和换背景，没有通知和开机自启。
- **两端数据不同步**：安卓用 Room，桌面用本地 JSON 文件，靠导出的备份 JSON 互相搬运（格式一致，双向可导入）。
- **Release 里的安装包**由 GitHub Actions 构建：同一版本号下一个 Release 同时挂 APK / MSI / DMG。
- **macOS 包**由 CI 在 `macos-latest` 上构建，是 Apple Silicon（arm64）版；Intel Mac 需要在 Intel runner 上另打一次。
