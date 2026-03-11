package org.sampletask.foreign_api_sample.task.exception

import org.sampletask.foreign_api_sample.common.ErrorCode
import org.springframework.http.HttpStatus

class TaskNotFoundException(val taskId: Long) :
	BusinessException(
		httpStatus = HttpStatus.NOT_FOUND,
		errorCode = ErrorCode.TASK_NOT_FOUND,
		message = ErrorCode.TASK_NOT_FOUND.message(taskId),
	)
