package org.sampletask.foreign_api_sample.task.service

import org.sampletask.foreign_api_sample.task.domain.TaskStatus
import org.sampletask.foreign_api_sample.task.repository.TaskRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.temporal.ChronoUnit

@Component
class IdempotencyKeyCleanupScheduler(
	private val taskRepository: TaskRepository,
	@Value("\${task.idempotency.expiry-minutes:3}") private val expiryMinutes: Long,
) {
	private val log = LoggerFactory.getLogger(javaClass)

	@Scheduled(cron = "\${task.idempotency.cleanup-cron:0 0 3 * * *}")
	@Transactional
	fun cleanup() {
		val cutoff = Instant.now().minus(expiryMinutes, ChronoUnit.MINUTES)
		val terminalStatuses = listOf(TaskStatus.COMPLETED.code, TaskStatus.FAILED.code)
		val deleted = taskRepository.deleteByStatusInAndCreatedAtBefore(terminalStatuses, cutoff)
		log.info("멱등성 키 정리: {}건 삭제", deleted)
	}
}
