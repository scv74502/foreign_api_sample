package org.sampletask.foreign_api_sample.common

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.sampletask.foreign_api_sample.task.domain.TaskStatus
import org.sampletask.foreign_api_sample.task.exception.ApiKeyUnavailableException
import org.sampletask.foreign_api_sample.task.exception.IdempotencyKeyConflictException
import org.sampletask.foreign_api_sample.task.exception.InvalidTaskStateException
import org.sampletask.foreign_api_sample.task.exception.MockWorkerException
import org.sampletask.foreign_api_sample.task.exception.TaskNotFoundException
import org.springframework.http.HttpStatus
import org.springframework.validation.BindingResult
import org.springframework.validation.FieldError
import org.springframework.web.bind.MethodArgumentNotValidException

class GlobalExceptionHandlerTest {
	private val handler = GlobalExceptionHandler()

	@Test
	fun `TaskNotFoundException은_404_반환`() {
		val response = handler.handleBusinessException(TaskNotFoundException(1L))
		assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
		assertThat(response.body?.code).isEqualTo("TASK_NOT_FOUND")
	}

	@Test
	fun `IdempotencyKeyConflictException은_409_반환`() {
		val response = handler.handleBusinessException(IdempotencyKeyConflictException("key-1"))
		assertThat(response.statusCode).isEqualTo(HttpStatus.CONFLICT)
		assertThat(response.body?.code).isEqualTo("IDEMPOTENCY_KEY_CONFLICT")
	}

	@Test
	fun `InvalidTaskStateException은_409_반환`() {
		val response =
			handler.handleBusinessException(
				InvalidTaskStateException(TaskStatus.PENDING, TaskStatus.COMPLETED),
			)
		assertThat(response.statusCode).isEqualTo(HttpStatus.CONFLICT)
		assertThat(response.body?.code).isEqualTo("INVALID_TASK_STATE")
	}

	@Test
	fun `MockWorkerException은_502_반환`() {
		val response =
			handler.handleSystemException(
				MockWorkerException(500, "error", org.sampletask.foreign_api_sample.task.domain.RecoveryAction.RETRY),
			)
		assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_GATEWAY)
		assertThat(response.body?.code).isEqualTo("EXTERNAL_SERVICE_ERROR")
	}

	@Test
	fun `ApiKeyUnavailableException은_503_반환`() {
		val response = handler.handleSystemException(ApiKeyUnavailableException("unavailable"))
		assertThat(response.statusCode).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
		assertThat(response.body?.code).isEqualTo("API_KEY_UNAVAILABLE")
	}

	@Test
	fun `URL_검증_실패_시_422_반환`() {
		val bindingResult = mock(BindingResult::class.java)
		val fieldError = FieldError(
			"createTaskRequest",
			"imageUrl",
			"not-a-url",
			false,
			arrayOf("URL"),
			null,
			"imageUrl 형식이 올바르지 않습니다",
		)
		`when`(bindingResult.fieldErrors).thenReturn(listOf(fieldError))
		val exception = MethodArgumentNotValidException(
			mock(org.springframework.core.MethodParameter::class.java),
			bindingResult,
		)

		val response = handler.handleValidation(exception)

		assertThat(response.statusCode).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY)
		assertThat(response.body?.code).isEqualTo("INVALID_URL_FORMAT")
	}

	@Test
	fun `일반_검증_실패_시_400_반환`() {
		val bindingResult = mock(BindingResult::class.java)
		val fieldError = FieldError(
			"createTaskRequest",
			"imageUrl",
			"",
			false,
			arrayOf("NotBlank"),
			null,
			"imageUrl은 필수입니다",
		)
		`when`(bindingResult.fieldErrors).thenReturn(listOf(fieldError))
		val exception = MethodArgumentNotValidException(
			mock(org.springframework.core.MethodParameter::class.java),
			bindingResult,
		)

		val response = handler.handleValidation(exception)

		assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
		assertThat(response.body?.code).isEqualTo("VALIDATION_ERROR")
	}
}
