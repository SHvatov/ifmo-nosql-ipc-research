package com.shvatov.redis.ipc.registrar

import com.shvatov.redis.ipc.annotation.publisher.Publish
import com.shvatov.redis.ipc.annotation.publisher.Publisher
import org.springframework.beans.factory.BeanInitializationException
import org.springframework.beans.factory.support.BeanDefinitionRegistry
import org.springframework.core.env.Environment
import org.springframework.core.type.AnnotationMetadata
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.lang.reflect.Method
import java.lang.reflect.ParameterizedType
import java.time.Duration

internal class RedisPublisherBeanDefinitionRegistrar(
    environment: Environment
) : AbstractRedisBeanDefinitionRegistrar(environment) {

    override fun registerBeanDefinitions(metadata: AnnotationMetadata, registry: BeanDefinitionRegistry) {
        doRegisterRedisPublishers(registry)
    }

    private fun doRegisterRedisPublishers(registry: BeanDefinitionRegistry) {
        reflections.getTypesAnnotatedWith(Publisher::class.java).forEach { publisher ->
            doRegisterRedisPublisher(registry, publisher)
        }
    }

    private fun doRegisterRedisPublisher(registry: BeanDefinitionRegistry, publisher: Class<*>) {
        publisher.publisherMethods.forEach {
            resolveListenerConfig(it)
        }
    }

    private val Class<*>.publisherMethods: Collection<Method>
        get() = methods.filter { it.isAnnotationPresent(Publish::class.java) }

    private fun resolveListenerConfig(method: Method): RedisPublisherConfig {
        val annotation = method.getAnnotation(Publish::class.java)
        return with(annotation) {
            RedisPublisherConfig(
                publishMethod = method,
                publishRequestToChannel = publishRequestToChannel
                    .takeIf { it.isNotBlank() }
                    ?: channel,
                receiveResponseFromChannel = receiveResponseFromChannel,
                awaitAtLeastOneReceiver = awaitAtLeastOneReceiver,
                retries = actualRetriesNumber,
                retriesBackoffDuration = actualRetriesBackoffDuration,
                responseAwaitTimeout = actualResponseAwaitTimeout
            ).apply { validate() }
        }
    }

    private val Publish.actualRetriesNumber: Int
        get() = if (retriesExpression.isNotBlank()) {
            environment.getRequiredProperty(retriesExpression, Int::class.java)
        } else retries

    private val Publish.actualRetriesBackoffDuration: Duration?
        get() = if (retriesBackoffDurationExpression.isNotBlank()) {
            environment.getRequiredProperty(retriesBackoffDurationExpression, Duration::class.java)
        } else if (retriesBackoffDuration > 0) {
            Duration.of(
                retriesBackoffDuration.toLong(),
                retriesBackoffDurationUnit.toChronoUnit()
            )
        } else null

    private val Publish.actualResponseAwaitTimeout: Duration?
        get() = if (responseAwaitTimeoutExpression.isNotBlank()) {
            environment.getRequiredProperty(responseAwaitTimeoutExpression, Duration::class.java)
        } else if (responseAwaitTimeout > 0) {
            Duration.of(
                responseAwaitTimeout.toLong(),
                responseAwaitTimeoutUnit.toChronoUnit()
            )
        } else null

    private fun RedisPublisherConfig.validate() {
        if (publishRequestToChannel.isBlank()) {
            throw BeanInitializationException("\"channel\" or \"publishRequestToChannel\" must be not empty")
        }

        val rsType = publishMethod.returnType
        if (!(rsType == Mono::class.java || rsType == Flux::class.java)) {
            throw BeanInitializationException(
                "Return type of the publisher method must be either Mono or Flux, got "
                        + rsType.simpleName
            )
        }

        if (!(publishMethodReturnsUnit
                    || publishMethodReturnsReceiversNumber
                    || publishMethodReturnsSingleResponse
                    || publishMethodReturnsMultipleResponses)) {
            throw BeanInitializationException(
                "Return type of the publisher method must be either Mono<Unit>, Mono<Long>, Mono/Flux<T>, got "
                        + rsType.simpleName
            )
        }
    }

    private data class RedisPublisherConfig(
        val publishMethod: Method,
        val publishRequestToChannel: String,
        val receiveResponseFromChannel: String?,
        val awaitAtLeastOneReceiver: Boolean,
        val retries: Int,
        val retriesBackoffDuration: Duration?,
        val responseAwaitTimeout: Duration?,

        val publishMethodReturnType: ParameterizedType =
            publishMethod.genericReturnType as ParameterizedType,

        val publishMethodReturnsUnit: Boolean =
            publishMethodReturnType.actualTypeArguments[0] == Unit::class.java
                    || publishMethodReturnType.actualTypeArguments[0] == Void::class.java,

        val publishMethodReturnsReceiversNumber: Boolean =
            publishMethodReturnType.actualTypeArguments[0] == Int::class.java
                    || publishMethodReturnType.actualTypeArguments[0] == Long::class.java,

        val publishMethodReturnsSingleResponse: Boolean =
            !publishMethodReturnsUnit
                    && !publishMethodReturnsReceiversNumber
                    && publishMethod.returnType == Mono::class.java,

        val publishMethodReturnsMultipleResponses: Boolean =
            !publishMethodReturnsUnit
                    && !publishMethodReturnsReceiversNumber
                    && publishMethod.returnType == Flux::class.java,

        val responseClass: Class<*> = publishMethodReturnType.actualTypeArguments[0] as Class<*>,
        val responsePublisherClass: Class<*> = publishMethod.returnType
    )
}
