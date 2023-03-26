package com.shvatov.diploma.annotation

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@ConditionalOnProperty(name = ["diploma.mode"], havingValue = "MESSAGE_QUEUE")
annotation class MessageQueueEnabled

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@ConditionalOnProperty(name = ["diploma.mode"], havingValue = "NOSQL_DATABASE")
annotation class NosqlDatabaseEnabled