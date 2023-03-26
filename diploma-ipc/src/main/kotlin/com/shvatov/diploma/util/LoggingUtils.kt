package com.shvatov.diploma.util

import org.slf4j.Logger
import org.slf4j.MDC

fun Logger.logEvent(message: String, vararg params: Any) {
    debug("[SYSTEM_TIME_NS=${System.nanoTime()},MESSAGE_ID=${MDC.get(MESSAGE_ID_KEY)},MESSAGE=${message}]", params)
}

const val MESSAGE_ID_KEY = "messageId"