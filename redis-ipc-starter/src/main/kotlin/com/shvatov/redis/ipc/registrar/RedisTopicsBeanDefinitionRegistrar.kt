package com.shvatov.redis.ipc.registrar

import com.shvatov.redis.ipc.annotation.listener.Listener
import org.reflections.Reflections
import org.reflections.scanners.MethodAnnotationsScanner
import org.reflections.util.ConfigurationBuilder
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.config.BeanDefinition
import org.springframework.beans.factory.config.ConstructorArgumentValues
import org.springframework.beans.factory.support.BeanDefinitionRegistry
import org.springframework.beans.factory.support.GenericBeanDefinition
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar
import org.springframework.core.env.Environment
import org.springframework.core.type.AnnotationMetadata
import org.springframework.data.redis.listener.ChannelTopic
import org.springframework.data.redis.listener.PatternTopic
import java.util.Collections

internal class RedisTopicsBeanDefinitionRegistrar(
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
        log.trace("Registering redis listener topics...")
        reflections.getMethodsAnnotatedWith(Listener::class.java).forEach { method ->
            val annotation = method.getAnnotation(Listener::class.java)

            with(annotation) {
                registerTopics(registry, channels, ChannelTopic::class.java)
                registerTopics(registry, channelPatterns, PatternTopic::class.java)
            }
        }

        log.trace("Registering redis publisher topics...")
        // todo
    }

    private fun registerTopics(registry: BeanDefinitionRegistry, topics: Array<String>, topicClass: Class<*>) {
        val registeredTopics = Collections.synchronizedSet(HashSet<String>())
        topics.resolvedFromEnvironment()
            .forEach { channel ->
                if (registeredTopics.add(channel)) {
                    registerTopic(registry, channel, topicClass)
                }
            }
    }

    private fun registerTopic(registry: BeanDefinitionRegistry, topic: String, topicClass: Class<*>) {
        log.trace("Registering redis listener topic {} ({})", topic, topicClass.simpleName)
        val beanDefinition = GenericBeanDefinition()
            .apply {
                setBeanClass(topicClass)
                scope = BeanDefinition.SCOPE_SINGLETON
                constructorArgumentValues = ConstructorArgumentValues()
                constructorArgumentValues.addGenericArgumentValue(topic)
            }
        registry.registerBeanDefinition(IPC_LISTENER_PREFIX + topic, beanDefinition)
    }

    private fun Array<String>.resolvedFromEnvironment() =
        map { environment.getProperty(it, it) }
            .filter { it.isNotBlank() }

    private companion object {
        const val PACKAGES_TO_SCAN_PROPERTY = "spring.data.redis.ipc.packages-to-scan"
        const val IPC_LISTENER_PREFIX = "ipc."

        val log: Logger = LoggerFactory.getLogger(RedisTopicsBeanDefinitionRegistrar::class.java)
    }

}
