package com.maksimowiczm.foodyou.assistant.domain

/**
 * Keeps the app running at foreground priority while a request is in flight.
 *
 * Without this, the OS treats the app as a background process the moment the screen locks or the
 * person switches away, and fairly quickly closes its network sockets outright - an agentic turn
 * mid tool-call chain then fails with something like "Software caused connection abort", and it
 * looks like a server problem when it is really just Android reclaiming a backgrounded app's
 * connection. [start] and [stop] bracket exactly one request; nothing else needs to know this
 * exists.
 */
interface AssistantKeepAlive {
    fun start()

    fun stop()
}
