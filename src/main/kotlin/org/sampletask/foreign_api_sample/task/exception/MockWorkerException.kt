package org.sampletask.foreign_api_sample.task.exception

import org.springframework.http.HttpStatus

class MockWorkerException(
	val upstreamHttpStatus: Int,
	val errorBody: String?,
	val isTransient: Boolean,
) : SystemException(
	httpStatus = HttpStatus.BAD_GATEWAY,
	errorCode = "EXTERNAL_SERVICE_ERROR",
	message = "Mock Worker error: HTTP $upstreamHttpStatus${if (errorBody != null) " - $errorBody" else ""}",
)
