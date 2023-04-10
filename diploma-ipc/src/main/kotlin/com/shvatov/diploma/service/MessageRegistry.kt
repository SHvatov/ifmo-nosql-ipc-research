package com.shvatov.diploma.service

import com.shvatov.diploma.dto.Message
import com.shvatov.diploma.util.MESSAGE_ID_KEY
import org.slf4j.MDC
import org.springframework.stereotype.Component

@Component
class MessageRegistry {

    fun create() = register(Message.create())
        .also { it.applyToMDC() }

    @Synchronized
    fun register(message: Message) =
        message.also { it.applyToMDC() }
}

private fun Message.applyToMDC() {
    MDC.put(MESSAGE_ID_KEY, id.toString())
}
