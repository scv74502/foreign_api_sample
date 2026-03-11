package org.sampletask.foreign_api_sample.task.service

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.sampletask.foreign_api_sample.task.domain.TaskStatus
import org.sampletask.foreign_api_sample.task.entity.TaskEntity
import org.sampletask.foreign_api_sample.task.exception.IdempotencyKeyConflictException
import org.sampletask.foreign_api_sample.task.exception.TaskNotFoundException
import org.sampletask.foreign_api_sample.task.repository.TaskRepository
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import java.time.Instant
import java.util.Optional

@ExtendWith(MockitoExtension::class)
class TaskServiceTest {
	@Mock
	private lateinit var taskRepository: TaskRepository

	@InjectMocks
	private lateinit var taskService: TaskService

	private fun createEntity(
		id: Long = 1L,
		status: Int = TaskStatus.PENDING.code,
		idempotencyKey: String = "test-key",
		imageUrl: String = "https://example.com/image.png",
	): TaskEntity {
		return TaskEntity(
			id = id,
			status = status,
			idempotencyKey = idempotencyKey,
			imageUrl = imageUrl,
			createdAt = Instant.now(),
			updatedAt = Instant.now(),
		)
	}

	@Test
	fun `createTask_-_새로운_작업_생성`() {
		val entity = createEntity()
		whenever(taskRepository.findByIdempotencyKeyAndCreatedAtAfter(any(), any())).thenReturn(null)
		whenever(taskRepository.save(any<TaskEntity>())).thenReturn(entity)

		val task = taskService.createTask("test-key", "https://example.com/image.png")

		assertThat(task.id).isEqualTo(1L)
		assertThat(task.status).isEqualTo(TaskStatus.PENDING)
	}

	@Test
	fun `createTask_-_멱등성_키로_기존_작업_반환`() {
		val entity = createEntity(id = 42L)
		whenever(taskRepository.findByIdempotencyKeyAndCreatedAtAfter(any(), any())).thenReturn(entity)

		val task = taskService.createTask("test-key", "https://example.com/image.png")

		assertThat(task.id).isEqualTo(42L)
	}

	@Test
	fun `createTask_-_동일_멱등성_키_다른_imageUrl_시_409_Conflict`() {
		val entity = createEntity(id = 42L, imageUrl = "https://example.com/original.png")
		whenever(taskRepository.findByIdempotencyKeyAndCreatedAtAfter(any(), any())).thenReturn(entity)

		assertThatThrownBy { taskService.createTask("test-key", "https://example.com/different.png") }
			.isInstanceOf(IdempotencyKeyConflictException::class.java)
	}

	@Test
	fun `getTask_-_존재하는_작업_조회`() {
		val entity = createEntity()
		whenever(taskRepository.findById(1L)).thenReturn(Optional.of(entity))

		val task = taskService.getTask(1L)

		assertThat(task.id).isEqualTo(1L)
	}

	@Test
	fun `getTask_-_존재하지_않는_작업_조회_시_예외`() {
		whenever(taskRepository.findById(999L)).thenReturn(Optional.empty())

		assertThatThrownBy { taskService.getTask(999L) }
			.isInstanceOf(TaskNotFoundException::class.java)
	}

	@Test
	fun `listTasks_-_페이지네이션_조회`() {
		val pageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt"))
		val entities = listOf(createEntity(id = 1L), createEntity(id = 2L, idempotencyKey = "key-2"))
		whenever(taskRepository.findAll(pageable)).thenReturn(PageImpl(entities, pageable, 2))

		val page = taskService.listTasks(null, pageable)

		assertThat(page.content).hasSize(2)
	}

	@Test
	fun `listTasks_-_상태_필터_조회`() {
		val pageable = PageRequest.of(0, 10)
		val entities = listOf(createEntity(status = TaskStatus.COMPLETED.code))
		whenever(taskRepository.findByStatus(TaskStatus.COMPLETED.code, pageable))
			.thenReturn(PageImpl(entities, pageable, 1))

		val page = taskService.listTasks(TaskStatus.COMPLETED, pageable)

		assertThat(page.content).hasSize(1)
	}
}
