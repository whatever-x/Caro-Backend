package com.whatever.caro.common.web.idempotency

import com.whatever.caro.auth.AuthUser
import com.whatever.caro.auth.SecurityUtil
import com.whatever.caro.common.exception.BusinessException
import com.whatever.caro.common.response.CommonErrorCode.IDEMPOTENCY_KEY_CONFLICT
import com.whatever.caro.common.response.CommonErrorCode.IDEMPOTENCY_REQUEST_IN_PROGRESS
import com.whatever.caro.common.response.CommonErrorCode.INVALID_IDEMPOTENCY_KEY
import com.whatever.caro.common.response.CommonErrorCode.MISSING_HEADER
import com.whatever.caro.common.web.filter.CachedBodyHttpServletRequest
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldContainOnly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import org.slf4j.MDC
import org.springframework.http.MediaType
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.method.HandlerMethod
import org.springframework.web.util.ContentCachingResponseWrapper
import java.time.Duration
import java.util.UUID

class IdempotencyInterceptorUnitTest :
    DescribeSpec({

        val idempotencyRepository = mockk<IdempotencyRepository>(relaxUnitFun = true)
        val properties = IdempotencyProperties(
            responseTtl = Duration.ofHours(24),
            processingTtl = Duration.ofSeconds(30),
        )
        val interceptor = IdempotencyInterceptor(idempotencyRepository, properties)

        mockkObject(SecurityUtil)

        afterSpec {
            unmockkObject(SecurityUtil)
        }

        afterEach {
            clearAllMocks(answers = false)
            SecurityContextHolder.clearContext()
            MDC.clear()
        }

        fun setupSecurityContext(
            userId: Long = 1L,
        ) {
            val authUser = AuthUser(
                userId = userId,
                jti = "test-jti",
                status = "ACTIVE",
            )
            val auth = UsernamePasswordAuthenticationToken(authUser, null, emptyList())
            SecurityContextHolder.getContext().authentication = auth
            every { SecurityUtil.currentUser() } returns authUser
        }

        fun cachedRequest(
            method: String = "POST",
            uri: String = "/api/v1/test",
            query: String? = null,
            body: ByteArray = ByteArray(0),
            idempotencyKey: String? = UUID.randomUUID().toString(),
        ): CachedBodyHttpServletRequest {
            val mockHttpServletRequest = MockHttpServletRequest(method, uri).apply {
                queryString = query
                setContent(body)
                idempotencyKey?.let { addHeader("Idempotency-Key", it) }
            }
            return CachedBodyHttpServletRequest(mockHttpServletRequest)
        }

        fun wrappedResponse(): Pair<MockHttpServletResponse, ContentCachingResponseWrapper> {
            val mock = MockHttpServletResponse()
            val wrapped = ContentCachingResponseWrapper(mock)
            return mock to wrapped
        }

        fun handlerMethod(
            cacheInternalErrorResponse: Boolean = false,
        ): HandlerMethod {
            val handler = mockk<HandlerMethod>()
            val annotation = mockk<Idempotent>()
            every { annotation.cacheInternalErrorResponse } returns cacheInternalErrorResponse
            every { handler.getMethodAnnotation(Idempotent::class.java) } returns annotation
            return handler
        }

        fun primeHash(
            request: CachedBodyHttpServletRequest,
            handler: HandlerMethod,
        ): String {
            every { idempotencyRepository.getCachedResponse(any()) } returns null
            every { idempotencyRepository.acquireIdempotencyProcessing(any(), any()) } returns true
            interceptor.preHandle(request, MockHttpServletResponse(), handler)
            return request.getAttribute("caro.idempotency.hash") as String
        }

        describe("preHandle()") {
            context("Idempotency-Key 헤더가 없을 때") {
                it("BusinessException(MISSING_HEADER)을 던지고, args에 헤더 이름이 담긴다") {
                    setupSecurityContext()
                    val request = cachedRequest(idempotencyKey = null)
                    val response = MockHttpServletResponse()
                    val handler = handlerMethod()

                    val exception = shouldThrow<BusinessException> {
                        interceptor.preHandle(request, response, handler)
                    }
                    exception.errorCode shouldBe MISSING_HEADER
                    exception.args.shouldContainOnly("Idempotency-Key")
                }
            }

            context("Idempotency-Key 헤더가 UUID 형식이 아닐 때") {
                it("BusinessException(INVALID_IDEMPOTENCY_KEY)을 던진다") {
                    setupSecurityContext()
                    val request = cachedRequest(idempotencyKey = "not-a-uuid")
                    val response = MockHttpServletResponse()
                    val handler = handlerMethod()

                    val exception = shouldThrow<BusinessException> {
                        interceptor.preHandle(request, response, handler)
                    }
                    exception.errorCode shouldBe INVALID_IDEMPOTENCY_KEY
                }
            }

            context("동일한 key와 hash의 캐시 응답이 있을 때") {
                it("캐시 본문/상태/contentType을 응답에 세팅하고 false를 리턴한다") {
                    setupSecurityContext()
                    val key = UUID.randomUUID().toString()
                    val bodyText = "cached body"
                    val body = bodyText.toByteArray()
                    val handler = handlerMethod()
                    val hash = primeHash(
                        request = cachedRequest(idempotencyKey = key, body = body),
                        handler = handler,
                    )
                    every { idempotencyRepository.getCachedResponse(any()) } returns IdempotencyEntry(
                        hash = hash,
                        statusCode = 200,
                        contentType = "application/json",
                        body = bodyText,
                    )

                    val request = cachedRequest(idempotencyKey = key, body = body)
                    val response = MockHttpServletResponse()

                    val result = interceptor.preHandle(request, response, handler)

                    result shouldBe false
                    response.status shouldBe 200
                    response.contentType?.startsWith("application/json") shouldBe true
                    response.contentAsString shouldBe bodyText
                }
            }

            context("동일한 key지만 다른 hash의 캐시 응답이 있을 때") {
                it("BusinessException(IDEMPOTENCY_KEY_CONFLICT)을 던진다") {
                    setupSecurityContext()
                    val key = UUID.randomUUID().toString()
                    val request = cachedRequest(idempotencyKey = key)
                    val response = MockHttpServletResponse()
                    val handler = handlerMethod()

                    every { idempotencyRepository.getCachedResponse(any()) } returns IdempotencyEntry(
                        hash = "different-hash-value",
                        statusCode = 200,
                        contentType = "application/json",
                        body = "cached body",
                    )

                    val exception = shouldThrow<BusinessException> {
                        interceptor.preHandle(request, response, handler)
                    }
                    exception.errorCode shouldBe IDEMPOTENCY_KEY_CONFLICT
                }
            }

            context("동일한 key와 body지만, query string이 다를 때") {
                it("hash 불일치로 BusinessException(IDEMPOTENCY_KEY_CONFLICT)을 던진다") {
                    setupSecurityContext()
                    val key = UUID.randomUUID().toString()
                    val sharedBody = "{}".toByteArray()

                    // given: 첫 번째 요청 진행
                    val request1 = cachedRequest(idempotencyKey = key, query = "page=1", body = sharedBody)
                    val response1 = MockHttpServletResponse()
                    val handler = handlerMethod()
                    every { idempotencyRepository.getCachedResponse(any()) } returns null
                    every { idempotencyRepository.acquireIdempotencyProcessing(any(), any()) } returns true
                    interceptor.preHandle(request1, response1, handler)

                    // 두 번재 요청에서 사용될 스터빙 진행
                    val storedHash = request1.getAttribute("caro.idempotency.hash") as String
                    every { idempotencyRepository.getCachedResponse(any()) } returns IdempotencyEntry(
                        hash = storedHash,
                        statusCode = 200,
                        contentType = "application/json",
                        body = "result",
                    )

                    // when & then: 두 번째 요청 진행
                    val request2 = cachedRequest(idempotencyKey = key, query = "page=2", body = sharedBody)
                    val response2 = MockHttpServletResponse()
                    val exception = shouldThrow<BusinessException> {
                        interceptor.preHandle(request2, response2, handler)
                    }
                    exception.errorCode shouldBe IDEMPOTENCY_KEY_CONFLICT
                }
            }

            context("동일 key와 body, query지만 path 끝의 trailing slash('/')만 다를 때") {
                it("사전 정규화로 hash가 동일하기 때문에 캐시 응답이 재생된다") {
                    setupSecurityContext()
                    val key = UUID.randomUUID().toString()
                    val bodyText = "body"
                    val sharedBody = bodyText.toByteArray()

                    // given: 첫 번째 요청 진행
                    val request1 = cachedRequest(uri = "/api/v1/test", idempotencyKey = key, body = sharedBody)
                    val response1 = MockHttpServletResponse()
                    val handler = handlerMethod()
                    every { idempotencyRepository.getCachedResponse(any()) } returns null
                    every { idempotencyRepository.acquireIdempotencyProcessing(any(), any()) } returns true
                    interceptor.preHandle(request1, response1, handler)

                    // 두 번째 요청에서 사용될 스터빙 진행
                    val storedHash = request1.getAttribute("caro.idempotency.hash") as String
                    every { idempotencyRepository.getCachedResponse(any()) } returns IdempotencyEntry(
                        hash = storedHash,
                        statusCode = 200,
                        contentType = "application/json",
                        body = bodyText,
                    )

                    // when: 두 번째 요청 진행
                    val request2 = cachedRequest(uri = "/api/v1/test/", idempotencyKey = key, body = sharedBody)
                    val response2 = MockHttpServletResponse()
                    val result = interceptor.preHandle(request2, response2, handler)

                    // then
                    result shouldBe false
                    response2.status shouldBe 200
                    response2.contentAsString shouldBe bodyText
                }
            }

            context("캐시가 없고 acquire이 진행중일 때 새로운 요청이 들어온다면") {
                it("BusinessException(IDEMPOTENCY_REQUEST_IN_PROGRESS)을 던진다") {
                    setupSecurityContext()
                    val request = cachedRequest()
                    val response = MockHttpServletResponse()
                    val handler = handlerMethod()

                    every { idempotencyRepository.getCachedResponse(any()) } returns null
                    every { idempotencyRepository.acquireIdempotencyProcessing(any(), any()) } returns false

                    val exception = shouldThrow<BusinessException> {
                        interceptor.preHandle(request, response, handler)
                    }
                    exception.errorCode shouldBe IDEMPOTENCY_REQUEST_IN_PROGRESS
                }
            }

            context("처음 들어온 요청이라면") {
                it("request에 redisKey/hash/cacheInternalErrorResponse attribute를 설정하고 true를 리턴한다") {
                    val userId = 1L
                    setupSecurityContext(userId = userId)
                    val key = UUID.randomUUID().toString()
                    val request = cachedRequest(idempotencyKey = key)
                    val response = MockHttpServletResponse()
                    val handler = handlerMethod(cacheInternalErrorResponse = false)

                    every { idempotencyRepository.getCachedResponse(any()) } returns null
                    every { idempotencyRepository.acquireIdempotencyProcessing(any(), any()) } returns true

                    val result = interceptor.preHandle(request, response, handler)
                    val expectedRedisKey = "idempotency:$userId:$key"

                    result shouldBe true
                    request.getAttribute("caro.idempotency.redisKey") shouldBe expectedRedisKey
                    request.getAttribute("caro.idempotency.hash") shouldNotBe null
                    request.getAttribute("caro.idempotency.hash") shouldNotBe null
                    request.getAttribute("caro.idempotency.cacheInternalErrorResponse") shouldBe false

                    verify { idempotencyRepository.acquireIdempotencyProcessing(expectedRedisKey, any()) }
                }
            }
        }

        describe("afterCompletion()") {
            val defaultRedisKey = "idempotency:1:test-key"
            val defaultHash = "test-hash"

            fun requestWith(
                redisKey: String = defaultRedisKey,
                hash: String? = defaultHash,
                cacheInternalErrorResponse: Boolean = false,
            ): MockHttpServletRequest =
                MockHttpServletRequest().apply {
                    setAttribute("caro.idempotency.redisKey", redisKey)
                    hash?.let { setAttribute("caro.idempotency.hash", it) }
                    setAttribute("caro.idempotency.cacheInternalErrorResponse", cacheInternalErrorResponse)
                }

            fun wrappedResponseWith(
                status: Int = 200,
                contentType: String? = null,
                body: ByteArray? = null,
            ): ContentCachingResponseWrapper {
                val wrapped = ContentCachingResponseWrapper(MockHttpServletResponse())
                wrapped.status = status
                contentType?.let { wrapped.contentType = it }
                body?.let { wrapped.outputStream.write(it) }
                return wrapped
            }

            context("redisKey/hash attribute가 없을 때") {
                it("redisKey가 없으면 response 캐싱을 진행하지 않는다") {
                    val request = MockHttpServletRequest()

                    interceptor.afterCompletion(request, wrappedResponseWith(), handlerMethod(), null)

                    verify(exactly = 0) { idempotencyRepository.saveResponse(any(), any(), any(), any(), any(), any()) }
                    verify(exactly = 0) { idempotencyRepository.deleteIdempotencyProcessing(any()) }
                }

                it("redisKey는 있지만 hash가 없으면 response 캐싱을 진행하지 않는다") {
                    val request = MockHttpServletRequest().apply {
                        setAttribute("caro.idempotency.redisKey", defaultRedisKey)
                    }

                    interceptor.afterCompletion(request, wrappedResponseWith(), handlerMethod(), null)

                    verify(exactly = 0) { idempotencyRepository.saveResponse(any(), any(), any(), any(), any(), any()) }
                    verify(exactly = 0) { idempotencyRepository.deleteIdempotencyProcessing(any()) }
                }
            }

            context("5xx대 응답이고 @Idempotent를 cacheInternalErrorResponse=false로 설정했다면") {
                it("processing 마커를 제거하고 saveResponse는 호출하지 않는다") {
                    val request = requestWith()
                    val wrapped = wrappedResponseWith(status = 500)

                    interceptor.afterCompletion(request, wrapped, handlerMethod(), null)

                    verify { idempotencyRepository.deleteIdempotencyProcessing(defaultRedisKey) }
                    verify(exactly = 0) { idempotencyRepository.saveResponse(any(), any(), any(), any(), any(), any()) }
                }
            }

            context("exception handler에서 처리되지 않은 예외가 있고 cacheInternalErrorResponse=false로 설정했다면") {
                it("processing 마커를 제거하고 saveResponse는 호출하지 않는다") {
                    val request = requestWith()
                    val wrapped = wrappedResponseWith(status = 200)

                    interceptor.afterCompletion(request, wrapped, handlerMethod(), RuntimeException("error"))

                    verify { idempotencyRepository.deleteIdempotencyProcessing(defaultRedisKey) }
                    verify(exactly = 0) { idempotencyRepository.saveResponse(any(), any(), any(), any(), any(), any()) }
                }
            }

            context("5xx대 응답이고 cacheInternalErrorResponse=true로 설정했다면") {
                it("saveResponse를 호출해 응답을 캐싱한다") {
                    val bodyText = "error response"
                    val request = requestWith(cacheInternalErrorResponse = true)
                    val wrapped = wrappedResponseWith(
                        status = 500,
                        contentType = "application/json",
                        body = bodyText.toByteArray(),
                    )

                    interceptor.afterCompletion(request, wrapped, handlerMethod(), null)

                    verify {
                        idempotencyRepository.saveResponse(
                            key = defaultRedisKey,
                            hash = defaultHash,
                            statusCode = 500,
                            contentType = "application/json",
                            body = bodyText,
                            expiresIn = any(),
                        )
                    }
                }
            }

            context("정상(2xx) 응답일 경우") {
                it("saveResponse를 호출해 응답을 캐싱한다") {
                    val bodyText = "success response"
                    val request = requestWith()
                    val wrapped = wrappedResponseWith(
                        status = 200,
                        contentType = "application/json",
                        body = bodyText.toByteArray(),
                    )

                    interceptor.afterCompletion(request, wrapped, handlerMethod(), null)

                    verify {
                        idempotencyRepository.saveResponse(
                            key = defaultRedisKey,
                            hash = defaultHash,
                            statusCode = 200,
                            contentType = "application/json",
                            body = bodyText,
                            expiresIn = any(),
                        )
                    }
                }
            }

            context("정상 응답 response.contentType이 null일 때") {
                it("saveResponse 호출 시 MediaType.APPLICATION_JSON_VALUE가 사용된다") {
                    val request = requestWith()
                    val wrapped = wrappedResponseWith(
                        status = 200,
                        body = "{}".toByteArray(),
                    )

                    interceptor.afterCompletion(request, wrapped, handlerMethod(), null)

                    verify {
                        idempotencyRepository.saveResponse(
                            any(),
                            any(),
                            any(),
                            MediaType.APPLICATION_JSON_VALUE,
                            any(),
                            any(),
                        )
                    }
                }
            }

            context("ContentCachingResponseWrapper로 wrap되지 않은 응답일 경우") {
                it("응답을 캐싱하지 않으며 processing 마커를 제거한다") {
                    val request = requestWith()
                    val plainResponse = MockHttpServletResponse().apply { status = 200 }

                    interceptor.afterCompletion(request, plainResponse, handlerMethod(), null)

                    verify(exactly = 0) { idempotencyRepository.saveResponse(any(), any(), any(), any(), any(), any()) }
                    verify { idempotencyRepository.deleteIdempotencyProcessing(defaultRedisKey) }
                }
            }

            context("saveResponse에서 예외가 발생할 때") {
                it("응답을 캐싱하지 않으며 processing 마커를 제거한다") {
                    val request = requestWith()
                    val wrapped = wrappedResponseWith()

                    every {
                        idempotencyRepository.saveResponse(any(), any(), any(), any(), any(), any())
                    } throws RuntimeException("redis down")

                    shouldThrow<RuntimeException> {
                        interceptor.afterCompletion(request, wrapped, handlerMethod(), null)
                    }

                    verify { idempotencyRepository.deleteIdempotencyProcessing(defaultRedisKey) }
                }
            }
        }
    })
