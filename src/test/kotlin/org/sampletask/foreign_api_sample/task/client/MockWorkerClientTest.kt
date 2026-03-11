package org.sampletask.foreign_api_sample.task.client

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import io.github.resilience4j.bulkhead.BulkheadConfig
import io.github.resilience4j.bulkhead.BulkheadRegistry
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import io.github.resilience4j.ratelimiter.RateLimiterConfig
import io.github.resilience4j.ratelimiter.RateLimiterRegistry
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.sampletask.foreign_api_sample.task.domain.RecoveryAction
import org.sampletask.foreign_api_sample.task.exception.MockWorkerException
import org.springframework.web.reactive.function.client.WebClient

class MockWorkerClientTest {
	private lateinit var wireMockServer: WireMockServer
	private lateinit var mockWorkerClient: MockWorkerClient
	private lateinit var apiKeyManager: ApiKeyManager

	@BeforeEach
	fun setUp() {
		wireMockServer = WireMockServer(wireMockConfig().dynamicPort())
		wireMockServer.start()

		val webClient = WebClient.builder().baseUrl(wireMockServer.baseUrl()).build()
		apiKeyManager = ApiKeyManager(webClient, "test-candidate", "test@example.com")

		wireMockServer.stubFor(
			post(urlEqualTo("/mock/auth/issue-key"))
				.willReturn(
					aResponse()
						.withStatus(200)
						.withHeader("Content-Type", "application/json")
						.withBody("""{"apiKey": "test-key"}"""),
				),
		)

		val cbRegistry = CircuitBreakerRegistry.of(CircuitBreakerConfig.ofDefaults())
		val rlRegistry = RateLimiterRegistry.of(RateLimiterConfig.ofDefaults())
		val bhRegistry = BulkheadRegistry.of(BulkheadConfig.ofDefaults())

		mockWorkerClient = MockWorkerClient(webClient, apiKeyManager, cbRegistry, rlRegistry, bhRegistry)
	}

	@AfterEach
	fun tearDown() {
		wireMockServer.stop()
	}

	@Test
	fun `submitProcess_성공`() {
		runTest {
			wireMockServer.stubFor(
				post(urlEqualTo("/mock/process"))
					.willReturn(
						aResponse()
							.withStatus(200)
							.withHeader("Content-Type", "application/json")
							.withBody("""{"jobId": "job-123"}"""),
					),
			)

			val response = mockWorkerClient.submitProcess("https://example.com/image.png")

			assertThat(response.jobId).isEqualTo("job-123")
		}
	}

	@Test
	fun `getJobStatus_성공`() {
		runTest {
			wireMockServer.stubFor(
				get(urlEqualTo("/mock/process/job-123"))
					.willReturn(
						aResponse()
							.withStatus(200)
							.withHeader("Content-Type", "application/json")
							.withBody("""{"jobId": "job-123", "status": "COMPLETED", "result": "processed"}"""),
					),
			)

			val response = mockWorkerClient.getJobStatus("job-123")

			assertThat(response.status).isEqualTo("COMPLETED")
			assertThat(response.result).isEqualTo("processed")
		}
	}

	@Nested
	@Suppress("ClassName")
	inner class 복구액션매핑 {

		@Test
		fun `500_에러_시_RETRY`() {
			wireMockServer.stubFor(
				post(urlEqualTo("/mock/process"))
					.willReturn(aResponse().withStatus(500).withBody("Internal Server Error")),
			)

			assertThatThrownBy {
				kotlinx.coroutines.runBlocking { mockWorkerClient.submitProcess("https://example.com/image.png") }
			}
				.isInstanceOf(MockWorkerException::class.java)
				.satisfies({
					val ex = it as MockWorkerException
					assertThat(ex.recoveryAction).isEqualTo(RecoveryAction.RETRY)
					assertThat(ex.upstreamHttpStatus).isEqualTo(500)
				})
		}

		@Test
		fun `429_에러_시_RETRY`() {
			wireMockServer.stubFor(
				post(urlEqualTo("/mock/process"))
					.willReturn(aResponse().withStatus(429).withBody("Too Many Requests")),
			)

			assertThatThrownBy {
				kotlinx.coroutines.runBlocking { mockWorkerClient.submitProcess("https://example.com/image.png") }
			}
				.isInstanceOf(MockWorkerException::class.java)
				.satisfies({
					val ex = it as MockWorkerException
					assertThat(ex.recoveryAction).isEqualTo(RecoveryAction.RETRY)
					assertThat(ex.upstreamHttpStatus).isEqualTo(429)
				})
		}

		@Test
		fun `503_에러_시_RETRY`() {
			wireMockServer.stubFor(
				post(urlEqualTo("/mock/process"))
					.willReturn(aResponse().withStatus(503).withBody("Service Unavailable")),
			)

			assertThatThrownBy {
				kotlinx.coroutines.runBlocking { mockWorkerClient.submitProcess("https://example.com/image.png") }
			}
				.isInstanceOf(MockWorkerException::class.java)
				.satisfies({
					val ex = it as MockWorkerException
					assertThat(ex.recoveryAction).isEqualTo(RecoveryAction.RETRY)
					assertThat(ex.upstreamHttpStatus).isEqualTo(503)
				})
		}

		@Test
		fun `400_에러_시_FAIL`() {
			wireMockServer.stubFor(
				post(urlEqualTo("/mock/process"))
					.willReturn(aResponse().withStatus(400).withBody("Bad Request")),
			)

			assertThatThrownBy {
				kotlinx.coroutines.runBlocking { mockWorkerClient.submitProcess("https://example.com/image.png") }
			}
				.isInstanceOf(MockWorkerException::class.java)
				.satisfies({
					val ex = it as MockWorkerException
					assertThat(ex.recoveryAction).isEqualTo(RecoveryAction.FAIL)
					assertThat(ex.upstreamHttpStatus).isEqualTo(400)
				})
		}

		@Test
		fun `422_에러_시_FAIL`() {
			wireMockServer.stubFor(
				post(urlEqualTo("/mock/process"))
					.willReturn(aResponse().withStatus(422).withBody("Unprocessable Entity")),
			)

			assertThatThrownBy {
				kotlinx.coroutines.runBlocking { mockWorkerClient.submitProcess("https://example.com/image.png") }
			}
				.isInstanceOf(MockWorkerException::class.java)
				.satisfies({
					val ex = it as MockWorkerException
					assertThat(ex.recoveryAction).isEqualTo(RecoveryAction.FAIL)
					assertThat(ex.upstreamHttpStatus).isEqualTo(422)
				})
		}

		@Test
		fun `404_에러_시_REVERT_TO_PENDING`() {
			wireMockServer.stubFor(
				get(urlEqualTo("/mock/process/job-404"))
					.willReturn(aResponse().withStatus(404).withBody("Not Found")),
			)

			assertThatThrownBy {
				kotlinx.coroutines.runBlocking { mockWorkerClient.getJobStatus("job-404") }
			}
				.isInstanceOf(MockWorkerException::class.java)
				.satisfies({
					val ex = it as MockWorkerException
					assertThat(ex.recoveryAction).isEqualTo(RecoveryAction.REVERT_TO_PENDING)
					assertThat(ex.upstreamHttpStatus).isEqualTo(404)
				})
		}
	}
}
