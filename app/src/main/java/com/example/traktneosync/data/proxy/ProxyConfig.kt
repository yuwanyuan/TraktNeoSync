package com.example.traktneosync.data.proxy

enum class ProxyType(val label: String) {
    NONE("不使用代理"),
    HTTP("HTTP"),
    SOCKS5("SOCKS5")
}

data class ProxyConfig(
    val type: ProxyType = ProxyType.NONE,
    val host: String = "",
    val port: Int = 0,
    val username: String = "",
    val password: String = ""
) {
    val isEnabled: Boolean get() = type != ProxyType.NONE && host.isNotBlank() && port > 0

    val displayText: String
        get() = when (type) {
            ProxyType.NONE -> "未配置"
            else -> "${type.label} $host:$port"
        }
}
