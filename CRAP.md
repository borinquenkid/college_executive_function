# Code Base CRAP Index Analysis

This document evaluates the codebase using the **CRAP (Change Risk Anti-Patterns) index**.
A higher CRAP index indicates higher risk when changing that file. A score **above 30** is considered highly risky.

## Heuristics Used
- **Complexity**: Approximated by counting control flow branches (`if`, `when`, `for`, `while`, `catch`, logical operators `&&`/`||`, safe calls `?.let`/`?.also`/`?.run`, collection operators `filter`/`map`/`forEach`/`any`/`all`) inside all methods + 1 base complexity per method.
- **Coverage**: Calculated exactly from Kover's XML test coverage report.
- **Formula**: $\text{CRAP} = \text{Complexity}^2 \times (1 - \text{Coverage})^3 + \text{Complexity}$

## Overall Summary
- **Total Files Analyzed**: 76
- **High-Risk Files (CRAP > 30)**: 17

### Top 15 High-Risk Files

| File | Complexity | Real Coverage | CRAP Index | Risk Status |
| :--- | :---: | :---: | :---: | :---: |
| CalendarAgent.kt | 65 | 66.1% | 230.02 | 🔴 HIGH |
| CommonSourceProviders.kt | 18 | 13.3% | 228.91 | 🔴 HIGH |
| GoogleRemoteCalendarRepository.kt | 29 | 38.9% | 220.94 | 🔴 HIGH |
| App.kt | 14 | 0.0% | 210.00 | 🔴 HIGH |
| AddRoutineItemDialog.kt | 13 | 0.0% | 182.00 | 🔴 HIGH |
| GeminiAIService.kt | 106 | 83.8% | 153.53 | 🔴 HIGH |
| CriticActorAIService.kt | 67 | 78.7% | 110.11 | 🔴 HIGH |
| EventAgent.kt | 70 | 86.0% | 83.57 | 🔴 HIGH |
| SettingsScreen.kt | 35 | 73.8% | 57.03 | 🔴 HIGH |
| AiPrompts.kt | 41 | 91.4% | 42.06 | 🔴 HIGH |
| CollisionResolver.kt | 41 | 94.4% | 41.30 | 🔴 HIGH |
| ModelManager.kt | 9 | 26.5% | 41.20 | 🔴 HIGH |
| EventPresenter.kt | 18 | 59.0% | 40.37 | 🔴 HIGH |
| GoogleDriveService.kt | 16 | 54.8% | 39.58 | 🔴 HIGH |
| AgentHarness.kt | 37 | 89.9% | 38.41 | 🔴 HIGH |

---

## Detailed File Breakdown

### CalendarAgent.kt (Score: 230.02 - 🔴 HIGH)
- **Total Complexity**: 65
- **Real Coverage**: 66.1%

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

### CommonSourceProviders.kt (Score: 228.91 - 🔴 HIGH)
- **Total Complexity**: 18
- **Real Coverage**: 13.3%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `SelectorUI` | 4 |
| `SelectorUI` | 3 |
| `SelectorUI` | 2 |
| `isAuthorized` | 1 |
| `isAuthorized` | 1 |
| `isAuthorized` | 1 |
| `DrivePickerDialog` | 1 |

### GoogleRemoteCalendarRepository.kt (Score: 220.94 - 🔴 HIGH)
- **Total Complexity**: 29
- **Real Coverage**: 38.9%

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

### App.kt (Score: 210.00 - 🔴 HIGH)
- **Total Complexity**: 14
- **Real Coverage**: 0.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `App` | 7 |
| `UniversalHomeLayout` | 7 |

### AddRoutineItemDialog.kt (Score: 182.00 - 🔴 HIGH)
- **Total Complexity**: 13
- **Real Coverage**: 0.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `AddRoutineItemDialog` | 11 |
| `ClickableField` | 1 |
| `TimePickerDialog` | 1 |

### GeminiAIService.kt (Score: 153.53 - 🔴 HIGH)
- **Total Complexity**: 106
- **Real Coverage**: 83.8%

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

### CriticActorAIService.kt (Score: 110.11 - 🔴 HIGH)
- **Total Complexity**: 67
- **Real Coverage**: 78.7%

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

### EventAgent.kt (Score: 83.57 - 🔴 HIGH)
- **Total Complexity**: 70
- **Real Coverage**: 86.0%

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

### SettingsScreen.kt (Score: 57.03 - 🔴 HIGH)
- **Total Complexity**: 35
- **Real Coverage**: 73.8%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `parseAndSave` | 9 |
| `SettingsScreen` | 1 |

### AiPrompts.kt (Score: 42.06 - 🔴 HIGH)
- **Total Complexity**: 41
- **Real Coverage**: 91.4%

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

### CollisionResolver.kt (Score: 41.30 - 🔴 HIGH)
- **Total Complexity**: 41
- **Real Coverage**: 94.4%

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

### ModelManager.kt (Score: 41.20 - 🔴 HIGH)
- **Total Complexity**: 9
- **Real Coverage**: 26.5%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `downloadModel` | 6 |
| `isModelDownloaded` | 2 |
| `getModelFile` | 1 |

### EventPresenter.kt (Score: 40.37 - 🔴 HIGH)
- **Total Complexity**: 18
- **Real Coverage**: 59.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `getEventBorderColor` | 9 |
| `getDeadlineChipText` | 4 |
| `getCategoryLabel` | 3 |
| `getDeadlineStatus` | 2 |

### GoogleDriveService.kt (Score: 39.58 - 🔴 HIGH)
- **Total Complexity**: 16
- **Real Coverage**: 54.8%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `<T> withToken` | 8 |
| `listFiles` | 3 |
| `getFileContent` | 3 |
| `validateConnection` | 2 |

### AgentHarness.kt (Score: 38.41 - 🔴 HIGH)
- **Total Complexity**: 37
- **Real Coverage**: 89.9%

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

### AcademicCalendar.kt (Score: 35.09 - 🔴 HIGH)
- **Total Complexity**: 29
- **Real Coverage**: 80.7%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `EventItemView` | 7 |
| `TaskDecompositionDialog` | 6 |
| `AcademicCalendar` | 1 |

### ContextAgent.kt (Score: 31.03 - 🔴 HIGH)
- **Total Complexity**: 31
- **Real Coverage**: 96.8%

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
- **Real Coverage**: 0.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `SourceItemView` | 5 |

### GoogleCalendarSyncService.kt (Score: 29.29 - 🟡 MEDIUM)
- **Total Complexity**: 29
- **Real Coverage**: 93.0%

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

### SqlDelightLocalCalendarRepository.kt (Score: 24.58 - 🟡 MEDIUM)
- **Total Complexity**: 24
- **Real Coverage**: 90.0%

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

### EventGenerator.kt (Score: 24.27 - 🟡 MEDIUM)
- **Total Complexity**: 12
- **Real Coverage**: 56.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `expandTimeEvent` | 5 |
| `expandDayEvent` | 5 |
| `expandEvents` | 2 |

### ErrorBanner.kt (Score: 20.49 - 🟡 MEDIUM)
- **Total Complexity**: 5
- **Real Coverage**: 14.8%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `QuotaErrorBanner` | 1 |
| `AnimatedErrorBanner` | 1 |

### SqlDelightUserPreferenceMemoryRepository.kt (Score: 20.47 - 🟡 MEDIUM)
- **Total Complexity**: 20
- **Real Coverage**: 89.5%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `getDerivedConstraints` | 12 |
| `logOverride` | 6 |
| `pruneOldLogs` | 1 |
| `clearAllLogs` | 1 |

### RoutineScreen.kt (Score: 20.00 - 🟡 MEDIUM)
- **Total Complexity**: 4
- **Real Coverage**: 0.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `RoutineScreen` | 2 |
| `RoutineEventView` | 2 |

### IcsStringBuilder.kt (Score: 19.54 - 🟡 MEDIUM)
- **Total Complexity**: 13
- **Real Coverage**: 66.2%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `buildIcsString` | 7 |
| `buildRecurrenceRule` | 3 |
| `formatDateTime` | 1 |
| `formatDate` | 1 |
| `plusDays` | 1 |

### IngestionAgent.kt (Score: 19.49 - 🟡 MEDIUM)
- **Total Complexity**: 10
- **Real Coverage**: 54.4%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `addLocalFile` | 3 |
| `addUrl` | 3 |
| `addDriveFile` | 3 |
| `persistSource` | 1 |

### AIService.kt (Score: 19.13 - 🟡 MEDIUM)
- **Total Complexity**: 9
- **Real Coverage**: 50.0%

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

### Event.kt (Score: 18.15 - 🟡 MEDIUM)
- **Total Complexity**: 18
- **Real Coverage**: 92.3%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `overlaps` | 4 |
| `overlaps` | 2 |
| `overlaps` | 1 |
| `Event.timeUntilDue` | 1 |
| `Event.studyProgress` | 1 |

### StudioPanel.kt (Score: 18.08 - 🟡 MEDIUM)
- **Total Complexity**: 18
- **Real Coverage**: 93.8%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `StudioPanel` | 1 |

### CalendarInterfaces.kt (Score: 16.43 - 🟡 MEDIUM)
- **Total Complexity**: 13
- **Real Coverage**: 72.7%

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

### DecompositionOrchestrator.kt (Score: 13.10 - 🟢 LOW)
- **Total Complexity**: 12
- **Real Coverage**: 80.3%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `decompose` | 7 |
| `isComplex` | 3 |
| `calculateSubDueDate` | 1 |
| `calculateDaysBeforeDue` | 1 |

### AppController.kt (Score: 12.51 - 🟢 LOW)
- **Total Complexity**: 9
- **Real Coverage**: 64.9%

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

### SourcesPanel.kt (Score: 12.00 - 🟢 LOW)
- **Total Complexity**: 3
- **Real Coverage**: 0.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `SourcesPanel` | 1 |

### SourceRepository.kt (Score: 12.00 - 🟢 LOW)
- **Total Complexity**: 3
- **Real Coverage**: 0.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `saveSource` | 1 |
| `getSourceMetadata` | 1 |
| `getSourceById` | 1 |

### ChatPanel.kt (Score: 11.24 - 🟢 LOW)
- **Total Complexity**: 11
- **Real Coverage**: 87.4%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `MessageView` | 4 |
| `ChatPanel` | 1 |

### Logger.kt (Score: 11.19 - 🟢 LOW)
- **Total Complexity**: 9
- **Real Coverage**: 70.0%

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

### TelemetryManager.kt (Score: 11.00 - 🟢 LOW)
- **Total Complexity**: 11
- **Real Coverage**: 100.0%

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

### SourceIngestionHandler.kt (Score: 9.59 - 🟢 LOW)
- **Total Complexity**: 8
- **Real Coverage**: 70.8%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `ingestLocalFile` | 1 |
| `ingestUrl` | 1 |
| `ingestDriveFile` | 1 |
| `buildIngestibleFilesQuery` | 1 |

### SqlDelightSourceRepository.kt (Score: 9.01 - 🟢 LOW)
- **Total Complexity**: 9
- **Real Coverage**: 95.9%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `saveSource` | 3 |
| `updateSourceMetadata` | 2 |
| `getSourceMetadata` | 1 |
| `getAllSources` | 1 |
| `getSourceById` | 1 |
| `getFragmentsForSource` | 1 |

### GoogleAuthService.kt (Score: 8.00 - 🟢 LOW)
- **Total Complexity**: 8
- **Real Coverage**: 100.0%

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

### GoogleAccountFlow.kt (Score: 8.00 - 🟢 LOW)
- **Total Complexity**: 8
- **Real Coverage**: 100.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `connect` | 5 |
| `disconnect` | 1 |
| `reportAuthError` | 1 |

### OAuthExchange.kt (Score: 7.20 - 🟢 LOW)
- **Total Complexity**: 6
- **Real Coverage**: 67.9%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `performTokenExchange` | 2 |
| `exchangeCodeForTokens` | 1 |
| `refreshAccessToken` | 1 |

### BugReporter.kt (Score: 6.00 - 🟢 LOW)
- **Total Complexity**: 6
- **Real Coverage**: 95.3%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `reportError` | 6 |

### SourceInterfaces.kt (Score: 6.00 - 🟢 LOW)
- **Total Complexity**: 2
- **Real Coverage**: 0.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `isAuthorized` | 1 |
| `SelectorUI` | 1 |

### IcsExport.kt (Score: 6.00 - 🟢 LOW)
- **Total Complexity**: 2
- **Real Coverage**: 0.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `generateIcsString` | 1 |
| `writeIcsFile` | 1 |

### Platform.kt (Score: 6.00 - 🟢 LOW)
- **Total Complexity**: 2
- **Real Coverage**: 0.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `rememberModelDirectoryPath` | 1 |
| `rememberDriverFactory` | 1 |

### DocxReader.kt (Score: 6.00 - 🟢 LOW)
- **Total Complexity**: 2
- **Real Coverage**: 0.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `readSource` | 1 |
| `rememberDocxReader` | 1 |

### UserPreferenceMemoryRepository.kt (Score: 6.00 - 🟢 LOW)
- **Total Complexity**: 2
- **Real Coverage**: 0.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `logOverride` | 1 |
| `getDerivedConstraints` | 1 |

### PdfReader.kt (Score: 6.00 - 🟢 LOW)
- **Total Complexity**: 2
- **Real Coverage**: 0.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `readSource` | 1 |
| `rememberPdfReader` | 1 |

### LocalFileReader.kt (Score: 6.00 - 🟢 LOW)
- **Total Complexity**: 2
- **Real Coverage**: 0.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `readText` | 1 |
| `rememberLocalFileReader` | 1 |

### NormalizationService.kt (Score: 5.46 - 🟢 LOW)
- **Total Complexity**: 5
- **Real Coverage**: 73.7%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `extract` | 5 |

### SyncProposal.kt (Score: 5.02 - 🟢 LOW)
- **Total Complexity**: 4
- **Real Coverage**: 60.0%


### SourceProcessor.kt (Score: 5.01 - 🟢 LOW)
- **Total Complexity**: 5
- **Real Coverage**: 91.7%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `split` | 3 |
| `process` | 2 |

### WebSourceReader.kt (Score: 4.11 - 🟢 LOW)
- **Total Complexity**: 4
- **Real Coverage**: 81.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `readTextFromUrl` | 2 |
| `cleanHtml` | 2 |

### SyncNegotiationDialog.kt (Score: 4.00 - 🟢 LOW)
- **Total Complexity**: 4
- **Real Coverage**: 97.6%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `SyncNegotiationDialog` | 1 |

### EventDisplayPipeline.kt (Score: 4.00 - 🟢 LOW)
- **Total Complexity**: 4
- **Real Coverage**: 100.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `getExpandedAndFilteredEvents` | 1 |

### KotlinxSerialization.kt (Score: 4.00 - 🟢 LOW)
- **Total Complexity**: 4
- **Real Coverage**: 100.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `serialize` | 1 |
| `deserialize` | 1 |
| `serialize` | 1 |
| `deserialize` | 1 |

### PreferencesRepository.kt (Score: 4.00 - 🟢 LOW)
- **Total Complexity**: 4
- **Real Coverage**: 100.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `getPreferences` | 3 |
| `savePreferences` | 1 |

### SourceFragment.kt (Score: 3.00 - 🟢 LOW)
- **Total Complexity**: 3
- **Real Coverage**: 100.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `toJson` | 1 |

### RoutineRepository.kt (Score: 3.00 - 🟢 LOW)
- **Total Complexity**: 3
- **Real Coverage**: 100.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `getRoutineEvents` | 2 |
| `saveRoutineEvents` | 1 |

### PlatformFileSystem.kt (Score: 2.00 - 🟢 LOW)
- **Total Complexity**: 1
- **Real Coverage**: 0.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `getFileSystem` | 1 |

### CheckInDialog.kt (Score: 2.00 - 🟢 LOW)
- **Total Complexity**: 1
- **Real Coverage**: 0.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `CheckInDialog` | 1 |

### RoutineItem.kt (Score: 2.00 - 🟢 LOW)
- **Total Complexity**: 1
- **Real Coverage**: 0.0%


### SemesterResolver.kt (Score: 2.00 - 🟢 LOW)
- **Total Complexity**: 2
- **Real Coverage**: 100.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `getSemesterRange` | 2 |

### RoutineSetupScreen.kt (Score: 2.00 - 🟢 LOW)
- **Total Complexity**: 1
- **Real Coverage**: 0.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `RoutineSetupScreen` | 1 |

### SettingsFactory.kt (Score: 2.00 - 🟢 LOW)
- **Total Complexity**: 1
- **Real Coverage**: 0.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `rememberSettings` | 1 |

### PlatformUtils.kt (Score: 2.00 - 🟢 LOW)
- **Total Complexity**: 1
- **Real Coverage**: 0.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `openBrowser` | 1 |

### FilePicker.kt (Score: 2.00 - 🟢 LOW)
- **Total Complexity**: 1
- **Real Coverage**: 0.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `FilePicker` | 1 |

### RecursiveDecompositionAIService.kt (Score: 1.13 - 🟢 LOW)
- **Total Complexity**: 1
- **Real Coverage**: 50.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `decomposeTask` | 1 |

### GoogleConnectionState.kt (Score: 1.06 - 🟢 LOW)
- **Total Complexity**: 1
- **Real Coverage**: 61.5%


### DependencyContainer.kt (Score: 1.01 - 🟢 LOW)
- **Total Complexity**: 1
- **Real Coverage**: 78.6%


### IcsCalendarSource.kt (Score: 1.00 - 🟢 LOW)
- **Total Complexity**: 1
- **Real Coverage**: 100.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `readSource` | 1 |

### DecomposedTask.kt (Score: 1.00 - 🟢 LOW)
- **Total Complexity**: 1
- **Real Coverage**: 100.0%


### SourceItem.kt (Score: 1.00 - 🟢 LOW)
- **Total Complexity**: 1
- **Real Coverage**: 100.0%


### StudyPreferences.kt (Score: 1.00 - 🟢 LOW)
- **Total Complexity**: 1
- **Real Coverage**: 100.0%


### UserOverrideLog.kt (Score: 1.00 - 🟢 LOW)
- **Total Complexity**: 1
- **Real Coverage**: 100.0%


