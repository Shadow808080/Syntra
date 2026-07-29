package com.example.syntra.net

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue

/**
 * How many notifications are waiting, app-wide.
 *
 * A process-scoped singleton rather than screen state: the badge is drawn in the chat
 * header, but the thing that changes it is a socket frame that can land while any
 * screen is up (or while none is). Keeping the count in whichever screen happened to
 * be listening meant it reset every time you switched tabs.
 *
 * Seeded from the server on launch, incremented by `notification.new`, and zeroed when
 * the inbox is opened.
 */
object NotificationBadge {
    var unread by mutableIntStateOf(0)

    /** Pulls the authoritative count. Silent on failure — a badge is not worth an error. */
    suspend fun refresh() {
        if (!ApiConfig.ENABLED) return
        runCatching { SyntraClient.getUnreadNotificationCount() }.onSuccess { unread = it }
    }

    fun clear() {
        unread = 0
    }
}
