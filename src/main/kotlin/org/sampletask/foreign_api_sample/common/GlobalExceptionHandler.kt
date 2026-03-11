package org.sampletask.foreign_api_sample.common

import org.sampletask.foreign_api_sample.common.exception.BusinessException
import org.sampletask.foreign_api_sample.common.exception.ErrorResponse
import org.sampletask.foreign_api_sample.common.exception.SystemException
import org.sampletask.foreign_api_sample.task.exception.ApiKeyUnavailableException
import org.sampletask.foreign_api_sample.task.exception.IdempotencyKeyConflictException
import org.sampletask.foreign_api_sample.task.exception.InvalidTaskStateException
import org.sampletask.foreign_api_sample.task.exception.MockWorkerException
import org.sampletask.foreign_api_sample.task.exception.TaskNotFoundException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.MissingRequestHeaderException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class GlobalExceptionHandler {
	private val log = LoggerFactory.getLogger(javaClass)

	@ExceptionHandler(BusinessException::class)
	fun handleBusinessException(e: BusinessException): ResponseEntity<ErrorResponse> {
		when (e) {
			is TaskNotFoundException -> log.debug("Task not found: {}", e.taskId)
			is IdempotencyKeyConflictException -> log.debug("Idempotency key conflict: {}", e.idempotencyKey)
			is InvalidTaskStateException -> log.debug("Invalid task state: {} -> {}", e.currentStatus, e.attemptedStatus)
			else -> log.debug("Business error: {}", e.message)
		}
		return ResponseEntity
			.status(e.httpStatus)
			.body(ErrorResponse(code = e.errorCode.code, message = e.message))
	}

	@ExceptionHandler(SystemException::class)
	fun handleSystemException(e: SystemException): ResponseEntity<ErrorResponse> {
		when (e) {
			is MockWorkerException -> log.error("외부 서비스 오류: {}", e.message)
			is ApiKeyUnavailableException -> log.error("API Key 사용 불가: {}", e.message)
			else -> log.error("System error: {}", e.message)
		}
		return ResponseEntity
			.status(e.httpStatus)
			.body(ErrorResponse(code = e.errorCode.code, message = e.message))
	}

	@ExceptionHandler(MethodArgumentNotValidException::class)
	fun handleValidation(e: MethodArgumentNotValidException): ResponseEntity<ErrorResponse> {
		val hasUrlViolation = e.bindingResult.fieldErrors.any { it.code == "URL" }
		val message =
			e.bindingResult.fieldErrors.joinToString(", ") {
				"${it.field}: ${it.defaultMessage}"
			}
		if (hasUrlViolation) {
			return ResponseEntity
				.status(HttpStatus.UNPROCESSABLE_ENTITY)
				.body(ErrorResponse(code = ErrorCode.INVALID_URL_FORMAT.code, message = message))
		}
		return ResponseEntity
			.status(HttpStatus.BAD_REQUEST)
			.body(ErrorResponse(code = ErrorCode.VALIDATION_ERROR.code, message = message))
	}

	@ExceptionHandler(MissingRequestHeaderException::class)
	fun handleMissingHeader(e: MissingRequestHeaderException): ResponseEntity<ErrorResponse> {
		return ResponseEntity
			.status(HttpStatus.BAD_REQUEST)
			.body(ErrorResponse(code = ErrorCode.MISSING_HEADER.code, message = e.message))
	}

	@ExceptionHandler(IllegalArgumentException::class)
	fun handleIllegalArgument(e: IllegalArgumentException): ResponseEntity<ErrorResponse> {
		return ResponseEntity
			.status(HttpStatus.BAD_REQUEST)
			.body(
				ErrorResponse(
					code = ErrorCode.BAD_REQUEST.code,
					message = e.message ?: ErrorCode.BAD_REQUEST.message("Bad request"),
				),
			)
	}
}
