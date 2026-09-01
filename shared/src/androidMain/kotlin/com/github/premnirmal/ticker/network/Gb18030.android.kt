package com.github.premnirmal.ticker.network

import java.nio.charset.Charset

/**
 * Android actual for [decodeGb18030].
 *
 * GB18030 is the mandatory charset on Android (a superset of GBK that also covers the rarer
 * codepoints Tencent may return), and `Charset.forName` is guaranteed to resolve it.
 */
actual fun decodeGb18030(bytes: ByteArray): String = String(bytes, GB18030)

private val GB18030: Charset = Charset.forName("GB18030")
