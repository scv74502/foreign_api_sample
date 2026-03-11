package org.sampletask.foreign_api_sample.task.client

import io.github.resilience4j.bulkhead.BulkheadRegistry
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import io.github.resilience4j.kotlin.bulkhead.executeSuspendFunction
import io.github.resilience4j.kotlin.circuitbreaker.executeSuspendFunction
import io.github.resilience4j.kotlin.ratelimiter.executeSuspendFunction
import io.github.resilience4j.ratelimiter.RateLimiterRegistry
import org.sampletask.foreign_api_sample.task.client.request.ProcessRequest
import org.sampletask.foreign_api_sample.task.client.response.JobStatusResponse
import org.sampletask.foreign_api_sample.task.client.response.ProcessResponse
import org.sampletask.foreign_api_sample.task.domain.RecoveryAction
import org.sampletask.foreign_api_sample.task.exception.MockWorkerException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatusCode
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import org.springframework.web.reactive.function.client.awaitBody

@Component
class MockWorkerClient(
	private val webClient: WebClient,
	private val apiKeyManager: ApiKeyManager,
	circuitBreakerRegistry: CircuitBreakerRegistry,
	rateLimiterRegistry: RateLimiterRegistry,
	bulkheadRegistry: BulkheadRegistry,
) {
	private val log = LoggerFactory.getLogger(javaClass)
	private val circuitBreaker = circuitBreakerRegistry.circuitBreaker("mock-worker")
	private val rateLimiter = rateLimiterRegistry.rateLimiter("mock-worker")
	private val bulkhead = bulkheadRegistry.bulkhead("mock-worker")

	suspend fun submitProcess(imageUrl: String): ProcessResponse {
		return withResilience {
			executeWithAuthRetry {
				webClient
					.post()
					.uri("/mock/process")
					.header("X-Api-Key", apiKeyManager.getApiKey())
					.bodyValue(ProcessRequest(imageUrl))
					.retrieve()
					.awaitBody<ProcessResponse>()
			}
		}
	}

	suspend fun getJobStatus(jobId: String): JobStatusResponse {
		return withResilience {
			executeWithAuthRetry {
				webClient
					.get()
					.uri("/mock/process/{jobId}", jobId)
					.header("X-Api-Key", apiKeyManager.getApiKey())
					.retrieve()
					.awaitBody<JobStatusResponse>()
			}
		}
	}

	private suspend fun <T> withResilience(block: suspend () -> T): T {
		return circuitBreaker.executeSuspendFunction {
			rateLimiter.executeSuspendFunction {
				bulkhead.executeSuspendFunction {
					block()
				}
			}
		}
	}

	private suspend fun <T> executeWithAuthRetry(block: suspend () -> T): T {
		return try {
			block()
		} catch (e: WebClientResponseException) {
			if (e.statusCode == HttpStatusCode.valueOf(401)) {
				log.info("401 응답 수신 - API Key 재발급 후 재시도")
				apiKeyManager.reissueApiKey()
				try {
					block()
				} catch (retryEx: WebClientResponseException) {
					throw toMockWorkerException(retryEx)
				}
			} else {
				throw toMockWorkerException(e)
			}
		}
	}

	private fun toMockWorkerException(e: WebClientResponseException): MockWorkerException {
		val statusCode = e.statusCode.value()
		val recoveryAction = when (statusCode) {
			404 -> RecoveryAction.REVERT_TO_PENDING
			429, 500, 502, 503, 504 -> RecoveryAction.RETRY
			else -> RecoveryAction.FAIL
		}
		return MockWorkerException(
			upstreamHttpStatus = statusCode,
			errorBody = e.responseBodyAsString,
			recoveryAction = recoveryAction,
		)
	}
}
