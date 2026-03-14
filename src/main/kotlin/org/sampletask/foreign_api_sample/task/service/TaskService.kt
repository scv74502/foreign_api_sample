package org.sampletask.foreign_api_sample.task.service

import org.sampletask.foreign_api_sample.task.domain.Task
import org.sampletask.foreign_api_sample.task.domain.TaskStatus
import org.sampletask.foreign_api_sample.task.entity.mapper.TaskMapper
import org.sampletask.foreign_api_sample.task.exception.DuplicateImageUrlRequestException
import org.sampletask.foreign_api_sample.task.exception.IdempotencyKeyConflictException
import org.sampletask.foreign_api_sample.task.exception.TaskNotFoundException
import org.sampletask.foreign_api_sample.task.repository.TaskRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest
import java.time.Instant
import java.time.temporal.ChronoUnit

@Service
@Transactional
class TaskService(
	private val taskRepository: TaskRepository,
	@Value("\${task.duplicate-image-url.window-minutes:3}") private val duplicateImageUrlWindowMinutes: Long,
	@Value("\${task.idempotency.expiry-minutes:3}") private val idempotencyExpiryMinutes: Long,
) {
	private val log = LoggerFactory.getLogger(javaClass)

	fun createTask(idempotencyKey: String, imageUrl: String): Task {
		val imageUrlHash = sha256(imageUrl)

		// 1. imageUrl 중복 검사 (3분 윈도우)
		val duplicateImageUrlCutoff = Instant.now().minus(duplicateImageUrlWindowMinutes, ChronoUnit.MINUTES)
		val duplicateByImageUrl = taskRepository.findByImageUrlHashAndCreatedAtAfter(imageUrlHash, duplicateImageUrlCutoff)
		if (duplicateByImageUrl != null && duplicateByImageUrl.idempotencyKey != idempotencyKey) {
			throw DuplicateImageUrlRequestException(imageUrl)
		}

		// 2. 멱등성 키 검사 (3분 윈도우)
		val idempotencyCutoff = Instant.now().minus(idempotencyExpiryMinutes, ChronoUnit.MINUTES)
		val existing = taskRepository.findByIdempotencyKeyAndCreatedAtAfter(idempotencyKey, idempotencyCutoff)

		if (existing != null) {
			if (existing.imageUrl != imageUrl) {
				throw IdempotencyKeyConflictException(idempotencyKey)
			}
			log.debug("멱등성 키 {} 로 기존 작업 반환: {}", idempotencyKey, existing.id)
			return TaskMapper.toDomain(existing)
		}

		// 3. 신규 생성
		val task =
			Task(
				idempotencyKey = idempotencyKey,
				imageUrl = imageUrl,
				imageUrlHash = imageUrlHash,
			)

		return try {
			val saved = taskRepository.save(TaskMapper.toEntity(task))
			TaskMapper.toDomain(saved)
		} catch (e: DataIntegrityViolationException) {
			// UNIQUE 제약 위반 race condition 대응
			val raceWinner = taskRepository.findByIdempotencyKeyAndCreatedAtAfter(idempotencyKey, idempotencyCutoff)
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
		fun sha256(input: String): String {
			val digest = MessageDigest.getInstance("SHA-256")
			return digest.digest(input.toByteArray()).joinToString("") { "%02x".format(it) }
		}
	}
}
