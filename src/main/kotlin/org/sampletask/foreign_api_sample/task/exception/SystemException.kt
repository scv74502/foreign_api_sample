package org.sampletask.foreign_api_sample.task.exception

import org.sampletask.foreign_api_sample.common.ErrorCode
import org.springframework.http.HttpStatus

sealed class SystemException(
	val httpStatus: HttpStatus,
	val errorCode: ErrorCode,
	override val message: String,
) : RuntimeException(message)
