# BabelStream (Android)

实时字幕与翻译（Android 版）。支持“系统音频（设备声音）”与“麦克风”两种采集路径，悬浮窗显示字幕，支持拖动、记忆位置与大小、右下角热区缩放、竖向调字号、右上角快捷按钮（返回应用/关闭悬浮窗并停止识别）。

本 README 覆盖真实实现与使用方法，便于直接构建和调试。

## 功能概览

- 实时识别与翻译：接入阿里云 Gummy Realtime（本地 AAR，见 `app/libs/nuisdk-release.aar`）
- 音频采集：
  - 系统音频（Android 10+，MediaProjection + AudioPlaybackCapture，带回退与兼容处理）
  - 麦克风（AudioRecord，多音源/采样率自适应并重采样）
- 悬浮窗字幕：
  - 右进左出“贴右裁切”显示，不回弹、不从头滚动
  - 两侧留白，非全屏宽
  - 右下角 64dp 热区缩放（横向改宽、纵向改字号，动态预览）
  - 位置/宽度/字号持久化，关闭再开保持上次状态
  - 顶角 18dp 按钮：设置（回到应用）、关闭（停止识别 + 关闭悬浮窗）
- 主界面：电平条、状态提示、两行（原文/译文）显示，可选显示模式

## 运行环境

- JDK 17
- Android Gradle Plugin 8.4.2（见根 `build.gradle`）
- compileSdk/targetSdk 34，minSdk 24
- Gradle Wrapper 已内置

## 构建

命令行：

```
./gradlew :app:assembleDebug
```

或使用 Android Studio 直接打开根目录并构建。

如首次构建提示网络问题，可先本地构建离线部分，再在联网环境同步依赖。

## 权限与前台服务

AndroidManifest 声明了：

- `RECORD_AUDIO`、`MODIFY_AUDIO_SETTINGS`
- `POST_NOTIFICATIONS`（Android 13+）
- `SYSTEM_ALERT_WINDOW`（悬浮窗）
- `FOREGROUND_SERVICE*`（含 mediaProjection、microphone、dataSync）

首次运行会按需申请麦克风/通知权限。使用悬浮窗前需授予“在其他应用上层显示”。

## 首次使用

1) 在“设置”页填写 API Key，选择音频输入源（系统音频/麦克风），可选目标语言/显示模式/样式参数。

2) 选择“系统音频”时注意：
- 仅 Android 10+ 支持
- 授权弹窗务必勾选“包含音频/设备声音”（不同机型文案不同）
- 只有允许“播放捕获”的 App 才能被抓到（部分音乐/通话应用会禁止）

3) 返回主界面，点击“开始”，或在设置页直接“开启悬浮识别”。

## 悬浮窗交互

- 拖动移动：按住任意空白处拖动
- 右下角缩放：右下角约 64dp×64dp 热区按住拖动
  - 横向拖：改变宽度，保持左右留白
  - 纵向拖：同步增减两行字号（译文权重大，转写按 0.6 倍变化）
- 顶角按钮：
  - 扳手：返回应用
  - 关闭：停止识别并关闭悬浮窗
- 记忆：位置/宽度/字号在松手时持久化，重新打开还原

## 目录与关键代码

- `app/src/main/java/com/babelstream/`
  - `MainActivity.java` 主界面；接收识别结果与电平，UI 控件
  - `OverlayService.java` 悬浮窗服务；拖动/缩放/记忆/顶角按钮逻辑
  - `RecognitionService.java` 前台识别服务；系统音频/麦克风采集、对接 Gummy SDK、广播文本/电平/状态
  - `PlaybackCaptureManager.java` 系统音频采集；立体声→单声道回退、重采样
  - `AudioCaptureManager.java` 麦克风采集；多音源/采样率自适应、重采样
  - `ConfigManager.java` 偏好存储；含 API Key/显示模式/样式 与 悬浮窗位置/宽度/字号
- `app/src/main/res/layout/`
  - `overlay_subtitle.xml` 悬浮窗布局（顶右角按钮 + 底右角三角 + 两个 HorizontalScrollView）
  - `activity_main.xml` 主界面布局

## 常见问题（系统音频）

- 电平始终 0：
  - 授权时未勾选“包含音频”（重新授权一次）
  - 播放的 App 不允许“播放捕获”（更换 App 测试）
  - 媒体音量为 0、静音/勿扰模式、蓝牙路由等
  - 厂商系统限制（“屏幕录制”设置里选择“系统声音”）
- 初始化失败：PlaybackCaptureManager 已内置回退到单声道；仍失败请提供日志

相关日志 TAG：`PlaybackCapture`、`RecognitionService`、`AudioCaptureManager`

## 开发小贴士

- 修改滚动/贴右裁切逻辑：`MainActivity#stickToRight`、`OverlayService#stickToRight`
- 调整热区大小/灵敏度：`OverlayService` 中 `edge` 与竖向字号 `deltaPx` 系数
- 默认留白/最小宽度/字号上下限可在 `OverlayService` 与 `ConfigManager` 中调整

## 构建产物

- Debug APK：`app/build/outputs/apk/debug/app-debug.apk`

## 许可与第三方

- 引用的阿里云 Gummy Android SDK（AAR）位于 `app/libs`，使用前请确保已有合法使用权与密钥。

