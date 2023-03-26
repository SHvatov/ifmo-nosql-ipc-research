package com.shvatov.diploma.dto

import org.apache.commons.lang3.RandomStringUtils.randomAlphanumeric
import java.lang.System.nanoTime
import java.util.concurrent.atomic.AtomicInteger

data class Message(
    val id: Long,
    val payload: String,
    val status: AtomicInteger
) {

    fun withStatus(newStatus: MessageStatus) =
        apply { status.set(newStatus.intValue) }

    fun compareAndSetStatus(expectedStatus: MessageStatus, newStatus: MessageStatus) =
        status.compareAndSet(expectedStatus.intValue, newStatus.intValue)

    companion object {
        fun create() =
            Message(
                id = nanoTime(),
                payload = randomAlphanumeric(256),
                status = AtomicInteger(MessageStatus.CREATED.intValue)
            )
    }
}

enum class MessageStatus(val intValue: Int) {
    CREATED(1),
    SENT(2),
    RECEIVED(3),
    PROCESSED(4),
    FINALIZED(5),
}