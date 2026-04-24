package com.borinquenterrier.cef

import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.client.util.store.FileDataStoreFactory
import com.russhwolf.settings.Settings
import java.io.InputStreamReader
import java.io.File

import java.io.FileInputStream
import java.nio.file.Paths

/**
 * JVM Implementation using the Local Server Flow.
 */
actual class GoogleAuthService actual constructor(private val settings: Settings) {

    private val jsonFactory = GsonFactory.getDefaultInstance()
    private val transport = NetHttpTransport()
    private val scopes = listOf(
        "https://www.googleapis.com/auth/calendar.events",
        "https://www.googleapis.com/auth/drive.readonly",
        "https://www.googleapis.com/auth/generative-language.retriever"
    )
    private val credentialsDir = File(System.getProperty("user.home"), ".cef_credentials")

    actual suspend fun login(): Pair<String, String?> {
        val flow = buildFlow()
        val receiver = LocalServerReceiver.Builder().setPort(8888).build()
        val credential = AuthorizationCodeInstalledApp(flow, receiver).authorize("user")
        return Pair(credential.accessToken, credential.refreshToken)
    }

    actual suspend fun refreshAccessToken(refreshToken: String): String? {
        val flow = buildFlow()
        val credential = flow.loadCredential("user") ?: return null
        if (credential.refreshToken != refreshToken) {
            // If they don't match, we might need to manually trigger refresh or reload
        }
        return if (credential.refreshToken()) credential.accessToken else null
    }

    private fun buildFlow(): GoogleAuthorizationCodeFlow {
        val envPath = System.getenv("CEF_GOOGLE_CLIENT_SECRET_PATH")
        val defaultPath = Paths.get(System.getProperty("user.home"), ".cef", "client_secret.json").toString()
        val secretPath = envPath ?: defaultPath
        
        val secretFile = File(secretPath)
        if (!secretFile.exists()) {
            throw IllegalStateException(
                "Google Client Secret not found.\n" +
                "Please place it at: $defaultPath\n" +
                "Or set the CEF_GOOGLE_CLIENT_SECRET_PATH environment variable."
            )
        }

        val clientSecrets = GoogleClientSecrets.load(jsonFactory, InputStreamReader(FileInputStream(secretFile)))

        return GoogleAuthorizationCodeFlow.Builder(
            transport, jsonFactory, clientSecrets, scopes
        ).setDataStoreFactory(FileDataStoreFactory(credentialsDir))
            .setAccessType("offline")
            .build()
    }
}
