# ADB 依赖源码

项目内固定了以下 Apache License 2.0 源码，避免依赖临时的 JitPack 构建。原始许可证分别保存在模块的 `LICENSE` 文件中。

| 模块 | 来源 | 固定版本 |
|---|---|---|
| `adb_kt` | https://github.com/rhythmcache/adb-kt | `394dea235b4bfb2ebbfba5a821bbe4f6cd59e460` |
| `spake2_kt` | https://github.com/rhythmcache/spake2-kt | `v1.0.2` / `68988da95a5d1550ba2c85c491938d2b5dfbbc4e` |

仅复制 `src/main` 源码与许可证，使用本工程的 Gradle 配置构建。两个模块的功能和许可独立于参考 APK。本地修改了 `adb_kt` 的 TLS 连接入口：要求传入配对时记录的设备证书 SHA-256 指纹，并在 TLS 握手时核对，避免把任意自签证书当成已配对电视。
