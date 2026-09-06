package com.whatever.caro.auth.internal.config

import com.whatever.caro.TestcontainersConfiguration
import com.whatever.caro.auth.internal.token.JwtTokenProvider
import com.whatever.caro.user.UserStatus
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.datatest.withData
import io.kotest.matchers.collections.shouldNotBeIn
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.HttpStatus
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

private sealed interface Access {
    data object PermitAll : Access

    data class HasAnyRole(
        val roles: Set<String>,
    ) : Access
}

private enum class Outcome {
    ALLOWED,
    UNAUTHORIZED,
    FORBIDDEN,
}

private data class Case(
    val path: String,
    val requester: Requester,
    val expected: Outcome,
)

private val SUSPENDED = UserStatus.SUSPENDED.name
private val ACTIVE = UserStatus.ACTIVE.name

// SecurityConfig.authorizeHttpRequests와 1:1로 대응
// 정책 수정 시 해당 map을 수정해 테스트를 진행 후 반영
private val API_POLICY: Map<String, Access> =
    PublicEndpoints.AUTH.associateWith { Access.PermitAll } +
        mapOf(
            "/auth/complete-registration" to Access.HasAnyRole(setOf(SUSPENDED)),
            "/nicknames/random" to Access.HasAnyRole(setOf(SUSPENDED, ACTIVE)),
            "/users/nicknames/check" to Access.HasAnyRole(setOf(SUSPENDED, ACTIVE)),
            "/auth/logout" to Access.HasAnyRole(setOf(SUSPENDED, ACTIVE)),
            "/decks" to Access.HasAnyRole(setOf(ACTIVE)),
        )

private sealed interface Requester {
    val label: String
    data object Anonymous : Requester {
        override val label = "anonymous"
    }
    data class Authenticated(
        val status: String,
    ) : Requester {
        override val label = status
    }
}

private val REQUESTERS: List<Requester> = listOf(
    Requester.Anonymous,
    Requester.Authenticated(SUSPENDED),
    Requester.Authenticated(ACTIVE),
    Requester.Authenticated("UNKNOWN"),
)

private val DENIED = setOf(HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN)

private fun expectOutcome(
    access: Access,
    requester: Requester,
): Outcome =
    when {
        access is Access.PermitAll -> Outcome.ALLOWED
        requester is Requester.Anonymous -> Outcome.UNAUTHORIZED
        (access is Access.HasAnyRole) && (requester is Requester.Authenticated) && (requester.status in access.roles) -> Outcome.ALLOWED
        else -> Outcome.FORBIDDEN
    }

private val TEST_CASES: List<Case> = API_POLICY.flatMap { (path, access) ->
    REQUESTERS.map { requester ->
        Case(
            path = path,
            requester = requester,
            expected = expectOutcome(access, requester),
        )
    }
}

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration::class)
class SecurityConfigAccessControlTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val jwtTokenProvider: JwtTokenProvider,
) : DescribeSpec({

    fun sendRequest(
        path: String,
        requester: Requester,
    ): HttpStatus {
        val result = mockMvc.get(path) {
            if (requester is Requester.Authenticated) {
                val token = jwtTokenProvider.generateAccessToken(
                    userId = 1L,
                    status = requester.status,
                ).token
                header("Authorization", "Bearer $token")
            }
        }.andReturn()
        return HttpStatus.valueOf(result.response.status)
    }

    describe("API 접근 제어 확인") {
        withData(
            nameFn = { "${it.requester.label} -> ${it.path} = ${it.expected}" },
            ts = TEST_CASES,
        ) { case ->
            val actual = sendRequest(case.path, case.requester)

            when (case.expected) {
                Outcome.UNAUTHORIZED -> actual shouldBe HttpStatus.UNAUTHORIZED
                Outcome.FORBIDDEN -> actual shouldBe HttpStatus.FORBIDDEN
                Outcome.ALLOWED -> actual shouldNotBeIn DENIED
            }
        }
    }
})
