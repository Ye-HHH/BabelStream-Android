# BabelStream Android 版本

实时语音识别与翻译 Android应用 - 基于阿里云DashScope API

## 📱 项目简介

这是BabelStream的Android移动版本,复现Windows桌面版的所有核心功能:
- ✅ 实时语音识别
- ✅ 多语言实时翻译
- ✅ 大字号字幕显示
- ✅ 简洁的设置界面

---

## 🎯 功能特性

### 核心功能
1. **实时语音识别** - 使用阿里云gummy-realtime-v1模型
2. **实时翻译** - 支持多语言互译
3. **音频采集** - 从麦克风录制PCM音频(16000Hz)
4. **字幕显示** - 大字号显示识别结果
5. **配置管理** - 保存API Key和用户偏好设置

### 界面功能
- **主界面**: 显示实时字幕和状态指示
- **设置界面**: 配置API Key、字体大小、翻译选项

---

## 📦 项目结构

```
BabelStream-Android/
├── app/
│   ├── src/main/
│   │   ├── java/com/babelstream/
│   │   │   ├── MainActivity.java          # 主界面
│   │   │   ├── SettingsActivity.java      # 设置界面
│   │   │   ├── ConfigManager.java         # 配置管理
│   │   │   ├── AudioCaptureManager.java   # 音频采集
│   │   │   └── RealtimeRecognizer.java    # 语音识别(待实现)
│   │   ├── res/
│   │   │   ├── layout/                    # 布局文件
│   │   │   └── values/                    # 资源文件
│   │   └── AndroidManifest.xml            # 权限配置
│   └── build.gradle                       # 应用级Gradle配置
├── build.gradle                           # 项目级Gradle配置
├── settings.gradle                        # Gradle设置
└── README.md                              # 本文件
```

---

## 🚀 快速开始

### 1. 环境要求

- **Android Studio**: Hedgehog (2023.1.1) 或更高版本
- **JDK**: Java 8 或更高
- **Android SDK**: API 24 (Android 7.0) 或更高
- **Gradle**: 8.0+

### 2. 导入项目

```bash
# 1. 克隆或复制项目到本地
cd /path/to/BabelStream-Android

# 2. 用Android Studio打开项目
# File -> Open -> 选择 BabelStream-Android 文件夹
```

### 3. 配置SDK

**重要**: 项目中的阿里云SDK依赖需要手动配置!

在 `app/build.gradle` 中,找到以下行:

```gradle
implementation 'com.alibaba.nls:nls-sdk-realtime:1.0.0'
```

替换为阿里云官方提供的真实SDK依赖。参考文档:
https://help.aliyun.com/zh/model-studio/real-time-java-sdk

### 4. 同步依赖

```bash
# Android Studio中点击:
Tools -> Gradle -> Sync Project with Gradle Files
```

### 5. 运行应用

1. 连接Android设备或启动模拟器
2. 点击 Run按钮(或按 Shift+F10)
3. 首次运行会请求麦克风权限
4. 在设置中输入你的阿里云API Key

---

## ⚙️ 配置说明

### API Key获取

1. 访问阿里云DashScope控制台: https://dashscope.console.aliyun.com/
2. 创建API Key
3. 在应用的设置界面中输入API Key

### 权限说明

应用需要以下权限:

| 权限 | 用途 |
|------|------|
| RECORD_AUDIO | 录制麦克风音频 |
| INTERNET | 连接阿里云API |
| ACCESS_NETWORK_STATE | 检查网络状态 |

---

## 🔨 待完成工作

由于这是快速原型,以下功能需要你继续开发:

### 高优先级

1. **RealtimeRecognizer.java** - 实现阿里云SDK集成
   - 参考文档: https://help.aliyun.com/zh/model-studio/real-time-java-sdk
   - 需要实现WebSocket连接和音频流发送
   - 处理识别结果回调

2. **MainActivity.java** - 主界面逻辑
   - 连接AudioCaptureManager和RealtimeRecognizer
   - 实时更新字幕TextView
   - 状态指示灯逻辑

3. **SettingsActivity.java** - 设置界面
   - API Key输入和保存
   - 字体大小选择
   - 翻译语言选择

4. **布局XML文件**
   - `activity_main.xml` - 主界面布局
   - `activity_settings.xml` - 设置界面布局

5. **资源文件**
   - `strings.xml` - 字符串资源
   - `colors.xml` - 颜色定义
   - `themes.xml` - 主题样式

### 中优先级

6. 权限动态申请逻辑 (Android 6.0+)
7. 错误处理和用户提示
8. 网络状态检测
9. 后台运行支持

### 低优先级

10. 悬浮窗模式
11. 历史记录保存
12. 更多语言支持
13. 界面美化

---

## 📚 开发参考

### 关键类说明

**ConfigManager.java**
- 功能: 使用SharedPreferences保存配置
- 方法: getApiKey(), setFontSize()等

**AudioCaptureManager.java**
- 功能: 使用AudioRecord录制音频
- 输出: PCM格式,16000Hz,单声道

**RealtimeRecognizer.java** (待实现)
- 功能: 封装阿里云SDK调用
- 接口: start(), stop(), sendAudio()

### 阿里云SDK使用示例

```java
// 初始化识别参数
TranslationRecognizerParam param = TranslationRecognizerParam.builder()
    .model("gummy-realtime-v1")
    .format("pcm")
    .sampleRate(16000)
    .transcriptionEnabled(true)
    .sourceLanguage("auto")
    .translationEnabled(true)
    .translationLanguages(new String[] {"zh"})
    .build();

// 创建识别器
TranslationRecognizerRealtime recognizer = new TranslationRecognizerRealtime();

// 启动识别
recognizer.call(param);

// 发送音频数据
recognizer.sendAudioFrame(audioData);

// 停止识别
recognizer.stop();
```

---

## 🐛 已知问题

1. **SDK依赖未配置** - 需要手动添加阿里云SDK
2. **核心类未实现** - RealtimeRecognizer需要完整实现
3. **界面未完成** - MainActivity和SettingsActivity需要补充逻辑
4. **布局文件缺失** - 所有XML布局文件需要创建

---

## 💡 开发建议

1. **先实现RealtimeRecognizer** - 这是核心功能
2. **测试音频采集** - 确保AudioCaptureManager正常工作
3. **简化界面** - 先实现最基本的功能,再优化UI
4. **参考Python版本** - 业务逻辑可以参考Windows版的实现

---

## 📞 技术支持

- **阿里云文档**: https://help.aliyun.com/zh/model-studio/
- **Android开发文档**: https://developer.android.com/docs
- **项目主页**: https://github.com/Ye-HHH/BabelStream

---

## 📝 版本历史

### v0.1.0 (2025-10-18) - 初始原型
- ✅ 项目结构创建
- ✅ Gradle配置完成
- ✅ AndroidManifest权限配置
- ✅ ConfigManager实现
- ✅ AudioCaptureManager实现
- ⏳ RealtimeRecognizer待实现
- ⏳ MainActivity待实现
- ⏳ SettingsActivity待实现
- ⏳ 布局文件待创建

---

**祝开发顺利! 🎉**

如有问题,请参考Windows版本的实现逻辑。
