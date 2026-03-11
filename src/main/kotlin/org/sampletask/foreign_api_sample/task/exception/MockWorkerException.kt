package org.sampletask.foreign_api_sample.task.exception

import org.sampletask.foreign_api_sample.common.ErrorCode
import org.sampletask.foreign_api_sample.common.exception.SystemException
import org.sampletask.foreign_api_sample.task.domain.RecoveryAction
import org.springframework.http.HttpStatus

class MockWorkerException(
	val upstreamHttpStatus: Int,
	val errorBody: String?,
	val recoveryAction: RecoveryAction,
	val retryAfterMs: Long? = null,
) : SystemException(
	httpStatus = HttpStatus.BAD_GATEWAY,
	errorCode = ErrorCode.EXTERNAL_SERVICE_ERROR,
	message = ErrorCode.EXTERNAL_SERVICE_ERROR.message(upstreamHttpStatus, if (errorBody != null) " - $errorBody" else ""),
)
