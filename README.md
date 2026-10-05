# Sine Wave Generator

一个用于生成正弦波并检测声音主频的 Android 应用。

An Android app for generating sine waves and detecting the dominant frequency of recorded audio.

## 功能 | Features

- 生成并播放 20–20,000 Hz 的正弦波，默认频率为 440 Hz。
- 通过输入或 1、10、100 Hz 步进调节频率；播放过程中也可实时调整。
- 使用麦克风和 FFT 实时估算环境声音的主频，并在静音时显示无有效读数。
- 切换到后台时自动停止播放和录音。
- 输出音频不需要权限；频率检测仅在用户启动检测时申请麦克风权限。

- Generate and play sine waves from 20 to 20,000 Hz, with a default frequency of 440 Hz.
- Set the frequency directly or adjust it in 1, 10, or 100 Hz steps, including during playback.
- Estimate the dominant frequency of microphone input in real time using an FFT.
- Automatically stop playback and recording when the app goes into the background.
- Audio output requires no permission. Microphone permission is requested only when detection is started.

## 运行 | Build and run

使用 Android Studio 打开项目，等待 Gradle 同步后运行 `app` 配置。项目最低支持 Android 8.0（API 26），使用 Android SDK API 36 编译。

Open the project in Android Studio, wait for Gradle sync, and run the `app` configuration. The minimum supported version is Android 8.0 (API 26); the project compiles against Android SDK API 36.

也可以在项目根目录构建 Debug APK：

You can also build a debug APK from the project root:

```powershell
./gradlew.bat assembleDebug
```

## 技术 | Technology

- Kotlin、Jetpack Compose、Material 3
- `AudioTrack` 以 44.1 kHz 单声道 PCM 流生成音频
- `AudioRecord` 采集麦克风输入；8192 点 FFT 用于频谱分析

- Kotlin, Jetpack Compose, and Material 3
- `AudioTrack` generates 44.1 kHz mono PCM audio
- `AudioRecord` captures microphone input; an 8192-point FFT analyzes its spectrum

## 检测精度说明 | Detection notes

检测结果是采样音频中幅值最大的频谱峰值，不保证等于复杂声音的基频。多个声源、噪声或谐波可能影响结果。8192 点 FFT 的频率分辨率约为 5.4 Hz，实际精度也受设备麦克风和录音处理影响。

The detected value is the strongest spectral peak in the captured audio and is not necessarily the fundamental frequency of a complex sound. Multiple sources, noise, or harmonics can affect the result. The 8192-point FFT has a bin spacing of about 5.4 Hz; actual accuracy also depends on the device microphone and recording pipeline.

## 隐私 | Privacy

本应用使用麦克风仅用于用户主动启动的频率检测。音频在设备上实时分析；项目不包含将录音上传至网络的功能。

The microphone is used only for frequency detection explicitly started by the user. Audio is analyzed on-device in real time; this project does not include functionality to upload recordings.

## 许可证 | License

本项目采用 MIT License，详见 [LICENSE](LICENSE)。公开发布前，请确认许可证中的版权归属行与实际权利人一致。

This project is licensed under the MIT License. See [LICENSE](LICENSE). Before publishing, make sure the copyright attribution in that file matches the actual rights holder.