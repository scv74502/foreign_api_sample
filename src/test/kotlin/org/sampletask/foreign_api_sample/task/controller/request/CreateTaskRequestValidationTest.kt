package org.sampletask.foreign_api_sample.task.controller.request

import jakarta.validation.Validation
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class CreateTaskRequestValidationTest {
	private val validator = Validation.buildDefaultValidatorFactory().validator

	@Test
	fun `유효한_URL이면_검증_통과`() {
		val request = CreateTaskRequest(imageUrl = "https://example.com/image.png")
		val violations = validator.validate(request)
		assertThat(violations).isEmpty()
	}

	@Test
	fun `빈_문자열이면_NotBlank_위반`() {
		val request = CreateTaskRequest(imageUrl = "")
		val violations = validator.validate(request)
		assertThat(violations).isNotEmpty
		assertThat(violations.map { it.message }).contains("imageUrl은 필수입니다")
	}

	@Test
	fun `잘못된_URL_포맷이면_URL_위반`() {
		val request = CreateTaskRequest(imageUrl = "not-a-url")
		val violations = validator.validate(request)
		assertThat(violations).isNotEmpty
		assertThat(violations.map { it.message }).contains("imageUrl 형식이 올바르지 않습니다")
	}
}
