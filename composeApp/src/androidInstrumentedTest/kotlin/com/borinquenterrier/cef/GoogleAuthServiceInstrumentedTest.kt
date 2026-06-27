package com.borinquenterrier.cef

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.russhwolf.settings.MapSettings
import junit.framework.TestCase.assertNotNull
import junit.framework.TestCase.assertNull
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GoogleAuthServiceInstrumentedTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val service = GoogleAuthService(MapSettings(), AppEnv())
    private var savedContext = AndroidAppContext.applicationContext

    @Before
    fun setup() {
        AndroidAppContext.applicationContext = context
    }

    @After
    fun teardown() {
        AndroidAppContext.applicationContext = savedContext
    }

    @Test
    fun refreshAccessToken_withNullContext_returnsNull() = runBlocking {
        AndroidAppContext.applicationContext = null
        val result = service.refreshAccessToken("unused")
        assertNull(result)
    }

    @Test
    fun refreshAccessToken_withValidContext_doesNotThrow() = runBlocking<Unit> {
        // Verifies the catch-all in refreshAccessToken swallows any GMS error.
        // Returns null when no GoogleSignIn session exists, or a token string if signed in.
        service.refreshAccessToken("unused") // must not throw
    }

    @Test
    fun refreshAccessToken_withSignedInAccount_returnsNonNullToken() = runBlocking {
        // Only asserts when getLastSignedInAccount() finds an account signed in
        // via the app's GoogleAuthActivity (separate from Android system accounts).
        val gmsAccount = GoogleSignIn.getLastSignedInAccount(context)
        if (gmsAccount == null) return@runBlocking // no GMS sign-in state on this emulator

        val result = service.refreshAccessToken("unused")
        assertNotNull("Expected a token but got null; account=$gmsAccount", result)
    }

    @Test
    fun logout_doesNotThrow() {
        service.logout()
    }

    @Test
    fun logout_thenRefreshReturnsNull() = runBlocking {
        service.logout()
        // After sign-out, getLastSignedInAccount() returns null, so token fetch skips
        val result = service.refreshAccessToken("unused")
        assertNull(result)
    }
}
