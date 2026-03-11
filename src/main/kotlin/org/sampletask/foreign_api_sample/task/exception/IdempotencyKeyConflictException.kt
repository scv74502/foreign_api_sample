package org.sampletask.foreign_api_sample.task.exception

import org.springframework.http.HttpStatus

class IdempotencyKeyConflictException(val idempotencyKey: String) :
	BusinessException(
		httpStatus = HttpStatus.CONFLICT,
		errorCode = "IDEMPOTENCY_KEY_CONFLICT",
		message = "Idempotency key conflict: $idempotencyKey",
	)
