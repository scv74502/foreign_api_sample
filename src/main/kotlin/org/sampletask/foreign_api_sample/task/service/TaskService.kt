package org.sampletask.foreign_api_sample.task.service

import org.sampletask.foreign_api_sample.task.domain.Task
import org.sampletask.foreign_api_sample.task.domain.TaskStatus
import org.sampletask.foreign_api_sample.task.entity.mapper.TaskMapper
import org.sampletask.foreign_api_sample.task.exception.IdempotencyKeyConflictException
import org.sampletask.foreign_api_sample.task.exception.TaskNotFoundException
import org.sampletask.foreign_api_sample.task.repository.TaskRepository
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.temporal.ChronoUnit

@Service
@Transactional
class TaskService(
	private val taskRepository: TaskRepository,
) {
	private val log = LoggerFactory.getLogger(javaClass)

	fun createTask(idempotencyKey: String, imageUrl: String): Task {
		val cutoff = Instant.now().minus(IDEMPOTENCY_EXPIRY_HOURS, ChronoUnit.HOURS)
		val existing = taskRepository.findByIdempotencyKeyAndCreatedAtAfter(idempotencyKey, cutoff)

		if (existing != null) {
			if (existing.imageUrl != imageUrl) {
				throw IdempotencyKeyConflictException(idempotencyKey)
			}
			log.debug("멱등성 키 {} 로 기존 작업 반환: {}", idempotencyKey, existing.id)
			return TaskMapper.toDomain(existing)
		}

		val task =
			Task(
				idempotencyKey = idempotencyKey,
				imageUrl = imageUrl,
			)

		return try {
			val saved = taskRepository.save(TaskMapper.toEntity(task))
			TaskMapper.toDomain(saved)
		} catch (e: DataIntegrityViolationException) {
			// UNIQUE 제약 위반 race condition 대응
			val raceWinner = taskRepository.findByIdempotencyKeyAndCreatedAtAfter(idempotencyKey, cutoff)
			if (raceWinner != null) {
				if (raceWinner.imageUrl != imageUrl) {
					throw IdempotencyKeyConflictException(idempotencyKey)
				}
				TaskMapper.toDomain(raceWinner)
			} else {
				throw IdempotencyKeyConflictException(idempotencyKey)
			}
		}
	}

	@Transactional(readOnly = true)
	fun getTask(taskId: Long): Task {
		val entity =
			taskRepository.findById(taskId)
				.orElseThrow { TaskNotFoundException(taskId) }
		return TaskMapper.toDomain(entity)
	}

	@Transactional(readOnly = true)
	fun listTasks(status: TaskStatus?, pageable: Pageable): Page<Task> {
		val page =
			if (status != null) {
				taskRepository.findByStatus(status.code, pageable)
			} else {
				taskRepository.findAll(pageable)
			}
		return page.map { TaskMapper.toDomain(it) }
	}

	fun updateTask(task: Task): Task {
		val entity =
			taskRepository.findById(task.id)
				.orElseThrow { TaskNotFoundException(task.id) }

		entity.status = task.status.code
		entity.externalJobId = task.externalJobId
		entity.retryCount = task.retryCount
		entity.result = task.result
		entity.errorCode = task.errorCode
		entity.errorMessage = task.errorMessage
		entity.updatedAt = task.updatedAt

		val saved = taskRepository.save(entity)
		return TaskMapper.toDomain(saved)
	}

	companion object {
		private const val IDEMPOTENCY_EXPIRY_HOURS = 24L
	}
}
