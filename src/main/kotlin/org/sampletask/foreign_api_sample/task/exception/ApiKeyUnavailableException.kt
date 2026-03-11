package org.sampletask.foreign_api_sample.task.exception

import org.springframework.http.HttpStatus

class ApiKeyUnavailableException(msg: String) :
	SystemException(
		httpStatus = HttpStatus.SERVICE_UNAVAILABLE,
		errorCode = "API_KEY_UNAVAILABLE",
		message = msg,
	)
