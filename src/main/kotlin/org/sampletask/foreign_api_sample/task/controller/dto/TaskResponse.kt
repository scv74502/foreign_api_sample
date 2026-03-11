package org.sampletask.foreign_api_sample.task.controller.dto

import io.swagger.v3.oas.annotations.media.Schema
import org.sampletask.foreign_api_sample.task.domain.Task
import java.time.Instant

@Schema(description = "작업 응답")
data class TaskResponse(
	@Schema(description = "작업 ID")
	val id: Long,
	@Schema(description = "작업 상태", example = "PENDING")
	val status: String,
	@Schema(description = "이미지 URL")
	val imageUrl: String,
	@Schema(description = "처리 결과", nullable = true)
	val result: String?,
	@Schema(description = "에러 코드", nullable = true)
	val errorCode: String?,
	@Schema(description = "에러 메시지", nullable = true)
	val errorMessage: String?,
	@Schema(description = "생성 시각")
	val createdAt: Instant,
	@Schema(description = "수정 시각")
	val updatedAt: Instant,
) {
	companion object {
		fun from(task: Task): TaskResponse {
			return TaskResponse(
				id = task.id,
				status = task.status.name,
				imageUrl = task.imageUrl,
				result = task.result,
				errorCode = task.errorCode,
				errorMessage = task.errorMessage,
				createdAt = task.createdAt,
				updatedAt = task.updatedAt,
			)
		}
	}
}
