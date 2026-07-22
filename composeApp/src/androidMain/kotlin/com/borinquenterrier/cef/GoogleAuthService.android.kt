package com.borinquenterrier.cef

import android.content.Intent
import com.google.android.gms.auth.GoogleAuthException
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.russhwolf.settings.Settings
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

actual class GoogleAuthService actual constructor(private val settings: Settings, private val appEnv: AppEnv) {
    actual suspend fun login(): Pair<String, String?> = withContext(Dispatchers.Main) {
        val context = AndroidAppContext.applicationContext
            ?: throw Exception("AndroidAppContext is not initialized. Cannot start login.")

        // GoogleAuthCallback.pendingDeferred is a single shared slot — a second concurrent
        // login() would silently overwrite it, leaving the first caller's await() below
        // waiting on a deferred nothing will ever complete again. Reject instead of hanging.
        val existing = GoogleAuthCallback.pendingDeferred
        if (existing != null && existing.isActive) {
            throw IllegalStateException("A Google sign-in is already in progress.")
        }

        val deferred = CompletableDeferred<Pair<String, String?>>()
        GoogleAuthCallback.pendingDeferred = deferred

        val intent = Intent(context, GoogleAuthActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)

        deferred.await()
    }

    @Suppress("UNUSED_PARAMETER")
    actual suspend fun refreshAccessToken(refreshToken: String): String? =
        withContext(Dispatchers.IO) {
            // On Android with GoogleAuthUtil, we just ask for the token again.
            // It manages caching and refreshing internally.
            val context = AndroidAppContext.applicationContext ?: return@withContext null
            try {
                val account =
                    GoogleSignIn.getLastSignedInAccount(context)?.account ?: return@withContext null
                val scopes = "oauth2:https://www.googleapis.com/auth/calendar"
                GoogleAuthUtil.getToken(context, account, scopes)
            } catch (e: GoogleAuthException) {
                // Includes UserRecoverableAuthException (e.g. revoked/expired consent) — there's
                // no Activity here to launch its recovery intent, so this still just fails to
                // null, forcing a full interactive re-login rather than in-place recovery. That's
                // a UX gap, not a crash. GoogleAuthException specifically means the auth grant
                // itself is the problem (vs IOException below, a plain network failure) — only
                // this case should mean "disconnect the account" to the caller.
                null
            }
        }

    actual fun logout() {
        val context = AndroidAppContext.applicationContext ?: return
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN).build()
        val client = GoogleSignIn.getClient(context, gso)
        client.signOut()
    }
}
