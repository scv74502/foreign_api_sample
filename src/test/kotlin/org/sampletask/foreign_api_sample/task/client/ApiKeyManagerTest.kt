package org.sampletask.foreign_api_sample.task.client

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.sampletask.foreign_api_sample.task.exception.ApiKeyUnavailableException
import org.springframework.web.reactive.function.client.WebClient

class ApiKeyManagerTest {
	private lateinit var wireMockServer: WireMockServer
	private lateinit var apiKeyManager: ApiKeyManager

	@BeforeEach
	fun setUp() {
		wireMockServer = WireMockServer(wireMockConfig().dynamicPort())
		wireMockServer.start()

		val webClient = WebClient.builder().baseUrl(wireMockServer.baseUrl()).build()
		apiKeyManager = ApiKeyManager(webClient, "test-candidate", "test@example.com")
	}

	@AfterEach
	fun tearDown() {
		wireMockServer.stop()
	}

	@Test
	fun `API_Key_발급_성공`() {
		runTest {
			wireMockServer.stubFor(
				post(urlEqualTo("/mock/auth/issue-key"))
					.willReturn(
						aResponse()
							.withStatus(200)
							.withHeader("Content-Type", "application/json")
							.withBody("""{"apiKey": "test-api-key-123"}"""),
					),
			)

			val key = apiKeyManager.getApiKey()

			assertThat(key).isEqualTo("test-api-key-123")
		}
	}

	@Test
	fun `API_Key_재발급_성공`() {
		runTest {
			wireMockServer.stubFor(
				post(urlEqualTo("/mock/auth/issue-key"))
					.willReturn(
						aResponse()
							.withStatus(200)
							.withHeader("Content-Type", "application/json")
							.withBody("""{"apiKey": "new-api-key-456"}"""),
					),
			)

			apiKeyManager.reissueApiKey()

			assertThat(apiKeyManager.apiKey).isEqualTo("new-api-key-456")
		}
	}

	@Test
	fun `API_Key_발급_3회_실패_시_예외_발생`() {
		wireMockServer.stubFor(
			post(urlEqualTo("/mock/auth/issue-key"))
				.willReturn(aResponse().withStatus(500)),
		)

		assertThatThrownBy {
			kotlinx.coroutines.runBlocking { apiKeyManager.reissueApiKey() }
		}.isInstanceOf(ApiKeyUnavailableException::class.java)
	}
}
