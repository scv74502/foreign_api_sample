package org.sampletask.foreign_api_sample.task.repository

import org.sampletask.foreign_api_sample.task.entity.TaskEntity
import org.springframework.data.jpa.repository.JpaRepository

interface TaskRepository : JpaRepository<TaskEntity, Long>
