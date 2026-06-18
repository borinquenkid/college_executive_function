# Code Base CRAP Index Analysis

This document evaluates the codebase using the **CRAP (Change Risk Anti-Patterns) index**.
A higher CRAP index indicates higher risk when changing that file. A score **above 30** is considered highly risky.

## Heuristics Used
- **Complexity**: Approximated by counting control flow branches (`if`, `when`, `for`, `while`, `catch`, logical operators `&&`/`||`, safe calls `?.let`/`?.also`/`?.run`, collection operators `filter`/`map`/`forEach`/`any`/`all`) inside all methods + 1 base complexity per method.
- **Coverage**: Calculated exactly from Kover's XML test coverage report.
- **Formula**: $\text{CRAP} = \text{Complexity}^2 \times (1 - \text{Coverage})^3 + \text{Complexity}$

## Overall Summary
- **Total Files Analyzed**: 159
- **High-Risk Files (CRAP > 30)**: 12

### Top 15 High-Risk Files

| File | Complexity | Real Coverage | CRAP Index | Risk Status |
| :--- | :---: | :---: | :---: | :---: |
| AcademicCalendar.kt | 22 | 35.2% | 153.91 | 🔴 HIGH |
| AcademicCalendarComponents.kt | 10 | 0.0% | 110.00 | 🔴 HIGH |
| CalendarInterfaces.kt | 13 | 27.3% | 78.01 | 🔴 HIGH |
| ConflictResolutionUI.kt | 8 | 0.0% | 72.00 | 🔴 HIGH |
| GeminiRequestExecutor.kt | 32 | 70.7% | 57.67 | 🔴 HIGH |
| EventAgent.kt | 47 | 89.5% | 49.58 | 🔴 HIGH |
| CalendarSyncManager.kt | 8 | 16.0% | 45.93 | 🔴 HIGH |
| GeminiAIService.kt | 34 | 84.4% | 38.39 | 🔴 HIGH |
| GoogleRemoteCalendarRepository.kt | 22 | 68.6% | 36.94 | 🔴 HIGH |
| CommonSourceProviders.kt | 25 | 73.5% | 36.67 | 🔴 HIGH |
| GoogleCalendarSyncService.kt | 29 | 83.2% | 32.98 | 🔴 HIGH |
| GoogleCalendarPanel.kt | 23 | 75.0% | 31.27 | 🔴 HIGH |
| SourceItemView.kt | 5 | 0.0% | 30.00 | 🟡 MEDIUM |
| CalendarPushResolver.kt | 21 | 73.3% | 29.36 | 🟡 MEDIUM |
| GeminiRetryService.kt | 27 | 88.5% | 28.11 | 🟡 MEDIUM |

---

## Detailed File Breakdown

### AcademicCalendar.kt (Score: 153.91 - 🔴 HIGH)
- **Total Complexity**: 22
- **Real Coverage**: 35.2%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `TaskDecompositionDialog` | 6 |
| `AcademicCalendar` | 1 |
| `EventItemView` | 1 |

### AcademicCalendarComponents.kt (Score: 110.00 - 🔴 HIGH)
- **Total Complexity**: 10
- **Real Coverage**: 0.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `GoogleLinkPrompt` | 1 |
| `AcademicCalendarHeader` | 1 |
| `EventListContent` | 1 |

### CalendarInterfaces.kt (Score: 78.01 - 🔴 HIGH)
- **Total Complexity**: 13
- **Real Coverage**: 27.3%

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

### ConflictResolutionUI.kt (Score: 72.00 - 🔴 HIGH)
- **Total Complexity**: 8
- **Real Coverage**: 0.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `ConflictItem` | 2 |
| `ConflictResolutionDialog` | 1 |
| `ConflictResolutionBanner` | 1 |

### GeminiRequestExecutor.kt (Score: 57.67 - 🔴 HIGH)
- **Total Complexity**: 32
- **Real Coverage**: 70.7%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `clearRateLimitResetForTesting` | 1 |
| `postToModel` | 1 |
| `<T> executeWithRetry` | 1 |
| `<T> executeWithRetryInternal` | 1 |

### EventAgent.kt (Score: 49.58 - 🔴 HIGH)
- **Total Complexity**: 47
- **Real Coverage**: 89.5%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `pushToCalendar` | 11 |
| `extractDeliverables` | 6 |
| `acceptDecomposition` | 3 |
| `rescheduleEvent` | 3 |
| `loadPersistedWarnings` | 2 |
| `generateStudyPlan` | 2 |
| `loadIncompleteEvents` | 2 |
| `estimatedRemainingSeconds` | 1 |
| `clearError` | 1 |
| `reportError` | 1 |
| *... and 11 more* | |

### CalendarSyncManager.kt (Score: 45.93 - 🔴 HIGH)
- **Total Complexity**: 8
- **Real Coverage**: 16.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `refreshEvents` | 2 |
| `initiateSyncIfNeeded` | 1 |
| `applySyncProposal` | 1 |

### GeminiAIService.kt (Score: 38.39 - 🔴 HIGH)
- **Total Complexity**: 34
- **Real Coverage**: 84.4%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `generateCalendarEvents` | 5 |
| `categorizeSource` | 4 |
| `analyzeDocument` | 3 |
| `generateCalendarEventsFromPrompt` | 2 |
| `generateChatResponse` | 2 |
| `extractSourceYears` | 1 |
| `filterToSourceYears` | 1 |
| `parseEventsJson` | 1 |
| `parseDecomposeTaskJson` | 1 |
| `parseCategorizeSourceJson` | 1 |
| *... and 7 more* | |

### GoogleRemoteCalendarRepository.kt (Score: 36.94 - 🔴 HIGH)
- **Total Complexity**: 22
- **Real Coverage**: 68.6%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `clearCalendar` | 5 |
| `deleteEvent` | 3 |
| `getAllEvents` | 2 |
| `saveEvent` | 2 |
| `updateEvent` | 2 |
| `mapCalendarException` | 2 |
| `getSettings` | 1 |
| `getAvailableCalendars` | 1 |
| `hardDeleteEvent` | 1 |
| `getEventsInRange` | 1 |
| *... and 2 more* | |

### CommonSourceProviders.kt (Score: 36.67 - 🔴 HIGH)
- **Total Complexity**: 25
- **Real Coverage**: 73.5%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `IngestingProgressDialog` | 5 |
| `SelectorUI` | 4 |
| `SelectorUI` | 4 |
| `SelectorUI` | 3 |
| `isAuthorized` | 1 |
| `isAuthorized` | 1 |
| `isAuthorized` | 1 |
| `DrivePickerDialog` | 1 |

### GoogleCalendarSyncService.kt (Score: 32.98 - 🔴 HIGH)
- **Total Complexity**: 29
- **Real Coverage**: 83.2%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `getEvents` | 10 |
| `<T> withToken` | 6 |
| `listCalendars` | 3 |
| `ensureSuccess` | 2 |
| `syncEvent` | 2 |
| `createCalendar` | 1 |
| `deleteEvent` | 1 |
| `fetchEventsPage` | 1 |

### GoogleCalendarPanel.kt (Score: 31.27 - 🔴 HIGH)
- **Total Complexity**: 23
- **Real Coverage**: 75.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `GoogleCalendarPanel` | 1 |
| `GoogleCalendarSelector` | 1 |
| `CreateCalendarDialog` | 1 |

### SourceItemView.kt (Score: 30.00 - 🟡 MEDIUM)
- **Total Complexity**: 5
- **Real Coverage**: 0.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `SourceItemView` | 1 |

### CalendarPushResolver.kt (Score: 29.36 - 🟡 MEDIUM)
- **Total Complexity**: 21
- **Real Coverage**: 73.3%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `buildResolver` | 3 |
| `resolveAndPush` | 1 |
| `resolveAndReschedule` | 1 |
| `resolveConflicts` | 1 |
| `persistResolvedEvents` | 1 |

### GeminiRetryService.kt (Score: 28.11 - 🟡 MEDIUM)
- **Total Complexity**: 27
- **Real Coverage**: 88.5%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `wait` | 7 |
| `checkRateLimitWindow` | 3 |
| `clearRateLimitResetForTesting` | 1 |
| `clearGlobalHold` | 1 |
| `clearGlobalHoldForTesting` | 1 |
| `cancelHold` | 1 |
| `activateGlobalHold` | 1 |
| `resolveRetryDelay` | 1 |
| `activateRateLimitWindow` | 1 |

### SqlDelightLocalCalendarRepository.kt (Score: 27.17 - 🟡 MEDIUM)
- **Total Complexity**: 24
- **Real Coverage**: 82.4%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `updateEvent` | 6 |
| `mapEntityToEvent` | 4 |
| `getAllEvents` | 3 |
| `saveEvent` | 2 |
| `getSettings` | 1 |
| `deleteEvent` | 1 |
| `hardDeleteEvent` | 1 |
| `getEventsInRange` | 1 |
| `getEventsBySyncStatus` | 1 |
| `getIncompleteEventsBefore` | 1 |

### CalendarAgent.kt (Score: 25.20 - 🟡 MEDIUM)
- **Total Complexity**: 23
- **Real Coverage**: 83.9%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `updateEvent` | 5 |
| `deleteEvent` | 4 |
| `saveEvent` | 3 |
| `synchronize` | 2 |
| `isLiveSyncEnabled` | 2 |
| `isGoogleLinked` | 2 |
| `getEvents` | 1 |
| `saveEventLocally` | 1 |
| `checkSyncProposals` | 1 |
| `applySyncNegotiation` | 1 |
| *... and 1 more* | |

### CriticActorAIService.kt (Score: 24.34 - 🟡 MEDIUM)
- **Total Complexity**: 23
- **Real Coverage**: 86.4%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `areEventListsDifferent` | 7 |
| `generateChatResponse` | 4 |
| `generateCalendarEvents` | 2 |
| `decomposeTask` | 2 |
| `generateStudyPlan` | 1 |
| `<T> runCritiqueLoop` | 1 |

### GeminiResponseParser.kt (Score: 23.01 - 🟡 MEDIUM)
- **Total Complexity**: 23
- **Real Coverage**: 97.9%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `filterToSourceYears` | 4 |
| `toEvent` | 4 |
| `parseCategorizeSourceJson` | 4 |
| `extractSourceYears` | 2 |
| `parseEventsJson` | 2 |
| `parseDecomposeTaskJson` | 2 |
| `extractJsonArray` | 2 |
| `parseClockTime` | 1 |
| `stripCodeFences` | 1 |

### Event.kt (Score: 22.02 - 🟡 MEDIUM)
- **Total Complexity**: 22
- **Real Coverage**: 96.5%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `overlaps` | 3 |
| `Event.withSyncStatus` | 2 |
| `Event.withCompletionStatus` | 2 |
| `Event.validate` | 2 |
| `overlaps` | 1 |
| `overlaps` | 1 |
| `Event.timeUntilDue` | 1 |
| `Event.studyProgress` | 1 |

### SyncNegotiator.kt (Score: 22.00 - 🟡 MEDIUM)
- **Total Complexity**: 22
- **Real Coverage**: 98.2%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `pushLocalChanges` | 7 |
| `buildNegotiation` | 2 |
| `findRemoteUpdatesAndConflicts` | 1 |
| `findDeletedLocalIds` | 1 |
| `buildProposedBaseCalendar` | 1 |

### CriticJsonCodec.kt (Score: 21.01 - 🟡 MEDIUM)
- **Total Complexity**: 21
- **Real Coverage**: 96.8%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `parseEventFromJson` | 7 |
| `serializeEvents` | 5 |
| `parseEvents` | 3 |
| `serializeTasks` | 2 |
| `parseTasks` | 2 |
| `parseTaskFromJson` | 2 |

### SqlDelightUserPreferenceMemoryRepository.kt (Score: 20.44 - 🟡 MEDIUM)
- **Total Complexity**: 20
- **Real Coverage**: 89.7%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `getDerivedConstraints` | 12 |
| `logOverride` | 6 |
| `pruneOldLogs` | 1 |
| `clearAllLogs` | 1 |

### GeminiModelNegotiator.kt (Score: 20.39 - 🟡 MEDIUM)
- **Total Complexity**: 20
- **Real Coverage**: 90.1%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `getAvailableModels` | 5 |
| `evictFromCache` | 2 |
| `clearBlacklistForTesting` | 1 |
| `blacklistModel` | 1 |
| `negotiateBestModel` | 1 |

### StudioPanel.kt (Score: 20.18 - 🟡 MEDIUM)
- **Total Complexity**: 20
- **Real Coverage**: 92.3%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `StudioPanel` | 1 |

### EventGenerationService.kt (Score: 20.11 - 🟡 MEDIUM)
- **Total Complexity**: 19
- **Real Coverage**: 85.5%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `extractDeliverables` | 6 |
| `normalize` | 6 |
| `buildScheduleContext` | 4 |
| `generateStudyPlan` | 2 |
| `generateDeterministicId` | 1 |

### RoutineScreen.kt (Score: 20.00 - 🟡 MEDIUM)
- **Total Complexity**: 4
- **Real Coverage**: 0.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `RoutineScreen` | 2 |
| `RoutineEventView` | 2 |

### SourceRepository.kt (Score: 20.00 - 🟡 MEDIUM)
- **Total Complexity**: 4
- **Real Coverage**: 0.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `saveSource` | 1 |
| `getSourceMetadata` | 1 |
| `getSourceById` | 1 |
| `deleteSource` | 1 |

### GoogleDriveService.kt (Score: 19.35 - 🟡 MEDIUM)
- **Total Complexity**: 19
- **Real Coverage**: 90.1%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `<T> withToken` | 8 |
| `validateConnectionResult` | 4 |
| `listFiles` | 3 |
| `getFileContent` | 3 |
| `validateConnection` | 1 |

### CollisionDetector.kt (Score: 19.02 - 🟡 MEDIUM)
- **Total Complexity**: 17
- **Real Coverage**: 80.9%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `findNextDaySlot` | 1 |
| `findNextTimeSlot` | 1 |
| `findTimeSlotOnDay` | 1 |

### StudyPlanBuilder.kt (Score: 18.00 - 🟡 MEDIUM)
- **Total Complexity**: 18
- **Real Coverage**: 100.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `formatHour` | 3 |
| `getSyllabusStudyPlanPrompt` | 1 |
| `getTaskDecompositionPrompt` | 1 |
| `getDecompositionCritiquePrompt` | 1 |

### EventPresenter.kt (Score: 18.00 - 🟡 MEDIUM)
- **Total Complexity**: 18
- **Real Coverage**: 100.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `getEventBorderColor` | 9 |
| `getDeadlineChipText` | 4 |
| `getCategoryLabel` | 3 |
| `getDeadlineStatus` | 2 |

### AppController.kt (Score: 17.54 - 🟡 MEDIUM)
- **Total Complexity**: 16
- **Real Coverage**: 81.8%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `collect` | 1 |
| `asStateFlow` | 1 |
| `collect` | 1 |
| `asStateFlow` | 1 |
| `loadSources` | 1 |
| `navigateTo` | 1 |
| `addEvents` | 1 |
| `clearEvents` | 1 |
| `addSource` | 1 |
| `reanalyzeSource` | 1 |
| *... and 6 more* | |

### ConflictResolver.kt (Score: 16.33 - 🟡 MEDIUM)
- **Total Complexity**: 14
- **Real Coverage**: 77.2%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `resolveConflicts` | 6 |
| `rescheduleEarlier` | 6 |
| `findConflict` | 1 |
| `isMovable` | 1 |

### SchedulingAlgorithm.kt (Score: 16.08 - 🟡 MEDIUM)
- **Total Complexity**: 16
- **Real Coverage**: 93.3%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `resolve` | 1 |
| `findNewSlotAndResolve` | 1 |
| `shiftEvent` | 1 |
| `findNextAvailableSlot` | 1 |

### ChatPanel.kt (Score: 15.90 - 🟡 MEDIUM)
- **Total Complexity**: 15
- **Real Coverage**: 84.1%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `MessageView` | 6 |
| `ChatPanel` | 1 |

### AppContent.kt (Score: 15.43 - 🟡 MEDIUM)
- **Total Complexity**: 10
- **Real Coverage**: 62.1%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `AppContent` | 10 |

### GoogleAccountFlow.kt (Score: 15.04 - 🟡 MEDIUM)
- **Total Complexity**: 15
- **Real Coverage**: 94.4%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `connect` | 5 |
| `handleInvalidAccessToken` | 4 |
| `checkConnectionOnStartup` | 3 |
| `disconnect` | 1 |
| `reportAuthError` | 1 |

### SyncNegotiationApplier.kt (Score: 15.00 - 🟢 LOW)
- **Total Complexity**: 15
- **Real Coverage**: 100.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `isLiveSyncEnabled` | 2 |
| `apply` | 1 |
| `applyDeletedLocalEvents` | 1 |
| `applyRemoteEventsToLocal` | 1 |
| `applyShiftedStudyBlocks` | 1 |

### DecompositionOrchestrator.kt (Score: 14.84 - 🟢 LOW)
- **Total Complexity**: 14
- **Real Coverage**: 83.8%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `decompose` | 9 |
| `isComplex` | 3 |
| `calculateSubDueDate` | 1 |
| `calculateDaysBeforeDue` | 1 |

### StudyBlockShiftResolver.kt (Score: 14.35 - 🟢 LOW)
- **Total Complexity**: 14
- **Real Coverage**: 87.9%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `resolveShifts` | 1 |
| `findCollidingEvent` | 1 |
| `hasShifted` | 1 |

### IcsStringBuilder.kt (Score: 13.00 - 🟢 LOW)
- **Total Complexity**: 13
- **Real Coverage**: 98.6%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `buildIcsString` | 7 |
| `buildRecurrenceRule` | 3 |
| `formatDateTime` | 1 |
| `formatDate` | 1 |
| `plusDays` | 1 |

### AddRoutineItemDialog.kt (Score: 13.00 - 🟢 LOW)
- **Total Complexity**: 13
- **Real Coverage**: 98.9%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `AddRoutineItemDialog` | 11 |
| `ClickableField` | 1 |
| `TimePickerDialog` | 1 |

### SourceLoader.kt (Score: 12.49 - 🟢 LOW)
- **Total Complexity**: 4
- **Real Coverage**: 19.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `loadSources` | 4 |

### EventGenerator.kt (Score: 12.00 - 🟢 LOW)
- **Total Complexity**: 12
- **Real Coverage**: 100.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `expandEvents` | 1 |
| `expandTimeEvent` | 1 |
| `expandDayEvent` | 1 |

### SourceProcessingPipeline.kt (Score: 11.10 - 🟢 LOW)
- **Total Complexity**: 5
- **Real Coverage**: 37.5%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `processSource` | 5 |

### EventBuilder.kt (Score: 11.03 - 🟢 LOW)
- **Total Complexity**: 11
- **Real Coverage**: 93.8%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `getSourceEventExtractionPrompt` | 9 |
| `getEventCritiquePrompt` | 1 |

### ContextAgent.kt (Score: 11.02 - 🟢 LOW)
- **Total Complexity**: 11
- **Real Coverage**: 94.9%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `analyzeSource` | 4 |
| `querySource` | 3 |
| `getSourceMetadata` | 1 |
| `queryAllSources` | 1 |

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

### SourceAdder.kt (Score: 11.00 - 🟢 LOW)
- **Total Complexity**: 11
- **Real Coverage**: 100.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `processSourceWithCache` | 5 |
| `addSource` | 3 |
| `handleFailure` | 3 |

### ChatBuilder.kt (Score: 11.00 - 🟢 LOW)
- **Total Complexity**: 11
- **Real Coverage**: 100.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `getChatCritiquePrompt` | 2 |
| `getMultiSourceChatPrompt` | 1 |

### AgentHarness.kt (Score: 10.46 - 🟢 LOW)
- **Total Complexity**: 10
- **Real Coverage**: 83.3%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `runHarness` | 4 |
| `getLastPollTime` | 1 |
| `setLastPollTime` | 1 |
| `getWatchedLocalDirectories` | 1 |
| `setWatchedLocalDirectories` | 1 |
| `getWatchedGDriveFolders` | 1 |
| `setWatchedGDriveFolders` | 1 |

### GoogleAuthManager.kt (Score: 10.40 - 🟢 LOW)
- **Total Complexity**: 5
- **Real Coverage**: 40.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `loginAndLink` | 2 |
| `unlinkAccount` | 2 |
| `isLinked` | 1 |

### StudyPreferencesPanel.kt (Score: 10.21 - 🟢 LOW)
- **Total Complexity**: 10
- **Real Coverage**: 87.1%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `StudyPreferencesPanel` | 1 |

### AiPrompts.kt (Score: 10.12 - 🟢 LOW)
- **Total Complexity**: 10
- **Real Coverage**: 89.5%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `getSourceEventExtractionPrompt` | 1 |
| `getSyllabusStudyPlanPrompt` | 1 |
| `getTaskDecompositionPrompt` | 1 |
| `getDocumentIntelligencePrompt` | 1 |
| `getSourceCategorizationPrompt` | 1 |
| `getMultiSourceChatPrompt` | 1 |
| `getEventCritiquePrompt` | 1 |
| `getChatCritiquePrompt` | 1 |
| `getDecompositionCritiquePrompt` | 1 |
| `getSyllabusAuditPrompt` | 1 |

### SourceManager.kt (Score: 10.03 - 🟢 LOW)
- **Total Complexity**: 10
- **Real Coverage**: 93.3%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `addSource` | 3 |
| `deleteSource` | 2 |
| `collect` | 1 |
| `asStateFlow` | 1 |
| `loadSources` | 1 |
| `reanalyzeSource` | 1 |
| `selectSource` | 1 |

### SqlDelightSourceRepository.kt (Score: 10.00 - 🟢 LOW)
- **Total Complexity**: 10
- **Real Coverage**: 96.4%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `saveSource` | 3 |
| `updateSourceMetadata` | 2 |
| `getSourceMetadata` | 1 |
| `getAllSources` | 1 |
| `getSourceById` | 1 |
| `getFragmentsForSource` | 1 |
| `deleteSource` | 1 |

### ActiveSemesterDetector.kt (Score: 10.00 - 🟢 LOW)
- **Total Complexity**: 10
- **Real Coverage**: 100.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `hasMultipleSemestersForSameYear` | 4 |
| `detect` | 3 |
| `contains` | 1 |
| `semestersFor` | 1 |

### SourceFactGrounder.kt (Score: 10.00 - 🟢 LOW)
- **Total Complexity**: 10
- **Real Coverage**: 100.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `groundFreeText` | 4 |
| `findUngrounded` | 2 |
| `extractClaims` | 1 |
| `normalizeOrdinals` | 1 |

### ModelManager.kt (Score: 10.00 - 🟢 LOW)
- **Total Complexity**: 10
- **Real Coverage**: 100.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `downloadModel` | 7 |
| `isModelDownloaded` | 2 |
| `getModelFile` | 1 |

### IngestionAgent.kt (Score: 10.00 - 🟢 LOW)
- **Total Complexity**: 10
- **Real Coverage**: 100.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `addLocalFile` | 2 |
| `addUrl` | 2 |
| `addDriveFile` | 2 |
| `resolveCategory` | 1 |
| `persistSource` | 1 |

### Logger.kt (Score: 9.27 - 🟢 LOW)
- **Total Complexity**: 9
- **Real Coverage**: 85.0%

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

### NormalizationService.kt (Score: 9.03 - 🟢 LOW)
- **Total Complexity**: 9
- **Real Coverage**: 92.9%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `extract` | 5 |
| `sanitizeTimes` | 4 |

### SettingsPreferencesParser.kt (Score: 9.00 - 🟢 LOW)
- **Total Complexity**: 9
- **Real Coverage**: 100.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `parse` | 1 |

### SemesterResolver.kt (Score: 9.00 - 🟢 LOW)
- **Total Complexity**: 9
- **Real Coverage**: 100.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `getExpandedRange` | 7 |
| `getSemesterRange` | 2 |

### AIService.kt (Score: 9.00 - 🟢 LOW)
- **Total Complexity**: 9
- **Real Coverage**: 100.0%

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

### WarningClassifier.kt (Score: 9.00 - 🟢 LOW)
- **Total Complexity**: 9
- **Real Coverage**: 100.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `activeSemesterFrom` | 2 |
| `classify` | 1 |

### SyllabusAuditor.kt (Score: 8.05 - 🟢 LOW)
- **Total Complexity**: 8
- **Real Coverage**: 90.9%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `audit` | 8 |

### SourceIngestionHandler.kt (Score: 8.04 - 🟢 LOW)
- **Total Complexity**: 8
- **Real Coverage**: 91.7%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `ingestLocalFile` | 1 |
| `ingestUrl` | 1 |
| `ingestDriveFile` | 1 |
| `buildIngestibleFilesQuery` | 1 |

### GeminiRequestQueue.kt (Score: 8.00 - 🟢 LOW)
- **Total Complexity**: 8
- **Real Coverage**: 96.2%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `<T> enqueue` | 3 |
| `estimatedRemainingSeconds` | 2 |
| `shared` | 1 |

### ConstraintValidator.kt (Score: 8.00 - 🟢 LOW)
- **Total Complexity**: 8
- **Real Coverage**: 100.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `isValidTimeSlot` | 1 |
| `isDayAvailable` | 1 |

### CategorizationBuilder.kt (Score: 8.00 - 🟢 LOW)
- **Total Complexity**: 8
- **Real Coverage**: 100.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `getSyllabusAuditPrompt` | 4 |
| `getSourceCategorizationPrompt` | 2 |
| `getDocumentIntelligencePrompt` | 2 |

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

### OAuthExchange.kt (Score: 7.20 - 🟢 LOW)
- **Total Complexity**: 6
- **Real Coverage**: 67.9%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `performTokenExchange` | 2 |
| `exchangeCodeForTokens` | 1 |
| `refreshAccessToken` | 1 |

### PollScheduler.kt (Score: 7.09 - 🟢 LOW)
- **Total Complexity**: 5
- **Real Coverage**: 56.3%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `shouldPoll` | 3 |
| `getLastPollTime` | 1 |
| `setLastPollTime` | 1 |

### PreferenceSerializer.kt (Score: 7.07 - 🟢 LOW)
- **Total Complexity**: 7
- **Real Coverage**: 88.9%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `deserializeDirectories` | 4 |
| `serializeDirectories` | 3 |

### SettingsScreen.kt (Score: 7.01 - 🟢 LOW)
- **Total Complexity**: 7
- **Real Coverage**: 93.3%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `SettingsScreen` | 1 |
| `savePreferences` | 1 |
| `formatCalendarError` | 1 |

### ConcurrentFolderFetcher.kt (Score: 7.00 - 🟢 LOW)
- **Total Complexity**: 7
- **Real Coverage**: 100.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `fetchFromFolders` | 4 |
| `fetchFromFolder` | 3 |

### TermNormalizer.kt (Score: 7.00 - 🟢 LOW)
- **Total Complexity**: 7
- **Real Coverage**: 100.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `extractQueryTerms` | 2 |
| `tokenizeFragment` | 2 |

### EventRangeFilter.kt (Score: 7.00 - 🟢 LOW)
- **Total Complexity**: 7
- **Real Coverage**: 100.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `filterByDateRange` | 3 |
| `filterBySyncStatus` | 2 |
| `filterIncompleteBeforeDate` | 2 |

### TFIDFScorer.kt (Score: 7.00 - 🟢 LOW)
- **Total Complexity**: 7
- **Real Coverage**: 100.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `scoreDocuments` | 1 |
| `calculateTFIDF` | 1 |

### StateFlowWrapper.kt (Score: 6.10 - 🟢 LOW)
- **Total Complexity**: 6
- **Real Coverage**: 85.7%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `collect` | 1 |
| `setValue` | 1 |
| `collect` | 1 |
| `asStateFlow` | 1 |
| `setValue` | 1 |
| `<T> mutableStateFlowWrapper` | 1 |

### FragmentRanker.kt (Score: 6.00 - 🟢 LOW)
- **Total Complexity**: 6
- **Real Coverage**: 95.5%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `rankFragments` | 1 |

### BugReporter.kt (Score: 6.00 - 🟢 LOW)
- **Total Complexity**: 6
- **Real Coverage**: 95.5%

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

### App.kt (Score: 6.00 - 🟢 LOW)
- **Total Complexity**: 2
- **Real Coverage**: 0.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `App` | 2 |

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

### AdvancedSettingsPanel.kt (Score: 6.00 - 🟢 LOW)
- **Total Complexity**: 2
- **Real Coverage**: 0.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `AdvancedSettingsPanel` | 1 |

### GeminiSetupPanel.kt (Score: 6.00 - 🟢 LOW)
- **Total Complexity**: 6
- **Real Coverage**: 100.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `GeminiSetupPanel` | 1 |

### DocxReader.kt (Score: 6.00 - 🟢 LOW)
- **Total Complexity**: 2
- **Real Coverage**: 0.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `readSource` | 1 |
| `rememberDocxReader` | 1 |

### AnalysisCacheRepository.kt (Score: 6.00 - 🟢 LOW)
- **Total Complexity**: 2
- **Real Coverage**: 0.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `getCached` | 1 |
| `evict` | 1 |

### CalendarEventGrouper.kt (Score: 6.00 - 🟢 LOW)
- **Total Complexity**: 6
- **Real Coverage**: 100.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `groupEventsByDate` | 2 |
| `isDecomposable` | 1 |
| `filterEventsByDateRange` | 1 |

### SourceScanner.kt (Score: 6.00 - 🟢 LOW)
- **Total Complexity**: 6
- **Real Coverage**: 100.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `getWatchedLocalDirectories` | 1 |
| `setWatchedLocalDirectories` | 1 |
| `getWatchedGDriveFolders` | 1 |
| `setWatchedGDriveFolders` | 1 |
| `scanNewLocalFiles` | 1 |
| `scanNewDriveFiles` | 1 |

### SourceContextBuilder.kt (Score: 6.00 - 🟢 LOW)
- **Total Complexity**: 6
- **Real Coverage**: 100.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `formatFragments` | 2 |
| `buildContextBlocks` | 1 |

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

### ErrorBanner.kt (Score: 5.01 - 🟢 LOW)
- **Total Complexity**: 5
- **Real Coverage**: 91.8%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `QuotaErrorBanner` | 1 |
| `AnimatedErrorBanner` | 1 |

### GeminiRequestBuilder.kt (Score: 5.01 - 🟢 LOW)
- **Total Complexity**: 5
- **Real Coverage**: 93.8%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `buildPostUrl` | 2 |
| `postToModel` | 2 |
| `hasApiKey` | 1 |

### TaskDecompositionService.kt (Score: 5.00 - 🟢 LOW)
- **Total Complexity**: 5
- **Real Coverage**: 95.5%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `decompose` | 1 |
| `applyDecomposition` | 1 |

### UniversalHomeLayout.kt (Score: 5.00 - 🟢 LOW)
- **Total Complexity**: 5
- **Real Coverage**: 96.9%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `UniversalHomeLayout` | 5 |

### LocalFileFetcher.kt (Score: 5.00 - 🟢 LOW)
- **Total Complexity**: 5
- **Real Coverage**: 100.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `fetchFromDirectories` | 5 |

### GroundingGuardAIService.kt (Score: 5.00 - 🟢 LOW)
- **Total Complexity**: 5
- **Real Coverage**: 100.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `groundToSource` | 2 |
| `generateCalendarEvents` | 1 |
| `generateStudyPlan` | 1 |
| `generateChatResponse` | 1 |

### CalendarIdResolver.kt (Score: 5.00 - 🟢 LOW)
- **Total Complexity**: 5
- **Real Coverage**: 100.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `getCEFCalendarId` | 3 |
| `resolveCalendarId` | 2 |

### SourceProcessor.kt (Score: 5.00 - 🟢 LOW)
- **Total Complexity**: 5
- **Real Coverage**: 100.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `split` | 3 |
| `process` | 2 |

### SourceSelector.kt (Score: 5.00 - 🟢 LOW)
- **Total Complexity**: 5
- **Real Coverage**: 100.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `autoSelectFirstFrom` | 2 |
| `clearIfRemovedFrom` | 2 |
| `selectSource` | 1 |

### LocalFileFilter.kt (Score: 4.84 - 🟢 LOW)
- **Total Complexity**: 4
- **Real Coverage**: 62.5%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `filterNewFiles` | 2 |
| `isSupportedFile` | 2 |

### LocalFileProcessor.kt (Score: 4.79 - 🟢 LOW)
- **Total Complexity**: 3
- **Real Coverage**: 41.7%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `processLocalFiles` | 3 |

### DriveFileProcessor.kt (Score: 4.79 - 🟢 LOW)
- **Total Complexity**: 3
- **Real Coverage**: 41.7%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `processDriveFiles` | 3 |

### SyncProposal.kt (Score: 4.25 - 🟢 LOW)
- **Total Complexity**: 4
- **Real Coverage**: 75.0%


### HarnessSourceProcessor.kt (Score: 4.13 - 🟢 LOW)
- **Total Complexity**: 3
- **Real Coverage**: 50.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `processSource` | 1 |
| `processLocalFiles` | 1 |
| `processDriveFiles` | 1 |

### PreferencesRepository.kt (Score: 4.10 - 🟢 LOW)
- **Total Complexity**: 4
- **Real Coverage**: 81.8%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `getPreferences` | 3 |
| `savePreferences` | 1 |

### WebSourceReader.kt (Score: 4.07 - 🟢 LOW)
- **Total Complexity**: 4
- **Real Coverage**: 83.3%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `readTextFromUrl` | 2 |
| `cleanHtml` | 2 |

### ErrorCategorizer.kt (Score: 4.02 - 🟢 LOW)
- **Total Complexity**: 4
- **Real Coverage**: 89.4%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `categorizeError` | 4 |

### EventDisplayPipeline.kt (Score: 4.01 - 🟢 LOW)
- **Total Complexity**: 4
- **Real Coverage**: 92.9%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `getExpandedAndFilteredEvents` | 1 |

### SourceDeleter.kt (Score: 4.00 - 🟢 LOW)
- **Total Complexity**: 4
- **Real Coverage**: 94.4%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `deleteSource` | 4 |

### SyncNegotiationDialog.kt (Score: 4.00 - 🟢 LOW)
- **Total Complexity**: 4
- **Real Coverage**: 96.6%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `SyncNegotiationDialog` | 1 |

### LocalDirectoryPreferences.kt (Score: 4.00 - 🟢 LOW)
- **Total Complexity**: 4
- **Real Coverage**: 100.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `getWatchedDirectories` | 2 |
| `setWatchedDirectories` | 2 |

### RetryAfterParser.kt (Score: 4.00 - 🟢 LOW)
- **Total Complexity**: 4
- **Real Coverage**: 100.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `extractRetryAfterMs` | 4 |

### DirectoryPreferencesManager.kt (Score: 4.00 - 🟢 LOW)
- **Total Complexity**: 4
- **Real Coverage**: 100.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `getWatchedLocalDirectories` | 1 |
| `setWatchedLocalDirectories` | 1 |
| `getWatchedGDriveFolders` | 1 |
| `setWatchedGDriveFolders` | 1 |

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

### DriveDirectoryPreferences.kt (Score: 4.00 - 🟢 LOW)
- **Total Complexity**: 4
- **Real Coverage**: 100.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `getWatchedFolders` | 2 |
| `setWatchedFolders` | 2 |

### DriveFileScanner.kt (Score: 4.00 - 🟢 LOW)
- **Total Complexity**: 4
- **Real Coverage**: 100.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `scanNewFiles` | 4 |

### SqlDelightAnalysisCacheRepository.kt (Score: 4.00 - 🟢 LOW)
- **Total Complexity**: 4
- **Real Coverage**: 100.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `getCached` | 2 |
| `putCache` | 1 |
| `evict` | 1 |

### DriveQueryBuilder.kt (Score: 3.41 - 🟢 LOW)
- **Total Complexity**: 3
- **Real Coverage**: 64.3%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `buildQueryForFolder` | 1 |
| `buildMimeTypeCriteria` | 1 |
| `getSupportedMimeTypes` | 1 |

### SourcesPanel.kt (Score: 3.33 - 🟢 LOW)
- **Total Complexity**: 3
- **Real Coverage**: 66.7%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `SourcesPanel` | 1 |

### GeminiBodyBuilder.kt (Score: 3.21 - 🟢 LOW)
- **Total Complexity**: 3
- **Real Coverage**: 71.4%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `buildJsonRequestBody` | 1 |
| `buildTextRequestBody` | 1 |

### ContributionValidator.kt (Score: 3.09 - 🟢 LOW)
- **Total Complexity**: 3
- **Real Coverage**: 78.1%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `validate` | 3 |

### SourceFragment.kt (Score: 3.00 - 🟢 LOW)
- **Total Complexity**: 3
- **Real Coverage**: 100.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `toJson` | 1 |

### GeminiErrorHandler.kt (Score: 3.00 - 🟢 LOW)
- **Total Complexity**: 3
- **Real Coverage**: 100.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `categorizeError` | 1 |
| `handleStructuralError` | 1 |
| `handleServerError` | 1 |

### FileDuplicateFilter.kt (Score: 3.00 - 🟢 LOW)
- **Total Complexity**: 3
- **Real Coverage**: 100.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `filterDuplicates` | 2 |
| `uriForFile` | 1 |

### LocalFileScanner.kt (Score: 3.00 - 🟢 LOW)
- **Total Complexity**: 3
- **Real Coverage**: 100.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `scanNewFiles` | 3 |

### EventConflictDetector.kt (Score: 3.00 - 🟢 LOW)
- **Total Complexity**: 3
- **Real Coverage**: 100.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `validateNoConflict` | 2 |
| `findConflict` | 1 |

### RoutineRepository.kt (Score: 3.00 - 🟢 LOW)
- **Total Complexity**: 3
- **Real Coverage**: 100.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `getRoutineEvents` | 2 |
| `saveRoutineEvents` | 1 |

### DriveFileFetcher.kt (Score: 2.09 - 🟢 LOW)
- **Total Complexity**: 2
- **Real Coverage**: 71.4%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `fetchFromFolders` | 1 |
| `deduplicateFiles` | 1 |

### PlatformFileSystem.kt (Score: 2.00 - 🟢 LOW)
- **Total Complexity**: 1
- **Real Coverage**: 0.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `getFileSystem` | 1 |

### RoutineItem.kt (Score: 2.00 - 🟢 LOW)
- **Total Complexity**: 1
- **Real Coverage**: 0.0%


### RoutineSetupScreen.kt (Score: 2.00 - 🟢 LOW)
- **Total Complexity**: 1
- **Real Coverage**: 0.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `RoutineSetupScreen` | 1 |

### UserPreferenceMemoryRepository.kt (Score: 2.00 - 🟢 LOW)
- **Total Complexity**: 2
- **Real Coverage**: 100.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `logOverride` | 1 |
| `getDerivedConstraints` | 1 |

### AiEventsService.kt (Score: 2.00 - 🟢 LOW)
- **Total Complexity**: 2
- **Real Coverage**: 100.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `addEvents` | 1 |
| `clearEvents` | 1 |

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

### DependencyContainer.kt (Score: 1.12 - 🟢 LOW)
- **Total Complexity**: 1
- **Real Coverage**: 50.5%


### GoogleConnectionState.kt (Score: 1.06 - 🟢 LOW)
- **Total Complexity**: 1
- **Real Coverage**: 61.5%


### CheckInDialog.kt (Score: 1.00 - 🟢 LOW)
- **Total Complexity**: 1
- **Real Coverage**: 94.4%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `CheckInDialog` | 1 |

### IcsCalendarSource.kt (Score: 1.00 - 🟢 LOW)
- **Total Complexity**: 1
- **Real Coverage**: 100.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `readSource` | 1 |

### CalendarErrorFormatter.kt (Score: 1.00 - 🟢 LOW)
- **Total Complexity**: 1
- **Real Coverage**: 100.0%


### ContentHasher.kt (Score: 1.00 - 🟢 LOW)
- **Total Complexity**: 1
- **Real Coverage**: 100.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `hash` | 1 |

### DecomposedTask.kt (Score: 1.00 - 🟢 LOW)
- **Total Complexity**: 1
- **Real Coverage**: 100.0%


### AppNavigationService.kt (Score: 1.00 - 🟢 LOW)
- **Total Complexity**: 1
- **Real Coverage**: 100.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `navigateTo` | 1 |

### SourceItem.kt (Score: 1.00 - 🟢 LOW)
- **Total Complexity**: 1
- **Real Coverage**: 100.0%


### CachedAnalysis.kt (Score: 1.00 - 🟢 LOW)
- **Total Complexity**: 1
- **Real Coverage**: 100.0%


### StudyPreferences.kt (Score: 1.00 - 🟢 LOW)
- **Total Complexity**: 1
- **Real Coverage**: 100.0%


### DocumentFrequencyCalculator.kt (Score: 1.00 - 🟢 LOW)
- **Total Complexity**: 1
- **Real Coverage**: 100.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `calculateDocumentFrequencies` | 1 |

### UserOverrideLog.kt (Score: 1.00 - 🟢 LOW)
- **Total Complexity**: 1
- **Real Coverage**: 100.0%


### CollisionResolver.kt (Score: 1.00 - 🟢 LOW)
- **Total Complexity**: 1
- **Real Coverage**: 100.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `resolve` | 1 |

### QuotaExhaustionDetector.kt (Score: 1.00 - 🟢 LOW)
- **Total Complexity**: 1
- **Real Coverage**: 100.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `isQuotaExhausted` | 1 |

