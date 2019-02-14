package com.viartemev.thewhiterabbit.common

import kotlinx.coroutines.newSingleThreadContext

/**
 * Resource management dispatcher.
 * Used as a dispatcher only for managing exchanges and queues.
 */
val resourceManagementDispatcher = newSingleThreadContext("ResourceManagementDispatcher")
