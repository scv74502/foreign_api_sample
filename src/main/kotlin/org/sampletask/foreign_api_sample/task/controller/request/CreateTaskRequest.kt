package org.sampletask.foreign_api_sample.task.controller.request

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank

@Schema(description = "작업 생성 요청")
data class CreateTaskRequest(
	@field:NotBlank(message = "imageUrl은 필수입니다")
	@Schema(description = "처리할 이미지 URL", example = "https://example.com/image.png")
	val imageUrl: String,
)
