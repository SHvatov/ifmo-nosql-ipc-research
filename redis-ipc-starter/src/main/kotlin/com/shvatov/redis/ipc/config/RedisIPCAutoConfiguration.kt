package com.shvatov.redis.ipc.config

import com.shvatov.redis.ipc.config.RedisIPCAutoConfiguration.RedisConnectionConfiguration
import com.shvatov.redis.ipc.config.RedisIPCAutoConfiguration.RedisConsumerConfiguration
import com.shvatov.redis.ipc.config.RedisIPCAutoConfiguration.RedisProducerConfiguration
import com.shvatov.redis.ipc.config.RedisIPCAutoConfiguration.RedisTemplateConfiguration
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import

@Configuration
@Import(
    value = [
        RedisConnectionConfiguration::class,
        RedisTemplateConfiguration::class,
        RedisConsumerConfiguration::class,
        RedisProducerConfiguration::class,
    ]
)
class RedisIPCAutoConfiguration {

    @Configuration
    class RedisConnectionConfiguration {

    }

    @Configuration
    class RedisTemplateConfiguration {

    }

    @Configuration
    class RedisConsumerConfiguration {

    }

    @Configuration
    class RedisProducerConfiguration {

    }

}
