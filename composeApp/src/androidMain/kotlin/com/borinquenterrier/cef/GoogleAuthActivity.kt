package com.borinquenterrier.cef

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object GoogleAuthCallback {
    var pendingDeferred: CompletableDeferred<Pair<String, String?>>? = null
}

class GoogleAuthActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // This is a transparent activity just to launch the Google Sign In Intent
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(
                Scope("https://www.googleapis.com/auth/calendar"),
                Scope("https://www.googleapis.com/auth/drive.readonly")
            )
            .build()

        startActivityForResult(GoogleSignIn.getClient(this, gso).signInIntent, 1001)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1001) {
            val task: Task<GoogleSignInAccount> = GoogleSignIn.getSignedInAccountFromIntent(data)
            try {
                val account = task.result
                if (account != null && account.account != null) {
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            val scopes =
                                "oauth2:https://www.googleapis.com/auth/calendar https://www.googleapis.com/auth/drive.readonly"
                            val token = GoogleAuthUtil.getToken(
                                this@GoogleAuthActivity,
                                account.account!!,
                                scopes
                            )
                            GoogleAuthCallback.pendingDeferred?.complete(Pair(token, null))
                            finish()
                        } catch (e: com.google.android.gms.auth.UserRecoverableAuthException) {
                            // If the error is recoverable, start the intent provided by Google
                            val recoveryIntent = e.intent
                            if (recoveryIntent != null) {
                                startActivityForResult(recoveryIntent, 1002)
                            } else {
                                GoogleAuthCallback.pendingDeferred?.completeExceptionally(e)
                                finish()
                            }
                        } catch (e: Exception) {
                            GoogleAuthCallback.pendingDeferred?.completeExceptionally(e)
                            finish()
                        }
                    }
                } else {
                    GoogleAuthCallback.pendingDeferred?.completeExceptionally(Exception("Google Sign-In failed or cancelled by user."))
                    finish()
                }
            } catch (e: Exception) {
                GoogleAuthCallback.pendingDeferred?.completeExceptionally(e)
                finish()
            }
        } else if (requestCode == 1002) {
            // After recovery intent finishes, try login again (or finish)
            if (resultCode == RESULT_OK) {
                // Try to get token again
                val account = GoogleSignIn.getLastSignedInAccount(this)
                if (account != null && account.account != null) {
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            val scopes =
                                "oauth2:https://www.googleapis.com/auth/calendar https://www.googleapis.com/auth/drive.readonly"
                            val token = GoogleAuthUtil.getToken(
                                this@GoogleAuthActivity,
                                account.account!!,
                                scopes
                            )
                            GoogleAuthCallback.pendingDeferred?.complete(Pair(token, null))
                        } catch (e: Exception) {
                            GoogleAuthCallback.pendingDeferred?.completeExceptionally(e)
                        } finally {
                            finish()
                        }
                    }
                } else {
                    finish()
                }
            } else {
                GoogleAuthCallback.pendingDeferred?.completeExceptionally(Exception("User failed to recover authentication."))
                finish()
            }
        }
    }
}
