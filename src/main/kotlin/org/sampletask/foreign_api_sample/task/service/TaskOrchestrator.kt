package org.sampletask.foreign_api_sample.task.service

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import org.sampletask.foreign_api_sample.common.ErrorCode
import org.sampletask.foreign_api_sample.task.client.MockWorkerClient
import org.sampletask.foreign_api_sample.task.domain.RecoveryAction
import org.sampletask.foreign_api_sample.task.domain.Task
import org.sampletask.foreign_api_sample.task.domain.TaskStatus
import org.sampletask.foreign_api_sample.task.exception.MockWorkerException
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class TaskOrchestrator(
	private val taskService: TaskService,
	private val mockWorkerClient: MockWorkerClient,
	private val scope: CoroutineScope,
	@Value("\${task.max-retry-count:3}") private val maxRetryCount: Int,
	@Value("\${task.polling.initial-interval-ms:2000}") private val initialIntervalMs: Long,
	@Value("\${task.polling.max-interval-ms:10000}") private val maxIntervalMs: Long,
	@Value("\${task.polling.multiplier:2.0}") private val multiplier: Double,
	@Value("\${task.polling.max-concurrent:5}") private val maxConcurrentPolling: Int,
) {
	private val log = LoggerFactory.getLogger(javaClass)
	private val pollingSemaphore = Semaphore(maxConcurrentPolling)

	fun submitAsync(task: Task, delayMs: Long? = null) {
		scope.launch {
			if (delayMs != null) delay(delayMs)
			processTask(task)
		}
	}

	suspend fun processTask(task: Task) {
		val taskId = task.id
		try {
			var current = taskService.getTask(taskId)
			if (current.status != TaskStatus.PENDING) {
				log.debug("작업 {} 이미 {} 상태 - 처리 건너뜀", taskId, current.status)
				return
			}

			current.transitionTo(TaskStatus.PROCESSING)
			current = taskService.updateTask(current)

			val processResponse = mockWorkerClient.submitProcess(current.imageUrl)
			current.externalJobId = processResponse.jobId
			current = taskService.updateTask(current)

			pollForResult(current)
		} catch (e: MockWorkerException) {
			handleError(taskId, e)
		} catch (e: Exception) {
			log.error("작업 {} 처리 중 예상치 못한 오류: {}", taskId, e.message, e)
			failTask(taskId, ErrorCode.INTERNAL_ERROR.code, e.message)
		}
	}

	private suspend fun pollForResult(task: Task) {
		val jobId = task.externalJobId ?: return
		val taskId = task.id
		var intervalMs = initialIntervalMs

		pollingSemaphore.acquire()
		try {
			while (true) {
				val jitter = intervalMs * (0.5 + Math.random() * 0.5)
				delay(jitter.toLong())

				try {
					val status = mockWorkerClient.getJobStatus(jobId)

					when (status.status) {
						"COMPLETED" -> {
							val current = taskService.getTask(taskId)
							current.result = status.result
							current.transitionTo(TaskStatus.COMPLETED)
							taskService.updateTask(current)
							log.info("작업 {} 완료", taskId)
							return
						}
						"FAILED" -> {
							failTask(taskId, status.errorCode, status.errorMessage)
							return
						}
						else -> {
							intervalMs = (intervalMs * multiplier).toLong().coerceAtMost(maxIntervalMs)
						}
					}
				} catch (e: MockWorkerException) {
					handleError(taskId, e)
					return
				}
			}
		} finally {
			pollingSemaphore.release()
		}
	}

	private fun handleError(taskId: Long, e: MockWorkerException) {
		when (e.recoveryAction) {
			RecoveryAction.RETRY -> {
				val current = taskService.getTask(taskId)
				if (current.retryCount < maxRetryCount) {
					current.retryCount++
					current.transitionTo(TaskStatus.PENDING)
					val pending = taskService.updateTask(current)
					log.warn("작업 {} 일시적 오류 (재시도 {}/{}): {}", taskId, pending.retryCount, maxRetryCount, e.message)
					submitAsync(pending)
				} else {
					failTask(
						taskId,
						ErrorCode.EXTERNAL_HTTP_ERROR.code,
						ErrorCode.EXTERNAL_HTTP_ERROR.message(e.upstreamHttpStatus),
					)
				}
			}
			RecoveryAction.REVERT_TO_PENDING -> {
				val current = taskService.getTask(taskId)
				current.externalJobId = null
				current.transitionTo(TaskStatus.PENDING)
				val updated = taskService.updateTask(current)
				submitAsync(updated)
				log.warn("작업 {} 404 응답 - PENDING 복귀", taskId)
			}
			RecoveryAction.FAIL -> {
				failTask(
					taskId,
					ErrorCode.EXTERNAL_HTTP_ERROR.code,
					ErrorCode.EXTERNAL_HTTP_ERROR.message(e.upstreamHttpStatus),
				)
			}
		}
	}

	private fun failTask(taskId: Long, errorCode: String?, errorMessage: String?) {
		try {
			val current = taskService.getTask(taskId)
			current.errorCode = errorCode
			current.errorMessage = errorMessage
			if (current.status != TaskStatus.FAILED) {
				current.transitionTo(TaskStatus.FAILED)
			}
			taskService.updateTask(current)
			log.error("작업 {} 최종 실패: {} - {}", taskId, errorCode, errorMessage)
		} catch (e: Exception) {
			log.error("작업 {} 실패 상태 저장 중 오류: {}", taskId, e.message)
		}
	}
}
