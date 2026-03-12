package org.sampletask.foreign_api_sample.task.service

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.argThat
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever
import org.sampletask.foreign_api_sample.task.domain.TaskStatus
import org.sampletask.foreign_api_sample.task.repository.TaskRepository

@ExtendWith(MockitoExtension::class)
class IdempotencyKeyCleanupSchedulerTest {
	@Mock
	private lateinit var taskRepository: TaskRepository

	private lateinit var scheduler: IdempotencyKeyCleanupScheduler

	@BeforeEach
	fun setUp() {
		scheduler = IdempotencyKeyCleanupScheduler(
			taskRepository = taskRepository,
			expiryMinutes = 3,
		)
	}

	@Nested
	@Suppress("ClassName")
	inner class 만료_레코드_정리 {

		@Test
		fun `만료된_종료_상태_레코드_삭제`() {
			val terminalStatuses = listOf(TaskStatus.COMPLETED.code, TaskStatus.FAILED.code)
			whenever(
				taskRepository.deleteByStatusInAndCreatedAtBefore(
					eq(terminalStatuses),
					argThat { true },
				),
			).thenReturn(5)

			scheduler.cleanup()

			org.mockito.kotlin.verify(taskRepository).deleteByStatusInAndCreatedAtBefore(
				eq(terminalStatuses),
				argThat { true },
			)
		}

		@Test
		fun `삭제_대상_없으면_0건_반환`() {
			val terminalStatuses = listOf(TaskStatus.COMPLETED.code, TaskStatus.FAILED.code)
			whenever(
				taskRepository.deleteByStatusInAndCreatedAtBefore(
					eq(terminalStatuses),
					argThat { true },
				),
			).thenReturn(0)

			scheduler.cleanup()

			org.mockito.kotlin.verify(taskRepository).deleteByStatusInAndCreatedAtBefore(
				eq(terminalStatuses),
				argThat { true },
			)
		}
	}
}
