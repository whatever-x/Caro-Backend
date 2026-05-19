package com.whatever.caro.common.web.filter

import jakarta.servlet.ReadListener
import jakarta.servlet.ServletInputStream
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletRequestWrapper
import org.springframework.util.StreamUtils
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.InputStreamReader

class CachedBodyHttpServletRequest(
    request: HttpServletRequest,
) : HttpServletRequestWrapper(request) {
    // 원본 request의 input stream은 해당 시점에서 소멸
    private val cachedBody: ByteArray = StreamUtils.copyToByteArray(request.inputStream)

    override fun getInputStream(): ServletInputStream = CachedServletInputStream(cachedBody)

    override fun getReader(): BufferedReader =
        BufferedReader(InputStreamReader(ByteArrayInputStream(cachedBody), Charsets.UTF_8))

    fun contentAsByteArray(): ByteArray = cachedBody.copyOf()
}

private class CachedServletInputStream(
    cachedBody: ByteArray,
) : ServletInputStream() {
    private val inputStream = ByteArrayInputStream(cachedBody)

    override fun read(): Int = inputStream.read()

    override fun isFinished(): Boolean = inputStream.available() == 0

    override fun isReady(): Boolean = true

    override fun setReadListener(
        listener: ReadListener?,
    ): Unit = throw UnsupportedOperationException()
}
