package com.borinquenterrier.cef

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    container: DependencyContainer,
    modifier: Modifier = Modifier,
) {
    val settings = container.settings
    val scope = rememberCoroutineScope()
    val googleFlow = container.googleAccountFlow
    val connectionState by googleFlow.state.collectAsState()

    var apiKey by remember {
        mutableStateOf(
            settings.getString(
                "CEF_GEMINI_API_KEY",
                settings.getString("GEMINI_API_KEY", "")
            )
        )
    }
    var showAdvanced by remember { mutableStateOf(value = false) }

    val preferencesRepository = remember { container.preferencesRepository }
    var preferences by remember { mutableStateOf(StudyPreferences()) }

    LaunchedEffect(preferencesRepository) {
        preferences = preferencesRepository.getPreferences()
    }

    var studyStartStr by remember { mutableStateOf(preferences.studyStartHour.toString()) }
    var studyEndStr by remember { mutableStateOf(preferences.studyEndHour.toString()) }
    var lunchStartStr by remember { mutableStateOf(preferences.lunchStartHour.toString()) }
    var lunchEndStr by remember { mutableStateOf(preferences.lunchEndHour.toString()) }
    var dinnerStartStr by remember { mutableStateOf(preferences.dinnerStartHour.toString()) }
    var dinnerEndStr by remember { mutableStateOf(preferences.dinnerEndHour.toString()) }
    var maxStudyBlockStr by remember { mutableStateOf(preferences.maxStudyBlockHours.toString()) }
    var preferredBreakStr by remember { mutableStateOf(preferences.preferredBreakMinutes.toString()) }
    var shareAnonymousBugReports by remember { mutableStateOf(preferences.shareAnonymousBugReports) }
    var googleCalendarId by remember { mutableStateOf(preferences.googleCalendarId) }
    var googleCalendarName by remember { mutableStateOf(preferences.googleCalendarName) }

    var calendars by remember { mutableStateOf<List<RemoteCalendarMetadata>>(emptyList()) }
    var isLoadingCalendars by remember { mutableStateOf(false) }
    var calendarLoadError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(preferences) {
        studyStartStr = preferences.studyStartHour.toString()
        studyEndStr = preferences.studyEndHour.toString()
        lunchStartStr = preferences.lunchStartHour.toString()
        lunchEndStr = preferences.lunchEndHour.toString()
        dinnerStartStr = preferences.dinnerStartHour.toString()
        dinnerEndStr = preferences.dinnerEndHour.toString()
        maxStudyBlockStr = preferences.maxStudyBlockHours.toString()
        preferredBreakStr = preferences.preferredBreakMinutes.toString()
        shareAnonymousBugReports = preferences.shareAnonymousBugReports
        googleCalendarId = preferences.googleCalendarId
        googleCalendarName = preferences.googleCalendarName
    }

    fun savePreferences(
        studyStart: String = studyStartStr,
        studyEnd: String = studyEndStr,
        lunchStart: String = lunchStartStr,
        lunchEnd: String = lunchEndStr,
        dinnerStart: String = dinnerStartStr,
        dinnerEnd: String = dinnerEndStr,
        maxBlock: String = maxStudyBlockStr,
        breakLen: String = preferredBreakStr,
        calId: String = googleCalendarId,
        calName: String = googleCalendarName
    ) {
        val newPrefs = SettingsPreferencesParser.parse(
            studyStartStr = studyStart,
            studyEndStr = studyEnd,
            lunchStartStr = lunchStart,
            lunchEndStr = lunchEnd,
            dinnerStartStr = dinnerStart,
            dinnerEndStr = dinnerEnd,
            maxStudyBlockStr = maxBlock,
            preferredBreakStr = breakLen,
            shareAnonymousBugReports = shareAnonymousBugReports,
            googleCalendarId = calId,
            googleCalendarName = calName,
            currentPrefs = preferences
        )
        preferences = newPrefs
        googleCalendarId = calId
        googleCalendarName = calName
        scope.launch {
            preferencesRepository.savePreferences(newPrefs)
        }
    }

    val isGoogleLinked = connectionState is GoogleConnectionState.Linked
    val isBusy = connectionState is GoogleConnectionState.Connecting
    val loginError = (connectionState as? GoogleConnectionState.Error)?.message

    LaunchedEffect(isGoogleLinked) {
        if (isGoogleLinked) {
            isLoadingCalendars = true
            calendarLoadError = null
            try {
                calendars = container.remoteRepository.getAvailableCalendars()
            } catch (e: Exception) {
                calendarLoadError = CalendarErrorFormatter.format(e)
            } finally {
                isLoadingCalendars = false
            }
        } else {
            calendars = emptyList()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Setup & Connections", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Connect your accounts to let the AI help you manage your studies.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        GeminiSetupPanel(
            apiKey = apiKey,
            onApiKeyChange = { apiKey = it },
            settings = settings
        )

        GoogleCalendarPanel(
            container = container,
            isGoogleLinked = isGoogleLinked,
            isBusy = isBusy,
            loginError = loginError,
            googleCalendarId = googleCalendarId,
            googleCalendarName = googleCalendarName,
            calendars = calendars,
            isLoadingCalendars = isLoadingCalendars,
            calendarLoadError = calendarLoadError,
            onCalendarIdChange = { id, name -> savePreferences(calId = id, calName = name) },
            onCalendarsRefresh = { calendars = container.remoteRepository.getAvailableCalendars() },
            onCalendarLoadError = { calendarLoadError = it },
            scope = scope
        )

        StudyPreferencesPanel(
            studyStartStr = studyStartStr,
            studyEndStr = studyEndStr,
            lunchStartStr = lunchStartStr,
            lunchEndStr = lunchEndStr,
            dinnerStartStr = dinnerStartStr,
            dinnerEndStr = dinnerEndStr,
            maxStudyBlockStr = maxStudyBlockStr,
            preferredBreakStr = preferredBreakStr,
            onStudyStartChange = { studyStartStr = it; savePreferences(studyStart = it) },
            onStudyEndChange = { studyEndStr = it; savePreferences(studyEnd = it) },
            onLunchStartChange = { lunchStartStr = it; savePreferences(lunchStart = it) },
            onLunchEndChange = { lunchEndStr = it; savePreferences(lunchEnd = it) },
            onDinnerStartChange = { dinnerStartStr = it; savePreferences(dinnerStart = it) },
            onDinnerEndChange = { dinnerEndStr = it; savePreferences(dinnerEnd = it) },
            onMaxStudyBlockChange = { maxStudyBlockStr = it; savePreferences(maxBlock = it) }
        ) { preferredBreakStr = it; savePreferences(breakLen = it) }

        TextButton(
            onClick = { showAdvanced = !showAdvanced },
            modifier = Modifier.align(Alignment.End)
        ) {
            Icon(
                if (showAdvanced) Icons.Default.Info else Icons.Default.Settings,
                contentDescription = null
            )
            Spacer(Modifier.width(4.dp))
            Text("Advanced")
        }

        if (showAdvanced) {
            AdvancedSettingsPanel(
                settings = settings,
                shareAnonymousBugReports = shareAnonymousBugReports,
                onBugReportsChange = {
                    shareAnonymousBugReports = it
                    savePreferences()
                }
            )
        }
    }
}
