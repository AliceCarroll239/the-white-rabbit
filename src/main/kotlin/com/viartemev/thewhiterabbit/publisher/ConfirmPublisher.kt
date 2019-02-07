package com.viartemev.thewhiterabbit.publisher

import com.rabbitmq.client.Channel
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.suspendCancellableCoroutine
import mu.KotlinLogging
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.Continuation

private val logger = KotlinLogging.logger {}

class ConfirmPublisher internal constructor(private val channel: Channel) {
    private val continuations = ConcurrentHashMap<Long, Continuation<Boolean>>()

    init {
        channel.addConfirmListener(AckListener(continuations))
    }

    /**
     * Asynchronous publish a message with the waiting of confirmation.
     *
     * @see com.viartemev.thewhiterabbit.publisher.OutboundMessage
     * @return acknowledgement - represent messages handled successfully or lost by the broker.
     * @throws java.io.IOException if an error is encountered
     */
    suspend fun publishWithConfirm(message: OutboundMessage): Boolean {
        val messageSequenceNumber = channel.nextPublishSeqNo
        logger.debug { "The message Sequence Number: $messageSequenceNumber" }

        return suspendCancellableCoroutine { continuation ->
            continuations[messageSequenceNumber] = continuation
            message.run { channel.basicPublish(exchange, routingKey, properties, msg.toByteArray()) }
        }
    }

    /**
     * Asynchronous publish a list of messages with the waiting of confirmation.
     *
     * @see com.viartemev.thewhiterabbit.publisher.OutboundMessage
     * @return list of acknowledgements - represent messages handled successfully or lost by the broker.
     * @throws java.io.IOException if an error is encountered
     */
    suspend fun publishWithConfirm(messages: List<OutboundMessage>): List<Deferred<Boolean>> = coroutineScope {
        messages.map { async { publishWithConfirm(it) } }
    }
}
