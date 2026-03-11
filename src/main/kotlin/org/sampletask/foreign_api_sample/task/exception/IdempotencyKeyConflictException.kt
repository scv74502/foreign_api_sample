package org.sampletask.foreign_api_sample.task.exception

import org.sampletask.foreign_api_sample.common.ErrorCode
import org.sampletask.foreign_api_sample.common.exception.BusinessException
import org.springframework.http.HttpStatus

class IdempotencyKeyConflictException(val idempotencyKey: String) :
	BusinessException(
		httpStatus = HttpStatus.CONFLICT,
		errorCode = ErrorCode.IDEMPOTENCY_KEY_CONFLICT,
		message = ErrorCode.IDEMPOTENCY_KEY_CONFLICT.message(idempotencyKey),
	)
