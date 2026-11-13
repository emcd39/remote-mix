# 目标设备应用元数据查询程序

`RemoteAppCatalogProbe.java` 编译为 `remote-app-catalog.dex`，由 Remote Mix 通过已建立的 ADB 会话复制到目标设备的 `/data/local/tmp` 并用 `app_process` 运行。程序以目标设备的 `shell` 身份只读查询 Leanback 与普通 Launcher 活动，调用目标设备 `PackageManager` 获取显示名称和图标，将 64×64 PNG 图标编码为 Base64 返回。它不在目标设备安装 APK，也不上传这些数据到外部服务。

重建：

```powershell
./scripts/build_remote_app_catalog.ps1 -JavaHome 'E:\cache\remote-mix-android\jdk\jdk-17.0.21+9'
```

需要 Android SDK Platform 35、Build Tools 35.0.0 和 JDK 17。`app_process` 运行路径参考 [scrcpy 的服务端开发文档](https://github.com/Genymobile/scrcpy/blob/master/doc/develop.md)；显示名称与图标来自 Android `ResolveInfo.loadLabel/loadIcon`。不同厂商可能限制 shell 程序或隐藏 API；查询失败时 Remote Mix 会回退到原有启动入口列表。
