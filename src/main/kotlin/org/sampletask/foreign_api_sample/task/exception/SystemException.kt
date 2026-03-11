package org.sampletask.foreign_api_sample.task.exception

import org.springframework.http.HttpStatus

sealed class SystemException(
	val httpStatus: HttpStatus,
	val errorCode: String,
	override val message: String,
) : RuntimeException(message)
