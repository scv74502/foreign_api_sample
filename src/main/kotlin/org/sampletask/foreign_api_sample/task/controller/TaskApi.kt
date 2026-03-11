package org.sampletask.foreign_api_sample.task.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.sampletask.foreign_api_sample.task.controller.dto.CreateTaskRequest
import org.sampletask.foreign_api_sample.task.controller.dto.TaskResponse
import org.springframework.data.domain.Page
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam

@Tag(name = "Tasks", description = "이미지 처리 작업 API")
@RequestMapping("/api/tasks")
interface TaskApi {
	@Operation(summary = "작업 생성", description = "이미지 처리 작업을 생성하고 비동기 처리를 시작합니다")
	@ApiResponses(
		ApiResponse(responseCode = "202", description = "작업 생성 완료 (비동기 처리 시작)"),
		ApiResponse(responseCode = "400", description = "잘못된 요청"),
		ApiResponse(responseCode = "409", description = "멱등성 키 충돌"),
	)
	@PostMapping
	fun createTask(
		@RequestHeader("X-Idempotency-Key") idempotencyKey: String,
		@Valid @RequestBody request: CreateTaskRequest,
	): ResponseEntity<TaskResponse>

	@Operation(summary = "작업 조회", description = "작업 ID로 작업 상태와 결과를 조회합니다")
	@ApiResponses(
		ApiResponse(responseCode = "200", description = "조회 성공"),
		ApiResponse(responseCode = "404", description = "작업을 찾을 수 없음"),
	)
	@GetMapping("/{taskId}")
	fun getTask(@PathVariable taskId: Long): ResponseEntity<TaskResponse>

	@Operation(summary = "작업 목록 조회", description = "작업 목록을 페이지네이션으로 조회합니다")
	@ApiResponse(responseCode = "200", description = "조회 성공")
	@GetMapping
	fun listTasks(
		@RequestParam(required = false) status: String?,
		@RequestParam(defaultValue = "0") page: Int,
		@RequestParam(defaultValue = "20") size: Int,
	): ResponseEntity<Page<TaskResponse>>
}
