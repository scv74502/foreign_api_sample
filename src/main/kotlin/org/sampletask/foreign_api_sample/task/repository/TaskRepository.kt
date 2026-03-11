package org.sampletask.foreign_api_sample.task.repository

import org.sampletask.foreign_api_sample.task.entity.TaskEntity
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import java.time.Instant

interface TaskRepository : JpaRepository<TaskEntity, Long> {
	fun findByIdempotencyKeyAndCreatedAtAfter(idempotencyKey: String, cutoff: Instant): TaskEntity?

	fun findByStatus(status: Int, pageable: Pageable): Page<TaskEntity>

	fun findAllByStatusIn(statuses: List<Int>): List<TaskEntity>
}
