package org.sampletask.foreign_api_sample.common

enum class ErrorCode(val code: String, val messageTemplate: String) {
	TASK_NOT_FOUND("TASK_NOT_FOUND", "Task not found: %s"),
	IDEMPOTENCY_KEY_CONFLICT("IDEMPOTENCY_KEY_CONFLICT", "Idempotency key conflict: %s"),
	INVALID_TASK_STATE("INVALID_TASK_STATE", "Cannot transition from %s to %s"),
	EXTERNAL_SERVICE_ERROR("EXTERNAL_SERVICE_ERROR", "Mock Worker error: HTTP %s%s"),
	EXTERNAL_HTTP_ERROR("EXTERNAL_HTTP_ERROR", "External service HTTP error: %s"),
	API_KEY_UNAVAILABLE("API_KEY_UNAVAILABLE", "API Key unavailable: %s"),
	INVALID_URL_FORMAT("INVALID_URL_FORMAT", "%s"),
	VALIDATION_ERROR("VALIDATION_ERROR", "%s"),
	MISSING_HEADER("MISSING_HEADER", "%s"),
	BAD_REQUEST("BAD_REQUEST", "%s"),
	INTERNAL_ERROR("INTERNAL_ERROR", "%s"),
	POLLING_TIMEOUT("POLLING_TIMEOUT", "폴링 최대 시간 초과: %s"),
	UNKNOWN_TASK_STATUS("UNKNOWN_TASK_STATUS", "Unknown TaskStatus code: %s"),
	;

	fun message(vararg args: Any?): String = messageTemplate.format(*args)
}
