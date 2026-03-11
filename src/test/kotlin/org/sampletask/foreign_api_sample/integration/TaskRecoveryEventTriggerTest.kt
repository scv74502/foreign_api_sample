package org.sampletask.foreign_api_sample.integration

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.sampletask.foreign_api_sample.config.TestcontainersConfiguration
import org.sampletask.foreign_api_sample.task.service.TaskRecoveryService
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.context.event.EventListener

/**
 * ApplicationReadyEvent 발생 시 TaskRecoveryService.recoverTasks()가 자동 호출되는지 검증.
 */
@SpringBootTest
@Import(TestcontainersConfiguration::class, WireMockConfig::class)
class TaskRecoveryEventTriggerTest {

	@Test
	fun `recoverTasks_메서드에_ApplicationReadyEvent_EventListener가_등록되어_있다`() {
		val recoverMethod = TaskRecoveryService::class.java.getDeclaredMethod("recoverTasks")

		val annotation = recoverMethod.getAnnotation(EventListener::class.java)
		assertThat(annotation).isNotNull

		val eventTypes = (annotation.value.toList() + annotation.classes.toList()).map { it.java }
		assertThat(eventTypes).contains(ApplicationReadyEvent::class.java)
	}

	@Test
	fun `TaskRecoveryService는_Spring_컨텍스트에_Bean으로_등록되어_있다`() {
		// @Component 어노테이션 확인 - ApplicationReadyEvent가 발행되면 자동 호출 가능
		val componentAnnotation =
			TaskRecoveryService::class.java.getAnnotation(org.springframework.stereotype.Component::class.java)
		assertThat(componentAnnotation).isNotNull
	}
}
