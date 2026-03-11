package org.sampletask.foreign_api_sample.config

import io.netty.channel.ChannelOption
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.web.reactive.function.client.WebClient
import reactor.netty.http.client.HttpClient
import java.time.Duration

@Configuration
class WebClientConfig(
	@Value("\${mock-worker.base-url}") private val baseUrl: String,
	@Value("\${mock-worker.timeout.connect-ms:5000}") private val connectTimeoutMs: Int,
	@Value("\${mock-worker.timeout.read-ms:10000}") private val readTimeoutMs: Long,
	@Value("\${mock-worker.timeout.write-ms:10000}") private val writeTimeoutMs: Long,
) {
	@Bean
	fun webClient(builder: WebClient.Builder): WebClient {
		val httpClient =
			HttpClient
				.create()
				.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectTimeoutMs)
				.responseTimeout(Duration.ofMillis(readTimeoutMs))

		return builder
			.baseUrl(baseUrl)
			.clientConnector(ReactorClientHttpConnector(httpClient))
			.build()
	}
}
