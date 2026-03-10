package org.sampletask.foreign_api_sample

enum class TaskStatus {
    CREATED,
    IN_PROGRESS,
    COMPLETED,
    FAILED,
    CANCELLED;

    fun canTransitionTo(target: TaskStatus): Boolean {
        return when (this) {
            CREATED -> target in listOf(IN_PROGRESS, CANCELLED)
            IN_PROGRESS -> target in listOf(COMPLETED, FAILED, CANCELLED)
            FAILED -> target == IN_PROGRESS
            COMPLETED, CANCELLED -> false
        }
    }
}
