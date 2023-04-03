package com.shvatov.redis.ipc

import com.shvatov.redis.ipc.IPCTest.TestRedisListenerConfiguration
import com.shvatov.redis.ipc.config.RedisIPCAutoConfiguration
import com.shvatov.redis.ipc.config.RedisIPCAutoConfiguration.RedisCommonConfiguration.Companion.REDIS_IPC_SCHEDULER_BEAN
import com.shvatov.redis.ipc.extension.RedisExtension
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.extension.ExtendWith
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration
import org.springframework.boot.autoconfigure.data.redis.RedisReactiveAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.data.redis.core.ReactiveRedisTemplate
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.testcontainers.shaded.org.awaitility.Awaitility
import reactor.core.scheduler.Scheduler
import java.time.Duration
import java.util.concurrent.TimeUnit.SECONDS
import java.util.concurrent.atomic.AtomicReference

@SpringBootTest(classes = [TestRedisListenerConfiguration::class])
@ExtendWith(SpringExtension::class)
@ExtendWith(RedisExtension::class)
@TestPropertySource(locations = ["classpath:application-test.properties"])
class IPCTest {

    @field:Autowired
    private lateinit var template: ReactiveRedisTemplate<String, ByteArray>

    @field:Autowired
    @field:Qualifier(REDIS_IPC_SCHEDULER_BEAN)
    private lateinit var ipcScheduler: Scheduler

    @SpringBootApplication
    @Import(
        value = [
            RedisIPCAutoConfiguration::class,
            RedisAutoConfiguration::class,
            RedisReactiveAutoConfiguration::class
        ]
    )
    class TestRedisListenerConfiguration {

    }

    @Test
    @Timeout(value = 20, unit = SECONDS)
    fun redisHasStartedTest() {
        val receiversHolder = AtomicReference<Long?>(null)

        template.convertAndSend(TEST_TOPIC, TEST_MESSAGE.toByteArray())
            .publishOn(ipcScheduler)
            .delaySubscription(Duration.ofSeconds(5))
            .subscribe {
                log.error("Sent message to topic, receivers: {}", it)
                receiversHolder.set(it)
            }

        Awaitility.await()
            .atMost(Duration.ofSeconds(10))
            .until { receiversHolder.get() != null }

        assertNotEquals(0, receiversHolder.get())
    }

    private companion object {
        const val TEST_TOPIC = "test_topic"
        const val TEST_MESSAGE = "test_message"

        val log: Logger = LoggerFactory.getLogger(IPCTest::class.java)
    }

}
