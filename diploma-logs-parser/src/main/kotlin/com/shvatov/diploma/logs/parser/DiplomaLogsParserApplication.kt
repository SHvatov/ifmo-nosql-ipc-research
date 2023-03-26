package com.shvatov.diploma.logs.parser

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.google.common.collect.HashBasedTable
import com.google.common.collect.Table
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.boot.CommandLineRunner
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import java.io.File
import java.time.LocalDateTime
import java.util.*

private val logger: Logger = LoggerFactory.getLogger(DiplomaLogsParserApplication::class.java)

fun main(args: Array<String>) {
    runApplication<DiplomaLogsParserApplication>(*args)
}

data class LogRecord @JsonCreator constructor(
    @JsonProperty("logger")
    val logger: String,
    @JsonProperty("timestamp")
    @JsonFormat(pattern = "dd MMM yyyy;HH:mm:ss.SSS")
    val timestamp: LocalDateTime,
    @JsonProperty("message")
    val message: String
)

data class LogRecordMessage(val systemTimeNs: Long, val messageId: Long, val message: String) {
    companion object {

        private val PATTERN = ("\\[SYSTEM_TIME_NS=([0-9]*)," +
                "MESSAGE_ID=([0-9]*)," +
                "MESSAGE=([a-zA-Z0-9\\s\\[\\]_]*)\\]").toPattern()

        fun fromString(str: String): LogRecordMessage? {
            val matcher = PATTERN.matcher(str)
            if (!matcher.matches()) {
                logger.warn("String {} does not match LogRecordMessage pattern, thus will be skipped", str)
                return null
            }
            return LogRecordMessage(
                systemTimeNs = matcher.group(1).toLong(),
                messageId = matcher.group(2).toLong(),
                message = matcher.group(3)
            )
        }
    }
}

typealias LogMap = HashMap<Long, TreeSet<LogRecordMessage>>

fun LogMap.put(message: LogRecordMessage) {
    computeIfAbsent(message.messageId) {
        TreeSet(Comparator.comparing(LogRecordMessage::systemTimeNs))
    }.add(message)
}

@SpringBootApplication
class DiplomaLogsParserApplication : CommandLineRunner {

    private val objectMapper: ObjectMapper =
        ObjectMapper()
            .apply {
                registerModule(JavaTimeModule())
            }

    override fun run(vararg args: String) {
        if (args.size != 1) {
            logger.error(
                "Expected single argument, which specifies the paths to the log files, " +
                        "got following: {}. Program execution has been interrupted.",
                args.joinToString(";")
            )
            return
        }

        val logFiles = args[0]
            .split(DEFAULT_DELIMITER)
            .map { it.trim() }
            .filter { it.endsWith(LOG_FILE_EXTENSION) }
            .map { File(it) }
            .filter { it.exists() and it.isFile }
            .ifEmpty {
                logger.warn("No *{} files have been provided.", LOG_FILE_EXTENSION)
                return
            }

        val log = LogMap()
        for (file in logFiles) {
            file.useLines { lines ->
                lines.forEach { line ->
                    val record = runCatching {
                        objectMapper.readValue(line, LogRecord::class.java)
                    }.onFailure {
                        logger.warn("Error while parsing line {}. It will be skipped.", line)
                    }
                        .getOrNull()
                        .also { logger.debug("Read following LogRecord from file {}: {}", file.name, it) }

                    if (record != null) {
                        val message = LogRecordMessage.fromString(record.message)
                            ?.also { logger.debug("Read following LogRecordMessage from file {}: {}", file.name, it) }
                        if (message != null) {
                            log.put(message)
                        }
                    }
                }
            }
        }
        logger.debug("Computed following LogMap: {}", log)

        class AverageCounter(private var sum: Long = 0, var number: Long = 0) {

            fun add(value: Long) {
                sum += value
                number += 1
            }

            fun calculate(): Double =
                if (number != 0L)
                    sum / number.toDouble()
                else
                    -1.0

        }

        fun Table<String, String, AverageCounter>.add(row: String, cell: String, value: Long) {
            if (!contains(row, cell)) {
                put(row, cell, AverageCounter())
            }
            get(row, cell)!!.add(value)
        }

        val stages = HashBasedTable.create<String, String, AverageCounter>()
        for ((messageId, messages) in log) {
            logger.debug("Processing message with id {}. Messages: {}", messageId, messages.joinToString(";"))

            val (first, last) = messages.first() to messages.last()
            stages.add(first.message, last.message, last.systemTimeNs - first.systemTimeNs)
            logger.info(
                "Stage {} - Stage {} = {} ns",
                first.message, last.message,
                last.systemTimeNs - first.systemTimeNs
            )

            var previous: LogRecordMessage? = null
            for (current in messages) {
                if (previous == null) {
                    previous = current
                    continue
                }
                stages.add(previous.message, current.message, current.systemTimeNs - previous.systemTimeNs)
                previous = current
            }
        }

        stages.cellSet().forEach { cell ->
            val averageCounter = stages.get(cell.rowKey, cell.columnKey)!!
            val average = averageCounter.calculate()
            logger.info(
                "Average time between stage \"{}\" and stage \"{}\" is {} ns",
                cell.rowKey, cell.columnKey, average
            )
        }
    }

    private companion object {

        const val DEFAULT_DELIMITER = ","

        const val LOG_FILE_EXTENSION = ".log"

    }

}
