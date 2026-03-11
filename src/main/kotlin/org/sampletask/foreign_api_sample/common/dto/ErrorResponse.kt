package org.sampletask.foreign_api_sample.common.dto

import io.swagger.v3.oas.annotations.media.Schema
import java.time.Instant

@Schema(description = "에러 응답")
data class ErrorResponse(
	@Schema(description = "에러 코드")
	val code: String,
	@Schema(description = "에러 메시지")
	val message: String,
	@Schema(description = "발생 시각")
	val timestamp: Instant = Instant.now(),
)
