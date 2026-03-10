package org.sampletask.foreign_api_sample.task.domain

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.ValueSource

class TaskStatusTest {

    @Nested
    inner class CanTransitionTo {

        @Test
        fun `PENDING에서 PROCESSING으로 전이 가능`() {
            assertThat(TaskStatus.PENDING.canTransitionTo(TaskStatus.PROCESSING)).isTrue()
        }

        @Test
        fun `PENDING에서 CANCELLED로 전이 가능`() {
            assertThat(TaskStatus.PENDING.canTransitionTo(TaskStatus.CANCELLED)).isTrue()
        }

        @ParameterizedTest
        @EnumSource(value = TaskStatus::class, names = ["PENDING", "COMPLETED", "FAILED"])
        fun `PENDING에서 PROCESSING, CANCELLED 외 전이 불가`(target: TaskStatus) {
            assertThat(TaskStatus.PENDING.canTransitionTo(target)).isFalse()
        }

        @Test
        fun `PROCESSING에서 COMPLETED로 전이 가능`() {
            assertThat(TaskStatus.PROCESSING.canTransitionTo(TaskStatus.COMPLETED)).isTrue()
        }

        @Test
        fun `PROCESSING에서 FAILED로 전이 가능`() {
            assertThat(TaskStatus.PROCESSING.canTransitionTo(TaskStatus.FAILED)).isTrue()
        }

        @Test
        fun `PROCESSING에서 CANCELLED로 전이 가능`() {
            assertThat(TaskStatus.PROCESSING.canTransitionTo(TaskStatus.CANCELLED)).isTrue()
        }

        @ParameterizedTest
        @EnumSource(value = TaskStatus::class, names = ["PENDING", "PROCESSING"])
        fun `PROCESSING에서 COMPLETED, FAILED, CANCELLED 외 전이 불가`(target: TaskStatus) {
            assertThat(TaskStatus.PROCESSING.canTransitionTo(target)).isFalse()
        }

        @Test
        fun `FAILED에서 PENDING으로 전이 가능`() {
            assertThat(TaskStatus.FAILED.canTransitionTo(TaskStatus.PENDING)).isTrue()
        }

        @ParameterizedTest
        @EnumSource(value = TaskStatus::class, names = ["PROCESSING", "COMPLETED", "FAILED", "CANCELLED"])
        fun `FAILED에서 PENDING 외 전이 불가`(target: TaskStatus) {
            assertThat(TaskStatus.FAILED.canTransitionTo(target)).isFalse()
        }

        @ParameterizedTest
        @EnumSource(TaskStatus::class)
        fun `COMPLETED에서 모든 상태로 전이 불가`(target: TaskStatus) {
            assertThat(TaskStatus.COMPLETED.canTransitionTo(target)).isFalse()
        }

        @ParameterizedTest
        @EnumSource(TaskStatus::class)
        fun `CANCELLED에서 모든 상태로 전이 불가`(target: TaskStatus) {
            assertThat(TaskStatus.CANCELLED.canTransitionTo(target)).isFalse()
        }
    }

    @Nested
    inner class FromCode {

        @Test
        fun `코드 0은 PENDING을 반환`() {
            assertThat(TaskStatus.fromCode(0)).isEqualTo(TaskStatus.PENDING)
        }

        @Test
        fun `코드 1은 PROCESSING을 반환`() {
            assertThat(TaskStatus.fromCode(1)).isEqualTo(TaskStatus.PROCESSING)
        }

        @Test
        fun `코드 2는 COMPLETED를 반환`() {
            assertThat(TaskStatus.fromCode(2)).isEqualTo(TaskStatus.COMPLETED)
        }

        @Test
        fun `코드 3은 FAILED를 반환`() {
            assertThat(TaskStatus.fromCode(3)).isEqualTo(TaskStatus.FAILED)
        }

        @Test
        fun `코드 4는 CANCELLED를 반환`() {
            assertThat(TaskStatus.fromCode(4)).isEqualTo(TaskStatus.CANCELLED)
        }

        @ParameterizedTest
        @ValueSource(ints = [-1, 5, 99])
        fun `유효하지 않은 코드는 IllegalArgumentException을 발생`(code: Int) {
            assertThatThrownBy { TaskStatus.fromCode(code) }
                .isInstanceOf(IllegalArgumentException::class.java)
                .hasMessageContaining("Unknown TaskStatus code: $code")
        }
    }
}
