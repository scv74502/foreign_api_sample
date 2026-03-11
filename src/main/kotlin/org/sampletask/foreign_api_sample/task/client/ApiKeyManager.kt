package org.sampletask.foreign_api_sample.task.client

import org.sampletask.foreign_api_sample.task.client.request.IssueKeyRequest
import org.sampletask.foreign_api_sample.task.client.response.IssueKeyResponse
import org.sampletask.foreign_api_sample.task.exception.ApiKeyUnavailableException
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBody
import java.util.concurrent.atomic.AtomicBoolean

@Component
class ApiKeyManager(
	private val webClient: WebClient,
	@Value("\${mock-worker.auth.candidate-name}") private val candidateName: String,
	@Value("\${mock-worker.auth.email}") private val email: String,
) {
	private val log = LoggerFactory.getLogger(javaClass)

	@Volatile
	private var _apiKey: String? = null
	val apiKey: String? get() = _apiKey

	private val reissueLock = AtomicBoolean(false)

	@EventListener(ApplicationReadyEvent::class)
	fun onApplicationReady() {
		try {
			kotlinx.coroutines.runBlocking { issueApiKey() }
		} catch (e: Exception) {
			log.warn("서버 시작 시 API Key 발급 실패 - 첫 요청 시 재시도합니다: {}", e.message)
		}
	}

	suspend fun getApiKey(): String {
		return apiKey ?: run {
			reissueApiKey()
			apiKey ?: throw ApiKeyUnavailableException("API Key를 발급받을 수 없습니다")
		}
	}

	suspend fun reissueApiKey() {
		if (!reissueLock.compareAndSet(false, true)) {
			log.debug("API Key 재발급이 이미 진행 중입니다")
			return
		}

		try {
			repeat(MAX_REISSUE_ATTEMPTS) { attempt ->
				try {
					issueApiKey()
					log.info("API Key 재발급 성공 (시도: {})", attempt + 1)
					return
				} catch (e: Exception) {
					log.warn("API Key 재발급 실패 (시도: {}): {}", attempt + 1, e.message)
					if (attempt == MAX_REISSUE_ATTEMPTS - 1) {
						throw ApiKeyUnavailableException(
							"API Key 재발급 실패 (${MAX_REISSUE_ATTEMPTS}회 시도): ${e.message}",
						)
					}
				}
			}
		} finally {
			reissueLock.set(false)
		}
	}

	private suspend fun issueApiKey() {
		val response =
			webClient
				.post()
				.uri("/mock/auth/issue-key")
				.bodyValue(IssueKeyRequest(candidateName, email))
				.retrieve()
				.awaitBody<IssueKeyResponse>()

		_apiKey = response.apiKey
	}

	companion object {
		private const val MAX_REISSUE_ATTEMPTS = 3
	}
}
