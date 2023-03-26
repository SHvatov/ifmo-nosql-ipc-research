package com.shvatov.diploma.service

import com.shvatov.diploma.dto.Message

interface MessageProducer {

    fun sendMessage(message: Message)
    
}