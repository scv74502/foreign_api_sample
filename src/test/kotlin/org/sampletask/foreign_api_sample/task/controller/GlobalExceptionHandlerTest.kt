package org.sampletask.foreign_api_sample.task.controller

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.sampletask.foreign_api_sample.task.domain.TaskStatus
import org.sampletask.foreign_api_sample.task.exception.ApiKeyUnavailableException
import org.sampletask.foreign_api_sample.task.exception.IdempotencyKeyConflictException
import org.sampletask.foreign_api_sample.task.exception.InvalidTaskStateException
import org.sampletask.foreign_api_sample.task.exception.MockWorkerException
import org.sampletask.foreign_api_sample.task.exception.TaskNotFoundException
import org.springframework.http.HttpStatus

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
		val response = handler.handleSystemException(MockWorkerException(500, "error", true))
		assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_GATEWAY)
		assertThat(response.body?.code).isEqualTo("EXTERNAL_SERVICE_ERROR")
	}

	@Test
	fun `ApiKeyUnavailableException은_503_반환`() {
		val response = handler.handleSystemException(ApiKeyUnavailableException("unavailable"))
		assertThat(response.statusCode).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
		assertThat(response.body?.code).isEqualTo("API_KEY_UNAVAILABLE")
	}
}
