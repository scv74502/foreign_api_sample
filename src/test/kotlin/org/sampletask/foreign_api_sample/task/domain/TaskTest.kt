package org.sampletask.foreign_api_sample.task.domain

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.sampletask.foreign_api_sample.task.exception.InvalidTaskStateException

class TaskTest {

	private fun createTask(status: TaskStatus = TaskStatus.PENDING): Task {
		return Task(
			idempotencyKey = "test-key",
			imageUrl = "https://example.com/image.png",
			status = status,
		)
	}

	@Suppress("ClassName")
	@Nested
	inner class 유효한_전이 {

		@Test
		fun `PENDING에서_PROCESSING으로_상태_변경`() {
			val task = createTask(TaskStatus.PENDING)

			task.transitionTo(TaskStatus.PROCESSING)

			assertThat(task.status).isEqualTo(TaskStatus.PROCESSING)
		}

		@Test
		fun `PROCESSING에서_COMPLETED로_상태_변경`() {
			val task = createTask(TaskStatus.PROCESSING)

			task.transitionTo(TaskStatus.COMPLETED)

			assertThat(task.status).isEqualTo(TaskStatus.COMPLETED)
		}

		@Test
		fun `PROCESSING에서_FAILED로_상태_변경`() {
			val task = createTask(TaskStatus.PROCESSING)

			task.transitionTo(TaskStatus.FAILED)

			assertThat(task.status).isEqualTo(TaskStatus.FAILED)
		}

		@Test
		fun `FAILED에서_PENDING으로_상태_변경`() {
			val task = createTask(TaskStatus.FAILED)

			task.transitionTo(TaskStatus.PENDING)

			assertThat(task.status).isEqualTo(TaskStatus.PENDING)
		}
	}

	@Suppress("ClassName")
	@Nested
	inner class 무효한_전이 {

		@Test
		fun `PENDING에서_COMPLETED로_전이_시_예외_발생`() {
			val task = createTask(TaskStatus.PENDING)

			assertThatThrownBy { task.transitionTo(TaskStatus.COMPLETED) }
				.isInstanceOf(InvalidTaskStateException::class.java)
				.hasMessageContaining("Cannot transition from PENDING to COMPLETED")
		}

		@Test
		fun `COMPLETED에서_PENDING으로_전이_시_예외_발생`() {
			val task = createTask(TaskStatus.COMPLETED)

			assertThatThrownBy { task.transitionTo(TaskStatus.PENDING) }
				.isInstanceOf(InvalidTaskStateException::class.java)
				.hasMessageContaining("Cannot transition from COMPLETED to PENDING")
		}
	}

	@Suppress("ClassName")
	@Nested
	inner class 전이_부수효과 {

		@Test
		fun `전이_성공_시_updatedAt이_갱신된다`() {
			val task = createTask(TaskStatus.PENDING)
			val beforeTransition = task.updatedAt

			Thread.sleep(10)
			task.transitionTo(TaskStatus.PROCESSING)

			assertThat(task.updatedAt).isAfter(beforeTransition)
		}

		@Test
		fun `전이_실패_시_상태가_변경되지_않는다`() {
			val task = createTask(TaskStatus.PENDING)

			assertThatThrownBy { task.transitionTo(TaskStatus.COMPLETED) }
				.isInstanceOf(InvalidTaskStateException::class.java)

			assertThat(task.status).isEqualTo(TaskStatus.PENDING)
		}
	}
}
