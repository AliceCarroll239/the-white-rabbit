package com.viartemev.thewhiterabbit.publisher

import com.rabbitmq.client.ConnectionFactory
import com.rabbitmq.client.MessageProperties
import com.viartemev.thewhiterabbit.channel.createConfirmChannel
import com.viartemev.thewhiterabbit.queue.QueueSpecification
import com.viartemev.thewhiterabbit.queue.declareQueue
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import kotlin.system.measureNanoTime
import kotlin.test.assertTrue

//FIXME add testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PublisherTest {

    private val QUEUE_NAME = "test_queue"
    private val EXCHANGE_NAME = ""
    lateinit var factory: ConnectionFactory


    @BeforeAll
    fun setUp() {
        factory = ConnectionFactory()
        factory.host = "localhost"
        factory.useNio()
    }

    @Test
    fun `test one message publishing`() {
        factory.newConnection().use { connection ->
            connection.createConfirmChannel().use { channel ->
                val publisher = channel.publisher()
                runBlocking {
                    channel.declareQueue(QueueSpecification(QUEUE_NAME))
                    val message = createMessage("Hello")
                    val ack = publisher.publishWithConfirm(message)
                    assertTrue { ack }
                }
            }
        }
    }

    @Test
    fun `test n-messages publishing manually`() {
        val times = 10
        val time = measureNanoTime {
            factory.newConnection().use { connection ->
                connection.createConfirmChannel().use { channel ->
                    val publisher = channel.publisher()
                    runBlocking {
                        channel.declareQueue(QueueSpecification(QUEUE_NAME))
                        val acks = coroutineScope {

                            (1..times).map {
                                async {
                                    publisher.publishWithConfirm(createMessage("Hello #$it"))
                                }
                            }.awaitAll()
                        }
                        assertTrue { acks.all { true } }
                    }
                }
            }
        }
        println("Time: $time")
    }

    @Test
    fun `test n-messages publishing`() {
        val times = 10
        val time = measureNanoTime {
            factory.newConnection().use { connection ->
                connection.createConfirmChannel().use { channel ->
                    val publisher = channel.publisher()
                    runBlocking {
                        channel.declareQueue(QueueSpecification(QUEUE_NAME))
                        val messages = (1..times).map { createMessage("Hello #$it") }
                        val acks = publisher.publishWithConfirm(messages).awaitAll()
                        assertTrue { acks.all { true } }
                    }
                }
            }
        }
        println("Time: $time")
    }

    private fun createMessage(body: String) = OutboundMessage(EXCHANGE_NAME, QUEUE_NAME, MessageProperties.PERSISTENT_BASIC, body)
}
