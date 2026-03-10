package org.sampletask.foreign_api_sample

import org.sampletask.foreign_api_sample.config.TestcontainersConfiguration
import org.springframework.boot.fromApplication
import org.springframework.boot.with


fun main(args: Array<String>) {
	fromApplication<ForeignApiSampleApplication>().with(TestcontainersConfiguration::class).run(*args)
}
