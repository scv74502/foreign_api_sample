package org.sampletask.foreign_api_sample

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "task")
class Task(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: TaskStatus = TaskStatus.CREATED,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now(),

    @Version
    val version: Long = 0
) {
    fun transitionTo(newStatus: TaskStatus) {
        if (!status.canTransitionTo(newStatus)) {
            throw IllegalStateException("Cannot transition from $status to $newStatus")
        }
        status = newStatus
        updatedAt = LocalDateTime.now()
    }
}
