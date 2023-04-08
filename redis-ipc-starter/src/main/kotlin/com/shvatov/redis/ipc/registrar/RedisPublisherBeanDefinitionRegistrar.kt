package com.shvatov.redis.ipc.registrar

import com.fasterxml.jackson.databind.ObjectMapper
import com.shvatov.redis.ipc.annotation.publisher.Publish
import com.shvatov.redis.ipc.annotation.publisher.Publisher
import com.shvatov.redis.ipc.config.RedisIPCAutoConfiguration.RedisCommonConfiguration
import com.shvatov.redis.ipc.config.RedisIPCAutoConfiguration.RedisListenerConfiguration
import net.bytebuddy.implementation.InvocationHandlerAdapter
import net.bytebuddy.matcher.ElementMatchers
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.BeanInitializationException
import org.springframework.beans.factory.config.BeanDefinition
import org.springframework.beans.factory.support.BeanDefinitionRegistry
import org.springframework.beans.factory.support.GenericBeanDefinition
import org.springframework.context.ApplicationContext
import org.springframework.context.ApplicationContextAware
import org.springframework.core.env.Environment
import org.springframework.core.type.AnnotationMetadata
import org.springframework.data.redis.listener.ReactiveRedisMessageListenerContainer
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.scheduler.Scheduler
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
        reflections.getTypesAnnotatedWith(Publisher::class.java).forEach { publisherClass ->
            doRegisterRedisPublisher(registry, publisherClass)
        }
    }

    private fun doRegisterRedisPublisher(registry: BeanDefinitionRegistry, publisherClass: Class<*>) {
        val publisherProxyClass = createPublisherProxyClass(publisherClass)
        registerPublisherProxyBean(registry, publisherProxyClass, publisherClass)
    }

    private fun createPublisherProxyClass(publisherClass: Class<*>): Class<*> {
        val publisherClassBuilder = byteBuddy.subclass(publisherClass)
            .implement(RedisPublisherProxy::class.java)

        val helpers = mutableListOf<RedisPublisherHelper>()
        publisherClass.publisherMethods.forEach { method ->
            val config = resolveListenerConfig(method)
            val helper = RedisPublisherHelper(config)
            publisherClassBuilder
                .method(ElementMatchers.`is`(method))
                .intercept(InvocationHandlerAdapter.of { _, _, args -> helper.send(args) })
            helpers.add(helper)
        }

        publisherClassBuilder
            .method(ElementMatchers.isOverriddenFrom(ApplicationContextAware::class.java))
            .intercept(InvocationHandlerAdapter.of { _, _, args ->
                helpers.forEach { helper ->
                    helper.init(args[0] as ApplicationContext)
                }
            })

        return publisherClassBuilder.make()
            .load(this::class.java.classLoader)
            .loaded
    }

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

    private fun registerPublisherProxyBean(
        registry: BeanDefinitionRegistry,
        publisherProxyClass: Class<*>,
        publisherClass: Class<*>
    ) {
        val beanDefinition = GenericBeanDefinition()
            .apply {
                setBeanClass(publisherProxyClass)
                setDependsOn(
                    RedisListenerConfiguration.REDIS_IPC_MESSAGE_LISTENER_CONTAINER_BEAN,
                    RedisCommonConfiguration.REDIS_IPC_OBJECT_MAPPER_BEAN,
                    RedisCommonConfiguration.REDIS_IPC_SCHEDULER_BEAN
                )
                scope = BeanDefinition.SCOPE_SINGLETON
            }
        registry.registerBeanDefinition(IPC_LISTENER_PREFIX + publisherClass.simpleName, beanDefinition)
    }

    private val Class<*>.publisherMethods: Collection<Method>
        get() = methods.filter { it.isAnnotationPresent(Publish::class.java) }

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

    internal interface RedisPublisherProxy : ApplicationContextAware

    private class RedisPublisherHelper(private val config: RedisPublisherConfig) {
        private lateinit var listenerContainer: ReactiveRedisMessageListenerContainer
        private lateinit var objectMapper: ObjectMapper
        private lateinit var scheduler: Scheduler

        fun init(applicationContext: ApplicationContext) {
            log.trace(
                "Initializing RedisPublisherHelper for {}#{}",
                config.publishMethod.declaringClass.simpleName,
                config.publishMethod
            )

            listenerContainer = applicationContext.getBean(
                RedisListenerConfiguration.REDIS_IPC_MESSAGE_LISTENER_CONTAINER_BEAN,
                ReactiveRedisMessageListenerContainer::class.java
            )

            objectMapper = applicationContext.getBean(
                RedisCommonConfiguration.REDIS_IPC_OBJECT_MAPPER_BEAN,
                ObjectMapper::class.java
            )

            scheduler = applicationContext.getBean(
                RedisCommonConfiguration.REDIS_IPC_SCHEDULER_BEAN,
                Scheduler::class.java
            )
        }

        fun send(args: Array<out Any>): Any {
            return Any()
        }

        private companion object {
            val log: Logger = LoggerFactory.getLogger(RedisPublisherHelper::class.java)
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
