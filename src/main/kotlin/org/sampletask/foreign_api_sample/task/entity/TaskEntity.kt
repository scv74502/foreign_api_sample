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
        Index(name = "idx_idempotency_key", columnList = "idempotency_key", unique = true)
    ]
)
class TaskEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(nullable = false, columnDefinition = "TINYINT", comment = "작업 상태 (TaskStatus enum의 code 값)")
    var status: Int = TaskStatus.PENDING.code,

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 255, comment = "멱등성 키 (중복 요청 방지)")
    val idempotencyKey: String,

    @Column(name = "image_url", nullable = false, length = 2048, comment = "처리 대상 이미지 URL")
    val imageUrl: String,

    @Column(name = "external_job_id", nullable = true, length = 255, comment = "외부 서비스(Mock Worker)에서 발급받은 작업 ID")
    var externalJobId: String? = null,

    @Column(name = "retry_count", nullable = false, comment = "재시도 횟수")
    var retryCount: Int = 0,

    @Column(nullable = true, columnDefinition = "TEXT", comment = "처리 결과 (완료 시)")
    var result: String? = null,

    @Column(name = "error_code", nullable = true, length = 100, comment = "에러 코드 (실패 시)")
    var errorCode: String? = null,

    @Column(name = "error_message", nullable = true, columnDefinition = "TEXT", comment = "에러 메시지 (실패 시)")
    var errorMessage: String? = null,

    @Column(name = "created_at", nullable = false, updatable = false, comment = "생성 시각")
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false, comment = "수정 시각")
    var updatedAt: Instant = Instant.now(),

    @Version
    val version: Long = 0
)
