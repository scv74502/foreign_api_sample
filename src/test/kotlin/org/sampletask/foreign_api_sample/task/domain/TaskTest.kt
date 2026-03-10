package org.sampletask.foreign_api_sample.task.domain

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class TaskTest {

    private fun createTask(status: TaskStatus = TaskStatus.PENDING): Task {
        return Task(
            idempotencyKey = "test-key",
            imageUrl = "https://example.com/image.png",
            status = status
        )
    }

    @Test
    fun `유효한 전이 - PENDING에서 PROCESSING으로 상태 변경`() {
        val task = createTask(TaskStatus.PENDING)

        task.transitionTo(TaskStatus.PROCESSING)

        assertThat(task.status).isEqualTo(TaskStatus.PROCESSING)
    }

    @Test
    fun `유효한 전이 - PROCESSING에서 COMPLETED로 상태 변경`() {
        val task = createTask(TaskStatus.PROCESSING)

        task.transitionTo(TaskStatus.COMPLETED)

        assertThat(task.status).isEqualTo(TaskStatus.COMPLETED)
    }

    @Test
    fun `유효한 전이 - PROCESSING에서 FAILED로 상태 변경`() {
        val task = createTask(TaskStatus.PROCESSING)

        task.transitionTo(TaskStatus.FAILED)

        assertThat(task.status).isEqualTo(TaskStatus.FAILED)
    }

    @Test
    fun `유효한 전이 - FAILED에서 PENDING으로 상태 변경`() {
        val task = createTask(TaskStatus.FAILED)

        task.transitionTo(TaskStatus.PENDING)

        assertThat(task.status).isEqualTo(TaskStatus.PENDING)
    }

    @Test
    fun `무효한 전이 - PENDING에서 COMPLETED로 전이 시 예외 발생`() {
        val task = createTask(TaskStatus.PENDING)

        assertThatThrownBy { task.transitionTo(TaskStatus.COMPLETED) }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("Cannot transition from PENDING to COMPLETED")
    }

    @Test
    fun `무효한 전이 - COMPLETED에서 PENDING으로 전이 시 예외 발생`() {
        val task = createTask(TaskStatus.COMPLETED)

        assertThatThrownBy { task.transitionTo(TaskStatus.PENDING) }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("Cannot transition from COMPLETED to PENDING")
    }

    @Test
    fun `전이 성공 시 updatedAt이 갱신된다`() {
        val task = createTask(TaskStatus.PENDING)
        val beforeTransition = task.updatedAt

        Thread.sleep(10)
        task.transitionTo(TaskStatus.PROCESSING)

        assertThat(task.updatedAt).isAfter(beforeTransition)
    }

    @Test
    fun `전이 실패 시 상태가 변경되지 않는다`() {
        val task = createTask(TaskStatus.PENDING)

        assertThatThrownBy { task.transitionTo(TaskStatus.COMPLETED) }
            .isInstanceOf(IllegalStateException::class.java)

        assertThat(task.status).isEqualTo(TaskStatus.PENDING)
    }
}
