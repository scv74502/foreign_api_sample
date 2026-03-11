package org.sampletask.foreign_api_sample.config

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.springframework.beans.factory.DisposableBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class CoroutineScopeConfig : DisposableBean {
	private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

	@Bean
	fun applicationScope(): CoroutineScope = scope

	override fun destroy() {
		scope.cancel()
	}
}
