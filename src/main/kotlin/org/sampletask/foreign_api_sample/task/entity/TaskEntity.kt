package org.sampletask.foreign_api_sample.task.entity

import jakarta.persistence.*
import org.sampletask.foreign_api_sample.task.domain.TaskStatus
import java.time.Instant

/**
 * 작업(Task) 테이블. 외부 API 호출 작업의 생성·진행·완료 상태를 추적한다.
 */
@Entity
@Table(
	name = "task",
	indexes = [
		Index(name = "idx_idempotency_key", columnList = "idempotency_key", unique = true),
		Index(name = "idx_image_url_hash_created_at", columnList = "image_url_hash, created_at"),
	],
)
class TaskEntity(
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	val id: Long = 0,

	@Column(nullable = false, columnDefinition = "TINYINT")
	var status: Int = TaskStatus.PENDING.code,

	@Column(name = "idempotency_key", nullable = false, unique = true, length = 255)
	val idempotencyKey: String,

	@Column(name = "image_url", nullable = false, length = 2048)
	val imageUrl: String,

	@Column(name = "image_url_hash", nullable = false, columnDefinition = "CHAR(64)")
	val imageUrlHash: String = "",

	@Column(name = "external_job_id", nullable = true, length = 255)
	var externalJobId: String? = null,

	@Column(name = "retry_count", nullable = false)
	var retryCount: Int = 0,

	@Column(nullable = true, columnDefinition = "TEXT")
	var result: String? = null,

	@Column(name = "error_code", nullable = true, length = 100)
	var errorCode: String? = null,

	@Column(name = "error_message", nullable = true, columnDefinition = "TEXT")
	var errorMessage: String? = null,

	@Column(name = "created_at", nullable = false, updatable = false)
	val createdAt: Instant = Instant.now(),

	@Column(name = "updated_at", nullable = false)
	var updatedAt: Instant = Instant.now(),

	@Version
	var version: Long = 0,
)
