package org.sampletask.foreign_api_sample.common.exception

import org.sampletask.foreign_api_sample.common.ErrorCode
import org.springframework.http.HttpStatus

abstract class BusinessException(
	val httpStatus: HttpStatus,
	val errorCode: ErrorCode,
	override val message: String,
) : RuntimeException(message)
