package com.example.traktneosync.data.proxy

import java.net.InetSocketAddress
import java.net.Proxy
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProxyProvider @Inject constructor() {
    var config: ProxyConfig = ProxyConfig()

    fun createProxy(): Proxy? {
        if (!config.isEnabled) return null
        return Proxy(
            when (config.type) {
                ProxyType.HTTP -> Proxy.Type.HTTP
                ProxyType.SOCKS5 -> Proxy.Type.SOCKS
                ProxyType.NONE -> return null
            },
            InetSocketAddress.createUnresolved(config.host, config.port)
        )
    }

    fun hasAuth(): Boolean = config.username.isNotBlank()

    fun authHeader(): String? {
        if (!hasAuth()) return null
        return okhttp3.Credentials.basic(config.username, config.password)
    }
}
