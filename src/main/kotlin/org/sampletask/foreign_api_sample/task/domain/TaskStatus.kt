package org.sampletask.foreign_api_sample.task.domain

import org.sampletask.foreign_api_sample.common.ErrorCode

enum class TaskStatus(val code: Int) {
	PENDING(0), // 요청 수신 완료, 외부 서비스에 미전달
	PROCESSING(1), // 외부 서비스에 전달됨, 작업 ID 보유
	COMPLETED(2), // 처리 완료, 결과 보유
	FAILED(3), // 처리 실패 (재시도 횟수에 따라 종료 또는 재시도)
	CANCELLED(4), // 작업 취소 (향후 요구사항 대비)
	;

	fun canTransitionTo(target: TaskStatus): Boolean {
		return when (this) {
			PENDING -> target in listOf(PROCESSING, CANCELLED)
			PROCESSING -> target in listOf(COMPLETED, FAILED, CANCELLED, PENDING) // PENDING 복귀는 recovery 전용
			FAILED -> target == PENDING // 재시도 시 PENDING으로 복귀
			COMPLETED, CANCELLED -> false
		}
	}

	companion object {
		private val CODE_MAP = entries.associateBy { it.code }

		fun fromCode(code: Int): TaskStatus {
			return CODE_MAP[code]
				?: throw IllegalArgumentException(ErrorCode.UNKNOWN_TASK_STATUS.message(code))
		}
	}
}
