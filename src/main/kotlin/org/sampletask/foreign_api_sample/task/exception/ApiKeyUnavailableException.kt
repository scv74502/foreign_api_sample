package org.sampletask.foreign_api_sample.task.exception

import org.sampletask.foreign_api_sample.common.ErrorCode
import org.sampletask.foreign_api_sample.common.exception.SystemException
import org.springframework.http.HttpStatus

class ApiKeyUnavailableException(detail: String) :
	SystemException(
		httpStatus = HttpStatus.SERVICE_UNAVAILABLE,
		errorCode = ErrorCode.API_KEY_UNAVAILABLE,
		message = ErrorCode.API_KEY_UNAVAILABLE.message(detail),
	)
