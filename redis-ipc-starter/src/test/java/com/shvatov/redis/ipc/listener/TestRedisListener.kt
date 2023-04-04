package com.shvatov.redis.ipc.listener

import com.shvatov.redis.ipc.annotation.listener.ChannelName
import com.shvatov.redis.ipc.annotation.listener.Payload
import com.shvatov.redis.ipc.annotation.listener.RedisListener
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.concurrent.TimeUnit.MILLISECONDS


open class TestRedisListener {

    data class TestMessage(
        val id: Long? = null,
        val payload: String? = null,
    )

    @RedisListener(
        channelPatterns = ["app.test-channel-pattern"],
        channels = ["channel-1", "channel-2", "test_topic"],
        bufferSize = 10,
        bufferingDuration = 500,
        bufferingDurationUnit = MILLISECONDS,
        retries = 5
    )
    fun onMessage(@ChannelName channel: String, @Payload(payloadClass = String::class) message: List<String>) {
        logger.error("Processing following message from channel {}: {}", channel, message)
    }

    private companion object {
        val logger: Logger = LoggerFactory.getLogger(TestRedisListener::class.java)
    }
}
