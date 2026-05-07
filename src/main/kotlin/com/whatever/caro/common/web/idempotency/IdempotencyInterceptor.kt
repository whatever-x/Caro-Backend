package com.whatever.caro.common.web.idempotency

import com.whatever.caro.auth.SecurityUtil
import com.whatever.caro.common.exception.BusinessException
import com.whatever.caro.common.response.CommonErrorCode.IDEMPOTENCY_KEY_CONFLICT
import com.whatever.caro.common.response.CommonErrorCode.IDEMPOTENCY_REQUEST_IN_PROGRESS
import com.whatever.caro.common.response.CommonErrorCode.INVALID_IDEMPOTENCY_KEY
import com.whatever.caro.common.response.CommonErrorCode.MISSING_HEADER
import com.whatever.caro.common.web.filter.CachedBodyHttpServletRequest
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.method.HandlerMethod
import org.springframework.web.servlet.HandlerInterceptor
import org.springframework.web.util.ContentCachingResponseWrapper
import org.springframework.web.util.WebUtils
import java.security.MessageDigest
import java.util.UUID

private val logger = KotlinLogging.logger {}

@Component
class IdempotencyInterceptor(
    private val repository: IdempotencyRepository,
    private val properties: IdempotencyProperties,
) : HandlerInterceptor {
    override fun preHandle(
        request: HttpServletRequest,
        response: HttpServletResponse,
        handler: Any,
    ): Boolean {
        val handlerMethod = handler as? HandlerMethod ?: return true
        val annotation = handlerMethod.getMethodAnnotation(Idempotent::class.java) ?: return true

        val key = request.getHeader("Idempotency-Key")
            ?: throw BusinessException(MISSING_HEADER, arrayOf("Idempotency-Key"))
        if (!isValidKey(key)) {
            throw BusinessException(INVALID_IDEMPOTENCY_KEY)
        }

        val userId = SecurityUtil.currentUser().userId
        val redisKey = "idempotency:$userId:$key"

        val cachedRequest = WebUtils.getNativeRequest(request, CachedBodyHttpServletRequest::class.java)
            ?: throw IllegalStateException(
                "RequestResponseCachingFilter must be registered before IdempotencyInterceptor",
            )

        val path = cachedRequest.requestURI.trimEnd('/').ifEmpty { "/" }
        val query = cachedRequest.queryString ?: ""
        val newHash = computeHash(
            cachedRequest.method,
            "$path?$query",
            cachedRequest.contentAsByteArray(),
        )

        repository.getCachedResponse(redisKey)?.let { cachedEntry ->
            if (cachedEntry.hash != newHash) {
                throw BusinessException(IDEMPOTENCY_KEY_CONFLICT)
            }
            response.status = cachedEntry.statusCode
            response.contentType = cachedEntry.contentType
            response.characterEncoding = Charsets.UTF_8.name()
            response.writer.write(cachedEntry.body)

            return false
        }

        if (!repository.acquireIdempotencyProcessing(redisKey, properties.processingTtl)) { // 이미 진행중일 경우
            throw BusinessException(IDEMPOTENCY_REQUEST_IN_PROGRESS)
        }

        cachedRequest.setAttribute(ATTR_REDIS_KEY, redisKey)
        cachedRequest.setAttribute(ATTR_HASH, newHash)
        cachedRequest.setAttribute(ATTR_CACHE_INTERNAL_ERROR_RESPONSE, annotation.cacheInternalErrorResponse)
        return true
    }

    override fun afterCompletion(
        request: HttpServletRequest,
        response: HttpServletResponse,
        handler: Any,
        ex: Exception?,
    ) {
        val redisKey = request.getAttribute(ATTR_REDIS_KEY) as? String ?: return
        val hash = request.getAttribute(ATTR_HASH) as? String ?: return
        val cacheInternalErrorResponse = request.getAttribute(ATTR_CACHE_INTERNAL_ERROR_RESPONSE) as? Boolean ?: false

        val isInternalError = response.status in 500..599 || ex != null
        if (!cacheInternalErrorResponse && isInternalError) {
            runCatching { repository.deleteIdempotencyProcessing(redisKey) }
            logger.warn {
                "Bypassing response cache: status=${response.status}, ex=${ex?.javaClass?.simpleName}, key=$redisKey"
            }
            return
        }

        var isSaved = false
        try {
            val response = WebUtils.getNativeResponse(
                response,
                ContentCachingResponseWrapper::class.java,
            ) ?: return
            val body = response.contentAsByteArray.toString(Charsets.UTF_8)

            repository.saveResponse(
                redisKey,
                hash,
                response.status,
                response.contentType ?: MediaType.APPLICATION_JSON_VALUE,
                body,
                properties.responseTtl,
            )
            isSaved = true
        } finally {
            if (!isSaved) { // 캐싱 과정에서 예외 발생 시 processing key 제거
                runCatching { repository.deleteIdempotencyProcessing(redisKey) }
                logger.warn { "Idempotency response cache skipped. Processing key released. key: $redisKey" }
            }
        }
    }

    private fun isValidKey(
        key: String,
    ): Boolean {
        try {
            UUID.fromString(key)
            return true
        } catch (e: IllegalArgumentException) {
            return false
        }
    }

    private fun computeHash(
        method: String,
        path: String,
        body: ByteArray,
    ): String {
        val md = MessageDigest.getInstance("SHA-256").apply {
            update(method.toByteArray())
            update(ASCII_US)
            update(path.toByteArray())
            update(ASCII_US)
            update(body)
        }
        return md.digest().toHexString()
    }

    companion object {
        private const val ATTR_REDIS_KEY = "caro.idempotency.redisKey"
        private const val ATTR_HASH = "caro.idempotency.hash"
        private const val ATTR_CACHE_INTERNAL_ERROR_RESPONSE = "caro.idempotency.cacheInternalErrorResponse"
        private const val ASCII_US: Byte = 0x1F
    }
}
