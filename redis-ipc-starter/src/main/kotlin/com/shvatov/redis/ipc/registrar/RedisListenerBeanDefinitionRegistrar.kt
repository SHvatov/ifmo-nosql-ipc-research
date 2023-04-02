package com.shvatov.redis.ipc.registrar

import com.fasterxml.jackson.databind.ObjectMapper
import com.shvatov.redis.ipc.annotation.listener.ChannelName
import com.shvatov.redis.ipc.annotation.listener.Payload
import com.shvatov.redis.ipc.annotation.listener.RedisListener
import com.shvatov.redis.ipc.config.RedisIPCAutoConfiguration.RedisCommonConfiguration.Companion.REDIS_IPC_SCHEDULER_BEAN
import com.shvatov.redis.ipc.config.RedisIPCAutoConfiguration.RedisListenerConfiguration.Companion.REDIS_IPC_MESSAGE_LISTENER_CONTAINER_BEAN
import com.shvatov.redis.ipc.config.RedisIPCAutoConfiguration.RedisListenerConfiguration.Companion.REDIS_IPC_OBJECT_MAPPER_BEAN
import net.bytebuddy.ByteBuddy
import net.bytebuddy.implementation.InvocationHandlerAdapter
import net.bytebuddy.matcher.ElementMatchers.isOverriddenFrom
import org.reactivestreams.Subscription
import org.reflections.Reflections
import org.reflections.scanners.MethodAnnotationsScanner
import org.reflections.util.ConfigurationBuilder
import org.springframework.beans.factory.DisposableBean
import org.springframework.beans.factory.InitializingBean
import org.springframework.beans.factory.config.BeanDefinition
import org.springframework.beans.factory.support.BeanDefinitionRegistry
import org.springframework.beans.factory.support.GenericBeanDefinition
import org.springframework.context.ApplicationContext
import org.springframework.context.ApplicationContextAware
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar
import org.springframework.core.env.Environment
import org.springframework.core.type.AnnotationMetadata
import org.springframework.data.redis.listener.ChannelTopic
import org.springframework.data.redis.listener.PatternTopic
import org.springframework.data.redis.listener.ReactiveRedisMessageListenerContainer
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair.fromSerializer
import org.springframework.data.redis.serializer.RedisSerializer.byteArray
import org.springframework.data.redis.serializer.RedisSerializer.string
import reactor.core.Disposable
import reactor.core.publisher.Flux
import reactor.core.scheduler.Scheduler
import reactor.util.retry.RetrySpec
import java.lang.reflect.Method
import java.nio.charset.Charset
import java.time.Duration
import java.util.Collections
import java.util.concurrent.atomic.AtomicReference

internal class RedisListenerBeanDefinitionRegistrar(
    private val environment: Environment
) : ImportBeanDefinitionRegistrar {

    private val reflections: Reflections = Reflections(
        ConfigurationBuilder()
            .forPackages(
                *environment
                    .getRequiredProperty(PACKAGES_TO_SCAN_PROPERTY)
                    .split(",")
                    .filter { it.isNotBlank() }
                    .toTypedArray()
            )
            .addScanners(MethodAnnotationsScanner())
    )

    override fun registerBeanDefinitions(metadata: AnnotationMetadata, registry: BeanDefinitionRegistry) {
        doRegisterRedisListeners(registry)
    }

    private fun doRegisterRedisListeners(registry: BeanDefinitionRegistry) {
        val processedClasses = Collections.synchronizedSet(HashSet<Class<*>>())
        reflections.getMethodsAnnotatedWith(RedisListener::class.java).forEach { method ->
            doRegisterRedisListener(method, processedClasses, registry)
        }
    }

    private fun doRegisterRedisListener(
        method: Method,
        processedClasses: MutableSet<Class<*>>,
        registry: BeanDefinitionRegistry
    ) {
        val declaringClass = method.declaringClass
        if (!processedClasses.add(declaringClass)) {
            throw IllegalStateException(
                "Found multiple listeners declared in the same class " +
                        declaringClass.simpleName
            )
        }

        val config = resolveListenerConfig(method)
        val listenerClass = createListenerClass(declaringClass, config)
        registerBeanDefinition(registry, listenerClass, config)
    }

    private fun createListenerClass(
        declaringClass: Class<*>,
        config: RedisListenerConfig
    ): Class<*> {
        val helper = RedisListenerHelper(config)
        return byteBuddy.subclass(declaringClass)
            .implement(RedisListenerProxy::class.java)
            .method(isOverriddenFrom(ApplicationContextAware::class.java))
            .intercept(InvocationHandlerAdapter.of { obj, _, args -> helper.init(obj, args[0] as ApplicationContext) })
            .method(isOverriddenFrom(InitializingBean::class.java))
            .intercept(InvocationHandlerAdapter.of { _, _, _ -> helper.subscribe() })
            .method(isOverriddenFrom(DisposableBean::class.java))
            .intercept(InvocationHandlerAdapter.of { _, _, _ -> helper.destroy() })
            .make()
            .load(this::class.java.classLoader)
            .loaded
    }

    private fun resolveListenerConfig(method: Method): RedisListenerConfig {
        val annotation: RedisListener = method.getAnnotation(RedisListener::class.java)
        return RedisListenerConfig(
            messageListenerMethod = method,
            messageListenerClass = method.declaringClass,
            payloadClass = method.parameters
                .first { it.isAnnotationPresent(Payload::class.java) }
                .let {
                    it.getAnnotation(Payload::class.java)
                        ?.payloadClass
                        ?.javaObjectType
                        ?: it.type
                },
            channelNameArgumentIsFirst = method.parameters
                .withIndex()
                .first { (_, param) -> param.isAnnotationPresent(ChannelName::class.java) }
                .index == 0,
            channels = annotation.channels.map { environment.getProperty(it, it) },
            channelPatterns = annotation.channelPatterns.map { environment.getProperty(it, it) },
            retries = if (annotation.retriesExpression.isNotBlank()) {
                environment.getRequiredProperty(annotation.retriesExpression, Int::class.java)
            } else annotation.retries,
            retriesBackOffDuration = if (annotation.retriesBackoffDurationExpression.isNotBlank()) {
                environment.getRequiredProperty(annotation.retriesBackoffDurationExpression, Duration::class.java)
            } else if (annotation.retriesBackoffDuration > 0) {
                Duration.of(
                    annotation.retriesBackoffDuration.toLong(),
                    annotation.retriesBackoffDurationUnit.toChronoUnit()
                )
            } else null,
            bufferSize = if (annotation.bufferSizeExpression.isNotBlank()) {
                environment.getRequiredProperty(annotation.bufferSizeExpression, Int::class.java)
            } else annotation.bufferSize,
            bufferingDuration = if (annotation.bufferingDurationExpression.isNotBlank()) {
                environment.getRequiredProperty(annotation.bufferSizeExpression, Duration::class.java)
            } else if (annotation.bufferingDuration > 0) {
                Duration.of(
                    annotation.bufferingDuration.toLong(),
                    annotation.bufferingDurationUnit.toChronoUnit()
                )
            } else null,
        ).apply { validateListenerConfig() }
    }

    private fun RedisListenerConfig.validateListenerConfig() {
        if (channels.isEmpty() && channelPatterns.isEmpty()) {
            throw IllegalStateException("\"channels\" or \"channelPatterns\" must be not empty!")
        }
    }

    private fun registerBeanDefinition(
        registry: BeanDefinitionRegistry,
        listenerClass: Class<*>,
        config: RedisListenerConfig
    ) {
        val beanDefinition = GenericBeanDefinition()
            .apply {
                setBeanClass(listenerClass)
                setDependsOn(
                    REDIS_IPC_MESSAGE_LISTENER_CONTAINER_BEAN,
                    REDIS_IPC_OBJECT_MAPPER_BEAN,
                    REDIS_IPC_SCHEDULER_BEAN
                )
                scope = BeanDefinition.SCOPE_SINGLETON
            }
        registry.registerBeanDefinition(IPC_LISTENER_PREFIX + config.messageListenerClass.simpleName, beanDefinition)
    }

    internal interface RedisListenerProxy : ApplicationContextAware, InitializingBean, DisposableBean

    private class RedisListenerHelper(private val config: RedisListenerConfig) {

        private lateinit var listenerContainer: ReactiveRedisMessageListenerContainer
        private lateinit var objectMapper: ObjectMapper
        private lateinit var disposable: Disposable
        private lateinit var originalListener: Any
        private lateinit var scheduler: Scheduler

        private val subscription = AtomicReference<Subscription?>(null)

        fun init(originalListener: Any, applicationContext: ApplicationContext) {
            this.originalListener = originalListener

            listenerContainer = applicationContext.getBean(
                REDIS_IPC_MESSAGE_LISTENER_CONTAINER_BEAN,
                ReactiveRedisMessageListenerContainer::class.java
            )

            objectMapper = applicationContext.getBean(REDIS_IPC_OBJECT_MAPPER_BEAN, ObjectMapper::class.java)

            scheduler = applicationContext.getBean(REDIS_IPC_SCHEDULER_BEAN, Scheduler::class.java)
        }

        fun subscribe() {
            val topics = config.channels.map { ChannelTopic(it) } +
                    config.channelPatterns.map { PatternTopic(it) }
            val listener = listenerContainer.receive(topics, fromSerializer(string()), fromSerializer(byteArray()))
                .publishOn(scheduler)
                .map {
                    val payload = when {
                        config.payloadClass.isAssignableFrom(ByteArray::class.java) -> it.message

                        config.payloadClass.isAssignableFrom(String::class.java) ->
                            String(it.message, Charset.defaultCharset())

                        else -> objectMapper.readValue(it.message, config.payloadClass)
                    }
                    RedisMessage(it.channel, payload)
                }

            var extendedListener = if (config.bufferSize > 1) {
                val bufferedListener = if (config.bufferingDuration != null) {
                    listener.bufferTimeout(config.bufferSize, config.bufferingDuration)
                } else listener.buffer(config.bufferSize)

                bufferedListener.flatMapIterable {
                    it.groupBy { (channelName, _) -> channelName }
                        .map { (channelName, messages) ->
                            RedisMessages(
                                channelName,
                                messages.map { (_, payload) -> payload })
                        }
                }
            } else listener

            extendedListener = extendedListener.flatMap {
                when (it) {
                    is RedisMessage -> {
                        val arguments = if (config.channelNameArgumentIsFirst) {
                            arrayOf(it.channelName, it.payload)
                        } else arrayOf(it.payload, it.channelName)

                        val processor = Flux.just(it)
                            .publishOn(scheduler)
                            .map { config.messageListenerMethod.invoke(originalListener, *arguments) }
                        if (config.retries > 0) {
                            if (config.retriesBackOffDuration != null) {
                                processor.retryWhen(
                                    RetrySpec.backoff(
                                        config.retries.toLong(),
                                        config.retriesBackOffDuration
                                    )
                                )
                            } else processor.retryWhen(RetrySpec.max(config.retries.toLong()))
                        } else processor
                    }

                    is RedisMessages -> {
                        val arguments = if (config.channelNameArgumentIsFirst) {
                            arrayOf(it.channelName, it.payloads)
                        } else arrayOf(it.payloads, it.channelName)

                        val processor = Flux.just(it)
                            .publishOn(scheduler)
                            .map { config.messageListenerMethod.invoke(originalListener, *arguments) }
                        if (config.retries > 0) {
                            if (config.retriesBackOffDuration != null) {
                                processor.retryWhen(
                                    RetrySpec.backoff(
                                        config.retries.toLong(),
                                        config.retriesBackOffDuration
                                    )
                                )
                            } else processor.retryWhen(RetrySpec.max(config.retries.toLong()))
                        } else processor
                    }

                    else -> Flux.error(UnsupportedOperationException())
                }
            }

            disposable = extendedListener
                .doOnSubscribe { subscription.set(it) }
                .subscribe()
        }

        fun destroy() {
            subscription.get()!!.cancel()
            disposable.dispose()
        }

        private data class RedisMessage(val channelName: String, val payload: Any)

        private data class RedisMessages(val channelName: String, val payloads: List<Any>)
    }

    private data class RedisListenerConfig(
        val messageListenerMethod: Method,
        val messageListenerClass: Class<*>,
        val payloadClass: Class<*>,
        val channelNameArgumentIsFirst: Boolean,
        val channelPatterns: List<String>,
        val channels: List<String>,
        val retries: Int,
        val retriesBackOffDuration: Duration?,
        val bufferSize: Int,
        val bufferingDuration: Duration?,
    )

    private companion object {
        const val PACKAGES_TO_SCAN_PROPERTY = "spring.data.redis.ipc.packages-to-scan"
        const val IPC_LISTENER_PREFIX = "ipc."

        val byteBuddy: ByteBuddy = ByteBuddy()
//        val log: Logger = LoggerFactory.getLogger("IPCRedis-Listener")
    }

}
