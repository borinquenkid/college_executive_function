# Codebase CRAP Index Analysis

This document evaluates the codebase using the **CRAP (Change Risk Anti-Patterns) index**.
A higher CRAP index indicates higher risk when changing that file. A score **above 30** is considered highly risky.

## Heuristics Used
- **Complexity**: Approximated by counting control flow branches (`if`, `when`, `for`, `while`, `catch`, logical operators `&&`/`||`, safe calls `?.let`/`?.also`/`?.run`, collection operators `filter`/`map`/`forEach`/`any`/`all`) inside all methods + 1 base complexity per method.
- **Coverage**: Estimated based on the presence of matching test files (found by scanning `commonTest` and `jvmTest` for references to classes inside each file) and the ratio of test code size to source code size (capped at 90%).
- **Formula**: $\text{CRAP} = \text{Complexity}^2 \times (1 - \text{Coverage})^3 + \text{Complexity}$

## Overall Summary
- **Total Files Analyzed**: 72
- **High-Risk Files (CRAP > 30)**: 16

### Top 15 High-Risk Files

| File | Complexity | Est. Coverage | CRAP Index | Test Files | Risk Status |
| :--- | :---: | :---: | :---: | :--- | :---: |
| AcademicCalendar.kt | 46 | 0.0% | 2162.00 | *None* | 🔴 HIGH |
| SettingsScreen.kt | 35 | 0.0% | 1260.00 | *None* | 🔴 HIGH |
| CriticActorAIService.kt | 67 | 42.6% | 917.45 | CriticActorAIServiceTest.kt | 🔴 HIGH |
| CommonSourceProviders.kt | 22 | 0.0% | 506.00 | *None* | 🔴 HIGH |
| StudioPanel.kt | 18 | 0.0% | 342.00 | *None* | 🔴 HIGH |
| AgentHarness.kt | 37 | 43.4% | 285.18 | AgentHarnessTest.kt | 🔴 HIGH |
| IcsStringBuilder.kt | 13 | 0.0% | 182.00 | *None* | 🔴 HIGH |
| AddRoutineItemDialog.kt | 13 | 0.0% | 182.00 | *None* | 🔴 HIGH |
| GeminiAIService.kt | 106 | 90.0% | 117.24 | QuotaExhaustionTest.kt, IngestionAgentTest.kt, BugReporterTest.kt, SyncNegotiationIntegrationTest.kt, EndToEndAcademicWorkflowTest.kt, ConfabulationGuardTest.kt, SyllabusEvaluationSuite.kt, IcsToGoogleIntegrationTest.kt, CalendarSyncIntegrationTest.kt, RetryDelayResolutionTest.kt, OAuthExchangeTest.kt, GoogleCalendarSyncServiceTest.kt, GoogleDriveHeadlessTest.kt, ComposeUiFlowsTest.kt, GeminiModelNegotiationTest.kt, ModelNegotiationIntegrationTest.kt, GoogleDriveServiceTest.kt, ContextAgentMultiSourceTest.kt, LocalFileReaderTest.kt | 🔴 HIGH |
| ModelManager.kt | 9 | 0.0% | 90.00 | *None* | 🔴 HIGH |
| EventAgent.kt | 70 | 90.0% | 74.90 | CollisionResolutionIntegrationTest.kt, SyllabusEvaluationSuite.kt, QuotaExhaustionTest.kt, AiExtractionIntegrationTest.kt, ComposeUiFlowsTest.kt, AiSchedulingIntegrationTest.kt, ModelNegotiationIntegrationTest.kt, EventSyncTest.kt, AgentHarnessTest.kt, MultiFormatAiIntegrationTest.kt, IntegrationTestHelpers.kt, EndToEndAcademicWorkflowTest.kt, EventAgentTest.kt | 🔴 HIGH |
| CalendarAgent.kt | 65 | 90.0% | 69.22 | CollisionResolutionIntegrationTest.kt, AiExtractionIntegrationTest.kt, ComposeUiFlowsTest.kt, CalendarSyncIntegrationTest.kt, AiSchedulingIntegrationTest.kt, EventSyncTest.kt, AgentHarnessTest.kt, SyncNegotiationIntegrationTest.kt, EventAgentTest.kt | 🔴 HIGH |
| App.kt | 14 | 40.0% | 56.34 | ComposeAppCommonTest.kt | 🔴 HIGH |
| AiPrompts.kt | 41 | 90.0% | 42.68 | CollisionResolutionIntegrationTest.kt, EventProgressTest.kt, QuotaExhaustionTest.kt, GoogleCalendarSyncServiceTest.kt, IcsToGoogleIntegrationTest.kt, CalendarSyncIntegrationTest.kt, EventOverlapTest.kt, GeminiModelNegotiationTest.kt, ModelNegotiationIntegrationTest.kt, EventSyncTest.kt, CollisionResolverTest.kt, AgentHarnessTest.kt, RetryDelayResolutionTest.kt, ContextAgentMultiSourceTest.kt, MultiFormatAiIntegrationTest.kt, CriticActorAIServiceTest.kt, SyncNegotiationIntegrationTest.kt, EventAgentTest.kt | 🔴 HIGH |
| CollisionResolver.kt | 41 | 90.0% | 42.68 | IcsToGoogleIntegrationTest.kt, GoogleDriveHeadlessTest.kt, GeminiModelNegotiationTest.kt, EventSyncTest.kt, CollisionResolverTest.kt | 🔴 HIGH |

---

## Detailed File Breakdown

### AcademicCalendar.kt (Score: 2162.00 - 🔴 HIGH)
- **Total Complexity**: 46
- **Estimated Coverage**: 0.0%
- **Matching Test Files**: None

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `EventItemView` | 20 |
| `TaskDecompositionDialog` | 6 |
| `AcademicCalendar` | 1 |

### SettingsScreen.kt (Score: 1260.00 - 🔴 HIGH)
- **Total Complexity**: 35
- **Estimated Coverage**: 0.0%
- **Matching Test Files**: None

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `parseAndSave` | 9 |
| `SettingsScreen` | 1 |

### CriticActorAIService.kt (Score: 917.45 - 🔴 HIGH)
- **Total Complexity**: 67
- **Estimated Coverage**: 42.6%
- **Matching Test Files**: CriticActorAIServiceTest.kt

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `parseEvents` | 15 |
| `parseTasks` | 9 |
| `generateCalendarEvents` | 7 |
| `decomposeTask` | 7 |
| `areEventListsDifferent` | 7 |
| `serializeEvents` | 5 |
| `generateChatResponse` | 4 |
| `areTaskListsDifferent` | 4 |
| `serializeTasks` | 2 |
| `generateStudyPlan` | 1 |

### CommonSourceProviders.kt (Score: 506.00 - 🔴 HIGH)
- **Total Complexity**: 22
- **Estimated Coverage**: 0.0%
- **Matching Test Files**: None

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `SelectorUI` | 5 |
| `SelectorUI` | 4 |
| `SelectorUI` | 4 |
| `isAuthorized` | 1 |
| `isAuthorized` | 1 |
| `isAuthorized` | 1 |
| `DrivePickerDialog` | 1 |

### StudioPanel.kt (Score: 342.00 - 🔴 HIGH)
- **Total Complexity**: 18
- **Estimated Coverage**: 0.0%
- **Matching Test Files**: None

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `StudioPanel` | 1 |

### AgentHarness.kt (Score: 285.18 - 🔴 HIGH)
- **Total Complexity**: 37
- **Estimated Coverage**: 43.4%
- **Matching Test Files**: AgentHarnessTest.kt

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `runHarness` | 23 |
| `processSourceSequentially` | 4 |
| `getWatchedLocalDirectories` | 3 |
| `getWatchedGDriveFolders` | 3 |
| `getLastPollTime` | 1 |
| `setLastPollTime` | 1 |
| `setWatchedLocalDirectories` | 1 |
| `setWatchedGDriveFolders` | 1 |

### IcsStringBuilder.kt (Score: 182.00 - 🔴 HIGH)
- **Total Complexity**: 13
- **Estimated Coverage**: 0.0%
- **Matching Test Files**: None

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `buildIcsString` | 7 |
| `buildRecurrenceRule` | 3 |
| `formatDateTime` | 1 |
| `formatDate` | 1 |
| `plusDays` | 1 |

### AddRoutineItemDialog.kt (Score: 182.00 - 🔴 HIGH)
- **Total Complexity**: 13
- **Estimated Coverage**: 0.0%
- **Matching Test Files**: None

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `AddRoutineItemDialog` | 11 |
| `ClickableField` | 1 |
| `TimePickerDialog` | 1 |

### GeminiAIService.kt (Score: 117.24 - 🔴 HIGH)
- **Total Complexity**: 106
- **Estimated Coverage**: 90.0%
- **Matching Test Files**: QuotaExhaustionTest.kt, IngestionAgentTest.kt, BugReporterTest.kt, SyncNegotiationIntegrationTest.kt, EndToEndAcademicWorkflowTest.kt, ConfabulationGuardTest.kt, SyllabusEvaluationSuite.kt, IcsToGoogleIntegrationTest.kt, CalendarSyncIntegrationTest.kt, RetryDelayResolutionTest.kt, OAuthExchangeTest.kt, GoogleCalendarSyncServiceTest.kt, GoogleDriveHeadlessTest.kt, ComposeUiFlowsTest.kt, GeminiModelNegotiationTest.kt, ModelNegotiationIntegrationTest.kt, GoogleDriveServiceTest.kt, ContextAgentMultiSourceTest.kt, LocalFileReaderTest.kt

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `parseEventsJson` | 17 |
| `decomposeTask` | 9 |
| `getAvailableModels` | 5 |
| `categorizeSource` | 5 |
| `filterToSourceYears` | 4 |
| `postToModel` | 3 |
| `generateCalendarEvents` | 3 |
| `extractSourceYears` | 2 |
| `generateCalendarEventsFromPrompt` | 2 |
| `generateChatResponse` | 2 |
| *... and 7 more* | |

### ModelManager.kt (Score: 90.00 - 🔴 HIGH)
- **Total Complexity**: 9
- **Estimated Coverage**: 0.0%
- **Matching Test Files**: None

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `downloadModel` | 6 |
| `isModelDownloaded` | 2 |
| `getModelFile` | 1 |

### EventAgent.kt (Score: 74.90 - 🔴 HIGH)
- **Total Complexity**: 70
- **Estimated Coverage**: 90.0%
- **Matching Test Files**: CollisionResolutionIntegrationTest.kt, SyllabusEvaluationSuite.kt, QuotaExhaustionTest.kt, AiExtractionIntegrationTest.kt, ComposeUiFlowsTest.kt, AiSchedulingIntegrationTest.kt, ModelNegotiationIntegrationTest.kt, EventSyncTest.kt, AgentHarnessTest.kt, MultiFormatAiIntegrationTest.kt, IntegrationTestHelpers.kt, EndToEndAcademicWorkflowTest.kt, EventAgentTest.kt

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `pushToCalendar` | 13 |
| `extractDeliverables` | 9 |
| `generateStudyPlan` | 9 |
| `auditSyllabus` | 8 |
| `acceptDecomposition` | 7 |
| `rescheduleEvent` | 7 |
| `markEventCompleted` | 4 |
| `decomposeTask` | 3 |
| `skipEvent` | 3 |
| `loadIncompleteEvents` | 2 |
| *... and 5 more* | |

### CalendarAgent.kt (Score: 69.22 - 🔴 HIGH)
- **Total Complexity**: 65
- **Estimated Coverage**: 90.0%
- **Matching Test Files**: CollisionResolutionIntegrationTest.kt, AiExtractionIntegrationTest.kt, ComposeUiFlowsTest.kt, CalendarSyncIntegrationTest.kt, AiSchedulingIntegrationTest.kt, EventSyncTest.kt, AgentHarnessTest.kt, SyncNegotiationIntegrationTest.kt, EventAgentTest.kt

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `checkSyncProposals` | 25 |
| `applySyncNegotiation` | 14 |
| `pushLocalChanges` | 7 |
| `updateEvent` | 6 |
| `saveEvent` | 4 |
| `deleteEvent` | 3 |
| `saveEventLocally` | 2 |
| `synchronize` | 2 |
| `getEvents` | 1 |
| `getIncompleteEventsBefore` | 1 |

### App.kt (Score: 56.34 - 🔴 HIGH)
- **Total Complexity**: 14
- **Estimated Coverage**: 40.0%
- **Matching Test Files**: ComposeAppCommonTest.kt

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `App` | 7 |
| `UniversalHomeLayout` | 7 |

### AiPrompts.kt (Score: 42.68 - 🔴 HIGH)
- **Total Complexity**: 41
- **Estimated Coverage**: 90.0%
- **Matching Test Files**: CollisionResolutionIntegrationTest.kt, EventProgressTest.kt, QuotaExhaustionTest.kt, GoogleCalendarSyncServiceTest.kt, IcsToGoogleIntegrationTest.kt, CalendarSyncIntegrationTest.kt, EventOverlapTest.kt, GeminiModelNegotiationTest.kt, ModelNegotiationIntegrationTest.kt, EventSyncTest.kt, CollisionResolverTest.kt, AgentHarnessTest.kt, RetryDelayResolutionTest.kt, ContextAgentMultiSourceTest.kt, MultiFormatAiIntegrationTest.kt, CriticActorAIServiceTest.kt, SyncNegotiationIntegrationTest.kt, EventAgentTest.kt

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `getSourceEventExtractionPrompt` | 4 |
| `getSyllabusAuditPrompt` | 4 |
| `formatHour` | 3 |
| `getTaskDecompositionPrompt` | 2 |
| `getDocumentIntelligencePrompt` | 2 |
| `getSourceCategorizationPrompt` | 2 |
| `getChatCritiquePrompt` | 2 |
| `getSyllabusStudyPlanPrompt` | 1 |
| `getMultiSourceChatPrompt` | 1 |
| `getEventCritiquePrompt` | 1 |
| *... and 1 more* | |

### CollisionResolver.kt (Score: 42.68 - 🔴 HIGH)
- **Total Complexity**: 41
- **Estimated Coverage**: 90.0%
- **Matching Test Files**: IcsToGoogleIntegrationTest.kt, GoogleDriveHeadlessTest.kt, GeminiModelNegotiationTest.kt, EventSyncTest.kt, CollisionResolverTest.kt

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `resolve` | 1 |
| `findNewSlotAndResolve` | 1 |
| `shiftEvent` | 1 |
| `findNextAvailableSlot` | 1 |
| `findNextDaySlot` | 1 |
| `isDayAvailable` | 1 |
| `findNextTimeSlot` | 1 |
| `findTimeSlotOnDay` | 1 |
| `isValidTimeSlot` | 1 |

### ContextAgent.kt (Score: 31.96 - 🔴 HIGH)
- **Total Complexity**: 31
- **Estimated Coverage**: 90.0%
- **Matching Test Files**: ComposeUiFlowsTest.kt, AgentHarnessTest.kt, ContextAgentMultiSourceTest.kt, EndToEndAcademicWorkflowTest.kt

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `analyzeSource` | 4 |
| `querySource` | 3 |
| `getSourceMetadata` | 1 |
| `rankFragments` | 1 |
| `queryAllSources` | 1 |

### SourceItemView.kt (Score: 30.00 - 🟡 MEDIUM)
- **Total Complexity**: 5
- **Estimated Coverage**: 0.0%
- **Matching Test Files**: None

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `SourceItemView` | 5 |

### ErrorBanner.kt (Score: 30.00 - 🟡 MEDIUM)
- **Total Complexity**: 5
- **Estimated Coverage**: 0.0%
- **Matching Test Files**: None

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `QuotaErrorBanner` | 1 |
| `AnimatedErrorBanner` | 1 |

### GoogleCalendarSyncService.kt (Score: 29.84 - 🟡 MEDIUM)
- **Total Complexity**: 29
- **Estimated Coverage**: 90.0%
- **Matching Test Files**: CollisionResolutionIntegrationTest.kt, IcsToGoogleIntegrationTest.kt, GoogleCalendarSyncServiceTest.kt, CalendarSyncIntegrationTest.kt, SyncNegotiationIntegrationTest.kt

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `getEvents` | 10 |
| `<T> withToken` | 6 |
| `listCalendars` | 3 |
| `ensureSuccess` | 2 |
| `syncEvent` | 2 |
| `fetchEventsPage` | 2 |
| `createCalendar` | 1 |
| `deleteEvent` | 1 |

### GoogleRemoteCalendarRepository.kt (Score: 29.84 - 🟡 MEDIUM)
- **Total Complexity**: 29
- **Estimated Coverage**: 90.0%
- **Matching Test Files**: CollisionResolutionIntegrationTest.kt, CalendarSyncIntegrationTest.kt, SyncNegotiationIntegrationTest.kt, IcsToGoogleIntegrationTest.kt

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `clearCalendar` | 6 |
| `deleteEvent` | 4 |
| `saveEvent` | 3 |
| `getEventsInRange` | 3 |
| `getAllEvents` | 2 |
| `getCEFCalendarId` | 2 |
| `updateEvent` | 2 |
| `getEventsBySyncStatus` | 2 |
| `getIncompleteEventsBefore` | 2 |
| `getSettings` | 1 |
| *... and 2 more* | |

### SqlDelightLocalCalendarRepository.kt (Score: 24.58 - 🟡 MEDIUM)
- **Total Complexity**: 24
- **Estimated Coverage**: 90.0%
- **Matching Test Files**: CollisionResolutionIntegrationTest.kt, CalendarSyncIntegrationTest.kt, SyncNegotiationIntegrationTest.kt

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `updateEvent` | 6 |
| `mapEntityToEvent` | 4 |
| `getAllEvents` | 3 |
| `saveEvent` | 2 |
| `getEventsInRange` | 2 |
| `getEventsBySyncStatus` | 2 |
| `getIncompleteEventsBefore` | 2 |
| `getSettings` | 1 |
| `deleteEvent` | 1 |
| `hardDeleteEvent` | 1 |

### SqlDelightUserPreferenceMemoryRepository.kt (Score: 20.40 - 🟡 MEDIUM)
- **Total Complexity**: 20
- **Estimated Coverage**: 90.0%
- **Matching Test Files**: UserPreferenceMemoryRepositoryTest.kt

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `getDerivedConstraints` | 12 |
| `logOverride` | 6 |
| `pruneOldLogs` | 1 |
| `clearAllLogs` | 1 |

### SyncNegotiationDialog.kt (Score: 20.00 - 🟡 MEDIUM)
- **Total Complexity**: 4
- **Estimated Coverage**: 0.0%
- **Matching Test Files**: None

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `SyncNegotiationDialog` | 1 |

### RoutineScreen.kt (Score: 20.00 - 🟡 MEDIUM)
- **Total Complexity**: 4
- **Estimated Coverage**: 0.0%
- **Matching Test Files**: None

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `RoutineScreen` | 2 |
| `RoutineEventView` | 2 |

### Event.kt (Score: 18.32 - 🟡 MEDIUM)
- **Total Complexity**: 18
- **Estimated Coverage**: 90.0%
- **Matching Test Files**: CollisionResolutionIntegrationTest.kt, UserPreferenceMemoryRepositoryTest.kt, ICalGeneratorTest.kt, QuotaExhaustionTest.kt, IntegrationTestHelpers.kt, EndToEndAcademicWorkflowTest.kt, ConfabulationGuardTest.kt, SyncNegotiationIntegrationTest.kt, SyllabusEvaluationSuite.kt, EventProgressTest.kt, RoutineRepositoryTest.kt, AiExtractionIntegrationTest.kt, IcsToGoogleIntegrationTest.kt, CalendarSyncIntegrationTest.kt, EventOverlapTest.kt, EventGeneratorTest.kt, GoogleCalendarSyncServiceTest.kt, ComposeUiFlowsTest.kt, GeminiModelNegotiationTest.kt, AiSchedulingIntegrationTest.kt, ModelNegotiationIntegrationTest.kt, AgentHarnessTest.kt, EventAgentTest.kt, EventSyncTest.kt, CollisionResolverTest.kt, MultiFormatAiIntegrationTest.kt, CriticActorAIServiceTest.kt

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `overlaps` | 4 |
| `overlaps` | 2 |
| `overlaps` | 1 |
| `Event.timeUntilDue` | 1 |
| `Event.studyProgress` | 1 |

### GoogleDriveService.kt (Score: 16.26 - 🟡 MEDIUM)
- **Total Complexity**: 16
- **Estimated Coverage**: 90.0%
- **Matching Test Files**: GoogleDriveHeadlessTest.kt, IngestionAgentTest.kt, GoogleDriveServiceTest.kt, AgentHarnessTest.kt, GoogleConnectionFsmTest.kt

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `<T> withToken` | 8 |
| `listFiles` | 3 |
| `getFileContent` | 3 |
| `validateConnection` | 2 |

### CalendarInterfaces.kt (Score: 13.17 - 🟢 LOW)
- **Total Complexity**: 13
- **Estimated Coverage**: 90.0%
- **Matching Test Files**: CollisionResolutionIntegrationTest.kt, UserPreferenceMemoryRepositoryTest.kt, ICalGeneratorTest.kt, QuotaExhaustionTest.kt, IngestionAgentTest.kt, GoogleAuthServiceTest.kt, IntegrationTestHelpers.kt, PdfReaderTest.kt, GoogleConnectionFsmTest.kt, EndToEndAcademicWorkflowTest.kt, ConfabulationGuardTest.kt, BugReporterTest.kt, SyncNegotiationIntegrationTest.kt, SyllabusEvaluationSuite.kt, EventProgressTest.kt, AiExtractionIntegrationTest.kt, IcsToGoogleIntegrationTest.kt, SourceRepositoryTest.kt, CalendarSyncIntegrationTest.kt, EventGeneratorTest.kt, RetryDelayResolutionTest.kt, GoogleDriveHeadlessTest.kt, ComposeUiFlowsTest.kt, GeminiModelNegotiationTest.kt, AiSchedulingIntegrationTest.kt, ModelNegotiationIntegrationTest.kt, GoogleDriveServiceTest.kt, AgentHarnessTest.kt, ContextAgentMultiSourceTest.kt, EventAgentTest.kt, EventSyncTest.kt, CollisionResolverTest.kt, MultiFormatAiIntegrationTest.kt, CriticActorAIServiceTest.kt, DecompositionOrchestratorTest.kt

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `getEvents` | 1 |
| `extract` | 1 |
| `getSettings` | 1 |
| `getAllEvents` | 1 |
| `saveEvent` | 1 |
| `updateEvent` | 1 |
| `deleteEvent` | 1 |
| `hardDeleteEvent` | 1 |
| `getEventsInRange` | 1 |
| `getEventsBySyncStatus` | 1 |
| *... and 3 more* | |

### EventGenerator.kt (Score: 12.14 - 🟢 LOW)
- **Total Complexity**: 12
- **Estimated Coverage**: 90.0%
- **Matching Test Files**: EventGeneratorTest.kt

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `expandTimeEvent` | 5 |
| `expandDayEvent` | 5 |
| `expandEvents` | 2 |

### DecompositionOrchestrator.kt (Score: 12.14 - 🟢 LOW)
- **Total Complexity**: 12
- **Estimated Coverage**: 90.0%
- **Matching Test Files**: ComposeUiFlowsTest.kt, GeminiModelNegotiationTest.kt, CriticActorAIServiceTest.kt, DecompositionOrchestratorTest.kt, EventAgentTest.kt

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `decompose` | 7 |
| `isComplex` | 3 |
| `calculateSubDueDate` | 1 |
| `calculateDaysBeforeDue` | 1 |

### ChatPanel.kt (Score: 11.18 - 🟢 LOW)
- **Total Complexity**: 11
- **Estimated Coverage**: 88.6%
- **Matching Test Files**: ContextAgentMultiSourceTest.kt

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `MessageView` | 4 |
| `ChatPanel` | 1 |

### TelemetryManager.kt (Score: 11.12 - 🟢 LOW)
- **Total Complexity**: 11
- **Estimated Coverage**: 90.0%
- **Matching Test Files**: BugReporterTest.kt, TelemetryManagerTest.kt

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `logCriticPass` | 2 |
| `getCriticTriggerRate` | 2 |
| `logJsonError` | 1 |
| `logRateLimitError` | 1 |
| `getJsonErrors` | 1 |
| `getRateLimitErrors` | 1 |
| `getCriticTotal` | 1 |
| `getCriticModified` | 1 |
| `clear` | 1 |

### OAuthExchange.kt (Score: 11.06 - 🟢 LOW)
- **Total Complexity**: 6
- **Estimated Coverage**: 48.0%
- **Matching Test Files**: OAuthExchangeTest.kt

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `performTokenExchange` | 2 |
| `exchangeCodeForTokens` | 1 |
| `refreshAccessToken` | 1 |

### IngestionAgent.kt (Score: 10.10 - 🟢 LOW)
- **Total Complexity**: 10
- **Estimated Coverage**: 90.0%
- **Matching Test Files**: IngestionAgentTest.kt, AgentHarnessTest.kt, EndToEndAcademicWorkflowTest.kt

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `addLocalFile` | 3 |
| `addUrl` | 3 |
| `addDriveFile` | 3 |
| `persistSource` | 1 |

### SqlDelightSourceRepository.kt (Score: 9.08 - 🟢 LOW)
- **Total Complexity**: 9
- **Estimated Coverage**: 90.0%
- **Matching Test Files**: IngestionAgentTest.kt, SourceRepositoryTest.kt, ContextAgentMultiSourceTest.kt, EndToEndAcademicWorkflowTest.kt

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `saveSource` | 3 |
| `updateSourceMetadata` | 2 |
| `getSourceMetadata` | 1 |
| `getAllSources` | 1 |
| `getSourceById` | 1 |
| `getFragmentsForSource` | 1 |

### AppController.kt (Score: 9.08 - 🟢 LOW)
- **Total Complexity**: 9
- **Estimated Coverage**: 90.0%
- **Matching Test Files**: CollisionResolutionIntegrationTest.kt, ApiKeyStorageTest.kt, ICalGeneratorTest.kt, QuotaExhaustionTest.kt, IngestionAgentTest.kt, GoogleAuthServiceTest.kt, IntegrationTestHelpers.kt, GoogleConnectionFsmTest.kt, EndToEndAcademicWorkflowTest.kt, SyncNegotiationIntegrationTest.kt, SyllabusEvaluationSuite.kt, RoutineRepositoryTest.kt, AiExtractionIntegrationTest.kt, IcsToGoogleIntegrationTest.kt, CalendarSyncIntegrationTest.kt, SourcesPanelTest.kt, GoogleTokenRepositoryTest.kt, GoogleCalendarSyncServiceTest.kt, GoogleDriveHeadlessTest.kt, ComposeUiFlowsTest.kt, GeminiModelNegotiationTest.kt, AiSchedulingIntegrationTest.kt, ModelNegotiationIntegrationTest.kt, AgentHarnessTest.kt, EventAgentTest.kt, TelemetryManagerTest.kt, EventSyncTest.kt, MultiFormatAiIntegrationTest.kt, CriticActorAIServiceTest.kt

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `addSource` | 2 |
| `navigateTo` | 1 |
| `addEvents` | 1 |
| `clearEvents` | 1 |
| `selectSource` | 1 |
| `addChatMessage` | 1 |
| `setScreenListener` | 1 |
| `setEventsListener` | 1 |

### AIService.kt (Score: 9.08 - 🟢 LOW)
- **Total Complexity**: 9
- **Estimated Coverage**: 90.0%
- **Matching Test Files**: SyllabusEvaluationSuite.kt, EventAgentTest.kt, QuotaExhaustionTest.kt, AiExtractionIntegrationTest.kt, IcsToGoogleIntegrationTest.kt, IngestionAgentTest.kt, EndToEndAcademicWorkflowTest.kt, GeminiModelNegotiationTest.kt, AiSchedulingIntegrationTest.kt, ModelNegotiationIntegrationTest.kt, EventSyncTest.kt, RetryDelayResolutionTest.kt, ContextAgentMultiSourceTest.kt, MultiFormatAiIntegrationTest.kt, CriticActorAIServiceTest.kt, DecompositionOrchestratorTest.kt, ConfabulationGuardTest.kt

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `isConfigured` | 1 |
| `generateCalendarEvents` | 1 |
| `analyzeDocument` | 1 |
| `categorizeSource` | 1 |
| `isConfigured` | 1 |
| `generateCalendarEvents` | 1 |
| `analyzeDocument` | 1 |
| `categorizeSource` | 1 |
| `rememberAIService` | 1 |

### Logger.kt (Score: 9.08 - 🟢 LOW)
- **Total Complexity**: 9
- **Estimated Coverage**: 90.0%
- **Matching Test Files**: CollisionResolutionIntegrationTest.kt, SyllabusEvaluationSuite.kt, CriticActorAIServiceTest.kt, AiExtractionIntegrationTest.kt, GoogleDriveHeadlessTest.kt, IcsToGoogleIntegrationTest.kt, GeminiModelNegotiationTest.kt, AiSchedulingIntegrationTest.kt, EventSyncTest.kt, AgentHarnessTest.kt, MultiFormatAiIntegrationTest.kt, BugReporterTest.kt, EndToEndAcademicWorkflowTest.kt, EventAgentTest.kt

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `d` | 2 |
| `appendToFile` | 2 |
| `isDebugEnabled` | 1 |
| `e` | 1 |
| `i` | 1 |
| `writeLogToFile` | 1 |
| `rememberLogger` | 1 |

### BugReporter.kt (Score: 8.51 - 🟢 LOW)
- **Total Complexity**: 6
- **Estimated Coverage**: 58.8%
- **Matching Test Files**: BugReporterTest.kt

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `reportError` | 6 |

### GoogleAuthService.kt (Score: 8.06 - 🟢 LOW)
- **Total Complexity**: 8
- **Estimated Coverage**: 90.0%
- **Matching Test Files**: CollisionResolutionIntegrationTest.kt, GoogleCalendarSyncServiceTest.kt, IcsToGoogleIntegrationTest.kt, CalendarSyncIntegrationTest.kt, GoogleAuthServiceTest.kt, GoogleDriveServiceTest.kt, GoogleTokenRepositoryTest.kt, AgentHarnessTest.kt, GoogleConnectionFsmTest.kt, SyncNegotiationIntegrationTest.kt

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `saveTokens` | 2 |
| `clearTokens` | 1 |
| `hasTokens` | 1 |
| `getAccessToken` | 1 |
| `getRefreshToken` | 1 |
| `login` | 1 |
| `logout` | 1 |

### GoogleAccountFlow.kt (Score: 8.06 - 🟢 LOW)
- **Total Complexity**: 8
- **Estimated Coverage**: 90.0%
- **Matching Test Files**: ComposeUiFlowsTest.kt, GoogleConnectionFsmTest.kt

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `connect` | 5 |
| `disconnect` | 1 |
| `reportAuthError` | 1 |

### SourceInterfaces.kt (Score: 6.00 - 🟢 LOW)
- **Total Complexity**: 2
- **Estimated Coverage**: 0.0%
- **Matching Test Files**: None

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `isAuthorized` | 1 |
| `SelectorUI` | 1 |

### IcsExport.kt (Score: 6.00 - 🟢 LOW)
- **Total Complexity**: 2
- **Estimated Coverage**: 0.0%
- **Matching Test Files**: None

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `generateIcsString` | 1 |
| `writeIcsFile` | 1 |

### Platform.kt (Score: 6.00 - 🟢 LOW)
- **Total Complexity**: 2
- **Estimated Coverage**: 0.0%
- **Matching Test Files**: None

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `rememberModelDirectoryPath` | 1 |
| `rememberDriverFactory` | 1 |

### NormalizationService.kt (Score: 5.03 - 🟢 LOW)
- **Total Complexity**: 5
- **Estimated Coverage**: 90.0%
- **Matching Test Files**: CollisionResolutionIntegrationTest.kt, EventSyncTest.kt, EndToEndAcademicWorkflowTest.kt, EventAgentTest.kt

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `extract` | 5 |

### SourceProcessor.kt (Score: 5.03 - 🟢 LOW)
- **Total Complexity**: 5
- **Estimated Coverage**: 90.0%
- **Matching Test Files**: SyllabusEvaluationSuite.kt, AiExtractionIntegrationTest.kt, SourceProcessorTest.kt, SourcesPanelTest.kt, MultiFormatAiIntegrationTest.kt, EventAgentTest.kt

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `split` | 3 |
| `process` | 2 |

### SourcesPanel.kt (Score: 4.94 - 🟢 LOW)
- **Total Complexity**: 3
- **Estimated Coverage**: 40.0%
- **Matching Test Files**: SourcesPanelTest.kt

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `SourcesPanel` | 1 |

### KotlinxSerialization.kt (Score: 4.89 - 🟢 LOW)
- **Total Complexity**: 4
- **Estimated Coverage**: 61.8%
- **Matching Test Files**: KotlinxSerializationTest.kt

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `serialize` | 1 |
| `deserialize` | 1 |
| `serialize` | 1 |
| `deserialize` | 1 |

### SyncProposal.kt (Score: 4.02 - 🟢 LOW)
- **Total Complexity**: 4
- **Estimated Coverage**: 90.0%
- **Matching Test Files**: ComposeUiFlowsTest.kt, SyncNegotiationIntegrationTest.kt


### WebSourceReader.kt (Score: 4.02 - 🟢 LOW)
- **Total Complexity**: 4
- **Estimated Coverage**: 90.0%
- **Matching Test Files**: IngestionAgentTest.kt, MultiFormatAiIntegrationTest.kt, WebSourceReaderTest.kt

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `readTextFromUrl` | 2 |
| `cleanHtml` | 2 |

### PreferencesRepository.kt (Score: 4.02 - 🟢 LOW)
- **Total Complexity**: 4
- **Estimated Coverage**: 90.0%
- **Matching Test Files**: ComposeUiFlowsTest.kt, BugReporterTest.kt, SyncNegotiationIntegrationTest.kt

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `getPreferences` | 3 |
| `savePreferences` | 1 |

### SourceFragment.kt (Score: 3.01 - 🟢 LOW)
- **Total Complexity**: 3
- **Estimated Coverage**: 90.0%
- **Matching Test Files**: DocxReaderTest.kt, SourceProcessorTest.kt, SourceRepositoryTest.kt, GeminiModelNegotiationTest.kt, AiSchedulingIntegrationTest.kt, ModelNegotiationIntegrationTest.kt, AgentHarnessTest.kt, ContextAgentMultiSourceTest.kt, PdfReaderTest.kt, CriticActorAIServiceTest.kt, EndToEndAcademicWorkflowTest.kt, EventAgentTest.kt

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `toJson` | 1 |

### SourceRepository.kt (Score: 3.01 - 🟢 LOW)
- **Total Complexity**: 3
- **Estimated Coverage**: 90.0%
- **Matching Test Files**: IngestionAgentTest.kt, SourceRepositoryTest.kt, AgentHarnessTest.kt, ContextAgentMultiSourceTest.kt, EndToEndAcademicWorkflowTest.kt

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `saveSource` | 1 |
| `getSourceMetadata` | 1 |
| `getSourceById` | 1 |

### RoutineRepository.kt (Score: 3.01 - 🟢 LOW)
- **Total Complexity**: 3
- **Estimated Coverage**: 90.0%
- **Matching Test Files**: RoutineRepositoryTest.kt

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `getRoutineEvents` | 2 |
| `saveRoutineEvents` | 1 |

### DocxReader.kt (Score: 2.00 - 🟢 LOW)
- **Total Complexity**: 2
- **Estimated Coverage**: 90.0%
- **Matching Test Files**: IngestionAgentTest.kt, DocxReaderTest.kt, MultiFormatAiIntegrationTest.kt

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `readSource` | 1 |
| `rememberDocxReader` | 1 |

### UserPreferenceMemoryRepository.kt (Score: 2.00 - 🟢 LOW)
- **Total Complexity**: 2
- **Estimated Coverage**: 90.0%
- **Matching Test Files**: UserPreferenceMemoryRepositoryTest.kt

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `logOverride` | 1 |
| `getDerivedConstraints` | 1 |

### PdfReader.kt (Score: 2.00 - 🟢 LOW)
- **Total Complexity**: 2
- **Estimated Coverage**: 90.0%
- **Matching Test Files**: IngestionAgentTest.kt, MultiFormatAiIntegrationTest.kt, SyllabusEvaluationSuite.kt, PdfReaderTest.kt

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `readSource` | 1 |
| `rememberPdfReader` | 1 |

### LocalFileReader.kt (Score: 2.00 - 🟢 LOW)
- **Total Complexity**: 2
- **Estimated Coverage**: 90.0%
- **Matching Test Files**: IngestionAgentTest.kt, LocalFileReaderTest.kt, AgentHarnessTest.kt

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `readText` | 1 |
| `rememberLocalFileReader` | 1 |

### PlatformFileSystem.kt (Score: 2.00 - 🟢 LOW)
- **Total Complexity**: 1
- **Estimated Coverage**: 0.0%
- **Matching Test Files**: None

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `getFileSystem` | 1 |

### RecursiveDecompositionAIService.kt (Score: 2.00 - 🟢 LOW)
- **Total Complexity**: 1
- **Estimated Coverage**: 0.0%
- **Matching Test Files**: None

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `decomposeTask` | 1 |

### CheckInDialog.kt (Score: 2.00 - 🟢 LOW)
- **Total Complexity**: 1
- **Estimated Coverage**: 0.0%
- **Matching Test Files**: None

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `CheckInDialog` | 1 |

### RoutineItem.kt (Score: 2.00 - 🟢 LOW)
- **Total Complexity**: 1
- **Estimated Coverage**: 0.0%
- **Matching Test Files**: None


### RoutineSetupScreen.kt (Score: 2.00 - 🟢 LOW)
- **Total Complexity**: 1
- **Estimated Coverage**: 0.0%
- **Matching Test Files**: None

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `RoutineSetupScreen` | 1 |

### SettingsFactory.kt (Score: 2.00 - 🟢 LOW)
- **Total Complexity**: 1
- **Estimated Coverage**: 0.0%
- **Matching Test Files**: None

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `rememberSettings` | 1 |

### PlatformUtils.kt (Score: 2.00 - 🟢 LOW)
- **Total Complexity**: 1
- **Estimated Coverage**: 0.0%
- **Matching Test Files**: None

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `openBrowser` | 1 |

### FilePicker.kt (Score: 2.00 - 🟢 LOW)
- **Total Complexity**: 1
- **Estimated Coverage**: 0.0%
- **Matching Test Files**: None

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `FilePicker` | 1 |

### IcsCalendarSource.kt (Score: 1.00 - 🟢 LOW)
- **Total Complexity**: 1
- **Estimated Coverage**: 90.0%
- **Matching Test Files**: IcsToGoogleIntegrationTest.kt

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `readSource` | 1 |

### DependencyContainer.kt (Score: 1.00 - 🟢 LOW)
- **Total Complexity**: 1
- **Estimated Coverage**: 90.0%
- **Matching Test Files**: GoogleDriveHeadlessTest.kt, ComposeUiFlowsTest.kt, EndToEndAcademicWorkflowTest.kt, EventAgentTest.kt


### DecomposedTask.kt (Score: 1.00 - 🟢 LOW)
- **Total Complexity**: 1
- **Estimated Coverage**: 90.0%
- **Matching Test Files**: ComposeUiFlowsTest.kt, CriticActorAIServiceTest.kt, DecompositionOrchestratorTest.kt, EventAgentTest.kt


### SourceItem.kt (Score: 1.00 - 🟢 LOW)
- **Total Complexity**: 1
- **Estimated Coverage**: 90.0%
- **Matching Test Files**: AiExtractionIntegrationTest.kt, IngestionAgentTest.kt, SourceRepositoryTest.kt, SourcesPanelTest.kt, AiSchedulingIntegrationTest.kt, GeminiModelNegotiationTest.kt, AgentHarnessTest.kt, ContextAgentMultiSourceTest.kt, CriticActorAIServiceTest.kt, EndToEndAcademicWorkflowTest.kt, EventAgentTest.kt


### StudyPreferences.kt (Score: 1.00 - 🟢 LOW)
- **Total Complexity**: 1
- **Estimated Coverage**: 90.0%
- **Matching Test Files**: CollisionResolverTest.kt, BugReporterTest.kt, SyncNegotiationIntegrationTest.kt


### UserOverrideLog.kt (Score: 1.00 - 🟢 LOW)
- **Total Complexity**: 1
- **Estimated Coverage**: 90.0%
- **Matching Test Files**: UserPreferenceMemoryRepositoryTest.kt, CollisionResolverTest.kt


### GoogleConnectionState.kt (Score: 1.00 - 🟢 LOW)
- **Total Complexity**: 1
- **Estimated Coverage**: 90.0%
- **Matching Test Files**: QuotaExhaustionTest.kt, AiExtractionIntegrationTest.kt, IcsToGoogleIntegrationTest.kt, ComposeUiFlowsTest.kt, BugReporterTest.kt, TelemetryManagerTest.kt, GoogleConnectionFsmTest.kt, EventAgentTest.kt


