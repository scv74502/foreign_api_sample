package org.sampletask.foreign_api_sample.task.exception

import org.springframework.http.HttpStatus

class TaskNotFoundException(val taskId: Long) :
	BusinessException(
		httpStatus = HttpStatus.NOT_FOUND,
		errorCode = "TASK_NOT_FOUND",
		message = "Task not found: $taskId",
	)
