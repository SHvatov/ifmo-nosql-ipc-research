package com.shvatov.diploma.nosql.config

import com.shvatov.diploma.nosql.consumer.RedisMessageConsumer
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.listener.ChannelTopic
import org.springframework.data.redis.listener.RedisMessageListenerContainer
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter
import org.springframework.data.redis.serializer.StringRedisSerializer


@Configuration
class RedisConfig(private val redisMessageConsumer: RedisMessageConsumer) {

    @Value(value = "\${spring.redis.host}")
    private lateinit var redisHostName: String

    @Value(value = "\${spring.redis.port}")
    private lateinit var redisPort: String

    @Value(value = "\${diploma.nosql.incoming-topic}")
    private lateinit var incomingTopic: String

    @Value(value = "\${diploma.nosql.outgoing-topic}")
    private lateinit var outgoingTopic: String

    @Bean
    fun jedisConnectionFactory() =
        JedisConnectionFactory().apply {
            hostName = redisHostName
            port = redisPort.toInt()
            usePool = true
        }

    @Bean
    fun redisContainer() =
        RedisMessageListenerContainer().apply {
            setConnectionFactory(jedisConnectionFactory())
            addMessageListener(messageListener(), incomingTopic())
        }

    @Bean
    fun messageListener() = MessageListenerAdapter(redisMessageConsumer)

    @Bean
    fun incomingTopic() = ChannelTopic(incomingTopic)

    @Bean
    fun outgoingTopic() = ChannelTopic(outgoingTopic)

    @Bean
    fun redisTemplate() =
        RedisTemplate<String, String>()
            .apply {
                setConnectionFactory(jedisConnectionFactory())
                setDefaultSerializer(StringRedisSerializer())
            }
}
