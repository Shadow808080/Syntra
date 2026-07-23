package com.example.syntra

import android.content.Context

/**
 * Remembers whether someone is signed in, so the app can gate itself behind
 * [AuthScreen]. Backed by SharedPreferences: the flag survives restarts, which is
 * what makes the gate feel like a real login instead of a splash screen.
 */
object SessionStore {

    private const val PREFS = "syntra_session"
    private const val KEY_SIGNED_IN = "signed_in"
    private const val KEY_EMAIL = "email"
    private const val KEY_REFRESH = "refresh_token"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isSignedIn(context: Context): Boolean =
        prefs(context).getBoolean(KEY_SIGNED_IN, false)

    fun signedInEmail(context: Context): String? =
        prefs(context).getString(KEY_EMAIL, null)

    /**
     * Supabase access tokens expire after an hour, so the refresh token is what
     * actually keeps someone signed in across restarts.
     */
    fun refreshToken(context: Context): String? =
        prefs(context).getString(KEY_REFRESH, null)

    fun markSignedIn(context: Context, email: String, refreshToken: String? = null) {
        val editor = prefs(context).edit()
            .putBoolean(KEY_SIGNED_IN, true)
            .putString(KEY_EMAIL, email)
        // Never overwrite a good token with null — that silently logs the user out
        // on the next launch.
        if (!refreshToken.isNullOrBlank()) editor.putString(KEY_REFRESH, refreshToken)
        editor.apply()
    }

    fun signOut(context: Context) {
        prefs(context).edit().clear().apply()
    }
}
