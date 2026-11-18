# 考勤记录（jilu_app）

纯个人自用的极简考勤 App：只需要回答一件事——**今天该上班的，上了没有；今天该休息的，加班了没有。**

设计决策见 [SPEC.md](./SPEC.md)。本文件只讲怎么构建和怎么用。

## 0. 功能一览

- 月历记考勤：工作日「上班了 / 没上班」，休息日「加班了 / 没加班」，点一下、再点撤销。
- 工作日可配置（周规则）+ 单日覆盖（法定节假日、调休补班）。
- 本月统计：出勤 / 应出勤 / 加班 / 未记录天数。
- 上下班两条提醒（只在工作日且当天未记录时提醒）。
- 背景可换：默认 / 5 套预设渐变配色 / 相册自定义图片（自动压缩 + 遮罩，可选「完整显示」或「铺满裁切」）。
- 本地存储 + JSON 备份导出 / 导入。

---

## 1. 构建环境（本机已装好）

> 下表路径是作者本机的安装位置，换机器时改成你自己的 JDK / Gradle / SDK 路径即可；`local.properties` 不进仓库。

| 组件 | 版本 | 路径 |
|---|---|---|
| JDK | Temurin 21.0.13 | `C:\Users\majinpeng\dev\tools\jdk\jdk-21.0.13+9` |
| Gradle | 8.13 | `C:\Users\majinpeng\dev\tools\gradle\gradle-8.13` |
| Android SDK | cmdline-tools 12.0 + platform 35 + build-tools 35.0.0 + platform-tools | `C:\Users\majinpeng\dev\tools\android-sdk` |

SDK 路径已写在 `local.properties`，换机器时改这一行即可。

## 2. 构建

PowerShell：

```powershell
$env:JAVA_HOME='C:\Users\majinpeng\dev\tools\jdk\jdk-21.0.13+9'
$env:ANDROID_HOME='C:\Users\majinpeng\dev\tools\android-sdk'
$env:ANDROID_SDK_ROOT=$env:ANDROID_HOME
$env:Path="$env:JAVA_HOME\bin;$env:ANDROID_HOME\platform-tools;$env:Path"

& 'C:\Users\majinpeng\dev\tools\gradle\gradle-8.13\bin\gradle.bat' `
  -p 'C:\Users\majinpeng\Documents\jilu_app' assembleDebug --console=plain
```

产物：`app\build\outputs\apk\debug\app-debug.apk`

单元测试（领域层，纯 JVM）：

```powershell
& 'C:\Users\majinpeng\dev\tools\gradle\gradle-8.13\bin\gradle.bat' `
  -p 'C:\Users\majinpeng\Documents\jilu_app' testDebugUnitTest --console=plain
```

## 3. 装到手机

手机开「开发者选项 → USB 调试」，连上电脑后：

```powershell
& 'C:\Users\majinpeng\dev\tools\android-sdk\platform-tools\adb.exe' install -r 'C:\Users\majinpeng\Documents\jilu_app\app\build\outputs\apk\debug\app-debug.apk'
```

或者把 `app-debug.apk` 拷到手机里直接点安装（需要允许「安装未知来源应用」）。

首次启动会申请通知权限，用于「今天还没记录」的提醒。

## 4. 目录结构

```
app/src/main/java/com/jilu/attendance/
├── AppContainer.kt         # 手写依赖装配（db / dao / 设置 / 备份）
├── data/                   # Room 实体、DAO、数据库、DataStore 设置、背景图存储
├── domain/                 # 纯函数：日型解析、月度统计（有单元测试）
├── backup/                 # JSON 导出 / 导入
├── reminder/               # 闹钟调度、通知、开机重排
└── ui/                     # Compose：月历、当日面板、设置、背景图层
app/src/test/java/com/jilu/attendance/domain/   # 领域层单测
```

## 5. 已知边界

- **精确闹钟**：Android 12+ 需要「闹钟与提醒」权限，未授予时降级为近似提醒（可能偏差几分钟），不会崩。
- **国产 ROM 后台限制**：小米 / 华为 / OPPO 等可能杀后台导致提醒延迟，需在系统设置里给本应用「自启动 / 后台运行」白名单。
- **导入是整体替换**：不是合并，导入前会弹确认并显示将被删除的条数。
- **未来日期**只能改日型（预排节假日 / 补班），不能打考勤标记。
- 修改工作日集合后，**历史记录的颜色和统计不变**（记录里存了当时的日型快照）。
- **背景图**只保存在本机私有目录（`files/background.jpg`，长边压缩到 1600px），不进 JSON 备份；换机导入备份后背景会回到默认，需要重新选图。
- 背景图会盖一层 55% 白色遮罩，所以照片再花也不会影响日期数字的可读性；如果觉得太淡/太重，改 `ui/BackgroundLayer.kt` 里的 alpha 即可。
- **图片适配**两种方式：`完整显示`（默认，整张图都在，比例不符的留白用同一张图的模糊版铺底）与 `铺满裁切`（等比放大铺满屏幕，裁掉边角）。切换在设置页「背景 → 图片」下。

## 6. 排错

**选图报「设置背景失败」**。取图链路是：系统文档选择器 → 先把原始字节整个拷到 `cache/background_pick.tmp` → 再本地解码压缩。读取 Uri 时先试 `openInputStream`，失败自动退回 `openFileDescriptor`（国产 ROM 相册 provider 常见只给 fd）。错误提示会带具体原因：

| 提示 | 含义 | 处理 |
|---|---|---|
| 系统拒绝了读取这张图片的权限（SecurityException） | ROM 权限拦截 | 在文件管理器里先把图片复制到本机再选，或换一张 |
| 无法解码这张图片…（HEIC） | 设备不支持该格式 | 换 JPG/PNG |
| 无法打开所选图片：xxx | provider 打不开 | 换一张，或从「文件」里选 |
| 所选文件是空的 | 拿到的不是图片 | 重新选一张 |

**源码被加密、Gradle 编译报乱码/无法解析**。本机装了透明加密（DLP）软件，据观察到它会把 `.kt` 文件整体加密成 `TSD-Header` 开头的二进制块，Gradle 只能读到密文。处理办法：把 `C:\Users\majinpeng\Documents\jilu_app` 加入 DLP 白名单。检测命令：

```powershell
$b=[IO.File]::ReadAllBytes('...\SettingsScreen.kt'); [Text.Encoding]::ASCII.GetString($b[0..11])
```

出现 `%TSD-Header-##%` 即为被加密，需要重写该文件或找 IT 加白。
