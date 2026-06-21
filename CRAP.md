# Code Base CRAP Index Analysis

This document evaluates the codebase using the **CRAP (Change Risk Anti-Patterns) index**.
A higher CRAP index indicates higher risk when changing that file. A score **above 30** is considered highly risky.

## Heuristics Used
- **Complexity**: Approximated by counting control flow branches (`if`, `when`, `for`, `while`, `catch`, logical operators `&&`/`||`, safe calls `?.let`/`?.also`/`?.run`, collection operators `filter`/`map`/`forEach`/`any`/`all`) inside all methods + 1 base complexity per method.
- **Coverage**: Calculated exactly from Kover's XML test coverage report.
- **Formula**: $\text{CRAP} = \text{Complexity}^2 \times (1 - \text{Coverage})^3 + \text{Complexity}$

## Overall Summary
- **Total Files Analyzed**: 197
- **High-Risk Files (CRAP > 30)**: 5

### Top 15 High-Risk Files

| File | Complexity | Real Coverage | CRAP Index | Risk Status |
| :--- | :---: | :---: | :---: | :---: |
| RemoteFirstWriter.kt | 8 | 0.0% | 72.00 | 🔴 HIGH |
| ResolvedEventWriter.kt | 7 | 0.0% | 56.00 | 🔴 HIGH |
| GoogleRemoteCalendarRepository.kt | 23 | 74.0% | 32.30 | 🔴 HIGH |
| SchedulingAlgorithm.kt | 16 | 60.9% | 31.34 | 🔴 HIGH |
| GoogleCalendarSyncService.kt | 30 | 97.1% | 30.02 | 🔴 HIGH |
| IngestingProgressDialog.kt | 5 | 0.0% | 30.00 | 🟡 MEDIUM |
| EventListContent.kt | 5 | 0.0% | 30.00 | 🟡 MEDIUM |
| SourceItemView.kt | 5 | 0.0% | 30.00 | 🟡 MEDIUM |
| EventItemView.kt | 5 | 0.0% | 30.00 | 🟡 MEDIUM |
| StudyBlockOverrideLogger.kt | 5 | 0.0% | 30.00 | 🟡 MEDIUM |
| GeminiAIService.kt | 29 | 92.1% | 29.42 | 🟡 MEDIUM |
| EventAgent.kt | 29 | 98.0% | 29.01 | 🟡 MEDIUM |
| EventDeduplicator.kt | 29 | 100.0% | 29.00 | 🟡 MEDIUM |
| GeminiRetryService.kt | 27 | 88.5% | 28.11 | 🟡 MEDIUM |
| StudyPlanBuilder.kt | 28 | 100.0% | 28.00 | 🟡 MEDIUM |

---

## Detailed File Breakdown

### RemoteFirstWriter.kt (Score: 72.00 - 🔴 HIGH)
- **Total Complexity**: 8
- **Real Coverage**: 0.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `save` | 4 |
| `update` | 4 |

### ResolvedEventWriter.kt (Score: 56.00 - 🔴 HIGH)
- **Total Complexity**: 7
- **Real Coverage**: 0.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `persist` | 1 |

### GoogleRemoteCalendarRepository.kt (Score: 32.30 - 🔴 HIGH)
- **Total Complexity**: 23
- **Real Coverage**: 74.0%

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
| `clearLocalCalendar` | 1 |
| *... and 3 more* | |

### SchedulingAlgorithm.kt (Score: 31.34 - 🔴 HIGH)
- **Total Complexity**: 16
- **Real Coverage**: 60.9%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `resolve` | 1 |
| `findNewSlotAndResolve` | 1 |
| `shiftEvent` | 1 |
| `findNextAvailableSlot` | 1 |

### GoogleCalendarSyncService.kt (Score: 30.02 - 🔴 HIGH)
- **Total Complexity**: 30
- **Real Coverage**: 97.1%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `getEvents` | 11 |
| `<T> withToken` | 6 |
| `listCalendars` | 3 |
| `ensureSuccess` | 2 |
| `syncEvent` | 2 |
| `createCalendar` | 1 |
| `deleteEvent` | 1 |
| `fetchEventsPage` | 1 |

### IngestingProgressDialog.kt (Score: 30.00 - 🟡 MEDIUM)
- **Total Complexity**: 5
- **Real Coverage**: 0.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `IngestingProgressDialog` | 5 |

### EventListContent.kt (Score: 30.00 - 🟡 MEDIUM)
- **Total Complexity**: 5
- **Real Coverage**: 0.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `EventListContent` | 1 |

### SourceItemView.kt (Score: 30.00 - 🟡 MEDIUM)
- **Total Complexity**: 5
- **Real Coverage**: 0.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `SourceItemView` | 1 |

### EventItemView.kt (Score: 30.00 - 🟡 MEDIUM)
- **Total Complexity**: 5
- **Real Coverage**: 0.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `EventItemView` | 1 |

### StudyBlockOverrideLogger.kt (Score: 30.00 - 🟡 MEDIUM)
- **Total Complexity**: 5
- **Real Coverage**: 0.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `checkMove` | 3 |
| `checkDelete` | 2 |

### GeminiAIService.kt (Score: 29.42 - 🟡 MEDIUM)
- **Total Complexity**: 29
- **Real Coverage**: 92.1%

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
| *... and 6 more* | |

### EventAgent.kt (Score: 29.01 - 🟡 MEDIUM)
- **Total Complexity**: 29
- **Real Coverage**: 98.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `loadPersistedWarnings` | 2 |
| `generateStudyPlan` | 2 |
| `loadIncompleteEvents` | 2 |
| `estimatedRemainingSeconds` | 1 |
| `clearError` | 1 |
| `reportError` | 1 |
| `setGeneratedEvents` | 1 |
| `clearUnresolvedConflicts` | 1 |
| `updateStatus` | 1 |
| `Exception.isQuotaError` | 1 |
| *... and 12 more* | |

### EventDeduplicator.kt (Score: 29.00 - 🟡 MEDIUM)
- **Total Complexity**: 29
- **Real Coverage**: 100.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `dedupSubmissionPairs` | 10 |
| `dedupByCommonTitlePrefix` | 9 |
| `dedup` | 3 |
| `commonPrefixLength` | 3 |
| `dateOf` | 2 |
| `canonicalTitle` | 1 |
| `submissionCanonical` | 1 |

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

### StudyPlanBuilder.kt (Score: 28.00 - 🟡 MEDIUM)
- **Total Complexity**: 28
- **Real Coverage**: 100.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `formatHour` | 3 |
| `getSyllabusStudyPlanPrompt` | 1 |
| `getStudyPlanCritiquePrompt` | 1 |
| `getTaskDecompositionPrompt` | 1 |
| `getDecompositionCritiquePrompt` | 1 |

### GeminiRequestExecutor.kt (Score: 27.70 - 🟡 MEDIUM)
- **Total Complexity**: 27
- **Real Coverage**: 90.2%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `clearRateLimitResetForTesting` | 1 |
| `postToModel` | 1 |
| `<T> executeWithRetry` | 1 |
| `<T> executeWithRetryInternal` | 1 |

### SqlDelightLocalCalendarRepository.kt (Score: 26.87 - 🟡 MEDIUM)
- **Total Complexity**: 24
- **Real Coverage**: 82.9%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `updateEvent` | 6 |
| `mapEntityToEvent` | 4 |
| `getAllEvents` | 3 |
| `getSettings` | 1 |
| `saveEvent` | 1 |
| `deleteEvent` | 1 |
| `hardDeleteEvent` | 1 |
| `clearLocalCalendar` | 1 |
| `getEventsInRange` | 1 |
| `getEventsBySyncStatus` | 1 |
| *... and 1 more* | |

### GeminiResponseParser.kt (Score: 25.01 - 🟡 MEDIUM)
- **Total Complexity**: 25
- **Real Coverage**: 98.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `toEvent` | 6 |
| `filterToSourceYears` | 4 |
| `parseCategorizeSourceJson` | 4 |
| `extractSourceYears` | 2 |
| `parseEventsJson` | 2 |
| `parseDecomposeTaskJson` | 2 |
| `extractJsonArray` | 2 |
| `parseClockTime` | 1 |
| `stripCodeFences` | 1 |

### AppController.kt (Score: 24.64 - 🟡 MEDIUM)
- **Total Complexity**: 19
- **Real Coverage**: 75.0%

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
| `resetForDemo` | 1 |
| `addSource` | 1 |
| *... and 7 more* | |

### CriticActorAIService.kt (Score: 24.11 - 🟡 MEDIUM)
- **Total Complexity**: 23
- **Real Coverage**: 87.2%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `areEventListsDifferent` | 7 |
| `generateChatResponse` | 4 |
| `generateCalendarEvents` | 2 |
| `decomposeTask` | 2 |
| `generateStudyPlan` | 1 |
| `<T> runCritiqueLoop` | 1 |

### CriticJsonCodec.kt (Score: 23.13 - 🟡 MEDIUM)
- **Total Complexity**: 23
- **Real Coverage**: 93.8%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `parseEventFromJson` | 9 |
| `serializeEvents` | 5 |
| `parseEvents` | 3 |
| `serializeTasks` | 2 |
| `parseTasks` | 2 |
| `parseTaskFromJson` | 2 |

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
| `pushLocalChanges` | 6 |
| `buildNegotiation` | 3 |
| `findRemoteUpdatesAndConflicts` | 1 |
| `findDeletedLocalIds` | 1 |
| `buildProposedBaseCalendar` | 1 |

### ConflictResolver.kt (Score: 22.00 - 🟡 MEDIUM)
- **Total Complexity**: 22
- **Real Coverage**: 100.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `resolveConflicts` | 8 |
| `rescheduleEarlier` | 6 |
| `rescheduleForward` | 6 |
| `findConflict` | 1 |
| `isMovable` | 1 |

### EventGenerationService.kt (Score: 21.46 - 🟡 MEDIUM)
- **Total Complexity**: 21
- **Real Coverage**: 89.9%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `normalize` | 5 |
| `buildScheduleContext` | 4 |
| `extractDeliverables` | 1 |
| `generateStudyPlan` | 1 |
| `generateDeterministicId` | 1 |

### EventBuilder.kt (Score: 21.11 - 🟡 MEDIUM)
- **Total Complexity**: 21
- **Real Coverage**: 93.8%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `getSourceEventExtractionPrompt` | 14 |
| `getEventCritiquePrompt` | 4 |

### EventPresenter.kt (Score: 21.00 - 🟡 MEDIUM)
- **Total Complexity**: 21
- **Real Coverage**: 100.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `getEventBorderColor` | 9 |
| `getDeadlineChipText` | 4 |
| `getCategoryLabel` | 3 |
| `getDeadlineStatus` | 2 |
| `getEventTimeText` | 2 |
| `showDeadlineInfo` | 1 |

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

### LocalOnlyRetrier.kt (Score: 20.00 - 🟡 MEDIUM)
- **Total Complexity**: 4
- **Real Coverage**: 0.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `retry` | 4 |

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

### ConditionalDialogs.kt (Score: 20.00 - 🟡 MEDIUM)
- **Total Complexity**: 4
- **Real Coverage**: 0.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `DecompositionDialogFor` | 1 |
| `SyncNegotiationDialogFor` | 1 |

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

### CommonSourceProviders.kt (Score: 17.65 - 🟡 MEDIUM)
- **Total Complexity**: 17
- **Real Coverage**: 86.9%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `SelectorUI` | 7 |
| `SelectorUI` | 4 |
| `SelectorUI` | 3 |
| `isAuthorized` | 1 |
| `isAuthorized` | 1 |
| `isAuthorized` | 1 |

### CalendarPushResolver.kt (Score: 16.58 - 🟡 MEDIUM)
- **Total Complexity**: 14
- **Real Coverage**: 76.4%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `buildResolver` | 3 |
| `resolveAndPush` | 1 |
| `resolveAndReschedule` | 1 |

### IngestionAgent.kt (Score: 16.00 - 🟡 MEDIUM)
- **Total Complexity**: 16
- **Real Coverage**: 100.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `addLocalFile` | 4 |
| `addUrl` | 4 |
| `addDriveFile` | 4 |
| `resolveCategory` | 1 |
| `persistSource` | 1 |

### SyncNegotiationApplier.kt (Score: 16.00 - 🟡 MEDIUM)
- **Total Complexity**: 16
- **Real Coverage**: 100.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `isLiveSyncEnabled` | 2 |
| `apply` | 1 |
| `applyDeletedLocalEvents` | 1 |
| `applyRemoteEventsToLocal` | 1 |
| `applyShiftedStudyBlocks` | 1 |

### AppContent.kt (Score: 15.69 - 🟡 MEDIUM)
- **Total Complexity**: 10
- **Real Coverage**: 61.5%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `AppContent` | 10 |

### DecompositionOrchestrator.kt (Score: 15.05 - 🟡 MEDIUM)
- **Total Complexity**: 14
- **Real Coverage**: 82.5%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `isComplex` | 3 |
| `decompose` | 1 |
| `calculateSubDueDate` | 1 |
| `calculateDaysBeforeDue` | 1 |

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

### GoogleCalendarPanel.kt (Score: 14.71 - 🟢 LOW)
- **Total Complexity**: 14
- **Real Coverage**: 84.6%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `GoogleCalendarPanel` | 1 |

### CalendarAgent.kt (Score: 14.43 - 🟢 LOW)
- **Total Complexity**: 12
- **Real Coverage**: 74.4%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `getEvents` | 1 |
| `saveEvent` | 1 |
| `updateEvent` | 1 |
| `saveEventLocally` | 1 |
| `hardDeleteLocalOnly` | 1 |
| `retryLocalOnly` | 1 |
| `deleteEvent` | 1 |
| `resetCalendar` | 1 |
| `checkSyncProposals` | 1 |
| `applySyncNegotiation` | 1 |
| *... and 2 more* | |

### StudyBlockShiftResolver.kt (Score: 14.12 - 🟢 LOW)
- **Total Complexity**: 14
- **Real Coverage**: 91.4%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `resolveShifts` | 1 |
| `findCollidingEvent` | 1 |
| `hasShifted` | 1 |

### CalendarInterfaces.kt (Score: 14.09 - 🟢 LOW)
- **Total Complexity**: 14
- **Real Coverage**: 92.3%

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
| *... and 4 more* | |

### WeekAnchorExtractor.kt (Score: 14.00 - 🟢 LOW)
- **Total Complexity**: 14
- **Real Coverage**: 100.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `needsAnchors` | 5 |
| `inject` | 4 |
| `collectAnchors` | 4 |

### CalendarPusher.kt (Score: 13.00 - 🟢 LOW)
- **Total Complexity**: 13
- **Real Coverage**: 98.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `push` | 13 |

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

### SourceAdder.kt (Score: 13.00 - 🟢 LOW)
- **Total Complexity**: 13
- **Real Coverage**: 100.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `processSourceWithCache` | 6 |
| `addSource` | 3 |
| `handleFailure` | 3 |
| `isCacheStale` | 1 |

### Tracer.kt (Score: 12.89 - 🟢 LOW)
- **Total Complexity**: 9
- **Real Coverage**: 63.6%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `<T> span` | 1 |
| `shutdown` | 1 |
| `setAttribute` | 1 |
| `recordException` | 1 |
| `<T> span` | 1 |
| `event` | 1 |
| `setAttribute` | 1 |
| `recordException` | 1 |
| `createTracer` | 1 |

### SourceLoader.kt (Score: 12.49 - 🟢 LOW)
- **Total Complexity**: 4
- **Real Coverage**: 19.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `loadSources` | 4 |

### ChatPanel.kt (Score: 12.41 - 🟢 LOW)
- **Total Complexity**: 12
- **Real Coverage**: 85.8%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `MessageView` | 6 |
| `ChatPanel` | 1 |

### AcademicCalendarHeader.kt (Score: 12.00 - 🟢 LOW)
- **Total Complexity**: 3
- **Real Coverage**: 0.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `AcademicCalendarHeader` | 1 |

### EventGenerator.kt (Score: 12.00 - 🟢 LOW)
- **Total Complexity**: 12
- **Real Coverage**: 100.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `expandEvents` | 1 |
| `expandTimeEvent` | 1 |
| `expandDayEvent` | 1 |

### EventDeleter.kt (Score: 12.00 - 🟢 LOW)
- **Total Complexity**: 3
- **Real Coverage**: 0.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `delete` | 3 |

### ConflictResolutionUI.kt (Score: 12.00 - 🟢 LOW)
- **Total Complexity**: 3
- **Real Coverage**: 0.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `ConflictItem` | 2 |
| `ConflictResolutionDialog` | 1 |

### AcademicCalendar.kt (Score: 11.53 - 🟢 LOW)
- **Total Complexity**: 4
- **Real Coverage**: 22.2%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `AcademicCalendar` | 1 |

### SourceIngestionHandler.kt (Score: 11.11 - 🟢 LOW)
- **Total Complexity**: 11
- **Real Coverage**: 90.3%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `ingestLocalFile` | 1 |
| `ingestLocalFiles` | 1 |
| `ingestUrl` | 1 |
| `ingestDriveFile` | 1 |
| `buildIngestibleFilesQuery` | 1 |

### ContextAgent.kt (Score: 11.01 - 🟢 LOW)
- **Total Complexity**: 11
- **Real Coverage**: 95.1%

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

### SemesterResolver.kt (Score: 11.00 - 🟢 LOW)
- **Total Complexity**: 11
- **Real Coverage**: 100.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `getExpandedRange` | 7 |
| `getSemesterRange` | 2 |
| `filterToActiveSemester` | 2 |

### ChatBuilder.kt (Score: 11.00 - 🟢 LOW)
- **Total Complexity**: 11
- **Real Coverage**: 100.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `getChatCritiquePrompt` | 2 |
| `getMultiSourceChatPrompt` | 1 |

### SourceManager.kt (Score: 10.80 - 🟢 LOW)
- **Total Complexity**: 10
- **Real Coverage**: 80.0%

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

### GroundingGuardAIService.kt (Score: 10.00 - 🟢 LOW)
- **Total Complexity**: 10
- **Real Coverage**: 100.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `generateCalendarEvents` | 1 |
| `generateStudyPlan` | 1 |
| `generateChatResponse` | 1 |
| `groundToSource` | 1 |

### CalendarSyncManager.kt (Score: 10.00 - 🟢 LOW)
- **Total Complexity**: 10
- **Real Coverage**: 100.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `refreshEvents` | 2 |
| `initiateSyncIfNeeded` | 1 |
| `applySyncProposal` | 1 |

### ModelManager.kt (Score: 10.00 - 🟢 LOW)
- **Total Complexity**: 10
- **Real Coverage**: 100.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `downloadModel` | 7 |
| `isModelDownloaded` | 2 |
| `getModelFile` | 1 |

### SettingsScreen.kt (Score: 9.39 - 🟢 LOW)
- **Total Complexity**: 9
- **Real Coverage**: 83.1%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `SettingsScreen` | 1 |
| `savePreferences` | 1 |
| `formatCalendarError` | 1 |

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

### StudioPanel.kt (Score: 9.01 - 🟢 LOW)
- **Total Complexity**: 9
- **Real Coverage**: 95.8%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `StudioPanel` | 1 |

### NormalizationService.kt (Score: 9.00 - 🟢 LOW)
- **Total Complexity**: 9
- **Real Coverage**: 96.4%

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

### AutoDecomposer.kt (Score: 9.00 - 🟢 LOW)
- **Total Complexity**: 9
- **Real Coverage**: 100.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `run` | 9 |

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

### SyllabusAuditor.kt (Score: 8.01 - 🟢 LOW)
- **Total Complexity**: 8
- **Real Coverage**: 95.5%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `audit` | 8 |

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

### RemoteFirstEventPersistence.kt (Score: 7.00 - 🟢 LOW)
- **Total Complexity**: 7
- **Real Coverage**: 100.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `reset` | 3 |
| `save` | 1 |
| `update` | 1 |
| `delete` | 1 |
| `retryLocalOnly` | 1 |

### DrivePickerDialog.kt (Score: 6.12 - 🟢 LOW)
- **Total Complexity**: 6
- **Real Coverage**: 85.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `DrivePickerDialog` | 1 |

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

### TaskDecompositionDialog.kt (Score: 6.01 - 🟢 LOW)
- **Total Complexity**: 6
- **Real Coverage**: 94.7%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `TaskDecompositionDialog` | 6 |

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

### GoogleLinkPrompt.kt (Score: 6.00 - 🟢 LOW)
- **Total Complexity**: 2
- **Real Coverage**: 0.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `GoogleLinkPrompt` | 1 |

### TaskDecompositionService.kt (Score: 6.00 - 🟢 LOW)
- **Total Complexity**: 6
- **Real Coverage**: 100.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `decompose` | 1 |
| `applyDecomposition` | 1 |
| `stepId` | 1 |

### CalendarEventGrouper.kt (Score: 6.00 - 🟢 LOW)
- **Total Complexity**: 6
- **Real Coverage**: 100.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `groupEventsByDate` | 2 |
| `isDecomposable` | 1 |
| `filterEventsByDateRange` | 1 |

### GeminiRateLimitPolicy.kt (Score: 6.00 - 🟢 LOW)
- **Total Complexity**: 6
- **Real Coverage**: 100.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `decide` | 1 |

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

### GoogleCalendarSelector.kt (Score: 5.17 - 🟢 LOW)
- **Total Complexity**: 4
- **Real Coverage**: 58.2%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `GoogleCalendarSelector` | 1 |

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

### SourceFragmentBatcher.kt (Score: 5.00 - 🟢 LOW)
- **Total Complexity**: 5
- **Real Coverage**: 100.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `batch` | 1 |

### SourceProcessingPipeline.kt (Score: 5.00 - 🟢 LOW)
- **Total Complexity**: 5
- **Real Coverage**: 100.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `processSource` | 5 |

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

### DeadlineSummary.kt (Score: 5.00 - 🟢 LOW)
- **Total Complexity**: 5
- **Real Coverage**: 100.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `from` | 5 |

### SourceSelector.kt (Score: 5.00 - 🟢 LOW)
- **Total Complexity**: 5
- **Real Coverage**: 100.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `autoSelectFirstFrom` | 2 |
| `clearIfRemovedFrom` | 2 |
| `selectSource` | 1 |

### SyncGate.kt (Score: 5.00 - 🟢 LOW)
- **Total Complexity**: 5
- **Real Coverage**: 100.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `isLiveSyncEnabled` | 2 |
| `isGoogleLinked` | 2 |
| `isLive` | 1 |

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

### CreateCalendarDialog.kt (Score: 4.09 - 🟢 LOW)
- **Total Complexity**: 4
- **Real Coverage**: 82.5%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `CreateCalendarDialog` | 1 |

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

### ChatInputPresenter.kt (Score: 4.00 - 🟢 LOW)
- **Total Complexity**: 4
- **Real Coverage**: 100.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `placeholder` | 2 |
| `chipLabel` | 2 |

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

### PushButtonState.kt (Score: 4.00 - 🟢 LOW)
- **Total Complexity**: 4
- **Real Coverage**: 100.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `variant` | 2 |
| `label` | 2 |

### DriveFileScanner.kt (Score: 4.00 - 🟢 LOW)
- **Total Complexity**: 4
- **Real Coverage**: 100.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `scanNewFiles` | 4 |

### DeliverableExtractor.kt (Score: 4.00 - 🟢 LOW)
- **Total Complexity**: 4
- **Real Coverage**: 100.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `run` | 4 |

### SqlDelightAnalysisCacheRepository.kt (Score: 4.00 - 🟢 LOW)
- **Total Complexity**: 4
- **Real Coverage**: 100.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `getCached` | 2 |
| `putCache` | 1 |
| `evict` | 1 |

### StudioStatusFormatter.kt (Score: 4.00 - 🟢 LOW)
- **Total Complexity**: 4
- **Real Coverage**: 100.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `format` | 1 |

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

### EventRescheduler.kt (Score: 3.00 - 🟢 LOW)
- **Total Complexity**: 3
- **Real Coverage**: 100.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `run` | 3 |

### ConflictResolutionPresenter.kt (Score: 3.00 - 🟢 LOW)
- **Total Complexity**: 3
- **Real Coverage**: 100.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `bodyText` | 1 |
| `instructionsText` | 1 |
| `hasReason` | 1 |

### DecompositionAcceptor.kt (Score: 3.00 - 🟢 LOW)
- **Total Complexity**: 3
- **Real Coverage**: 100.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `run` | 3 |

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

### EventTimeRepairer.kt (Score: 3.00 - 🟢 LOW)
- **Total Complexity**: 3
- **Real Coverage**: 100.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `repair` | 3 |

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

### AgentAction.kt (Score: 2.00 - 🟢 LOW)
- **Total Complexity**: 1
- **Real Coverage**: 0.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `run` | 1 |

### AcademicCalendarSyncHelper.kt (Score: 2.00 - 🟢 LOW)
- **Total Complexity**: 2
- **Real Coverage**: 100.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `performCalendarSync` | 1 |

### RetryCountdown.kt (Score: 2.00 - 🟢 LOW)
- **Total Complexity**: 2
- **Real Coverage**: 100.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `secondsRemaining` | 2 |

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

### WarningAggregator.kt (Score: 2.00 - 🟢 LOW)
- **Total Complexity**: 2
- **Real Coverage**: 100.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `collect` | 1 |

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

### CalendarDisplayName.kt (Score: 2.00 - 🟢 LOW)
- **Total Complexity**: 2
- **Real Coverage**: 100.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `resolve` | 2 |

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

### AppEnv.kt (Score: 1.18 - 🟢 LOW)
- **Total Complexity**: 1
- **Real Coverage**: 43.8%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `get` | 1 |

### DependencyContainer.kt (Score: 1.14 - 🟢 LOW)
- **Total Complexity**: 1
- **Real Coverage**: 48.6%


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

