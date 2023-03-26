package com.shvatov.diploma.service

import com.shvatov.diploma.dto.Message
import com.shvatov.diploma.util.MESSAGE_ID_KEY
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tag
import org.slf4j.MDC
import org.springframework.stereotype.Component

@Component
class MessageRegistry(private val meterRegistry: MeterRegistry) {

    fun create() = register(Message.create())
        .also { it.applyToMDC() }

    @Synchronized
    fun register(message: Message) =
        message.also {
            it.applyToMDC()
            meterRegistry.gauge("message_status", listOf(Tag.of("id", it.id.toString())), it.status)
        }
}

private fun Message.applyToMDC() {
    MDC.put(MESSAGE_ID_KEY, id.toString())
}