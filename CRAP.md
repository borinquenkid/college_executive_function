# Code Base CRAP Index Analysis

This document evaluates the codebase using the **CRAP (Change Risk Anti-Patterns) index**.
A higher CRAP index indicates higher risk when changing that file. A score **above 30** is considered highly risky.

## Heuristics Used
- **Complexity**: Approximated by counting control flow branches (`if`, `when`, `for`, `while`, `catch`, logical operators `&&`/`||`, safe calls `?.let`/`?.also`/`?.run`, collection operators `filter`/`map`/`forEach`/`any`/`all`) inside all methods + 1 base complexity per method.
- **Coverage**: Calculated exactly from Kover's XML test coverage report.
- **Formula**: $\text{CRAP} = \text{Complexity}^2 \times (1 - \text{Coverage})^3 + \text{Complexity}$

## Overall Summary
- **Total Files Analyzed**: 149
- **High-Risk Files (CRAP > 30)**: 12

### Top 15 High-Risk Files

| File | Complexity | Real Coverage | CRAP Index | Risk Status |
| :--- | :---: | :---: | :---: | :---: |
| ConflictResolver.kt | 14 | 0.0% | 210.00 | 🔴 HIGH |
| AcademicCalendarComponents.kt | 10 | 0.0% | 110.00 | 🔴 HIGH |
| ConflictResolutionUI.kt | 8 | 0.0% | 72.00 | 🔴 HIGH |
| ConcurrentFolderFetcher.kt | 7 | 0.0% | 56.00 | 🔴 HIGH |
| TermNormalizer.kt | 7 | 0.0% | 56.00 | 🔴 HIGH |
| EventRangeFilter.kt | 7 | 0.0% | 56.00 | 🔴 HIGH |
| PreferenceSerializer.kt | 7 | 0.0% | 56.00 | 🔴 HIGH |
| TFIDFScorer.kt | 7 | 0.0% | 56.00 | 🔴 HIGH |
| FragmentRanker.kt | 6 | 0.0% | 42.00 | 🔴 HIGH |
| SourceContextBuilder.kt | 6 | 0.0% | 42.00 | 🔴 HIGH |
| EventAgent.kt | 34 | 99.1% | 34.00 | 🔴 HIGH |
| GeminiRequestExecutor.kt | 25 | 79.6% | 30.30 | 🔴 HIGH |
| LocalFileFetcher.kt | 5 | 0.0% | 30.00 | 🟡 MEDIUM |
| CalendarIdResolver.kt | 5 | 0.0% | 30.00 | 🟡 MEDIUM |
| SourceItemView.kt | 5 | 0.0% | 30.00 | 🟡 MEDIUM |

---

## Detailed File Breakdown

### ConflictResolver.kt (Score: 210.00 - 🔴 HIGH)
- **Total Complexity**: 14
- **Real Coverage**: 0.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `resolveConflicts` | 6 |
| `rescheduleEarlier` | 6 |
| `findConflict` | 1 |
| `isMovable` | 1 |

### AcademicCalendarComponents.kt (Score: 110.00 - 🔴 HIGH)
- **Total Complexity**: 10
- **Real Coverage**: 0.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `GoogleLinkPrompt` | 1 |
| `AcademicCalendarHeader` | 1 |
| `EventListContent` | 1 |

### ConflictResolutionUI.kt (Score: 72.00 - 🔴 HIGH)
- **Total Complexity**: 8
- **Real Coverage**: 0.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `ConflictItem` | 2 |
| `ConflictResolutionDialog` | 1 |
| `ConflictResolutionBanner` | 1 |

### ConcurrentFolderFetcher.kt (Score: 56.00 - 🔴 HIGH)
- **Total Complexity**: 7
- **Real Coverage**: 0.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `fetchFromFolders` | 4 |
| `fetchFromFolder` | 3 |

### TermNormalizer.kt (Score: 56.00 - 🔴 HIGH)
- **Total Complexity**: 7
- **Real Coverage**: 0.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `extractQueryTerms` | 2 |
| `tokenizeFragment` | 2 |

### EventRangeFilter.kt (Score: 56.00 - 🔴 HIGH)
- **Total Complexity**: 7
- **Real Coverage**: 0.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `filterByDateRange` | 3 |
| `filterBySyncStatus` | 2 |
| `filterIncompleteBeforeDate` | 2 |

### PreferenceSerializer.kt (Score: 56.00 - 🔴 HIGH)
- **Total Complexity**: 7
- **Real Coverage**: 0.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `deserializeDirectories` | 4 |
| `serializeDirectories` | 3 |

### TFIDFScorer.kt (Score: 56.00 - 🔴 HIGH)
- **Total Complexity**: 7
- **Real Coverage**: 0.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `scoreDocuments` | 1 |
| `calculateTFIDF` | 1 |

### FragmentRanker.kt (Score: 42.00 - 🔴 HIGH)
- **Total Complexity**: 6
- **Real Coverage**: 0.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `rankFragments` | 1 |

### SourceContextBuilder.kt (Score: 42.00 - 🔴 HIGH)
- **Total Complexity**: 6
- **Real Coverage**: 0.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `formatFragments` | 2 |
| `buildContextBlocks` | 1 |

### EventAgent.kt (Score: 34.00 - 🔴 HIGH)
- **Total Complexity**: 34
- **Real Coverage**: 99.1%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `pushToCalendar` | 8 |
| `acceptDecomposition` | 3 |
| `rescheduleEvent` | 3 |
| `generateStudyPlan` | 2 |
| `loadIncompleteEvents` | 2 |
| `clearError` | 1 |
| `clearUnresolvedConflicts` | 1 |
| `updateStatus` | 1 |
| `Exception.isQuotaError` | 1 |
| `runAgentAction` | 1 |
| *... and 7 more* | |

### GeminiRequestExecutor.kt (Score: 30.30 - 🔴 HIGH)
- **Total Complexity**: 25
- **Real Coverage**: 79.6%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `clearRateLimitResetForTesting` | 1 |
| `postToModel` | 1 |
| `<T> executeWithRetry` | 1 |

### LocalFileFetcher.kt (Score: 30.00 - 🟡 MEDIUM)
- **Total Complexity**: 5
- **Real Coverage**: 0.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `fetchFromDirectories` | 5 |

### CalendarIdResolver.kt (Score: 30.00 - 🟡 MEDIUM)
- **Total Complexity**: 5
- **Real Coverage**: 0.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `getCEFCalendarId` | 3 |
| `resolveCalendarId` | 2 |

### SourceItemView.kt (Score: 30.00 - 🟡 MEDIUM)
- **Total Complexity**: 5
- **Real Coverage**: 0.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `SourceItemView` | 1 |

### SourceSelector.kt (Score: 30.00 - 🟡 MEDIUM)
- **Total Complexity**: 5
- **Real Coverage**: 0.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `autoSelectFirstFrom` | 2 |
| `clearIfRemovedFrom` | 2 |
| `selectSource` | 1 |

### GeminiRequestBuilder.kt (Score: 30.00 - 🟡 MEDIUM)
- **Total Complexity**: 5
- **Real Coverage**: 0.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `buildPostUrl` | 2 |
| `postToModel` | 2 |
| `hasApiKey` | 1 |

### GoogleCalendarSyncService.kt (Score: 29.13 - 🟡 MEDIUM)
- **Total Complexity**: 29
- **Real Coverage**: 94.6%

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

### GeminiAIService.kt (Score: 28.20 - 🟡 MEDIUM)
- **Total Complexity**: 26
- **Real Coverage**: 85.2%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `categorizeSource` | 4 |
| `analyzeDocument` | 3 |
| `generateCalendarEvents` | 2 |
| `generateCalendarEventsFromPrompt` | 2 |
| `generateChatResponse` | 2 |
| `extractSourceYears` | 1 |
| `filterToSourceYears` | 1 |
| `parseEventsJson` | 1 |
| `parseDecomposeTaskJson` | 1 |
| `parseCategorizeSourceJson` | 1 |
| *... and 6 more* | |

### GoogleCalendarPanel.kt (Score: 27.05 - 🟡 MEDIUM)
- **Total Complexity**: 23
- **Real Coverage**: 80.3%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `GoogleCalendarPanel` | 1 |
| `GoogleCalendarSelector` | 1 |
| `CreateCalendarDialog` | 1 |

### AcademicCalendar.kt (Score: 25.22 - 🟡 MEDIUM)
- **Total Complexity**: 22
- **Real Coverage**: 81.2%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `EventItemView` | 7 |
| `TaskDecompositionDialog` | 6 |
| `AcademicCalendar` | 1 |

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
| `getSettings` | 1 |
| `deleteEvent` | 1 |
| `hardDeleteEvent` | 1 |
| `getEventsInRange` | 1 |
| `getEventsBySyncStatus` | 1 |
| `getIncompleteEventsBefore` | 1 |

### CollisionDetector.kt (Score: 23.97 - 🟡 MEDIUM)
- **Total Complexity**: 17
- **Real Coverage**: 71.1%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `findNextDaySlot` | 1 |
| `findNextTimeSlot` | 1 |
| `findTimeSlotOnDay` | 1 |

### CriticActorAIService.kt (Score: 23.89 - 🟡 MEDIUM)
- **Total Complexity**: 23
- **Real Coverage**: 88.1%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `areEventListsDifferent` | 7 |
| `generateChatResponse` | 4 |
| `generateCalendarEvents` | 2 |
| `decomposeTask` | 2 |
| `generateStudyPlan` | 1 |
| `<T> runCritiqueLoop` | 1 |

### CommonSourceProviders.kt (Score: 23.58 - 🟡 MEDIUM)
- **Total Complexity**: 21
- **Real Coverage**: 82.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `SelectorUI` | 4 |
| `SelectorUI` | 4 |
| `SelectorUI` | 3 |
| `isAuthorized` | 1 |
| `isAuthorized` | 1 |
| `isAuthorized` | 1 |
| `DrivePickerDialog` | 1 |
| `IngestingProgressDialog` | 1 |

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

### GoogleRemoteCalendarRepository.kt (Score: 22.05 - 🟡 MEDIUM)
- **Total Complexity**: 22
- **Real Coverage**: 95.2%

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

### SyncNegotiator.kt (Score: 22.03 - 🟡 MEDIUM)
- **Total Complexity**: 22
- **Real Coverage**: 96.2%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `pushLocalChanges` | 7 |
| `buildNegotiation` | 2 |
| `findRemoteUpdatesAndConflicts` | 1 |
| `findDeletedLocalIds` | 1 |
| `buildProposedBaseCalendar` | 1 |

### CalendarPushResolver.kt (Score: 21.05 - 🟡 MEDIUM)
- **Total Complexity**: 21
- **Real Coverage**: 95.1%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `buildResolver` | 3 |
| `resolveAndPush` | 1 |
| `resolveAndReschedule` | 1 |
| `resolveConflicts` | 1 |
| `persistResolvedEvents` | 1 |

### CriticJsonCodec.kt (Score: 21.02 - 🟡 MEDIUM)
- **Total Complexity**: 21
- **Real Coverage**: 96.6%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `parseEventFromJson` | 7 |
| `serializeEvents` | 5 |
| `parseEvents` | 3 |
| `serializeTasks` | 2 |
| `parseTasks` | 2 |
| `parseTaskFromJson` | 2 |

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

### GeminiModelNegotiator.kt (Score: 20.24 - 🟡 MEDIUM)
- **Total Complexity**: 20
- **Real Coverage**: 91.6%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `getAvailableModels` | 5 |
| `evictFromCache` | 2 |
| `clearBlacklistForTesting` | 1 |
| `blacklistModel` | 1 |
| `negotiateBestModel` | 1 |

### Event.kt (Score: 20.10 - 🟡 MEDIUM)
- **Total Complexity**: 20
- **Real Coverage**: 93.8%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `overlaps` | 3 |
| `Event.withSyncStatus` | 2 |
| `Event.withCompletionStatus` | 2 |
| `overlaps` | 1 |
| `overlaps` | 1 |
| `Event.timeUntilDue` | 1 |
| `Event.studyProgress` | 1 |

### LocalDirectoryPreferences.kt (Score: 20.00 - 🟡 MEDIUM)
- **Total Complexity**: 4
- **Real Coverage**: 0.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `getWatchedDirectories` | 2 |
| `setWatchedDirectories` | 2 |

### RetryAfterParser.kt (Score: 20.00 - 🟡 MEDIUM)
- **Total Complexity**: 4
- **Real Coverage**: 0.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `extractRetryAfterMs` | 4 |

### DirectoryPreferencesManager.kt (Score: 20.00 - 🟡 MEDIUM)
- **Total Complexity**: 4
- **Real Coverage**: 0.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `getWatchedLocalDirectories` | 1 |
| `setWatchedLocalDirectories` | 1 |
| `getWatchedGDriveFolders` | 1 |
| `setWatchedGDriveFolders` | 1 |

### SourceDeleter.kt (Score: 20.00 - 🟡 MEDIUM)
- **Total Complexity**: 4
- **Real Coverage**: 0.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `deleteSource` | 4 |

### DriveDirectoryPreferences.kt (Score: 20.00 - 🟡 MEDIUM)
- **Total Complexity**: 4
- **Real Coverage**: 0.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `getWatchedFolders` | 2 |
| `setWatchedFolders` | 2 |

### ErrorCategorizer.kt (Score: 20.00 - 🟡 MEDIUM)
- **Total Complexity**: 4
- **Real Coverage**: 0.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `categorizeError` | 4 |

### DriveFileScanner.kt (Score: 20.00 - 🟡 MEDIUM)
- **Total Complexity**: 4
- **Real Coverage**: 0.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `scanNewFiles` | 4 |

### SourceLoader.kt (Score: 20.00 - 🟡 MEDIUM)
- **Total Complexity**: 4
- **Real Coverage**: 0.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `loadSources` | 4 |

### LocalFileFilter.kt (Score: 20.00 - 🟡 MEDIUM)
- **Total Complexity**: 4
- **Real Coverage**: 0.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `filterNewFiles` | 2 |
| `isSupportedFile` | 2 |

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

### EventGenerationService.kt (Score: 19.71 - 🟡 MEDIUM)
- **Total Complexity**: 19
- **Real Coverage**: 87.5%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `extractDeliverables` | 6 |
| `normalize` | 6 |
| `buildScheduleContext` | 4 |
| `generateStudyPlan` | 2 |
| `generateDeterministicId` | 1 |

### CalendarAgent.kt (Score: 18.24 - 🟡 MEDIUM)
- **Total Complexity**: 18
- **Real Coverage**: 90.9%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `updateEvent` | 4 |
| `deleteEvent` | 3 |
| `saveEvent` | 2 |
| `synchronize` | 2 |
| `isLiveSyncEnabled` | 2 |
| `getEvents` | 1 |
| `saveEventLocally` | 1 |
| `checkSyncProposals` | 1 |
| `applySyncNegotiation` | 1 |
| `getIncompleteEventsBefore` | 1 |

### StudioPanel.kt (Score: 18.08 - 🟡 MEDIUM)
- **Total Complexity**: 18
- **Real Coverage**: 93.8%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `StudioPanel` | 1 |

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

### GoogleDriveService.kt (Score: 17.00 - 🟡 MEDIUM)
- **Total Complexity**: 17
- **Real Coverage**: 98.5%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `<T> withToken` | 8 |
| `validateConnection` | 3 |
| `listFiles` | 3 |
| `getFileContent` | 3 |

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

### SchedulingAlgorithm.kt (Score: 16.11 - 🟡 MEDIUM)
- **Total Complexity**: 16
- **Real Coverage**: 92.5%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `resolve` | 1 |
| `findNewSlotAndResolve` | 1 |
| `shiftEvent` | 1 |
| `findNextAvailableSlot` | 1 |

### GeminiRetryService.kt (Score: 16.00 - 🟡 MEDIUM)
- **Total Complexity**: 16
- **Real Coverage**: 100.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `checkRateLimitWindow` | 2 |
| `clearRateLimitResetForTesting` | 1 |
| `resolveRetryDelay` | 1 |
| `activateRateLimitWindow` | 1 |
| `wait` | 1 |

### ChatPanel.kt (Score: 15.10 - 🟡 MEDIUM)
- **Total Complexity**: 14
- **Real Coverage**: 82.2%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `MessageView` | 6 |
| `ChatPanel` | 1 |

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

### StudyBlockShiftResolver.kt (Score: 14.42 - 🟢 LOW)
- **Total Complexity**: 14
- **Real Coverage**: 87.1%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `resolveShifts` | 1 |
| `findCollidingEvent` | 1 |
| `hasShifted` | 1 |

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

### IcsStringBuilder.kt (Score: 13.00 - 🟢 LOW)
- **Total Complexity**: 13
- **Real Coverage**: 98.5%

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
- **Real Coverage**: 98.7%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `AddRoutineItemDialog` | 11 |
| `ClickableField` | 1 |
| `TimePickerDialog` | 1 |

### AppController.kt (Score: 12.28 - 🟢 LOW)
- **Total Complexity**: 11
- **Real Coverage**: 78.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `loadSources` | 1 |
| `navigateTo` | 1 |
| `addEvents` | 1 |
| `clearEvents` | 1 |
| `addSource` | 1 |
| `launchInScope` | 1 |
| `deleteSource` | 1 |
| `selectSource` | 1 |
| `addChatMessage` | 1 |
| `setScreenListener` | 1 |
| *... and 1 more* | |

### CalendarSyncManager.kt (Score: 12.10 - 🟢 LOW)
- **Total Complexity**: 8
- **Real Coverage**: 60.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `refreshEvents` | 2 |
| `initiateSyncIfNeeded` | 1 |
| `applySyncProposal` | 1 |

### EventGenerator.kt (Score: 12.00 - 🟢 LOW)
- **Total Complexity**: 12
- **Real Coverage**: 100.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `expandEvents` | 1 |
| `expandTimeEvent` | 1 |
| `expandDayEvent` | 1 |

### GeminiErrorHandler.kt (Score: 12.00 - 🟢 LOW)
- **Total Complexity**: 3
- **Real Coverage**: 0.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `categorizeError` | 1 |
| `handleStructuralError` | 1 |
| `handleServerError` | 1 |

### SourceAdder.kt (Score: 12.00 - 🟢 LOW)
- **Total Complexity**: 3
- **Real Coverage**: 0.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `addSource` | 3 |

### DriveQueryBuilder.kt (Score: 12.00 - 🟢 LOW)
- **Total Complexity**: 3
- **Real Coverage**: 0.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `buildQueryForFolder` | 1 |
| `buildMimeTypeCriteria` | 1 |
| `getSupportedMimeTypes` | 1 |

### FileDuplicateFilter.kt (Score: 12.00 - 🟢 LOW)
- **Total Complexity**: 3
- **Real Coverage**: 0.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `filterDuplicates` | 2 |
| `uriForFile` | 1 |

### LocalFileScanner.kt (Score: 12.00 - 🟢 LOW)
- **Total Complexity**: 3
- **Real Coverage**: 0.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `scanNewFiles` | 3 |

### EventConflictDetector.kt (Score: 12.00 - 🟢 LOW)
- **Total Complexity**: 3
- **Real Coverage**: 0.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `validateNoConflict` | 2 |
| `findConflict` | 1 |

### SourceProcessingPipeline.kt (Score: 11.10 - 🟢 LOW)
- **Total Complexity**: 5
- **Real Coverage**: 37.5%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `processSource` | 5 |

### ContextAgent.kt (Score: 11.00 - 🟢 LOW)
- **Total Complexity**: 11
- **Real Coverage**: 96.8%

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

### AgentHarness.kt (Score: 10.53 - 🟢 LOW)
- **Total Complexity**: 10
- **Real Coverage**: 82.6%

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

### StudyPreferencesPanel.kt (Score: 10.39 - 🟢 LOW)
- **Total Complexity**: 10
- **Real Coverage**: 84.3%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `StudyPreferencesPanel` | 1 |

### AiPrompts.kt (Score: 10.14 - 🟢 LOW)
- **Total Complexity**: 10
- **Real Coverage**: 88.9%

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

### SqlDelightSourceRepository.kt (Score: 10.01 - 🟢 LOW)
- **Total Complexity**: 10
- **Real Coverage**: 96.2%

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

### SettingsPreferencesParser.kt (Score: 9.00 - 🟢 LOW)
- **Total Complexity**: 9
- **Real Coverage**: 100.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `parse` | 1 |

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

### ChatBuilder.kt (Score: 9.00 - 🟢 LOW)
- **Total Complexity**: 9
- **Real Coverage**: 100.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `getChatCritiquePrompt` | 2 |
| `getMultiSourceChatPrompt` | 1 |

### SyllabusAuditor.kt (Score: 8.06 - 🟢 LOW)
- **Total Complexity**: 8
- **Real Coverage**: 90.5%

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

### PollScheduler.kt (Score: 7.09 - 🟢 LOW)
- **Total Complexity**: 5
- **Real Coverage**: 56.3%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `shouldPoll` | 3 |
| `getLastPollTime` | 1 |
| `setLastPollTime` | 1 |

### SettingsScreen.kt (Score: 7.02 - 🟢 LOW)
- **Total Complexity**: 7
- **Real Coverage**: 93.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `SettingsScreen` | 1 |
| `savePreferences` | 1 |
| `formatCalendarError` | 1 |

### SourceManager.kt (Score: 6.51 - 🟢 LOW)
- **Total Complexity**: 5
- **Real Coverage**: 60.8%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `deleteSource` | 2 |
| `loadSources` | 1 |
| `addSource` | 1 |
| `selectSource` | 1 |

### AppContent.kt (Score: 6.10 - 🟢 LOW)
- **Total Complexity**: 6
- **Real Coverage**: 85.7%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `AppContent` | 6 |

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

### EventBuilder.kt (Score: 6.00 - 🟢 LOW)
- **Total Complexity**: 6
- **Real Coverage**: 100.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `getSourceEventExtractionPrompt` | 4 |
| `getEventCritiquePrompt` | 1 |

### DriveFileFetcher.kt (Score: 6.00 - 🟢 LOW)
- **Total Complexity**: 2
- **Real Coverage**: 0.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `fetchFromFolders` | 1 |
| `deduplicateFiles` | 1 |

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

### SourceProcessor.kt (Score: 5.01 - 🟢 LOW)
- **Total Complexity**: 5
- **Real Coverage**: 91.7%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `split` | 3 |
| `process` | 2 |

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
- **Real Coverage**: 97.6%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `UniversalHomeLayout` | 5 |

### NormalizationService.kt (Score: 5.00 - 🟢 LOW)
- **Total Complexity**: 5
- **Real Coverage**: 100.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `extract` | 5 |

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

### SyncProposal.kt (Score: 4.13 - 🟢 LOW)
- **Total Complexity**: 4
- **Real Coverage**: 80.0%


### HarnessSourceProcessor.kt (Score: 4.13 - 🟢 LOW)
- **Total Complexity**: 3
- **Real Coverage**: 50.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `processSource` | 1 |
| `processLocalFiles` | 1 |
| `processDriveFiles` | 1 |

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

### GroundingGuardAIService.kt (Score: 4.00 - 🟢 LOW)
- **Total Complexity**: 4
- **Real Coverage**: 100.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `generateCalendarEvents` | 1 |
| `generateStudyPlan` | 1 |
| `groundToSource` | 1 |

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

### SourcesPanel.kt (Score: 3.50 - 🟢 LOW)
- **Total Complexity**: 3
- **Real Coverage**: 61.9%

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

### UserPreferenceMemoryRepository.kt (Score: 2.00 - 🟢 LOW)
- **Total Complexity**: 2
- **Real Coverage**: 100.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `logOverride` | 1 |
| `getDerivedConstraints` | 1 |

### DocumentFrequencyCalculator.kt (Score: 2.00 - 🟢 LOW)
- **Total Complexity**: 1
- **Real Coverage**: 0.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `calculateDocumentFrequencies` | 1 |

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

### QuotaExhaustionDetector.kt (Score: 2.00 - 🟢 LOW)
- **Total Complexity**: 1
- **Real Coverage**: 0.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `isQuotaExhausted` | 1 |

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


### DependencyContainer.kt (Score: 1.00 - 🟢 LOW)
- **Total Complexity**: 1
- **Real Coverage**: 83.8%


### CheckInDialog.kt (Score: 1.00 - 🟢 LOW)
- **Total Complexity**: 1
- **Real Coverage**: 94.0%

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


### StudyPreferences.kt (Score: 1.00 - 🟢 LOW)
- **Total Complexity**: 1
- **Real Coverage**: 100.0%


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

