package com.shvatov.redis.ipc.config

import com.shvatov.redis.ipc.config.RedisIPCAutoConfiguration.RedisCommonConfiguration
import com.shvatov.redis.ipc.config.RedisIPCAutoConfiguration.RedisCommonConfiguration.Companion.REDIS_IPC_TASK_EXECUTOR_BEAN
import com.shvatov.redis.ipc.config.RedisIPCAutoConfiguration.RedisListenerConfiguration
import com.shvatov.redis.ipc.config.RedisIPCAutoConfiguration.RedisPublisherConfiguration
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.DependsOn
import org.springframework.context.annotation.Import
import org.springframework.core.task.TaskExecutor
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.listener.RedisMessageListenerContainer
import org.springframework.data.redis.serializer.RedisSerializer
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor

/**
 * @see [org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration]
 */
@Configuration("redis.ipc.auto-configuration")
@Import(
    value = [
        RedisCommonConfiguration::class,
        RedisListenerConfiguration::class,
        RedisPublisherConfiguration::class,
    ]
)
class RedisIPCAutoConfiguration {

    @Configuration("redis.ipc.common-configuration")
    class RedisCommonConfiguration {

        @Bean(REDIS_IPC_TEMPLATE_BEAN)
        fun redisIpcTemplate(redisConnectionFactory: RedisConnectionFactory) =
            RedisTemplate<String, ByteArray>().apply {
                // connection factory
                setConnectionFactory(redisConnectionFactory)

                // serialization
                isEnableDefaultSerializer = true
                keySerializer = RedisSerializer.string()
                valueSerializer = RedisSerializer.byteArray()
            }

        @Bean(REDIS_IPC_TASK_EXECUTOR_BEAN)
        fun redisIpcTaskExecutor(
            @Value("\${spring.data.redis.task-executor.core-pool-size:1}") corePoolSize: Int,
            @Value("\${spring.data.redis.task-executor.max-pool-size:25}") maxPoolSize: Int,
            @Value("\${spring.data.redis.task-executor.keep-alive-seconds:60}") keepAliveSeconds: Int,
            @Value("\${spring.data.redis.task-executor.queue-capacity:10000}") queueCapacity: Int,
        ) = ThreadPoolTaskExecutor().apply {
            setThreadNamePrefix(IPC_THREAD_POOL_PREFIX)
            setKeepAliveSeconds(keepAliveSeconds)
            setQueueCapacity(queueCapacity)
            setCorePoolSize(corePoolSize)
            setMaxPoolSize(maxPoolSize)
        }

        internal companion object {
            private const val IPC_THREAD_POOL_PREFIX = "redis-ipc"

            const val REDIS_IPC_TEMPLATE_BEAN = "redisIpcTemplate"
            const val REDIS_IPC_TASK_EXECUTOR_BEAN = "redisIpcTaskExecutor"
        }
    }

    @DependsOn("redis.ipc.common-configuration")
    @Configuration("redis.ipc.consumer-configuration")
    class RedisListenerConfiguration {

        @Bean(REDIS_IPC_MESSAGE_LISTENER_CONTAINER_BEAN)
        fun redisIpcMessageListenerContainer(
            connectionFactory: RedisConnectionFactory,
            @Qualifier(REDIS_IPC_TASK_EXECUTOR_BEAN) taskExecutor: TaskExecutor
        ) = RedisMessageListenerContainer().apply {
            setConnectionFactory(connectionFactory)
            setTaskExecutor(taskExecutor)
        }

        internal companion object {
            const val REDIS_IPC_MESSAGE_LISTENER_CONTAINER_BEAN = "redisIpcMessageListenerContainer"
        }
    }

    @DependsOn("redis.ipc.common-configuration")
    @Configuration("redis.ipc.publisher-configuration")
    class RedisPublisherConfiguration {

    }

}
