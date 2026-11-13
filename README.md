# Remote Mix

Remote Mix 是一款安卓电视与电视盒子遥控 App。首页可管理多台设备；选择设备后，可在 ADB、蓝牙和红外三种控制方式之间切换。

## 功能

- **设备管理**：添加、修改、删除设备，分别保存连接地址、蓝牙设备和红外码组。
- **ADB 遥控**：搜索局域网中的设备，支持普通 TCP 调试和配对码无线调试；可读取目标设备的应用名称、图标与包名，点选应用即可打开。
- **蓝牙遥控**：搜索附近可见设备或让电视搜索手机，完成系统配对后以 Bluetooth HID 发送方向、确认、媒体等按键。切换 App 内页面时不会主动断开连接。
- **红外遥控**：按设备类型和品牌逐步测试电源及其他按键，确认有效后保存码组。手机需要自带红外发射器。
- **外观**：四套可保存的配色，遥控器按键使用图标和圆形方向盘。

## 下载与安装

从 [GitHub Releases](https://github.com/emcd39/remote-mix/releases) 下载最新 APK，在安卓手机上安装。当前自动发布的是 **Debug 测试包**。GitHub 托管构建机的调试签名可能随构建变化；如果新包无法覆盖安装旧包，需先卸载旧包，卸载会清除本机保存的设备设置。

首次使用时先在首页添加设备：

1. ADB：在电视上开启网络调试，使用“搜索”或手动填写 IP 与连接端口；无线调试配对码对应的是另一个配对端口。
2. 蓝牙：让电视进入蓝牙可见／配对模式，点击“连接”搜索；也可点击“配对”，让电视搜索这部手机。
3. 红外：确认手机有红外发射器，按向导测试并保存有效码组。

## 构建与自动发布

本地构建需要 JDK 17、Android SDK Platform 35 和 Build Tools 35.0.0。用 Android Studio 打开本目录，或运行 Gradle Wrapper 的 Java 入口：

```text
java -cp gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain :app:testDebugUnitTest :app:assembleDebug
```

推送到 `main` 或手动运行 [Build Android APK](.github/workflows/android-apk.yml) 后，GitHub Actions 会测试、编译，并把 APK 直接附到新的 [Releases](https://github.com/emcd39/remote-mix/releases) 测试版中。
