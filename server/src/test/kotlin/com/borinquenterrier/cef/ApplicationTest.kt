package com.borinquenterrier.cef

import com.borinquenterrier.cef.lti.LtiTestSupport
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

class ApplicationTest {

    @Test
    fun testRoot() = testApplication {
        // "/" doesn't touch containers or the directory DB, but module()'s defaults for those
        // resolve *eagerly* (see resolvedDirectoryDatabase in Application.kt) — pass an isolated
        // one so this test can't accidentally touch the real ~/.cef/tenants directory.
        val tempDir = Files.createTempDirectory("cef-application-test").toFile().absolutePath
        val directoryDatabase = DirectoryDatabase(tempDir)
        application {
            module(
                ltiPlatformConfig = LtiTestSupport.config,
                ltiVerifier = LtiTestSupport.verifier(),
                directoryDatabase = directoryDatabase,
                dbFactory = TenantDatabaseFactory(tempDir),
                appBaseUrl = "https://test.example.edu"
            )
        }
        val response = client.get("/")
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("Ktor: ${Greeting().greet()}", response.bodyAsText())
    }
}