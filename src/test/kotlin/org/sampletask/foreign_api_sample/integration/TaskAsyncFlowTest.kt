package org.sampletask.foreign_api_sample.integration

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import org.awaitility.kotlin.atMost
import org.awaitility.kotlin.await
import org.awaitility.kotlin.untilAsserted
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.sampletask.foreign_api_sample.config.TestcontainersConfiguration
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Duration

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration::class, WireMockConfig::class)
class TaskAsyncFlowTest {
	@Autowired
	private lateinit var mockMvc: MockMvc

	@Autowired
	private lateinit var wireMockServer: WireMockServer

	@BeforeEach
	fun setUp() {
		wireMockServer.stubFor(
			post(urlEqualTo("/mock/auth/issue-key"))
				.willReturn(
					aResponse()
						.withStatus(200)
						.withHeader("Content-Type", "application/json")
						.withBody("""{"apiKey": "test-key"}"""),
				),
		)
	}

	@Test
	fun `정상_완료_흐름_-_PENDING에서_COMPLETED까지`() {
		val jobId = "job-flow-complete-${System.nanoTime()}"

		wireMockServer.stubFor(
			post(urlEqualTo("/mock/process"))
				.willReturn(
					aResponse()
						.withStatus(200)
						.withHeader("Content-Type", "application/json")
						.withBody("""{"jobId": "$jobId"}"""),
				),
		)

		wireMockServer.stubFor(
			get(urlEqualTo("/mock/process/$jobId"))
				.willReturn(
					aResponse()
						.withStatus(200)
						.withHeader("Content-Type", "application/json")
						.withBody("""{"jobId": "$jobId", "status": "COMPLETED", "result": "processed-result"}"""),
				),
		)

		val key = "async-complete-${System.nanoTime()}"
		val createResult =
			mockMvc
				.perform(
					MockMvcRequestBuilders
						.post("/api/tasks")
						.header("X-Idempotency-Key", key)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""{"imageUrl": "https://example.com/async-test-${System.nanoTime()}.png"}"""),
				).andExpect(status().isAccepted)
				.andReturn()

		val taskId =
			createResult.response.contentAsString
				.substringAfter("\"id\":")
				.substringBefore(",")
				.trim()

		await atMost Duration.ofSeconds(15) untilAsserted {
			mockMvc
				.perform(MockMvcRequestBuilders.get("/api/tasks/$taskId"))
				.andExpect(status().isOk)
				.andExpect(jsonPath("$.status").value("COMPLETED"))
				.andExpect(jsonPath("$.result").value("processed-result"))
		}
	}

	@Test
	fun `실패_흐름_-_외부_서비스_FAILED_반환`() {
		val jobId = "job-flow-fail-${System.nanoTime()}"

		wireMockServer.stubFor(
			post(urlEqualTo("/mock/process"))
				.willReturn(
					aResponse()
						.withStatus(200)
						.withHeader("Content-Type", "application/json")
						.withBody("""{"jobId": "$jobId"}"""),
				),
		)

		wireMockServer.stubFor(
			get(urlEqualTo("/mock/process/$jobId"))
				.willReturn(
					aResponse()
						.withStatus(200)
						.withHeader("Content-Type", "application/json")
						.withBody(
							"""{"jobId": "$jobId", "status": "FAILED", "errorCode": "PROC_ERR", "errorMessage": "processing failed"}""",
						),
				),
		)

		val key = "async-fail-${System.nanoTime()}"
		val createResult =
			mockMvc
				.perform(
					MockMvcRequestBuilders
						.post("/api/tasks")
						.header("X-Idempotency-Key", key)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""{"imageUrl": "https://example.com/fail-test-${System.nanoTime()}.png"}"""),
				).andExpect(status().isAccepted)
				.andReturn()

		val taskId =
			createResult.response.contentAsString
				.substringAfter("\"id\":")
				.substringBefore(",")
				.trim()

		await atMost Duration.ofSeconds(15) untilAsserted {
			mockMvc
				.perform(MockMvcRequestBuilders.get("/api/tasks/$taskId"))
				.andExpect(status().isOk)
				.andExpect(jsonPath("$.status").value("FAILED"))
		}
	}
}
