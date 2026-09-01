package com.github.premnirmal.ticker.network

/**
 * Decodes GB18030/GBK-encoded bytes into a [String].
 *
 * Tencent's quote endpoint (`qt.gtimg.cn`) still answers with GBK rather than UTF-8 — every numeric
 * field is ASCII, but the instrument names are Chinese and would otherwise be mojibake. Charset
 * support is the one part of that response that has no multiplatform stdlib equivalent, so it is an
 * `expect`/`actual`: Android goes through `java.nio.charset`, iOS through `NSString`.
 *
 * Implementations are best-effort: if the platform converter is unavailable they fall back to a
 * UTF-8 decode, which keeps every price correct and only mangles the name.
 */
expect fun decodeGb18030(bytes: ByteArray): String
