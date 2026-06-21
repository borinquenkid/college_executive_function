package com.borinquenterrier.cef

import android.content.Intent
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

        val deferred = CompletableDeferred<Pair<String, String?>>()
        GoogleAuthCallback.pendingDeferred = deferred

        val intent = Intent(context, GoogleAuthActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)

        deferred.await()
    }

    actual suspend fun refreshAccessToken(refreshToken: String): String? =
        withContext(Dispatchers.IO) {
            // On Android with GoogleAuthUtil, we just ask for the token again.
            // It manages caching and refreshing internally.
            val context = AndroidAppContext.applicationContext ?: return@withContext null
            try {
                val account =
                    GoogleSignIn.getLastSignedInAccount(context)?.account ?: return@withContext null
                val scopes =
                    "oauth2:https://www.googleapis.com/auth/calendar https://www.googleapis.com/auth/drive.readonly"
                GoogleAuthUtil.getToken(context, account, scopes)
            } catch (e: Throwable) {
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
