package org.sampletask.foreign_api_sample.integration

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean

@TestConfiguration
class WireMockConfig {
	companion object {
		val WIRE_MOCK_SERVER: WireMockServer =
			WireMockServer(wireMockConfig().port(8089)).also {
				if (!it.isRunning) {
					it.start()
				}
			}
	}

	@Bean
	fun wireMockServer(): WireMockServer = WIRE_MOCK_SERVER
}
