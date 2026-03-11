package org.sampletask.foreign_api_sample.task.exception

import org.sampletask.foreign_api_sample.common.ErrorCode
import org.sampletask.foreign_api_sample.common.exception.BusinessException
import org.sampletask.foreign_api_sample.task.domain.TaskStatus
import org.springframework.http.HttpStatus

class InvalidTaskStateException(
	val currentStatus: TaskStatus,
	val attemptedStatus: TaskStatus,
) : BusinessException(
	httpStatus = HttpStatus.CONFLICT,
	errorCode = ErrorCode.INVALID_TASK_STATE,
	message = ErrorCode.INVALID_TASK_STATE.message(currentStatus, attemptedStatus),
)
