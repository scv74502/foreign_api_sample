package org.sampletask.foreign_api_sample.task.domain

import java.time.Instant

class Task(
	val id: Long = 0,
	var status: TaskStatus = TaskStatus.PENDING,
	val idempotencyKey: String,
	val imageUrl: String,
	var externalJobId: String? = null,
	var retryCount: Int = 0,
	var result: String? = null,
	var errorCode: String? = null,
	var errorMessage: String? = null,
	val createdAt: Instant = Instant.now(),
	var updatedAt: Instant = Instant.now(),
	val version: Long = 0,
) {
	fun transitionTo(taskStatus: TaskStatus) {
		if (!status.canTransitionTo(taskStatus)) {
			throw IllegalStateException("Cannot transition from $status to $taskStatus")
		}
		status = taskStatus
		updatedAt = Instant.now()
	}
}
