package org.sampletask.foreign_api_sample.task.service

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.sampletask.foreign_api_sample.task.client.MockWorkerClient
import org.sampletask.foreign_api_sample.task.client.response.JobStatusResponse
import org.sampletask.foreign_api_sample.task.domain.RecoveryAction
import org.sampletask.foreign_api_sample.task.domain.TaskStatus
import org.sampletask.foreign_api_sample.task.entity.TaskEntity
import org.sampletask.foreign_api_sample.task.exception.MockWorkerException
import org.sampletask.foreign_api_sample.task.repository.TaskRepository
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(MockitoExtension::class)
class TaskRecoveryServiceTest {
	@Mock
	private lateinit var taskRepository: TaskRepository

	@Mock
	private lateinit var taskService: TaskService

	@Mock
	private lateinit var taskOrchestrator: TaskOrchestrator

	@Mock
	private lateinit var mockWorkerClient: MockWorkerClient

	private lateinit var recoveryService: TaskRecoveryService

	@BeforeEach
	fun setUp() {
		val scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher())
		recoveryService =
			TaskRecoveryService(
				taskRepository = taskRepository,
				taskService = taskService,
				taskOrchestrator = taskOrchestrator,
				mockWorkerClient = mockWorkerClient,
				scope = scope,
			)
	}

	@Nested
	@Suppress("ClassName")
	inner class 복구_대상_없음 {

		@Test
		fun `복구_대상_없으면_아무것도_하지_않음`() {
			whenever(taskRepository.findAllByStatusIn(any())).thenReturn(emptyList())

			recoveryService.recoverTasks()

			verify(taskOrchestrator, never()).submitAsync(any(), anyOrNull())
		}
	}

	@Nested
	@Suppress("ClassName")
	inner class PENDING_작업_복구 {

		@Test
		fun `PENDING_작업은_Orchestrator에_재등록`() {
			val entity = createEntity(id = 1L, status = TaskStatus.PENDING)
			whenever(taskRepository.findAllByStatusIn(any())).thenReturn(listOf(entity))

			recoveryService.recoverTasks()

			verify(taskOrchestrator).submitAsync(any(), anyOrNull())
		}
	}

	@Nested
	@Suppress("ClassName")
	inner class PROCESSING_작업_복구 {

		@Test
		fun `jobId_없으면_PENDING_복귀_후_재등록`() {
			val entity = createEntity(id = 2L, status = TaskStatus.PROCESSING, externalJobId = null)
			whenever(taskRepository.findAllByStatusIn(any())).thenReturn(listOf(entity))
			whenever(taskService.updateTask(any())).thenAnswer { it.arguments[0] }

			recoveryService.recoverTasks()

			verify(taskService).updateTask(any())
			verify(taskOrchestrator).submitAsync(any(), anyOrNull())
		}

		@Test
		fun `외부_상태_COMPLETED면_작업_완료_처리`() {
			runTest {
				val entity = createEntity(id = 3L, status = TaskStatus.PROCESSING, externalJobId = "job-3")
				whenever(taskRepository.findAllByStatusIn(any())).thenReturn(listOf(entity))
				whenever(taskService.updateTask(any())).thenAnswer { it.arguments[0] }
				whenever(mockWorkerClient.getJobStatus("job-3")).thenReturn(
					JobStatusResponse(jobId = "job-3", status = "COMPLETED", result = "done"),
				)

				recoveryService.recoverTasks()

				verify(taskService).updateTask(
					org.mockito.kotlin.argThat { status == TaskStatus.COMPLETED },
				)
			}
		}

		@Test
		fun `외부_상태_FAILED면_작업_실패_처리`() {
			runTest {
				val entity = createEntity(id = 4L, status = TaskStatus.PROCESSING, externalJobId = "job-4")
				whenever(taskRepository.findAllByStatusIn(any())).thenReturn(listOf(entity))
				whenever(taskService.updateTask(any())).thenAnswer { it.arguments[0] }
				whenever(mockWorkerClient.getJobStatus("job-4")).thenReturn(
					JobStatusResponse(jobId = "job-4", status = "FAILED", errorCode = "ERR", errorMessage = "fail"),
				)

				recoveryService.recoverTasks()

				verify(taskService).updateTask(
					org.mockito.kotlin.argThat { status == TaskStatus.FAILED },
				)
			}
		}

		@Test
		fun `외부_상태_PROCESSING이면_폴링_재개`() {
			runTest {
				val entity = createEntity(id = 5L, status = TaskStatus.PROCESSING, externalJobId = "job-5")
				whenever(taskRepository.findAllByStatusIn(any())).thenReturn(listOf(entity))
				whenever(mockWorkerClient.getJobStatus("job-5")).thenReturn(
					JobStatusResponse(jobId = "job-5", status = "PROCESSING"),
				)

				recoveryService.recoverTasks()

				verify(taskOrchestrator).submitAsync(any(), anyOrNull())
			}
		}

		@Test
		fun `외부_조회_404_시_PENDING_복귀`() {
			runTest {
				val entity = createEntity(id = 6L, status = TaskStatus.PROCESSING, externalJobId = "job-6")
				whenever(taskRepository.findAllByStatusIn(any())).thenReturn(listOf(entity))
				whenever(taskService.updateTask(any())).thenAnswer { it.arguments[0] }
				whenever(mockWorkerClient.getJobStatus("job-6"))
					.thenThrow(MockWorkerException(404, "Not Found", RecoveryAction.REVERT_TO_PENDING))

				recoveryService.recoverTasks()

				verify(taskService).updateTask(
					org.mockito.kotlin.argThat {
						externalJobId == null && status == TaskStatus.PENDING
					},
				)
				verify(taskOrchestrator).submitAsync(any(), anyOrNull())
			}
		}

		@Test
		fun `외부_조회_기타_오류_시_폴링_재개`() {
			runTest {
				val entity = createEntity(id = 7L, status = TaskStatus.PROCESSING, externalJobId = "job-7")
				whenever(taskRepository.findAllByStatusIn(any())).thenReturn(listOf(entity))
				whenever(mockWorkerClient.getJobStatus("job-7"))
					.thenThrow(MockWorkerException(500, "Server Error", RecoveryAction.RETRY))

				recoveryService.recoverTasks()

				verify(taskOrchestrator).submitAsync(any(), anyOrNull())
			}
		}
	}

	private fun createEntity(id: Long, status: TaskStatus, externalJobId: String? = null): TaskEntity {
		return TaskEntity(
			id = id,
			status = status.code,
			idempotencyKey = "key-$id",
			imageUrl = "https://example.com/image.png",
			externalJobId = externalJobId,
			createdAt = Instant.now(),
			updatedAt = Instant.now(),
		)
	}
}
