package com.example.syntra.net

import android.content.Context
import android.widget.Toast

/**
 * The one way to block and unblock. Every screen calls these — nothing calls
 * [SyntraClient.blockUser] / [SyntraClient.unblockUser] or [BlockStore] directly.
 *
 * WHY THIS EXISTS. Blocking was implemented separately at each entry point, and the
 * copies had drifted apart badly enough that the same action behaved differently
 * depending on where you started it:
 *
 *  - the chat list fired the request and forgot it, then said "Diblokir di perangkat
 *    ini. Server belum punya fitur blokir." — untrue for months, and it left people
 *    believing the block was cosmetic;
 *  - the chat screen awaited nothing either but claimed success outright, and closed
 *    the screen before anyone could see a failure;
 *  - the profile screen was optimistic with no rollback.
 *
 * So a block made from the home screen and a block made from inside the chat could end
 * up in genuinely different states. Behaviour belongs in one place; screens decide what
 * to SHOW, not what blocking MEANS.
 *
 * The asymmetry between the two calls is deliberate:
 *  - [block] is optimistic — hiding someone immediately is the safe direction, and it
 *    rolls back if the server refuses.
 *  - [unblock] confirms FIRST — clearing the mirror on a request that actually failed
 *    is how "sudah dibuka tapi masih terblokir" happened: the next sync trusts the
 *    server and puts the block straight back.
 */
object BlockActions {

    /**
     * Blocks [username]. Returns true when the server confirmed.
     *
     * The local mirror is updated immediately and reverted if the request fails, so the
     * UI never claims a block that does not exist on the server.
     */
    suspend fun block(context: Context, username: String?, userId: String?): Boolean {
        if (username.isNullOrBlank()) {
            // Without a username there is nothing to send: the API is keyed by it.
            // Recording it locally would only fake a block until the next sync.
            return false
        }
        BlockStore.add(context, username, userId)
        if (!ApiConfig.ENABLED) return true
        val ok = runCatching { SyntraClient.blockUser(username) }.isSuccess
        if (!ok) BlockStore.remove(context, username, userId)
        return ok
    }

    /** Unblocks [username]. Returns true when the server confirmed. */
    suspend fun unblock(context: Context, username: String?, userId: String?): Boolean {
        if (username.isNullOrBlank()) return false
        if (!ApiConfig.ENABLED) {
            BlockStore.remove(context, username, userId)
            return true
        }
        val ok = runCatching { SyntraClient.unblockUser(username) }.isSuccess
        if (ok) BlockStore.remove(context, username, userId)
        return ok
    }

    /** The failure message, identical everywhere so the app speaks with one voice. */
    fun reportFailure(context: Context, blocking: Boolean) {
        Toast.makeText(
            context,
            if (blocking) "Gagal memblokir. Periksa koneksi lalu coba lagi."
            else "Gagal membuka blokir. Periksa koneksi lalu coba lagi.",
            Toast.LENGTH_LONG,
        ).show()
    }
}
