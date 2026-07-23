package com.example.syntra.net

/**
 * Connection settings for the Syntra backend.
 *
 * The app talks to **one** host: the Syntra REST/WebSocket API. It holds no
 * third-party keys — authentication is the backend's job, exposed as
 * `POST /api/v1/auth/` (contract in `auth-api.md`).
 */
object ApiConfig {

    /** Master switch. `false` = use dummy data; `true` = talk to the backend. */
    const val ENABLED = true

    /**
     * REST base. Use the machine's LAN IP so a physical phone can reach it —
     * `localhost` on the phone means the phone itself. On an emulator the host is
     * reachable at `http://10.0.2.2:8081` instead.
     */
    const val BASE_URL = "http://192.168.1.6:8081"

    /** WebSocket base — same host, `ws://` (or `wss://` behind TLS). */
    const val WS_URL = "ws://192.168.1.6:8081"

    /**
     * Only for a backend running with `AUTH_DEV_BYPASS=true` and `APP_ENV` other
     * than production: send `X-Debug-User: <uuid>` and skip logging in entirely.
     * Leave blank for the normal flow.
     */
    const val DEBUG_USER_ID = ""

    val useDebugUser: Boolean get() = DEBUG_USER_ID.isNotBlank()
}
