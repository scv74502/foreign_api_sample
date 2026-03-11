package org.sampletask.foreign_api_sample.task.service

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.sampletask.foreign_api_sample.task.client.MockWorkerClient
import org.sampletask.foreign_api_sample.task.client.response.JobStatusResponse
import org.sampletask.foreign_api_sample.task.client.response.ProcessResponse
import org.sampletask.foreign_api_sample.task.domain.RecoveryAction
import org.sampletask.foreign_api_sample.task.domain.Task
import org.sampletask.foreign_api_sample.task.domain.TaskStatus
import org.sampletask.foreign_api_sample.task.exception.MockWorkerException
import java.util.concurrent.atomic.AtomicInteger

@ExtendWith(MockitoExtension::class)
class TaskOrchestratorTest {
	@Mock
	private lateinit var taskService: TaskService

	@Mock
	private lateinit var mockWorkerClient: MockWorkerClient

	private lateinit var orchestrator: TaskOrchestrator

	private fun createTask(id: Long = 1L, status: TaskStatus = TaskStatus.PENDING): Task {
		return Task(
			id = id,
			status = status,
			idempotencyKey = "test-key",
			imageUrl = "https://example.com/image.png",
		)
	}

	@BeforeEach
	fun setUp() {
		val scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher())
		orchestrator =
			TaskOrchestrator(
				taskService = taskService,
				mockWorkerClient = mockWorkerClient,
				scope = scope,
				maxRetryCount = 3,
				initialIntervalMs = 10,
				maxIntervalMs = 50,
				multiplier = 2.0,
				maxConcurrentPolling = 2,
			)
	}

	@Test
	fun `정상_처리_흐름_PENDING에서_COMPLETED`() {
		runTest {
			val task = createTask()

			whenever(taskService.getTask(1L)).thenReturn(task)
			whenever(taskService.updateTask(any())).thenAnswer { it.arguments[0] as Task }
			whenever(mockWorkerClient.submitProcess(any())).thenReturn(ProcessResponse("job-123"))
			whenever(mockWorkerClient.getJobStatus("job-123")).thenReturn(
				JobStatusResponse(jobId = "job-123", status = "COMPLETED", result = "result-data"),
			)

			orchestrator.processTask(task)

			assertThat(task.status).isEqualTo(TaskStatus.COMPLETED)
			assertThat(task.result).isEqualTo("result-data")
		}
	}

	@Test
	fun `외부_서비스_FAILED_반환_시_작업_FAILED`() {
		runTest {
			val task = createTask()

			whenever(taskService.getTask(1L)).thenReturn(task)
			whenever(taskService.updateTask(any())).thenAnswer { it.arguments[0] as Task }
			whenever(mockWorkerClient.submitProcess(any())).thenReturn(ProcessResponse("job-123"))
			whenever(mockWorkerClient.getJobStatus("job-123")).thenReturn(
				JobStatusResponse(jobId = "job-123", status = "FAILED", errorCode = "ERR", errorMessage = "failed"),
			)

			orchestrator.processTask(task)

			assertThat(task.status).isEqualTo(TaskStatus.FAILED)
			assertThat(task.errorCode).isEqualTo("ERR")
		}
	}

	@Nested
	@Suppress("ClassName")
	inner class 복구액션별에러처리 {

		@Test
		fun `RETRY_재시도_횟수_미만이면_PROCESSING에서_PENDING_직접_전이`() {
			runTest {
				val task = createTask()

				whenever(taskService.getTask(1L)).thenReturn(task)
				whenever(taskService.updateTask(any())).thenAnswer { it.arguments[0] as Task }
				whenever(mockWorkerClient.submitProcess(any()))
					.thenThrow(MockWorkerException(500, "Internal Server Error", RecoveryAction.RETRY))

				orchestrator.processTask(task)

				assertThat(task.retryCount).isEqualTo(1)
				assertThat(task.status).isEqualTo(TaskStatus.PENDING)
				// PROCESSING→PENDING 1회 업데이트 (FAILED 중간 전이 없음)
				verify(taskService, times(2)).updateTask(any()) // PROCESSING 전이 + PENDING 전이
			}
		}

		@Test
		fun `RETRY_재시도_횟수_초과_시_FAILED`() {
			runTest {
				val task = createTask()
				task.retryCount = 3

				whenever(taskService.getTask(1L)).thenReturn(task)
				whenever(taskService.updateTask(any())).thenAnswer { it.arguments[0] as Task }
				whenever(mockWorkerClient.submitProcess(any()))
					.thenThrow(MockWorkerException(500, "Internal Server Error", RecoveryAction.RETRY))

				orchestrator.processTask(task)

				assertThat(task.status).isEqualTo(TaskStatus.FAILED)
				assertThat(task.errorCode).isEqualTo("EXTERNAL_HTTP_ERROR")
			}
		}

		@Test
		fun `FAIL_즉시_FAILED_처리`() {
			runTest {
				val task = createTask()

				whenever(taskService.getTask(1L)).thenReturn(task)
				whenever(taskService.updateTask(any())).thenAnswer { it.arguments[0] as Task }
				whenever(mockWorkerClient.submitProcess(any()))
					.thenThrow(MockWorkerException(400, "Bad Request", RecoveryAction.FAIL))

				orchestrator.processTask(task)

				assertThat(task.status).isEqualTo(TaskStatus.FAILED)
				assertThat(task.errorCode).isEqualTo("EXTERNAL_HTTP_ERROR")
			}
		}

		@Test
		fun `RETRY_retryAfterMs_전달_시_submitAsync에_delayMs_전달`() {
			runTest {
				val task = createTask()

				whenever(taskService.getTask(1L)).thenReturn(task)
				whenever(taskService.updateTask(any())).thenAnswer { it.arguments[0] as Task }
				whenever(mockWorkerClient.submitProcess(any()))
					.thenThrow(
						MockWorkerException(429, "Too Many Requests", RecoveryAction.RETRY, retryAfterMs = 5000L),
					)

				orchestrator.processTask(task)

				assertThat(task.retryCount).isEqualTo(1)
				assertThat(task.status).isEqualTo(TaskStatus.PENDING)
			}
		}

		@Test
		fun `REVERT_TO_PENDING_externalJobId_초기화_후_PENDING_복귀`() {
			runTest {
				val task = createTask(status = TaskStatus.PENDING)
				task.externalJobId = "job-123"

				whenever(taskService.getTask(1L)).thenReturn(task)
				whenever(taskService.updateTask(any())).thenAnswer { it.arguments[0] as Task }
				whenever(mockWorkerClient.submitProcess(any()))
					.thenThrow(MockWorkerException(404, "Not Found", RecoveryAction.REVERT_TO_PENDING))

				orchestrator.processTask(task)

				assertThat(task.externalJobId).isNull()
				assertThat(task.status).isEqualTo(TaskStatus.PENDING)
			}
		}
	}

	@Nested
	@Suppress("ClassName")
	inner class 폴링_백오프_jitter {

		@Test
		fun `폴링_2회_이상_시_backoff_적용_확인`() {
			runTest {
				val task = createTask()

				whenever(taskService.getTask(1L)).thenReturn(task)
				whenever(taskService.updateTask(any())).thenAnswer { it.arguments[0] as Task }
				whenever(mockWorkerClient.submitProcess(any())).thenReturn(ProcessResponse("job-123"))
				whenever(mockWorkerClient.getJobStatus("job-123"))
					.thenReturn(
						JobStatusResponse(jobId = "job-123", status = "PROCESSING"),
					)
					.thenReturn(
						JobStatusResponse(jobId = "job-123", status = "PROCESSING"),
					)
					.thenReturn(
						JobStatusResponse(jobId = "job-123", status = "COMPLETED", result = "result-data"),
					)

				orchestrator.processTask(task)

				assertThat(task.status).isEqualTo(TaskStatus.COMPLETED)
				assertThat(task.result).isEqualTo("result-data")
				verify(mockWorkerClient, times(3)).getJobStatus("job-123")
			}
		}
	}

	@Nested
	@Suppress("ClassName")
	inner class 동시_폴링_제한 {

		@Test
		fun `maxConcurrentPolling_초과_동시_폴링_불가`() {
			runTest {
				val concurrentCount = AtomicInteger(0)
				val maxObserved = AtomicInteger(0)

				val tasks = (1L..3L).map { id ->
					createTask(id = id)
				}

				tasks.forEach { task ->
					whenever(taskService.getTask(task.id)).thenReturn(task)
				}
				whenever(taskService.updateTask(any())).thenAnswer { it.arguments[0] as Task }
				whenever(mockWorkerClient.submitProcess(any())).thenAnswer {
					ProcessResponse("job-shared")
				}

				whenever(mockWorkerClient.getJobStatus("job-shared")).thenAnswer {
					val current = concurrentCount.incrementAndGet()
					maxObserved.updateAndGet { max -> maxOf(max, current) }
					Thread.sleep(10)
					concurrentCount.decrementAndGet()
					JobStatusResponse(jobId = "job-shared", status = "COMPLETED", result = "result")
				}

				val deferred = tasks.map { task ->
					async { orchestrator.processTask(task) }
				}
				deferred.awaitAll()

				assertThat(maxObserved.get()).isLessThanOrEqualTo(2)
				tasks.forEach { task ->
					assertThat(task.status).isEqualTo(TaskStatus.COMPLETED)
				}
			}
		}
	}
}
