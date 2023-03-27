package com.shvatov.redis.ipc.listener

import com.shvatov.redis.ipc.config.RedisIPCAutoConfiguration
import com.shvatov.redis.ipc.extension.RedisExtension
import com.shvatov.redis.ipc.listener.TestRedisListener.TestRedisListenerConfiguration
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.data.redis.connection.Message
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.listener.ChannelTopic
import org.springframework.data.redis.listener.RedisMessageListenerContainer
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.junit.jupiter.SpringExtension
import java.util.concurrent.atomic.AtomicReference

@SpringBootTest(classes = [TestRedisListenerConfiguration::class])
@ExtendWith(SpringExtension::class)
@ExtendWith(RedisExtension::class)
@TestPropertySource(locations = ["classpath:application-test.yaml"])
class TestRedisListener {

    @field:Autowired
    private lateinit var topic: ChannelTopic

    @field:Autowired
    private lateinit var template: RedisTemplate<String, ByteArray>

    @field:Autowired
    private lateinit var messageListenerContainer: RedisMessageListenerContainer

    @SpringBootApplication
    @Import(value = [RedisIPCAutoConfiguration::class, RedisAutoConfiguration::class])
    class TestRedisListenerConfiguration {

        @Bean
        fun topic() = ChannelTopic(TEST_TOPIC)

    }

    @Test
    fun redisHasStartedTest() {
        val messageHolder = AtomicReference<Message?>(null)
        val receiversHolder = AtomicReference<Long>(0)

        messageListenerContainer.addMessageListener({ message, _ -> messageHolder.set(message) }, topic)

        receiversHolder.set(template.convertAndSend(TEST_TOPIC, TEST_MESSAGE.toByteArray()))

        assertNotEquals(0, receiversHolder.get())
        assertNotNull(messageHolder.get())
    }

    private companion object {
        const val TEST_TOPIC = "test_topic"
        const val TEST_MESSAGE = "test_message"
    }

}
