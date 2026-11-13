package com.remotemix.app

enum class RemoteAction(val label: String, val adbCode: Int, val irKey: String, val hidKey: Int, val hidConsumer: Int = 0) {
    POWER("电源", 26, "power", 0, 0x30),
    UP("上", 19, "up", 0x52), DOWN("下", 20, "down", 0x51),
    LEFT("左", 21, "left", 0x50), RIGHT("右", 22, "right", 0x4F),
    OK("确定", 23, "ok", 0x28), BACK("返回", 4, "back", 0x29),
    HOME("主页", 3, "home", 0, 0x223), MENU("菜单", 82, "menu", 0x65),
    VOLUME_UP("音量+", 24, "vol+", 0, 0xE9),
    VOLUME_DOWN("音量−", 25, "vol-", 0, 0xEA),
    MUTE("静音", 164, "mute", 0, 0xE2),
    CHANNEL_UP("频道+", 166, "ch+", 0, 0x9C),
    CHANNEL_DOWN("频道−", 167, "ch-", 0, 0x9D),
    PLAY_PAUSE("播放/暂停", 85, "play", 0, 0xCD),
    INPUT("信号源", 178, "input", 0, 0x183)
}

enum class ControlMode(val label: String) { ADB("ADB"), BLUETOOTH("蓝牙"), IR("红外") }
