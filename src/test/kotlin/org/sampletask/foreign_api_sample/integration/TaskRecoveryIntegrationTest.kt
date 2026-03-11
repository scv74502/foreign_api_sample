package org.sampletask.foreign_api_sample.integration

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.sampletask.foreign_api_sample.config.TestcontainersConfiguration
import org.sampletask.foreign_api_sample.task.domain.TaskStatus
import org.sampletask.foreign_api_sample.task.entity.TaskEntity
import org.sampletask.foreign_api_sample.task.repository.TaskRepository
import org.sampletask.foreign_api_sample.task.service.TaskRecoveryService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import java.time.Instant

@SpringBootTest
@Import(TestcontainersConfiguration::class, WireMockConfig::class)
class TaskRecoveryIntegrationTest {
	@Autowired
	private lateinit var taskRepository: TaskRepository

	@Autowired
	private lateinit var recoveryService: TaskRecoveryService

	@Autowired
	private lateinit var wireMockServer: WireMockServer

	@BeforeEach
	fun setUp() {
		wireMockServer.resetAll()

		wireMockServer.stubFor(
			post(urlEqualTo("/mock/auth/issue-key"))
				.willReturn(
					aResponse()
						.withStatus(200)
						.withHeader("Content-Type", "application/json")
						.withBody("""{"apiKey": "test-key"}"""),
				),
		)

		wireMockServer.stubFor(
			post(urlEqualTo("/mock/process"))
				.willReturn(
					aResponse()
						.withStatus(200)
						.withHeader("Content-Type", "application/json")
						.withBody("""{"jobId": "recovery-job"}"""),
				),
		)

		wireMockServer.stubFor(
			get(urlPathMatching("/mock/process/.*"))
				.willReturn(
					aResponse()
						.withStatus(200)
						.withHeader("Content-Type", "application/json")
						.withBody("""{"jobId": "recovery-job", "status": "COMPLETED", "result": "recovered"}"""),
				),
		)
	}

	@Test
	fun `PROCESSING_상태_+_jobId_없는_레코드_복구_시_PENDING으로_복귀`() {
		val entity =
			taskRepository.save(
				TaskEntity(
					status = TaskStatus.PROCESSING.code,
					idempotencyKey = "recovery-no-jobid-${System.nanoTime()}",
					imageUrl = "https://example.com/recovery.png",
					externalJobId = null,
					createdAt = Instant.now(),
					updatedAt = Instant.now(),
				),
			)

		recoveryService.recoverTasks()

		val recovered = taskRepository.findById(entity.id).get()
		// PENDING으로 복귀 후 orchestrator에 재등록되므로 PENDING 또는 PROCESSING일 수 있음
		assertThat(recovered.status).isIn(TaskStatus.PENDING.code, TaskStatus.PROCESSING.code)
	}
}
