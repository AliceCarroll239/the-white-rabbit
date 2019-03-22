package com.viartemev.thewhiterabbit.channel

import com.viartemev.thewhiterabbit.AbstractTestContainersTest
import com.viartemev.thewhiterabbit.queue.QueueSpecification
import com.viartemev.thewhiterabbit.queue.declareQueue
import com.viartemev.thewhiterabbit.utils.createMessage
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.testcontainers.shaded.org.apache.commons.lang.RandomStringUtils
import java.lang.Thread.sleep
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.TimeUnit

private val logger = KotlinLogging.logger {}

class TxPublisherTest : AbstractTestContainersTest() {

    private lateinit var oneTimeQueue: String

    @BeforeEach
    fun setUpEach() {
        oneTimeQueue = randomQueue()
    }

    private fun randomQueue() = "test-queue-" + RandomStringUtils.randomNumeric(5)

    @Test
    fun `test message publishing with tx implicit commit`() {
        factory.newConnection().use { conn ->
            runBlocking {
                conn.txChannel {
                    declareQueue(QueueSpecification(oneTimeQueue))
                    transaction {
                        val message = createMessage(queue = oneTimeQueue, body = "Hello from tx")
                        publish(message)
                    }
                }
            }
        }

        sleep(5000)
        val info = httpRabbitMQClient.getQueue(DEFAULT_VHOST, oneTimeQueue)
        assertEquals(1, info.messagesReady)
    }

    @Test
    fun `test message publishing with tx explicit commit`() {
        factory.newConnection().use { conn ->
            runBlocking {
                conn.txChannel {
                    declareQueue(QueueSpecification(oneTimeQueue))
                    transaction {
                        val message = createMessage(queue = oneTimeQueue, body = "Hello from tx")
                        publish(message)
                        commit()
                    }
                }
            }
        }

        sleep(5000)
        val info = httpRabbitMQClient.getQueue(DEFAULT_VHOST, oneTimeQueue)
        assertEquals(1, info.messagesReady)
    }

    @Test
    fun `test message publishing with tx implicit rollback`() {
        factory.newConnection().use { conn ->
            runBlocking {
                conn.txChannel {
                    declareQueue(QueueSpecification(oneTimeQueue))
                    transaction {
                        val message = createMessage(queue = oneTimeQueue, body = "Hello from tx")
                        publish(message)
                        throw RuntimeException("sth bad happened")
                    }
                }
            }
        }

        sleep(5000)
        val info = httpRabbitMQClient.getQueue(DEFAULT_VHOST, oneTimeQueue)
        assertEquals(0, info.messagesReady)
    }

    @Test
    fun `test message publishing with tx explicit rollback`() {
        factory.newConnection().use { conn ->
            runBlocking {
                conn.txChannel {
                    declareQueue(QueueSpecification(oneTimeQueue))
                    transaction {
                        val message = createMessage(queue = oneTimeQueue, body = "Hello from tx")
                        publish(message)
                        rollback()
                    }
                }
            }
        }

        sleep(5000)
        val info = httpRabbitMQClient.getQueue(DEFAULT_VHOST, oneTimeQueue)
        assertEquals(0, info.messagesReady)
    }

    @Test
    fun `test 2 successful tx series`() {
        factory.newConnection().use { conn ->
            runBlocking {
                conn.txChannel {
                    declareQueue(QueueSpecification(oneTimeQueue))
                    transaction {
                        val message = createMessage(queue = oneTimeQueue, body = "Hello from tx 1")
                        publish(message)
                    }
                    transaction {
                        val message = createMessage(queue = oneTimeQueue, body = "Hello from tx 2")
                        publish(message)
                    }
                }
            }
        }

        sleep(5000)
        val info = httpRabbitMQClient.getQueue(DEFAULT_VHOST, oneTimeQueue)
        assertEquals(2, info.messagesReady)
    }

    @Test
    fun `test 3 mixed tx series`() {
        factory.newConnection().use { conn ->
            runBlocking {
                conn.txChannel {
                    declareQueue(QueueSpecification(oneTimeQueue))
                    transaction {
                        val message = createMessage(queue = oneTimeQueue, body = "Hello from successful tx")
                        publish(message)
                    }
                    transaction {
                        val message = createMessage(queue = oneTimeQueue, body = "Hello from failed tx")
                        publish(message)
                        rollback()
                    }
                }
            }
        }

        sleep(5000)
        val info = httpRabbitMQClient.getQueue(DEFAULT_VHOST, oneTimeQueue)
        assertEquals(1, info.messagesReady)
    }


    @Test
    fun `test consume message within successful tx`() {

        factory.newConnection().use { conn ->

            val count = 10
            val latch = CountDownLatch(count)

            runBlocking {
                conn.txChannel {
                    declareQueue(QueueSpecification(oneTimeQueue))

                    transaction {
                        (1..count).map {
                            publish(createMessage(queue = oneTimeQueue, body = "message #$it"))
                        }
                    }

                    transaction {
                        consume(oneTimeQueue) {
                            for (i in 1..count)
                                consumeMessageWithConfirm {
                                    logger.info { "processing msg:" + String(it.body) }
                                    latch.countDown()
                                }
                        }
                    }
                }
            }

            assertTrue(latch.await(1, TimeUnit.SECONDS))
            sleep(5000)
            assertEquals(0, httpRabbitMQClient.getQueue(DEFAULT_VHOST, oneTimeQueue).messagesReady)
        }
    }

    @Test
    fun `test consume several messages with explicit rollback`() {

        factory.newConnection().use { conn ->

            val count = 10
            val latch = CountDownLatch(count)

            runBlocking {
                conn.txChannel {
                    declareQueue(QueueSpecification(oneTimeQueue))

                    transaction {
                        (1..count).map {
                            publish(createMessage(queue = oneTimeQueue, body = "message #$it"))
                        }
                    }

                    delay(5000)
                    assertEquals(count, httpRabbitMQClient.getQueue(DEFAULT_VHOST, oneTimeQueue).messagesReady.toInt())

                    transaction {
                        consume(oneTimeQueue) {
                            for (i in 1..count) {
                                delay(50)
                                consumeMessageWithConfirm {
                                    logger.info { "processing msg:" + String(it.body) }
                                    latch.countDown()
                                }
                            }
                        }
                        rollback()
                    }
                }
            }

            sleep(5000)
            assertEquals(count, httpRabbitMQClient.getQueue(DEFAULT_VHOST, oneTimeQueue).messagesReady.toInt())
        }
    }

    @Test
    fun `test consume several messages with implicit rollback`() {

        factory.newConnection().use { conn ->

            val count = 100
            val latch = CountDownLatch(count)

            runBlocking {
                conn.txChannel {
                    declareQueue(QueueSpecification(oneTimeQueue))

                    transaction {
                        (1..count).map {
                            publish(createMessage(queue = oneTimeQueue, body = "message #$it"))
                        }
                    }

                    delay(5000)
                    assertEquals(count, httpRabbitMQClient.getQueue(DEFAULT_VHOST, oneTimeQueue).messagesReady.toInt())

                    transaction {
                        consume(oneTimeQueue) {
                            for (i in 1..count) {
                                delay(50)
                                if (ThreadLocalRandom.current().nextBoolean())
                                    throw RuntimeException("sth wrong while processing msg #$i")
                                consumeMessageWithConfirm {
                                    logger.info { "processing msg:" + String(it.body) }
                                    latch.countDown()
                                }
                            }
                        }
                        throw RuntimeException("sth bad happened")
                    }
                }
            }

            sleep(5000)
            assertEquals(count, httpRabbitMQClient.getQueue(DEFAULT_VHOST, oneTimeQueue).messagesReady.toInt())
        }
    }
}
