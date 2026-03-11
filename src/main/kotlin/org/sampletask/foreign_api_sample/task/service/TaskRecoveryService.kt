package org.sampletask.foreign_api_sample.task.service

import kotlinx.coroutines.runBlocking
import org.sampletask.foreign_api_sample.task.client.MockWorkerClient
import org.sampletask.foreign_api_sample.task.domain.RecoveryAction
import org.sampletask.foreign_api_sample.task.domain.TaskStatus
import org.sampletask.foreign_api_sample.task.entity.mapper.TaskMapper
import org.sampletask.foreign_api_sample.task.exception.MockWorkerException
import org.sampletask.foreign_api_sample.task.repository.TaskRepository
import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

@Component
class TaskRecoveryService(
	private val taskRepository: TaskRepository,
	private val taskService: TaskService,
	private val taskOrchestrator: TaskOrchestrator,
	private val mockWorkerClient: MockWorkerClient,
) {
	private val log = LoggerFactory.getLogger(javaClass)

	@EventListener(ApplicationReadyEvent::class)
	fun recoverTasks() {
		val processingAndPending =
			taskRepository.findAllByStatusIn(
				listOf(TaskStatus.PROCESSING.code, TaskStatus.PENDING.code),
			)

		if (processingAndPending.isEmpty()) {
			log.info("복구 대상 작업 없음")
			return
		}

		log.info("복구 대상 작업 {}건 발견", processingAndPending.size)

		processingAndPending.forEach { entity ->
			val task = TaskMapper.toDomain(entity)

			when (task.status) {
				TaskStatus.PROCESSING -> recoverProcessingTask(task)
				TaskStatus.PENDING -> {
					log.info("PENDING 작업 {} 재등록", task.id)
					taskOrchestrator.submitAsync(task)
				}
				else -> {}
			}
		}
	}

	private fun recoverProcessingTask(task: org.sampletask.foreign_api_sample.task.domain.Task) {
		if (task.externalJobId != null) {
			try {
				runBlocking {
					val status = mockWorkerClient.getJobStatus(task.externalJobId!!)
					when (status.status) {
						"COMPLETED" -> {
							task.result = status.result
							task.transitionTo(TaskStatus.COMPLETED)
							taskService.updateTask(task)
							log.info("PROCESSING 작업 {} 외부 상태 확인 → COMPLETED", task.id)
						}
						"FAILED" -> {
							task.errorCode = status.errorCode
							task.errorMessage = status.errorMessage
							task.transitionTo(TaskStatus.FAILED)
							taskService.updateTask(task)
							log.info("PROCESSING 작업 {} 외부 상태 확인 → FAILED", task.id)
						}
						else -> {
							log.info("PROCESSING 작업 {} 외부 상태 {} - 폴링 재개", task.id, status.status)
							taskOrchestrator.submitAsync(task)
						}
					}
				}
			} catch (e: MockWorkerException) {
				if (e.recoveryAction == RecoveryAction.REVERT_TO_PENDING) {
					log.warn("작업 {} 의 외부 Job 404 - PENDING 복귀", task.id)
					task.externalJobId = null
					task.transitionTo(TaskStatus.PENDING)
					taskService.updateTask(task)
					taskOrchestrator.submitAsync(task)
				} else {
					log.error("작업 {} 복구 중 오류: {}", task.id, e.message)
					taskOrchestrator.submitAsync(task)
				}
			} catch (e: Exception) {
				log.error("작업 {} 복구 중 예상치 못한 오류: {}", task.id, e.message, e)
				taskOrchestrator.submitAsync(task)
			}
		} else {
			log.info("PROCESSING 작업 {} 에 jobId 없음 - PENDING 복귀", task.id)
			task.transitionTo(TaskStatus.PENDING)
			taskService.updateTask(task)
			taskOrchestrator.submitAsync(task)
		}
	}
}
