package org.sampletask.foreign_api_sample.task.exception

import org.sampletask.foreign_api_sample.task.domain.TaskStatus
import org.springframework.http.HttpStatus

class InvalidTaskStateException(
	val currentStatus: TaskStatus,
	val attemptedStatus: TaskStatus,
) : BusinessException(
	httpStatus = HttpStatus.CONFLICT,
	errorCode = "INVALID_TASK_STATE",
	message = "Cannot transition from $currentStatus to $attemptedStatus",
)
