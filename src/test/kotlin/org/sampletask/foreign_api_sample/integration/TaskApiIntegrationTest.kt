package org.sampletask.foreign_api_sample.integration

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching
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

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration::class, WireMockConfig::class)
class TaskApiIntegrationTest {
	@Autowired
	private lateinit var mockMvc: MockMvc

	@Autowired
	private lateinit var wireMockServer: WireMockServer

	@BeforeEach
	fun setUp() {
		wireMockServer.resetAll()

		// API Key 발급 stub
		wireMockServer.stubFor(
			post(urlEqualTo("/mock/auth/issue-key"))
				.willReturn(
					aResponse()
						.withStatus(200)
						.withHeader("Content-Type", "application/json")
						.withBody("""{"apiKey": "test-api-key"}"""),
				),
		)

		// process 제출 stub
		wireMockServer.stubFor(
			post(urlEqualTo("/mock/process"))
				.willReturn(
					aResponse()
						.withStatus(200)
						.withHeader("Content-Type", "application/json")
						.withBody("""{"jobId": "job-integration-test"}"""),
				),
		)

		// job status stub
		wireMockServer.stubFor(
			get(urlPathMatching("/mock/process/.*"))
				.willReturn(
					aResponse()
						.withStatus(200)
						.withHeader("Content-Type", "application/json")
						.withBody("""{"jobId": "job-integration-test", "status": "PROCESSING"}"""),
				),
		)
	}

	private fun uniqueUrl() = "https://example.com/test-${System.nanoTime()}.png"

	@Test
	fun `작업_생성_시_202_반환`() {
		val url = uniqueUrl()
		mockMvc
			.perform(
				MockMvcRequestBuilders
					.post("/api/tasks")
					.header("X-Idempotency-Key", "integration-key-1-${System.nanoTime()}")
					.contentType(MediaType.APPLICATION_JSON)
					.content("""{"imageUrl": "$url"}"""),
			).andExpect(status().isAccepted)
			.andExpect(jsonPath("$.id").exists())
			.andExpect(jsonPath("$.status").value("PENDING"))
			.andExpect(jsonPath("$.imageUrl").value(url))
	}

	@Test
	fun `동일_멱등성_키_재요청_시_같은_작업_반환`() {
		val key = "integration-key-idempotent-${System.nanoTime()}"
		val url = uniqueUrl()

		val result1 =
			mockMvc
				.perform(
					MockMvcRequestBuilders
						.post("/api/tasks")
						.header("X-Idempotency-Key", key)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""{"imageUrl": "$url"}"""),
				).andExpect(status().isAccepted)
				.andReturn()

		val result2 =
			mockMvc
				.perform(
					MockMvcRequestBuilders
						.post("/api/tasks")
						.header("X-Idempotency-Key", key)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""{"imageUrl": "$url"}"""),
				).andExpect(status().isAccepted)
				.andReturn()

		val body1 = result1.response.contentAsString
		val body2 = result2.response.contentAsString
		// 같은 ID가 반환되어야 함
		assert(body1.contains(body2.substringAfter("\"id\":").substringBefore(",")))
	}

	@Test
	fun `존재하지_않는_작업_조회_시_404`() {
		mockMvc
			.perform(MockMvcRequestBuilders.get("/api/tasks/999999"))
			.andExpect(status().isNotFound)
			.andExpect(jsonPath("$.code").value("TASK_NOT_FOUND"))
	}

	@Test
	fun `작업_생성_후_조회_시_200`() {
		val key = "integration-key-get-${System.nanoTime()}"
		val url = uniqueUrl()
		val createResult =
			mockMvc
				.perform(
					MockMvcRequestBuilders
						.post("/api/tasks")
						.header("X-Idempotency-Key", key)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""{"imageUrl": "$url"}"""),
				).andExpect(status().isAccepted)
				.andReturn()

		val idStr =
			createResult.response.contentAsString
				.substringAfter("\"id\":")
				.substringBefore(",")
				.trim()

		mockMvc
			.perform(MockMvcRequestBuilders.get("/api/tasks/$idStr"))
			.andExpect(status().isOk)
			.andExpect(jsonPath("$.id").value(idStr.toLong()))
	}

	@Test
	fun `작업_목록_페이지네이션_조회`() {
		// 작업 2개 생성
		repeat(2) { i ->
			mockMvc.perform(
				MockMvcRequestBuilders
					.post("/api/tasks")
					.header("X-Idempotency-Key", "integration-page-${System.nanoTime()}-$i")
					.contentType(MediaType.APPLICATION_JSON)
					.content("""{"imageUrl": "${uniqueUrl()}"}"""),
			)
		}

		mockMvc
			.perform(
				MockMvcRequestBuilders
					.get("/api/tasks")
					.param("page", "0")
					.param("size", "10"),
			).andExpect(status().isOk)
			.andExpect(jsonPath("$.content").isArray)
	}

	@Test
	fun `X-Idempotency-Key_헤더_없으면_400`() {
		mockMvc
			.perform(
				MockMvcRequestBuilders
					.post("/api/tasks")
					.contentType(MediaType.APPLICATION_JSON)
					.content("""{"imageUrl": "${uniqueUrl()}"}"""),
			).andExpect(status().isBadRequest)
	}

	@Test
	fun `유효하지_않은_URL_포맷이면_422`() {
		mockMvc
			.perform(
				MockMvcRequestBuilders
					.post("/api/tasks")
					.header("X-Idempotency-Key", "validation-url-key-${System.nanoTime()}")
					.contentType(MediaType.APPLICATION_JSON)
					.content("""{"imageUrl": "not-a-url"}"""),
			).andExpect(status().isUnprocessableEntity)
			.andExpect(jsonPath("$.code").value("INVALID_URL_FORMAT"))
	}

	@Test
	fun `동일_imageUrl_3분_내_재요청_시_409`() {
		val url = "https://example.com/duplicate-test-${System.nanoTime()}.png"

		// 첫 번째 요청 성공
		mockMvc
			.perform(
				MockMvcRequestBuilders
					.post("/api/tasks")
					.header("X-Idempotency-Key", "dup-img-key-1-${System.nanoTime()}")
					.contentType(MediaType.APPLICATION_JSON)
					.content("""{"imageUrl": "$url"}"""),
			).andExpect(status().isAccepted)

		// 두 번째 요청 (다른 멱등성 키, 같은 imageUrl) -> 409
		mockMvc
			.perform(
				MockMvcRequestBuilders
					.post("/api/tasks")
					.header("X-Idempotency-Key", "dup-img-key-2-${System.nanoTime()}")
					.contentType(MediaType.APPLICATION_JSON)
					.content("""{"imageUrl": "$url"}"""),
			).andExpect(status().isConflict)
			.andExpect(jsonPath("$.code").value("DUPLICATE_IMAGE_URL"))
	}

	@Test
	fun `imageUrl이_빈_문자열이면_400`() {
		mockMvc
			.perform(
				MockMvcRequestBuilders
					.post("/api/tasks")
					.header("X-Idempotency-Key", "validation-key-${System.nanoTime()}")
					.contentType(MediaType.APPLICATION_JSON)
					.content("""{"imageUrl": ""}"""),
			).andExpect(status().isBadRequest)
			.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
	}
}
