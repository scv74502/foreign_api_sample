package org.sampletask.foreign_api_sample.task.service

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.sampletask.foreign_api_sample.task.domain.TaskStatus
import org.sampletask.foreign_api_sample.task.entity.TaskEntity
import org.sampletask.foreign_api_sample.task.repository.TaskRepository
import java.time.Instant

@ExtendWith(MockitoExtension::class)
class TaskRecoveryServiceTest {
	@Mock
	private lateinit var taskRepository: TaskRepository

	@Mock
	private lateinit var taskService: TaskService

	@Mock
	private lateinit var taskOrchestrator: TaskOrchestrator

	@Mock
	private lateinit var mockWorkerClient: org.sampletask.foreign_api_sample.task.client.MockWorkerClient

	@InjectMocks
	private lateinit var recoveryService: TaskRecoveryService

	@Test
	fun `복구_대상_없으면_아무것도_하지_않음`() {
		whenever(taskRepository.findAllByStatusIn(any())).thenReturn(emptyList())

		recoveryService.recoverTasks()

		verify(taskOrchestrator, never()).submitAsync(any())
	}

	@Test
	fun `PENDING_작업은_Orchestrator에_재등록`() {
		val entity =
			TaskEntity(
				id = 1L,
				status = TaskStatus.PENDING.code,
				idempotencyKey = "key-1",
				imageUrl = "https://example.com/image.png",
				createdAt = Instant.now(),
				updatedAt = Instant.now(),
			)
		whenever(taskRepository.findAllByStatusIn(any())).thenReturn(listOf(entity))

		recoveryService.recoverTasks()

		verify(taskOrchestrator).submitAsync(any())
	}

	@Test
	fun `PROCESSING_작업(jobId_없음)은_PENDING_복귀_후_재등록`() {
		val entity =
			TaskEntity(
				id = 2L,
				status = TaskStatus.PROCESSING.code,
				idempotencyKey = "key-2",
				imageUrl = "https://example.com/image.png",
				externalJobId = null,
				createdAt = Instant.now(),
				updatedAt = Instant.now(),
			)
		whenever(taskRepository.findAllByStatusIn(any())).thenReturn(listOf(entity))
		whenever(taskService.updateTask(any())).thenAnswer { it.arguments[0] }

		recoveryService.recoverTasks()

		verify(taskService).updateTask(any())
		verify(taskOrchestrator).submitAsync(any())
	}
}
