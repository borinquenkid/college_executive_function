package com.borinquenterrier.cef

import android.app.Activity
import android.app.Instrumentation.ActivityResult
import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.matcher.ComponentNameMatchers.hasClassName
import androidx.test.espresso.intent.matcher.IntentMatchers.hasComponent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import junit.framework.TestCase.assertNotNull
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GoogleAuthActivityInstrumentedTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun setup() {
        AndroidAppContext.applicationContext = context
        Intents.init()
    }

    @After
    fun teardown() {
        Intents.release()
        GoogleAuthCallback.pendingDeferred = null
    }

    @Test
    fun signInCancelled_completesDeferred_exceptionally() = runBlocking {
        val deferred = CompletableDeferred<Pair<String, String?>>()
        GoogleAuthCallback.pendingDeferred = deferred

        // Intercept the Google Sign-In HubActivity and return RESULT_CANCELED,
        // so onActivityResult(1001, RESULT_CANCELED, null) fires without showing any UI.
        Intents.intending(
            hasComponent(hasClassName("com.google.android.gms.auth.api.signin.internal.SignInHubActivity"))
        ).respondWith(ActivityResult(Activity.RESULT_CANCELED, null))

        ActivityScenario.launch<GoogleAuthActivity>(
            Intent(context, GoogleAuthActivity::class.java)
        ).use { /* activity launches, gets RESULT_CANCELED from stub, calls finish() */ }

        val error = withTimeoutOrNull(5_000L) {
            runCatching { deferred.await() }.exceptionOrNull()
        }
        assertNotNull("Deferred should complete exceptionally when sign-in is cancelled", error)
    }
}
