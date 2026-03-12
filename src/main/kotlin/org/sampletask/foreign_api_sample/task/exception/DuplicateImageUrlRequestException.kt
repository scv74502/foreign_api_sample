package org.sampletask.foreign_api_sample.task.exception

import org.sampletask.foreign_api_sample.common.ErrorCode
import org.sampletask.foreign_api_sample.common.exception.BusinessException
import org.springframework.http.HttpStatus

class DuplicateImageUrlRequestException(val imageUrl: String) :
	BusinessException(
		httpStatus = HttpStatus.CONFLICT,
		errorCode = ErrorCode.DUPLICATE_IMAGE_URL,
		message = ErrorCode.DUPLICATE_IMAGE_URL.message(imageUrl),
	)
