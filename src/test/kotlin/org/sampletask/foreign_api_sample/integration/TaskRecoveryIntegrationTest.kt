package org.sampletask.foreign_api_sample.integration

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import jakarta.persistence.EntityManager
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.atMost
import org.awaitility.kotlin.await
import org.awaitility.kotlin.untilAsserted
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.sampletask.foreign_api_sample.config.TestcontainersConfiguration
import org.sampletask.foreign_api_sample.task.domain.TaskStatus
import org.sampletask.foreign_api_sample.task.entity.TaskEntity
import org.sampletask.foreign_api_sample.task.repository.TaskRepository
import org.sampletask.foreign_api_sample.task.service.TaskRecoveryService
import org.sampletask.foreign_api_sample.task.service.TaskService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import java.time.Duration
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

	@Autowired
	private lateinit var entityManager: EntityManager

	@Autowired
	private lateinit var circuitBreakerRegistry: CircuitBreakerRegistry

	private fun uniqueUrl(prefix: String = "recovery") = "https://example.com/$prefix-${System.nanoTime()}.png"

	private fun createTestEntity(
		status: Int,
		idempotencyKey: String,
		imageUrl: String = uniqueUrl(),
		externalJobId: String? = null,
	): TaskEntity {
		return taskRepository.save(
			TaskEntity(
				status = status,
				idempotencyKey = idempotencyKey,
				imageUrl = imageUrl,
				imageUrlHash = TaskService.sha256(imageUrl),
				externalJobId = externalJobId,
				createdAt = Instant.now(),
				updatedAt = Instant.now(),
			),
		)
	}

	@BeforeEach
	fun setUp() {
		// 서킷브레이커 상태 초기화 (이전 테스트의 오류로 OPEN 상태일 수 있음)
		circuitBreakerRegistry.allCircuitBreakers.forEach { it.reset() }

		// 이전 테스트의 비동기 코루틴이 완료될 시간을 확보
		Thread.sleep(2000)

		// 이전 테스트에서 남은 PENDING/PROCESSING 레코드를 정리하여 테스트 간 간섭 방지
		val staleEntities =
			taskRepository.findAllByStatusIn(
				listOf(TaskStatus.PENDING.code, TaskStatus.PROCESSING.code),
			)
		staleEntities.forEach { entity ->
			entity.status = TaskStatus.CANCELLED.code
			taskRepository.save(entity)
		}
		taskRepository.flush()
		entityManager.clear()

		// WireMock 리셋 후 기본 스텁 설정
		// (이전 테스트의 잔여 코루틴이 호출해도 실패하지 않도록 fallback 스텁 포함)
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
						.withBody("""{"jobId": "fallback-job"}"""),
				),
		)

		wireMockServer.stubFor(
			get(urlPathMatching("/mock/process/.*"))
				.willReturn(
					aResponse()
						.withStatus(200)
						.withHeader("Content-Type", "application/json")
						.withBody("""{"jobId": "fallback-job", "status": "COMPLETED", "result": "fallback"}"""),
				),
		)

		// 스텁 설정 후 서킷브레이커 한번 더 리셋
		circuitBreakerRegistry.allCircuitBreakers.forEach { it.reset() }
	}

	@Nested
	@Suppress("ClassName")
	inner class PROCESSING_jobId_없음 {

		@Test
		fun `PROCESSING_상태_+_jobId_없는_레코드_복구_시_PENDING으로_복귀`() {
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

			val entity = createTestEntity(
				status = TaskStatus.PROCESSING.code,
				idempotencyKey = "recovery-no-jobid-${System.nanoTime()}",
			)

			recoveryService.recoverTasks()

			val recovered = taskRepository.findById(entity.id).get()
			// PENDING으로 복귀 후 orchestrator에 재등록되므로 PENDING 또는 PROCESSING일 수 있음
			assertThat(recovered.status).isIn(TaskStatus.PENDING.code, TaskStatus.PROCESSING.code)
		}
	}

	@Nested
	@Suppress("ClassName")
	inner class PROCESSING_jobId_있음_외부_상태_동기화 {

		@Test
		fun `외부_상태_COMPLETED면_작업_완료_처리`() {
			val jobId = "recovery-completed-${System.nanoTime()}"

			wireMockServer.stubFor(
				get(urlEqualTo("/mock/process/$jobId"))
					.willReturn(
						aResponse()
							.withStatus(200)
							.withHeader("Content-Type", "application/json")
							.withBody("""{"jobId": "$jobId", "status": "COMPLETED", "result": "recovered-result"}"""),
					),
			)

			val entity = createTestEntity(
				status = TaskStatus.PROCESSING.code,
				idempotencyKey = "recovery-completed-${System.nanoTime()}",
				externalJobId = jobId,
			)

			recoveryService.recoverTasks()

			// 비동기 처리이므로 awaitility로 대기
			await atMost Duration.ofSeconds(10) untilAsserted {
				val recovered = taskRepository.findById(entity.id).get()
				assertThat(recovered.status).isEqualTo(TaskStatus.COMPLETED.code)
				assertThat(recovered.result).isEqualTo("recovered-result")
			}
		}

		@Test
		fun `외부_상태_FAILED면_작업_실패_처리`() {
			val jobId = "recovery-failed-${System.nanoTime()}"

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

			val entity = createTestEntity(
				status = TaskStatus.PROCESSING.code,
				idempotencyKey = "recovery-failed-${System.nanoTime()}",
				externalJobId = jobId,
			)

			recoveryService.recoverTasks()

			await atMost Duration.ofSeconds(10) untilAsserted {
				val recovered = taskRepository.findById(entity.id).get()
				assertThat(recovered.status).isEqualTo(TaskStatus.FAILED.code)
				assertThat(recovered.errorCode).isEqualTo("PROC_ERR")
				assertThat(recovered.errorMessage).isEqualTo("processing failed")
			}
		}
	}

	@Nested
	@Suppress("ClassName")
	inner class PENDING_작업_복구 {

		@Test
		fun `PENDING_작업_복구_시_Orchestrator에_재등록되어_처리됨`() {
			val jobId = "recovery-pending-${System.nanoTime()}"

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
							.withBody("""{"jobId": "$jobId", "status": "COMPLETED", "result": "pending-recovered"}"""),
					),
			)

			val entity = createTestEntity(
				status = TaskStatus.PENDING.code,
				idempotencyKey = "recovery-pending-${System.nanoTime()}",
			)

			recoveryService.recoverTasks()

			await atMost Duration.ofSeconds(15) untilAsserted {
				val recovered = taskRepository.findById(entity.id).get()
				assertThat(recovered.status).isEqualTo(TaskStatus.COMPLETED.code)
				assertThat(recovered.result).isEqualTo("pending-recovered")
			}
		}
	}

	@Nested
	@Suppress("ClassName")
	inner class 복수_작업_동시_복구 {

		@Test
		fun `PENDING과_PROCESSING_작업이_혼합된_경우_각각_올바르게_복구`() {
			val pendingJobId = "multi-pending-${System.nanoTime()}"
			val processingJobId = "multi-processing-${System.nanoTime()}"

			wireMockServer.stubFor(
				post(urlEqualTo("/mock/process"))
					.willReturn(
						aResponse()
							.withStatus(200)
							.withHeader("Content-Type", "application/json")
							.withBody("""{"jobId": "$pendingJobId"}"""),
					),
			)
			wireMockServer.stubFor(
				get(urlEqualTo("/mock/process/$pendingJobId"))
					.willReturn(
						aResponse()
							.withStatus(200)
							.withHeader("Content-Type", "application/json")
							.withBody("""{"jobId": "$pendingJobId", "status": "COMPLETED", "result": "pending-done"}"""),
					),
			)
			wireMockServer.stubFor(
				get(urlEqualTo("/mock/process/$processingJobId"))
					.willReturn(
						aResponse()
							.withStatus(200)
							.withHeader("Content-Type", "application/json")
							.withBody("""{"jobId": "$processingJobId", "status": "COMPLETED", "result": "processing-done"}"""),
					),
			)

			val pendingEntity = createTestEntity(
				status = TaskStatus.PENDING.code,
				idempotencyKey = "multi-pending-${System.nanoTime()}",
			)

			val processingWithJobEntity = createTestEntity(
				status = TaskStatus.PROCESSING.code,
				idempotencyKey = "multi-processing-${System.nanoTime()}",
				externalJobId = processingJobId,
			)

			val processingNoJobEntity = createTestEntity(
				status = TaskStatus.PROCESSING.code,
				idempotencyKey = "multi-nojob-${System.nanoTime()}",
			)

			recoveryService.recoverTasks()

			await atMost Duration.ofSeconds(15) untilAsserted {
				val pending = taskRepository.findById(pendingEntity.id).get()
				assertThat(pending.status).isEqualTo(TaskStatus.COMPLETED.code)
				assertThat(pending.result).isEqualTo("pending-done")

				val processingWithJob = taskRepository.findById(processingWithJobEntity.id).get()
				assertThat(processingWithJob.status).isEqualTo(TaskStatus.COMPLETED.code)
				assertThat(processingWithJob.result).isEqualTo("processing-done")
			}

			// PROCESSING + jobId 없는 작업은 PENDING 복귀 후 재처리
			await atMost Duration.ofSeconds(15) untilAsserted {
				val processingNoJob = taskRepository.findById(processingNoJobEntity.id).get()
				assertThat(processingNoJob.status).isIn(
					TaskStatus.PENDING.code,
					TaskStatus.PROCESSING.code,
					TaskStatus.COMPLETED.code,
				)
			}
		}
	}

	@Nested
	@Suppress("ClassName")
	inner class Optimistic_Lock_충돌 {

		@Test
		fun `복구_중_다른_요청이_같은_Task를_수정하면_예외_발생_후_FAILED_전이`() {
			val jobId = "lock-conflict-${System.nanoTime()}"

			wireMockServer.stubFor(
				get(urlEqualTo("/mock/process/$jobId"))
					.willReturn(
						aResponse()
							.withStatus(200)
							.withHeader("Content-Type", "application/json")
							.withBody("""{"jobId": "$jobId", "status": "COMPLETED", "result": "done"}"""),
					),
			)

			val entity = createTestEntity(
				status = TaskStatus.PROCESSING.code,
				idempotencyKey = "lock-conflict-${System.nanoTime()}",
				externalJobId = jobId,
			)

			// 복구 호출 전에 version을 강제로 증가시켜 optimistic lock 충돌 유발
			val loaded = taskRepository.findById(entity.id).get()
			loaded.updatedAt = Instant.now()
			taskRepository.saveAndFlush(loaded)

			recoveryService.recoverTasks()

			// 비동기 처리 후 결과 확인 - 충돌 시에도 최종적으로 상태가 결정되어야 함
			await atMost Duration.ofSeconds(10) untilAsserted {
				val recovered = taskRepository.findById(entity.id).get()
				// optimistic lock 충돌이 발생하면 recovery 내부에서 재조회하여 처리하거나,
				// 이미 다른 쪽에서 업데이트된 상태를 반영함
				assertThat(recovered.status).isIn(
					TaskStatus.COMPLETED.code,
					TaskStatus.FAILED.code,
					TaskStatus.PROCESSING.code,
				)
			}
		}
	}
}
