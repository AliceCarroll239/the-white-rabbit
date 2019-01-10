package com.viartemev.thewhiterabbit.consumer

import com.rabbitmq.client.ConnectionFactory
import com.rabbitmq.client.Delivery
import com.viartemev.thewhiterabbit.channel.consumer
import com.viartemev.thewhiterabbit.queue.Queue
import com.viartemev.thewhiterabbit.queue.QueueSpecification
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

//FIXME add testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ConfirmConsumerTest {

    private val QUEUE_NAME = "test_queue"
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
            connection.createChannel().use { channel ->
                val consumer = channel.consumer(QUEUE_NAME)
                runBlocking {
                    Queue.declareQueue(channel, QueueSpecification(QUEUE_NAME))
                    for (i in 1..3) {
                        launch {
                            consumer.consumeWithConfirm({ handleDelivery(it) })
                        }
                    }
                }
            }
        }
    }

    suspend fun handleDelivery(message: Delivery) {
        println("Got a message: ${String(message.body)}. Let's do some async work...")
        delay(100)
        println("Work is done")
    }
}