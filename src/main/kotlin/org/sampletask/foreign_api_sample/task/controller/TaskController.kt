package org.sampletask.foreign_api_sample.task.controller

import jakarta.validation.Valid
import org.sampletask.foreign_api_sample.task.controller.request.CreateTaskRequest
import org.sampletask.foreign_api_sample.task.controller.response.TaskResponse
import org.sampletask.foreign_api_sample.task.domain.TaskStatus
import org.sampletask.foreign_api_sample.task.service.TaskOrchestrator
import org.sampletask.foreign_api_sample.task.service.TaskService
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController

@RestController
class TaskController(
	private val taskService: TaskService,
	private val taskOrchestrator: TaskOrchestrator,
) : TaskApi {
	override fun createTask(idempotencyKey: String, @Valid request: CreateTaskRequest): ResponseEntity<TaskResponse> {
		val task = taskService.createTask(idempotencyKey, request.imageUrl)

		if (task.status == TaskStatus.PENDING) {
			taskOrchestrator.submitAsync(task)
		}

		return ResponseEntity
			.status(HttpStatus.ACCEPTED)
			.body(TaskResponse.from(task))
	}

	override fun getTask(taskId: Long): ResponseEntity<TaskResponse> {
		val task = taskService.getTask(taskId)
		return ResponseEntity.ok(TaskResponse.from(task))
	}

	override fun listTasks(status: String?, page: Int, size: Int): ResponseEntity<Page<TaskResponse>> {
		val pageSize = size.coerceAtMost(MAX_PAGE_SIZE)
		val pageable = PageRequest.of(page, pageSize, Sort.by(Sort.Direction.DESC, "createdAt"))
		val taskStatus = status?.let { TaskStatus.valueOf(it) }

		val tasks = taskService.listTasks(taskStatus, pageable)
		return ResponseEntity.ok(tasks.map { TaskResponse.from(it) })
	}

	companion object {
		private const val MAX_PAGE_SIZE = 50
	}
}
