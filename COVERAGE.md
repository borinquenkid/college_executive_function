# Code Coverage Report

This report displays the **actual test coverage** for all classes in `composeApp/src/commonMain/kotlin`.
Generated using the **JetBrains Kover** plugin after running JVM unit/integration tests.

## Overall Metrics
- **Overall Line Coverage**: **83.84%** (7368/8788 lines)
- **Total Source Files**: 85

## Coverage by File

| Status | File | Line Coverage | Branch Coverage | Instruction Coverage | Classes |
| :---: | :--- | :---: | :---: | :---: | :--- |
| 🔴 | SourceInterfaces.kt | 0.0% (0/0) | N/A | 0.0% | *None* |
| 🔴 | PlatformFileSystem.kt | 0.0% (0/0) | N/A | 0.0% | *None* |
| 🔴 | App.kt | 0.0% (0/52) | 0.0% (0/4) | 0.0% | AppKt, AppKt$App$containerState$1$1, AppKt$App$containerState$1$1$1 |
| 🔴 | IcsExport.kt | 0.0% (0/0) | N/A | 0.0% | *None* |
| 🔴 | Platform.kt | 0.0% (0/0) | N/A | 0.0% | *None* |
| 🔴 | SourceItemView.kt | 0.0% (0/46) | 0.0% (0/42) | 1.6% | SourceItemViewKt |
| 🔴 | DocxReader.kt | 0.0% (0/0) | N/A | 0.0% | *None* |
| 🔴 | RoutineItem.kt | 0.0% (0/24) | 0.0% (0/4) | 0.0% | RoutineItem, RoutineItem$Companion |
| 🔴 | RoutineSetupScreen.kt | 0.0% (0/10) | 0.0% (0/8) | 0.0% | RoutineSetupScreenKt |
| 🔴 | SettingsFactory.kt | 0.0% (0/0) | N/A | 0.0% | *None* |
| 🔴 | PlatformUtils.kt | 0.0% (0/0) | N/A | 0.0% | *None* |
| 🔴 | RoutineScreen.kt | 0.0% (0/88) | 0.0% (0/28) | 0.0% | ComposableSingletons$RoutineScreenKt, RoutineScreenKt, RoutineScreenKt$RoutineScreen$1$1, RoutineScreenKt$RoutineScreen$4$1$1 |
| 🔴 | SourceRepository.kt | 0.0% (0/0) | N/A | 0.0% | *None* |
| 🔴 | PdfReader.kt | 0.0% (0/0) | N/A | 0.0% | *None* |
| 🔴 | LocalFileReader.kt | 0.0% (0/0) | N/A | 0.0% | *None* |
| 🔴 | FilePicker.kt | 0.0% (0/0) | N/A | 0.0% | *None* |
| 🔴 | ErrorBanner.kt | 14.8% (18/122) | 22.7% (10/44) | 27.7% | ComposableSingletons$ErrorBannerKt, ErrorBannerKt |
| 🟡 | RecursiveDecompositionAIService.kt | 50.0% (4/8) | N/A | 38.1% | RecursiveDecompositionAIService |
| 🟡 | IngestionAgent.kt | 54.4% (62/114) | 38.9% (14/36) | 45.4% | IngestionAgent |
| 🟡 | EventGenerator.kt | 56.0% (28/50) | 42.3% (22/52) | 54.9% | EventGenerator |
| 🟡 | GoogleConnectionState.kt | 61.5% (16/26) | 0.0% (0/20) | 79.4% | GoogleConnectionEvent, GoogleConnectionEvent$ConnectionFailed, GoogleConnectionEvent$ConnectionSuccess, GoogleConnectionEvent$Disconnect, GoogleConnectionEvent$StartConnection, GoogleConnectionState, GoogleConnectionState$Companion, GoogleConnectionState$Connecting, GoogleConnectionState$Error, GoogleConnectionState$Error$Companion, GoogleConnectionState$Linked, GoogleConnectionState$Unlinked |
| 🟡 | SourcesPanel.kt | 65.0% (52/80) | 61.5% (32/52) | 79.2% | SourcesPanelKt, SourcesPanelKt$SourcesPanel$1$2$1$1$1$1 |
| 🟡 | IcsStringBuilder.kt | 66.2% (90/136) | 39.1% (18/46) | 69.6% | IcsStringBuilder |
| 🟡 | OAuthExchange.kt | 67.9% (38/56) | 25.0% (8/32) | 59.3% | OAuthExchange, TokenResponse, TokenResponse$Companion |
| 🟡 | Logger.kt | 70.0% (28/40) | 50.0% (6/12) | 53.2% | Logger, LoggerKt |
| 🟡 | AppController.kt | 70.3% (52/74) | 33.3% (4/12) | 74.7% | AppController, AppController$1, AppController$1$1, AppController$2, AppController$2$1, AppScreen, AppScreen$Calendar, AppScreen$Home, AppScreen$Routine, AppScreen$Settings |
| 🟡 | CalendarInterfaces.kt | 72.7% (16/22) | N/A | 68.2% | OverlapException, RemoteCalendarMetadata, StudentCalendarRepository |
| 🟡 | NormalizationService.kt | 73.7% (28/38) | 46.2% (24/52) | 68.7% | NormalizationService |
| 🟡 | SettingsScreen.kt | 73.8% (462/626) | 46.4% (52/112) | 73.1% | ComposableSingletons$SettingsScreenKt, SettingsScreenKt, SettingsScreenKt$SettingsScreen$1$1, SettingsScreenKt$SettingsScreen$2$1, SettingsScreenKt$SettingsScreen$3$2$1$2$1$1, SettingsScreenKt$SettingsScreen$parseAndSave$1 |
| 🟡 | SyncNegotiationApplier.kt | 76.5% (52/68) | 39.1% (36/92) | 70.8% | SyncNegotiationApplier |
| 🟡 | DependencyContainer.kt | 79.3% (92/116) | N/A | 95.2% | DependencyContainer |
| 🟢 | SyncProposal.kt | 80.0% (32/40) | 25.0% (4/16) | 55.3% | SyncNegotiation, SyncProposal$DirectConflict, SyncProposal$StudyBlockShift |
| 🟢 | DecompositionOrchestrator.kt | 80.3% (98/122) | 81.3% (26/32) | 90.5% | DecompositionOrchestrator, WorkUnit$SubTask, WorkUnit$Task |
| 🟢 | WebSourceReader.kt | 81.0% (34/42) | N/A | 86.6% | WebSourceReader |
| 🟢 | GeminiResponseParser.kt | 81.6% (160/196) | 42.1% (90/214) | 72.7% | GeminiResponseParser |
| 🟢 | AcademicCalendar.kt | 81.6% (498/610) | 54.3% (102/188) | 81.4% | AcademicCalendarKt, AcademicCalendarKt$AcademicCalendar$1$1, AcademicCalendarKt$AcademicCalendar$2$1, AcademicCalendarKt$AcademicCalendar$2$1$1, AcademicCalendarKt$AcademicCalendar$5$1$1$1, AcademicCalendarKt$AcademicCalendar$6$1$1$2$1$1$2$1$1, AcademicCalendarKt$AcademicCalendar$6$1$1$3$1$3$1$1, AcademicCalendarKt$AcademicCalendar$6$1$1$4$2$1$1, AcademicCalendarKt$TaskDecompositionDialog$2$1$2$1$1, AcademicCalendarKt$TaskDecompositionDialog$2$1$3$1$1$1, AcademicCalendarKt$TaskDecompositionDialog$2$1$4$1$1, ComposableSingletons$AcademicCalendarKt |
| 🟢 | GeminiModelNegotiator.kt | 81.9% (136/166) | 47.6% (78/164) | 74.3% | GeminiModelNegotiator, GeminiModelNegotiator$Companion, ModelInfo, ModelInfo$Companion, ModelListResponse, ModelListResponse$Companion |
| 🟢 | CommonSourceProviders.kt | 83.5% (274/328) | 45.7% (64/140) | 83.2% | CommonSourceProvidersKt, CommonSourceProvidersKt$DrivePickerDialog$1$1, CommonSourceProvidersKt$DrivePickerDialog$3$1$1$1$1$1$1, ComposableSingletons$CommonSourceProvidersKt, GoogleDriveSourceProvider, LocalFileSourceProvider, UrlSourceProvider |
| 🟢 | GeminiAIService.kt | 84.6% (362/428) | 54.2% (154/284) | 75.3% | Candidate, Candidate$Companion, Content, Content$Companion, GeminiAIService, GeminiAIService$1, GeminiAIService$Companion, GeminiAIService$TaskTier, GeminiResponse, GeminiResponse$Companion, Part, Part$Companion |
| 🟢 | AppContent.kt | 85.7% (96/112) | 75.0% (24/32) | 86.2% | AppContentKt, AppContentKt$AppContent$1$1, AppContentKt$AppContent$1$1$1, AppContentKt$AppContent$1$1$2, ComposableSingletons$AppContentKt |
| 🟢 | StudyBlockShiftResolver.kt | 87.1% (54/62) | 56.7% (34/60) | 87.4% | StudyBlockShiftResolver |
| 🟢 | ChatPanel.kt | 87.4% (208/238) | 50.0% (36/72) | 84.8% | ChatMessage, ChatPanelKt, ChatPanelKt$ChatPanel$1$3$2$1$1, ComposableSingletons$ChatPanelKt |
| 🟢 | EventAgent.kt | 87.7% (412/470) | 48.6% (134/276) | 79.6% | AgentError, AgentError$GenericError, AgentError$QuotaExhausted, EventAgent |
| 🟢 | CriticActorAIService.kt | 88.1% (148/168) | 44.7% (68/152) | 85.5% | CriticActorAIService |
| 🟢 | SqlDelightUserPreferenceMemoryRepository.kt | 89.5% (102/114) | 75.0% (54/72) | 86.1% | SqlDelightUserPreferenceMemoryRepository, SqlDelightUserPreferenceMemoryRepository$clearAllLogs$2, SqlDelightUserPreferenceMemoryRepository$getDerivedConstraints$2, SqlDelightUserPreferenceMemoryRepository$logOverride$2, SqlDelightUserPreferenceMemoryRepository$pruneOldLogs$2 |
| 🟢 | AgentHarness.kt | 89.9% (196/218) | 71.1% (54/76) | 90.1% | AgentHarness, AgentHarness$runHarness$2, AgentHarness$runHarness$2$driveDeferreds$1$1, AgentHarness$runHarness$2$localDeferreds$1$1 |
| 🟢 | SqlDelightLocalCalendarRepository.kt | 90.0% (144/160) | 76.2% (64/84) | 86.7% | SqlDelightLocalCalendarRepository |
| 🟢 | CalendarAgent.kt | 90.9% (80/88) | 55.3% (42/76) | 89.7% | CalendarAgent |
| 🟢 | SourceProcessor.kt | 91.7% (22/24) | 83.3% (10/12) | 86.6% | SourceProcessor |
| 🟢 | SourceIngestionHandler.kt | 91.7% (44/48) | 100.0% (4/4) | 95.9% | GoogleDriveQueryBuilder, SourceIngestionHandler, SourceIngestionHandler$ingestDriveFile$1, SourceIngestionHandler$ingestLocalFile$1, SourceIngestionHandler$ingestUrl$1 |
| 🟢 | UniversalHomeLayout.kt | 92.0% (160/174) | 81.3% (26/32) | 93.7% | ComposableSingletons$UniversalHomeLayoutKt, UniversalHomeLayoutKt, UniversalHomeLayoutKt$UniversalHomeLayout$1$3$1$1$2$1$1 |
| 🟢 | Event.kt | 92.6% (150/162) | 36.5% (132/362) | 72.1% | AcademicCategory, CompletionStatus, DayEvent, DayEvent$Companion, Event, Event$DefaultImpls, EventKt, EventSource, Recurrence, Recurrence$Companion, SyncStatus, TimeEvent, TimeEvent$Companion |
| 🟢 | StudioPanel.kt | 93.8% (330/352) | 72.9% (70/96) | 95.0% | ComposableSingletons$StudioPanelKt, StudioPanelKt, StudioPanelKt$StudioPanel$1$1, StudioPanelKt$StudioPanel$2$1, StudioPanelKt$StudioPanel$3$3$1$1$1$1$1, StudioPanelKt$StudioPanel$3$3$1$3$1$1$1, StudioPanelKt$StudioPanel$3$3$1$4$1$1$1, StudioPanelKt$StudioPanel$3$3$1$5$1 |
| 🟢 | CheckInDialog.kt | 94.0% (188/200) | 40.0% (8/20) | 91.0% | CheckInDialogKt, CheckInDialogKt$CheckInDialog$1$1$1$1$1$1$1, CheckInDialogKt$CheckInDialog$1$1$1$1$1$1$1$1$2$1$1, CheckInDialogKt$CheckInDialog$1$1$1$1$1$1$1$1$2$1$1$1, CheckInDialogKt$CheckInDialog$1$1$1$1$1$1$1$1$2$2$1, CheckInDialogKt$CheckInDialog$1$1$1$1$1$1$1$1$2$2$1$1, CheckInDialogKt$CheckInDialog$1$1$1$1$1$1$1$1$2$3$1, CheckInDialogKt$CheckInDialog$1$1$1$1$1$1$1$1$2$3$1$1, ComposableSingletons$CheckInDialogKt |
| 🟢 | AiPrompts.kt | 94.4% (134/142) | 90.0% (36/40) | 92.4% | AiPrompts, SourceContextBlock |
| 🟢 | GoogleRemoteCalendarRepository.kt | 94.4% (68/72) | 85.0% (68/80) | 97.9% | GoogleRemoteCalendarRepository |
| 🟢 | GoogleCalendarSyncService.kt | 94.6% (244/258) | 45.1% (130/288) | 79.5% | GoogleApiException, GoogleCalendarDiscoveryItem, GoogleCalendarDiscoveryItem$Companion, GoogleCalendarEventsResponse, GoogleCalendarEventsResponse$Companion, GoogleCalendarItem, GoogleCalendarItem$Companion, GoogleCalendarListDiscoveryResponse, GoogleCalendarListDiscoveryResponse$Companion, GoogleCalendarSyncService, GoogleCalendarSyncService$createCalendar$2, GoogleCalendarSyncService$deleteEvent$2, GoogleCalendarSyncService$fetchEventsPage$2, GoogleCalendarSyncService$listCalendars$2, GoogleCalendarSyncService$syncEvent$2, GoogleEvent, GoogleEvent$Companion, GoogleEventDateTime, GoogleEventDateTime$Companion |
| 🟢 | BugReporter.kt | 95.3% (82/86) | 50.0% (8/16) | 84.1% | BugReporter, BugReporter$reportError$1, TelemetryStats, TelemetryStats$Companion |
| 🟢 | SqlDelightSourceRepository.kt | 95.9% (94/98) | 100.0% (20/20) | 92.4% | SqlDelightSourceRepository, SqlDelightSourceRepository$getAllSources$2, SqlDelightSourceRepository$getFragmentsForSource$2, SqlDelightSourceRepository$getSourceById$2, SqlDelightSourceRepository$getSourceMetadata$2, SqlDelightSourceRepository$saveSource$2, SqlDelightSourceRepository$updateSourceMetadata$2 |
| 🟢 | SyncNegotiator.kt | 96.2% (100/104) | 63.2% (48/76) | 93.5% | SyncNegotiator |
| 🟢 | CollisionResolver.kt | 96.3% (206/214) | 82.6% (142/172) | 96.3% | CollisionResolver, ResolutionResult$Conflict, ResolutionResult$Success |
| 🟢 | CriticJsonCodec.kt | 96.6% (172/178) | 45.6% (82/180) | 78.4% | CriticJsonCodec, RawCriticEvent, RawCriticEvent$Companion, RawCriticTask, RawCriticTask$Companion |
| 🟢 | ContextAgent.kt | 96.8% (182/188) | 67.5% (54/80) | 95.4% | ContextAgent |
| 🟢 | SyncNegotiationDialog.kt | 97.6% (166/170) | 50.0% (16/32) | 97.0% | ComposableSingletons$SyncNegotiationDialogKt, SyncNegotiationDialogKt, SyncNegotiationDialogKt$SyncNegotiationDialog$1$1$1$1$1$1$1, SyncNegotiationDialogKt$SyncNegotiationDialog$1$1$1$2$1$1$1 |
| 🟢 | GoogleDriveService.kt | 98.4% (122/124) | 63.0% (58/92) | 87.5% | DriveFile, DriveFile$Companion, DriveFileListResponse, DriveFileListResponse$Companion, GoogleDriveService, GoogleDriveService$getFileContent$2, GoogleDriveService$listFiles$2 |
| 🟢 | AddRoutineItemDialog.kt | 98.7% (298/302) | 66.7% (56/84) | 99.1% | AddRoutineItemDialogKt, ComposableSingletons$AddRoutineItemDialogKt |
| 🟢 | EventDisplayPipeline.kt | 100.0% (28/28) | 83.3% (10/12) | 100.0% | EventDisplayPipeline |
| 🟢 | IcsCalendarSource.kt | 100.0% (18/18) | N/A | 100.0% | IcsCalendarSource |
| 🟢 | SourceFragment.kt | 100.0% (24/24) | 34.4% (22/64) | 70.1% | SourceFragment, SourceFragment$Companion, SourceType, SourceType$Companion |
| 🟢 | DecomposedTask.kt | 100.0% (8/8) | N/A | 100.0% | DecomposedTask |
| 🟢 | TelemetryManager.kt | 100.0% (50/50) | 100.0% (8/8) | 100.0% | TelemetryManager |
| 🟢 | GroundingGuardAIService.kt | 100.0% (28/28) | 75.0% (6/8) | 99.3% | GroundingGuardAIService |
| 🟢 | SourceItem.kt | 100.0% (20/20) | N/A | 100.0% | SourceCategory, SourceCategory$Companion, SourceItem |
| 🟢 | StudyPreferences.kt | 100.0% (22/22) | 50.0% (72/144) | 68.2% | StudyPreferences, StudyPreferences$Companion |
| 🟢 | KotlinxSerialization.kt | 100.0% (12/12) | N/A | 100.0% | LocalDateSerializer, LocalTimeSerializer |
| 🟢 | SemesterResolver.kt | 100.0% (18/18) | 83.3% (20/24) | 95.9% | SemesterResolver |
| 🟢 | GoogleAuthService.kt | 100.0% (34/34) | 70.0% (14/20) | 98.2% | GoogleTokenRepository |
| 🟢 | UserPreferenceMemoryRepository.kt | 100.0% (2/2) | N/A | 100.0% | UserPreferenceMemoryRepository |
| 🟢 | ModelManager.kt | 100.0% (76/76) | 77.8% (28/36) | 96.0% | DownloadProgress, ModelManager, ModelManager$downloadModel$2, ModelManager$downloadModel$2$1 |
| 🟢 | UserOverrideLog.kt | 100.0% (10/10) | N/A | 100.0% | OverrideAction, UserPreferenceConstraint |
| 🟢 | PreferencesRepository.kt | 100.0% (22/22) | 100.0% (4/4) | 100.0% | PreferencesRepository, PreferencesRepository$getPreferences$2, PreferencesRepository$savePreferences$2 |
| 🟢 | GoogleAccountFlow.kt | 100.0% (62/62) | 75.0% (12/16) | 98.3% | GoogleAccountFlow |
| 🟢 | EventPresenter.kt | 100.0% (78/78) | 100.0% (76/76) | 100.0% | EventPresenter, EventPresenter$DeadlineStatus |
| 🟢 | AIService.kt | 100.0% (4/4) | N/A | 100.0% | AIService |
| 🟢 | RoutineRepository.kt | 100.0% (18/18) | N/A | 100.0% | RoutineRepository, RoutineRepository$getRoutineEvents$2, RoutineRepository$saveRoutineEvents$2 |
