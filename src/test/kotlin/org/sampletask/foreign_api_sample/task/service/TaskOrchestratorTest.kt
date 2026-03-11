package org.sampletask.foreign_api_sample.task.service

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.sampletask.foreign_api_sample.task.client.MockWorkerClient
import org.sampletask.foreign_api_sample.task.client.dto.JobStatusResponse
import org.sampletask.foreign_api_sample.task.client.dto.ProcessResponse
import org.sampletask.foreign_api_sample.task.domain.Task
import org.sampletask.foreign_api_sample.task.domain.TaskStatus
import org.sampletask.foreign_api_sample.task.exception.MockWorkerException

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
		val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
		orchestrator =
			TaskOrchestrator(
				taskService = taskService,
				mockWorkerClient = mockWorkerClient,
				scope = scope,
				maxRetryCount = 3,
				initialIntervalMs = 10,
				maxIntervalMs = 50,
			)
	}

	@Test
	fun `정상_처리_흐름_-_PENDING에서_COMPLETED`() {
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

	@Test
	fun `transient_에러_시_재시도`() {
		runTest {
			val task = createTask()

			whenever(taskService.getTask(1L)).thenReturn(task)
			whenever(taskService.updateTask(any())).thenAnswer { it.arguments[0] as Task }
			whenever(mockWorkerClient.submitProcess(any()))
				.thenThrow(MockWorkerException(500, "Internal Server Error", true))

			orchestrator.processTask(task)

			assertThat(task.retryCount).isEqualTo(1)
		}
	}

	@Test
	fun `permanent_에러_시_즉시_FAILED`() {
		runTest {
			val task = createTask()

			whenever(taskService.getTask(1L)).thenReturn(task)
			whenever(taskService.updateTask(any())).thenAnswer { it.arguments[0] as Task }
			whenever(mockWorkerClient.submitProcess(any()))
				.thenThrow(MockWorkerException(400, "Bad Request", false))

			orchestrator.processTask(task)

			assertThat(task.status).isEqualTo(TaskStatus.FAILED)
			assertThat(task.errorCode).isEqualTo("HTTP_400")
		}
	}
}
