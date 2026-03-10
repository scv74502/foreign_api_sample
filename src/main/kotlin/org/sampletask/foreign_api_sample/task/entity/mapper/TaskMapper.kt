package org.sampletask.foreign_api_sample.task.entity.mapper

import org.sampletask.foreign_api_sample.task.domain.Task
import org.sampletask.foreign_api_sample.task.domain.TaskStatus
import org.sampletask.foreign_api_sample.task.entity.TaskEntity

object TaskMapper {

    fun toDomain(entity: TaskEntity): Task {
        return Task(
            id = entity.id,
            status = TaskStatus.fromCode(entity.status),
            idempotencyKey = entity.idempotencyKey,
            imageUrl = entity.imageUrl,
            externalJobId = entity.externalJobId,
            retryCount = entity.retryCount,
            result = entity.result,
            errorCode = entity.errorCode,
            errorMessage = entity.errorMessage,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt,
            version = entity.version
        )
    }

    fun toEntity(domain: Task): TaskEntity {
        return TaskEntity(
            id = domain.id,
            status = domain.status.code,
            idempotencyKey = domain.idempotencyKey,
            imageUrl = domain.imageUrl,
            externalJobId = domain.externalJobId,
            retryCount = domain.retryCount,
            result = domain.result,
            errorCode = domain.errorCode,
            errorMessage = domain.errorMessage,
            createdAt = domain.createdAt,
            updatedAt = domain.updatedAt,
            version = domain.version
        )
    }
}
