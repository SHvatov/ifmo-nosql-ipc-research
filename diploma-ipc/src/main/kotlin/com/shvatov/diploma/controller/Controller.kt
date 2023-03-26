package com.shvatov.diploma.controller

import com.shvatov.diploma.service.MessageProducer
import com.shvatov.diploma.service.MessageRegistry
import com.shvatov.diploma.util.logEvent
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Lazy
import org.springframework.scheduling.annotation.Async
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api")
class Controller(
    private val messageRegistry: MessageRegistry,
    private val messageProducer: MessageProducer
) {

    @Lazy
    @Autowired
    lateinit var self: Controller

    @GetMapping("/meter")
    fun meterIPC() {
        self.meterIPCAsync()
    }

    @Async
    fun meterIPCAsync() {
        val message = messageRegistry.create()
        log.logEvent("Created random message")
        messageProducer.sendMessage(message)
    }

    private companion object {
        val log: Logger = LoggerFactory.getLogger(Controller::class.java)
    }
}