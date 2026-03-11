package org.sampletask.foreign_api_sample.task.client.response

data class JobStatusResponse(
	val jobId: String,
	val status: String,
	val result: String? = null,
	val errorCode: String? = null,
	val errorMessage: String? = null,
)
