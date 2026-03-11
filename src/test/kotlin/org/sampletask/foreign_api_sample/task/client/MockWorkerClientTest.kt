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
import org.junit.jupiter.api.Test
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

		// API Key 발급 stub
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

	@Test
	fun `500_에러_시_MockWorkerException_발생_(transient)`() {
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
				assertThat(ex.isTransient).isTrue()
				assertThat(ex.upstreamHttpStatus).isEqualTo(500)
			})
	}

	@Test
	fun `400_에러_시_MockWorkerException_발생_(permanent)`() {
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
				assertThat(ex.isTransient).isFalse()
				assertThat(ex.upstreamHttpStatus).isEqualTo(400)
			})
	}
}
