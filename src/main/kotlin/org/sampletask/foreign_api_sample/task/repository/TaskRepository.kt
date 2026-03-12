package org.sampletask.foreign_api_sample.task.repository

import org.sampletask.foreign_api_sample.task.entity.TaskEntity
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import java.time.Instant

interface TaskRepository : JpaRepository<TaskEntity, Long> {
	fun findByIdempotencyKeyAndCreatedAtAfter(idempotencyKey: String, cutoff: Instant): TaskEntity?

	fun findByImageUrlHashAndCreatedAtAfter(imageUrlHash: String, cutoff: Instant): TaskEntity?

	fun findByStatus(status: Int, pageable: Pageable): Page<TaskEntity>

	fun findAllByStatusIn(statuses: List<Int>): List<TaskEntity>

	@Modifying
	@Query("DELETE FROM TaskEntity t WHERE t.status IN :statuses AND t.createdAt < :cutoff")
	fun deleteByStatusInAndCreatedAtBefore(statuses: List<Int>, cutoff: Instant): Int
}
