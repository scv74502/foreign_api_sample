package org.sampletask.foreign_api_sample.task.domain

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.ValueSource

class TaskStatusTest {

	@Suppress("ClassName")
	@Nested
	inner class 전이_가능_여부 {

		@Test
		fun `PENDING에서_PROCESSING으로_전이_가능`() {
			assertThat(TaskStatus.PENDING.canTransitionTo(TaskStatus.PROCESSING)).isTrue()
		}

		@Test
		fun `PENDING에서_CANCELLED로_전이_가능`() {
			assertThat(TaskStatus.PENDING.canTransitionTo(TaskStatus.CANCELLED)).isTrue()
		}

		@ParameterizedTest
		@EnumSource(value = TaskStatus::class, names = ["PENDING", "COMPLETED", "FAILED"])
		fun `PENDING에서_PROCESSING과_CANCELLED_외_전이_불가`(target: TaskStatus) {
			assertThat(TaskStatus.PENDING.canTransitionTo(target)).isFalse()
		}

		@Test
		fun `PROCESSING에서_COMPLETED로_전이_가능`() {
			assertThat(TaskStatus.PROCESSING.canTransitionTo(TaskStatus.COMPLETED)).isTrue()
		}

		@Test
		fun `PROCESSING에서_FAILED로_전이_가능`() {
			assertThat(TaskStatus.PROCESSING.canTransitionTo(TaskStatus.FAILED)).isTrue()
		}

		@Test
		fun `PROCESSING에서_CANCELLED로_전이_가능`() {
			assertThat(TaskStatus.PROCESSING.canTransitionTo(TaskStatus.CANCELLED)).isTrue()
		}

		@Test
		fun `PROCESSING에서_PENDING으로_전이_가능_recovery_전용`() {
			assertThat(TaskStatus.PROCESSING.canTransitionTo(TaskStatus.PENDING)).isTrue()
		}

		@ParameterizedTest
		@EnumSource(value = TaskStatus::class, names = ["PROCESSING"])
		fun `PROCESSING에서_COMPLETED와_FAILED와_CANCELLED와_PENDING_외_전이_불가`(target: TaskStatus) {
			assertThat(TaskStatus.PROCESSING.canTransitionTo(target)).isFalse()
		}

		@Test
		fun `FAILED에서_PENDING으로_전이_가능`() {
			assertThat(TaskStatus.FAILED.canTransitionTo(TaskStatus.PENDING)).isTrue()
		}

		@ParameterizedTest
		@EnumSource(value = TaskStatus::class, names = ["PROCESSING", "COMPLETED", "FAILED", "CANCELLED"])
		fun `FAILED에서_PENDING_외_전이_불가`(target: TaskStatus) {
			assertThat(TaskStatus.FAILED.canTransitionTo(target)).isFalse()
		}

		@ParameterizedTest
		@EnumSource(TaskStatus::class)
		fun `COMPLETED에서_모든_상태로_전이_불가`(target: TaskStatus) {
			assertThat(TaskStatus.COMPLETED.canTransitionTo(target)).isFalse()
		}

		@ParameterizedTest
		@EnumSource(TaskStatus::class)
		fun `CANCELLED에서_모든_상태로_전이_불가`(target: TaskStatus) {
			assertThat(TaskStatus.CANCELLED.canTransitionTo(target)).isFalse()
		}
	}

	@Suppress("ClassName")
	@Nested
	inner class 코드_변환 {

		@Test
		fun `코드_0은_PENDING을_반환`() {
			assertThat(TaskStatus.fromCode(0)).isEqualTo(TaskStatus.PENDING)
		}

		@Test
		fun `코드_1은_PROCESSING을_반환`() {
			assertThat(TaskStatus.fromCode(1)).isEqualTo(TaskStatus.PROCESSING)
		}

		@Test
		fun `코드_2는_COMPLETED를_반환`() {
			assertThat(TaskStatus.fromCode(2)).isEqualTo(TaskStatus.COMPLETED)
		}

		@Test
		fun `코드_3은_FAILED를_반환`() {
			assertThat(TaskStatus.fromCode(3)).isEqualTo(TaskStatus.FAILED)
		}

		@Test
		fun `코드_4는_CANCELLED를_반환`() {
			assertThat(TaskStatus.fromCode(4)).isEqualTo(TaskStatus.CANCELLED)
		}

		@ParameterizedTest
		@ValueSource(ints = [-1, 5, 99])
		fun `유효하지_않은_코드는_IllegalArgumentException을_발생`(code: Int) {
			assertThatThrownBy { TaskStatus.fromCode(code) }
				.isInstanceOf(IllegalArgumentException::class.java)
				.hasMessageContaining("Unknown TaskStatus code: $code")
		}
	}
}
