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
     * REST base. Points at the backend's Cloudflare Tunnel, so any phone with
     * internet reaches it — no LAN/Wi-Fi dependency. No trailing slash: paths
     * like `/api/v1/...` are appended directly.
     */
    const val BASE_URL = "https://classifieds-jesus-arts-implementation.trycloudflare.com"

    /** WebSocket base — same host over TLS, so `wss://`. */
    const val WS_URL = "wss://classifieds-jesus-arts-implementation.trycloudflare.com"

    /**
     * Only for a backend running with `AUTH_DEV_BYPASS=true` and `APP_ENV` other
     * than production: send `X-Debug-User: <uuid>` and skip logging in entirely.
     * Leave blank for the normal flow.
     */
    const val DEBUG_USER_ID = ""

    val useDebugUser: Boolean get() = DEBUG_USER_ID.isNotBlank()
}
