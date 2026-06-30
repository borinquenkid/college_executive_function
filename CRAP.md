# Code Base CRAP Index Analysis

This document evaluates the codebase using the **CRAP (Change Risk Anti-Patterns) index**.
A higher CRAP index indicates higher risk when changing that file. A score **above 30** is considered highly risky.

## Heuristics Used
- **Complexity**: Approximated by counting control flow branches (`if`, `when`, `for`, `while`, `catch`, logical operators `&&`/`||`, safe calls `?.let`/`?.also`/`?.run`, collection operators `filter`/`map`/`forEach`/`any`/`all`) inside all methods + 1 base complexity per method.
- **Coverage**: Calculated exactly from Kover's XML test coverage report.
- **Formula**: $\text{CRAP} = \text{Complexity}^2 \times (1 - \text{Coverage})^3 + \text{Complexity}$

## Overall Summary
- **Total Files Analyzed**: 174
- **High-Risk Files (CRAP > 30)**: 87

### Top 15 High-Risk Files

| File | Complexity | Real Coverage | CRAP Index | Risk Status |
| :--- | :---: | :---: | :---: | :---: |
| EventAgent.kt | 33 | 0.0% | 1122.00 | 🔴 HIGH |
| HttpOtelTracer.kt | 32 | 0.0% | 1056.00 | 🔴 HIGH |
| EventDeduplicator.kt | 29 | 0.0% | 870.00 | 🔴 HIGH |
| GeminiAIService.kt | 29 | 0.0% | 870.00 | 🔴 HIGH |
| StudyPlanBuilder.kt | 28 | 0.0% | 812.00 | 🔴 HIGH |
| GeminiRetryService.kt | 28 | 0.0% | 812.00 | 🔴 HIGH |
| GoogleCalendarSyncService.kt | 27 | 0.0% | 756.00 | 🔴 HIGH |
| GeminiRequestExecutor.kt | 27 | 0.0% | 756.00 | 🔴 HIGH |
| SqlDelightLocalCalendarRepository.kt | 26 | 0.0% | 702.00 | 🔴 HIGH |
| GeminiResponseParser.kt | 25 | 0.0% | 650.00 | 🔴 HIGH |
| CriticJsonCodec.kt | 23 | 0.0% | 552.00 | 🔴 HIGH |
| CriticActorAIService.kt | 23 | 0.0% | 552.00 | 🔴 HIGH |
| EventBuilder.kt | 22 | 0.0% | 506.00 | 🔴 HIGH |
| GoogleRemoteCalendarRepository.kt | 22 | 0.0% | 506.00 | 🔴 HIGH |
| ConflictResolver.kt | 22 | 0.0% | 506.00 | 🔴 HIGH |

---

## Detailed File Breakdown

### EventAgent.kt (Score: 1122.00 - 🔴 HIGH)
- **Total Complexity**: 33
- **Real Coverage**: 0.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `friendlyError` | 4 |
| `loadPersistedWarnings` | 2 |
| `generateStudyPlan` | 2 |
| `loadIncompleteEvents` | 2 |
| `estimatedRemainingSeconds` | 1 |
| `clearError` | 1 |
| `reportError` | 1 |
| `setGeneratedEvents` | 1 |
| `clearUnresolvedConflicts` | 1 |
| `updateStatus` | 1 |
| *... and 13 more* | |

### HttpOtelTracer.kt (Score: 1056.00 - 🔴 HIGH)
- **Total Complexity**: 32
- **Real Coverage**: 0.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `base64` | 6 |
| `create` | 4 |
| `recordException` | 3 |
| `<T> span` | 1 |
| `event` | 1 |
| `shutdown` | 1 |
| `export` | 1 |
| `buildJson` | 1 |
| `setAttribute` | 1 |
| `setAttribute` | 1 |
| *... and 4 more* | |

### EventDeduplicator.kt (Score: 870.00 - 🔴 HIGH)
- **Total Complexity**: 29
- **Real Coverage**: 0.0%

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

### GeminiAIService.kt (Score: 870.00 - 🔴 HIGH)
- **Total Complexity**: 29
- **Real Coverage**: 0.0%

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

### StudyPlanBuilder.kt (Score: 812.00 - 🔴 HIGH)
- **Total Complexity**: 28
- **Real Coverage**: 0.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `formatHour` | 3 |
| `getSyllabusStudyPlanPrompt` | 1 |
| `getStudyPlanCritiquePrompt` | 1 |
| `getTaskDecompositionPrompt` | 1 |
| `getDecompositionCritiquePrompt` | 1 |

### GeminiRetryService.kt (Score: 812.00 - 🔴 HIGH)
- **Total Complexity**: 28
- **Real Coverage**: 0.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `wait` | 7 |
| `checkRateLimitWindow` | 3 |
| `clearRateLimitResetForTesting` | 1 |
| `clearGlobalHold` | 1 |
| `isGlobalHoldActive` | 1 |
| `isRateLimitWindowActive` | 1 |
| `clearGlobalHoldForTesting` | 1 |
| `cancelHold` | 1 |
| `activateGlobalHold` | 1 |
| `resolveRetryDelay` | 1 |
| *... and 1 more* | |

### GoogleCalendarSyncService.kt (Score: 756.00 - 🔴 HIGH)
- **Total Complexity**: 27
- **Real Coverage**: 0.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `getEvents` | 11 |
| `listCalendars` | 3 |
| `toCalendarException` | 2 |
| `ensureSuccess` | 2 |
| `syncEvent` | 2 |
| `<T> withToken` | 1 |
| `createCalendar` | 1 |
| `deleteEvent` | 1 |
| `fetchEventsPage` | 1 |

### GeminiRequestExecutor.kt (Score: 756.00 - 🔴 HIGH)
- **Total Complexity**: 27
- **Real Coverage**: 0.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `clearRateLimitResetForTesting` | 1 |
| `postToModel` | 1 |
| `<T> executeWithRetry` | 1 |
| `<T> executeWithRetryInternal` | 1 |

### SqlDelightLocalCalendarRepository.kt (Score: 702.00 - 🔴 HIGH)
- **Total Complexity**: 26
- **Real Coverage**: 0.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `updateEvent` | 8 |
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

### GeminiResponseParser.kt (Score: 650.00 - 🔴 HIGH)
- **Total Complexity**: 25
- **Real Coverage**: 0.0%

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

### CriticJsonCodec.kt (Score: 552.00 - 🔴 HIGH)
- **Total Complexity**: 23
- **Real Coverage**: 0.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `parseEventFromJson` | 9 |
| `serializeEvents` | 5 |
| `parseEvents` | 3 |
| `serializeTasks` | 2 |
| `parseTasks` | 2 |
| `parseTaskFromJson` | 2 |

### CriticActorAIService.kt (Score: 552.00 - 🔴 HIGH)
- **Total Complexity**: 23
- **Real Coverage**: 0.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `areEventListsDifferent` | 7 |
| `generateChatResponse` | 4 |
| `generateCalendarEvents` | 2 |
| `decomposeTask` | 2 |
| `generateStudyPlan` | 1 |
| `<T> runCritiqueLoop` | 1 |

### EventBuilder.kt (Score: 506.00 - 🔴 HIGH)
- **Total Complexity**: 22
- **Real Coverage**: 0.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `getSourceEventExtractionPrompt` | 14 |
| `getEventCritiquePrompt` | 4 |

### GoogleRemoteCalendarRepository.kt (Score: 506.00 - 🔴 HIGH)
- **Total Complexity**: 22
- **Real Coverage**: 0.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `clearCalendar` | 5 |
| `deleteEvent` | 3 |
| `getAvailableCalendars` | 2 |
| `getAllEvents` | 2 |
| `saveEvent` | 2 |
| `updateEvent` | 2 |
| `getSettings` | 1 |
| `hardDeleteEvent` | 1 |
| `clearLocalCalendar` | 1 |
| `getEventsInRange` | 1 |
| *... and 2 more* | |

### ConflictResolver.kt (Score: 506.00 - 🔴 HIGH)
- **Total Complexity**: 22
- **Real Coverage**: 0.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `resolveConflicts` | 8 |
| `rescheduleEarlier` | 6 |
| `rescheduleForward` | 6 |
| `findConflict` | 1 |
| `isMovable` | 1 |

### SyncNegotiator.kt (Score: 506.00 - 🔴 HIGH)
- **Total Complexity**: 22
- **Real Coverage**: 0.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `pushLocalChanges` | 6 |
| `buildNegotiation` | 3 |
| `findRemoteUpdatesAndConflicts` | 1 |
| `findDeletedLocalIds` | 1 |
| `buildProposedBaseCalendar` | 1 |

### Event.kt (Score: 506.00 - 🔴 HIGH)
- **Total Complexity**: 22
- **Real Coverage**: 0.0%

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

### SourceAdder.kt (Score: 462.00 - 🔴 HIGH)
- **Total Complexity**: 21
- **Real Coverage**: 0.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `processSourceWithCache` | 6 |
| `semesterFilter` | 4 |
| `applyFilter` | 4 |
| `addSource` | 3 |
| `handleFailure` | 3 |
| `isCacheStale` | 1 |

### EventPresenter.kt (Score: 462.00 - 🔴 HIGH)
- **Total Complexity**: 21
- **Real Coverage**: 0.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `getEventBorderColor` | 9 |
| `getDeadlineChipText` | 4 |
| `getCategoryLabel` | 3 |
| `getDeadlineStatus` | 2 |
| `getEventTimeText` | 2 |
| `showDeadlineInfo` | 1 |

### SqlDelightUserPreferenceMemoryRepository.kt (Score: 420.00 - 🔴 HIGH)
- **Total Complexity**: 20
- **Real Coverage**: 0.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `getDerivedConstraints` | 12 |
| `logOverride` | 6 |
| `pruneOldLogs` | 1 |
| `clearAllLogs` | 1 |

### GeminiModelNegotiator.kt (Score: 420.00 - 🔴 HIGH)
- **Total Complexity**: 20
- **Real Coverage**: 0.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `getAvailableModels` | 5 |
| `evictFromCache` | 2 |
| `clearBlacklistForTesting` | 1 |
| `blacklistModel` | 1 |
| `negotiateBestModel` | 1 |

### EventGenerationService.kt (Score: 380.00 - 🔴 HIGH)
- **Total Complexity**: 19
- **Real Coverage**: 0.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `normalize` | 5 |
| `buildScheduleContext` | 3 |
| `extractDeliverables` | 1 |
| `generateStudyPlan` | 1 |
| `generateDeterministicId` | 1 |

### AppController.kt (Score: 380.00 - 🔴 HIGH)
- **Total Complexity**: 19
- **Real Coverage**: 0.0%

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

### GoogleDriveService.kt (Score: 380.00 - 🔴 HIGH)
- **Total Complexity**: 19
- **Real Coverage**: 0.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `<T> withToken` | 8 |
| `validateConnectionResult` | 4 |
| `listFiles` | 3 |
| `getFileContent` | 3 |
| `validateConnection` | 1 |

### CollisionDetector.kt (Score: 306.00 - 🔴 HIGH)
- **Total Complexity**: 17
- **Real Coverage**: 0.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `findNextDaySlot` | 1 |
| `findNextTimeSlot` | 1 |
| `findTimeSlotOnDay` | 1 |

### CommonSourceProviders.kt (Score: 306.00 - 🔴 HIGH)
- **Total Complexity**: 17
- **Real Coverage**: 0.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `SelectorUI` | 7 |
| `SelectorUI` | 4 |
| `SelectorUI` | 3 |
| `isAuthorized` | 1 |
| `isAuthorized` | 1 |
| `isAuthorized` | 1 |

### IngestionAgent.kt (Score: 272.00 - 🔴 HIGH)
- **Total Complexity**: 16
- **Real Coverage**: 0.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `addLocalFile` | 4 |
| `addUrl` | 4 |
| `addDriveFile` | 4 |
| `resolveCategory` | 1 |
| `persistSource` | 1 |

### SyncNegotiationApplier.kt (Score: 272.00 - 🔴 HIGH)
- **Total Complexity**: 16
- **Real Coverage**: 0.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `isLiveSyncEnabled` | 2 |
| `apply` | 1 |
| `applyDeletedLocalEvents` | 1 |
| `applyRemoteEventsToLocal` | 1 |
| `applyShiftedStudyBlocks` | 1 |

### GoogleAccountFlow.kt (Score: 240.00 - 🔴 HIGH)
- **Total Complexity**: 15
- **Real Coverage**: 0.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `connect` | 5 |
| `handleInvalidAccessToken` | 4 |
| `checkConnectionOnStartup` | 3 |
| `disconnect` | 1 |
| `reportAuthError` | 1 |

### CalendarInterfaces.kt (Score: 210.00 - 🔴 HIGH)
- **Total Complexity**: 14
- **Real Coverage**: 0.0%

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

### IcsStringBuilder.kt (Score: 210.00 - 🔴 HIGH)
- **Total Complexity**: 14
- **Real Coverage**: 0.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `buildIcsString` | 7 |
| `buildRecurrenceRule` | 4 |
| `formatDateTime` | 1 |
| `formatDate` | 1 |
| `plusDays` | 1 |

### DecompositionOrchestrator.kt (Score: 210.00 - 🔴 HIGH)
- **Total Complexity**: 14
- **Real Coverage**: 0.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `isComplex` | 3 |
| `decompose` | 1 |
| `calculateSubDueDate` | 1 |
| `calculateDaysBeforeDue` | 1 |

### WeekAnchorExtractor.kt (Score: 210.00 - 🔴 HIGH)
- **Total Complexity**: 14
- **Real Coverage**: 0.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `needsAnchors` | 5 |
| `inject` | 4 |
| `collectAnchors` | 4 |

### CalendarPusher.kt (Score: 182.00 - 🔴 HIGH)
- **Total Complexity**: 13
- **Real Coverage**: 0.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `push` | 13 |

### AppContent.kt (Score: 156.00 - 🔴 HIGH)
- **Total Complexity**: 12
- **Real Coverage**: 0.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `AppContent` | 12 |

### EventGenerator.kt (Score: 156.00 - 🔴 HIGH)
- **Total Complexity**: 12
- **Real Coverage**: 0.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `expandEvents` | 1 |
| `expandTimeEvent` | 1 |
| `expandDayEvent` | 1 |

### CalendarPushResolver.kt (Score: 156.00 - 🔴 HIGH)
- **Total Complexity**: 12
- **Real Coverage**: 0.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `resolveAndPush` | 1 |
| `resolveAndReschedule` | 1 |
| `buildResolver` | 1 |

### StudyBlockShiftResolver.kt (Score: 156.00 - 🔴 HIGH)
- **Total Complexity**: 12
- **Real Coverage**: 0.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `resolveShifts` | 1 |
| `findCollidingEvent` | 1 |
| `hasShifted` | 1 |

### CalendarAgent.kt (Score: 156.00 - 🔴 HIGH)
- **Total Complexity**: 12
- **Real Coverage**: 0.0%

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

### ContextAgent.kt (Score: 132.00 - 🔴 HIGH)
- **Total Complexity**: 11
- **Real Coverage**: 0.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `analyzeSource` | 4 |
| `querySource` | 3 |
| `getSourceMetadata` | 1 |
| `queryAllSources` | 1 |

### TelemetryManager.kt (Score: 132.00 - 🔴 HIGH)
- **Total Complexity**: 11
- **Real Coverage**: 0.0%

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

### GeminiRequestQueue.kt (Score: 132.00 - 🔴 HIGH)
- **Total Complexity**: 11
- **Real Coverage**: 0.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `<T> enqueue` | 3 |
| `notifyRateLimit` | 2 |
| `estimatedRemainingSeconds` | 2 |
| `shared` | 1 |
| `resetExtendedInterval` | 1 |

### SemesterResolver.kt (Score: 132.00 - 🔴 HIGH)
- **Total Complexity**: 11
- **Real Coverage**: 0.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `getExpandedRange` | 7 |
| `getSemesterRange` | 2 |
| `filterToActiveSemester` | 2 |

### SourceIngestionHandler.kt (Score: 132.00 - 🔴 HIGH)
- **Total Complexity**: 11
- **Real Coverage**: 0.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `ingestLocalFile` | 1 |
| `ingestLocalFiles` | 1 |
| `ingestUrl` | 1 |
| `ingestDriveFile` | 1 |
| `buildIngestibleFilesQuery` | 1 |

### Logger.kt (Score: 132.00 - 🔴 HIGH)
- **Total Complexity**: 11
- **Real Coverage**: 0.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `e` | 3 |
| `d` | 2 |
| `appendToFile` | 2 |
| `isDebugEnabled` | 1 |
| `i` | 1 |
| `writeLogToFile` | 1 |
| `rememberLogger` | 1 |

### ChatBuilder.kt (Score: 132.00 - 🔴 HIGH)
- **Total Complexity**: 11
- **Real Coverage**: 0.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `getChatCritiquePrompt` | 2 |
| `getMultiSourceChatPrompt` | 1 |

### ActiveSemesterDetector.kt (Score: 110.00 - 🔴 HIGH)
- **Total Complexity**: 10
- **Real Coverage**: 0.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `hasMultipleSemestersForSameYear` | 4 |
| `detect` | 3 |
| `contains` | 1 |
| `semestersFor` | 1 |

### AgentHarness.kt (Score: 110.00 - 🔴 HIGH)
- **Total Complexity**: 10
- **Real Coverage**: 0.0%

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

### SourceFactGrounder.kt (Score: 110.00 - 🔴 HIGH)
- **Total Complexity**: 10
- **Real Coverage**: 0.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `groundFreeText` | 4 |
| `findUngrounded` | 2 |
| `extractClaims` | 1 |
| `normalizeOrdinals` | 1 |

### GroundingGuardAIService.kt (Score: 110.00 - 🔴 HIGH)
- **Total Complexity**: 10
- **Real Coverage**: 0.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `generateCalendarEvents` | 1 |
| `generateStudyPlan` | 1 |
| `generateChatResponse` | 1 |
| `groundToSource` | 1 |

### CalendarSyncManager.kt (Score: 110.00 - 🔴 HIGH)
- **Total Complexity**: 10
- **Real Coverage**: 0.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `refreshEvents` | 2 |
| `initiateSyncIfNeeded` | 1 |
| `applySyncProposal` | 1 |

### SourceManager.kt (Score: 110.00 - 🔴 HIGH)
- **Total Complexity**: 10
- **Real Coverage**: 0.0%

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

### SqlDelightSourceRepository.kt (Score: 110.00 - 🔴 HIGH)
- **Total Complexity**: 10
- **Real Coverage**: 0.0%

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

### ModelManager.kt (Score: 110.00 - 🔴 HIGH)
- **Total Complexity**: 10
- **Real Coverage**: 0.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `downloadModel` | 7 |
| `isModelDownloaded` | 2 |
| `getModelFile` | 1 |

### AiPrompts.kt (Score: 110.00 - 🔴 HIGH)
- **Total Complexity**: 10
- **Real Coverage**: 0.0%

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

### DriveFileFilter.kt (Score: 90.00 - 🔴 HIGH)
- **Total Complexity**: 9
- **Real Coverage**: 0.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `from` | 2 |
| `matchesType` | 2 |
| `filter` | 1 |
| `sort` | 1 |
| `matchesQuery` | 1 |

### NormalizationService.kt (Score: 90.00 - 🔴 HIGH)
- **Total Complexity**: 9
- **Real Coverage**: 0.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `extract` | 5 |
| `sanitizeTimes` | 4 |

### SettingsPreferencesParser.kt (Score: 90.00 - 🔴 HIGH)
- **Total Complexity**: 9
- **Real Coverage**: 0.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `parse` | 1 |

### Tracer.kt (Score: 90.00 - 🔴 HIGH)
- **Total Complexity**: 9
- **Real Coverage**: 0.0%

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

### SchedulingAlgorithm.kt (Score: 90.00 - 🔴 HIGH)
- **Total Complexity**: 9
- **Real Coverage**: 0.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `resolve` | 1 |
| `shiftEvent` | 1 |
| `findNextAvailableSlot` | 1 |

### AutoDecomposer.kt (Score: 90.00 - 🔴 HIGH)
- **Total Complexity**: 9
- **Real Coverage**: 0.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `run` | 9 |

### AIService.kt (Score: 90.00 - 🔴 HIGH)
- **Total Complexity**: 9
- **Real Coverage**: 0.0%

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

### WarningClassifier.kt (Score: 90.00 - 🔴 HIGH)
- **Total Complexity**: 9
- **Real Coverage**: 0.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `activeSemesterFrom` | 2 |
| `classify` | 1 |

### GoogleTokenService.kt (Score: 72.00 - 🔴 HIGH)
- **Total Complexity**: 8
- **Real Coverage**: 0.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `<T> withToken` | 8 |

### ConstraintValidator.kt (Score: 72.00 - 🔴 HIGH)
- **Total Complexity**: 8
- **Real Coverage**: 0.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `isValidTimeSlot` | 1 |
| `isDayAvailable` | 1 |

### CategorizationBuilder.kt (Score: 72.00 - 🔴 HIGH)
- **Total Complexity**: 8
- **Real Coverage**: 0.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `getSyllabusAuditPrompt` | 4 |
| `getSourceCategorizationPrompt` | 2 |
| `getDocumentIntelligencePrompt` | 2 |

### GoogleAuthService.kt (Score: 72.00 - 🔴 HIGH)
- **Total Complexity**: 8
- **Real Coverage**: 0.0%

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

### SyllabusAuditor.kt (Score: 72.00 - 🔴 HIGH)
- **Total Complexity**: 8
- **Real Coverage**: 0.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `audit` | 8 |

### RemoteFirstWriter.kt (Score: 72.00 - 🔴 HIGH)
- **Total Complexity**: 8
- **Real Coverage**: 0.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `save` | 4 |
| `update` | 4 |

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

### StudioPanel.kt (Score: 56.00 - 🔴 HIGH)
- **Total Complexity**: 7
- **Real Coverage**: 0.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `StudioPanel` | 1 |

### ResolvedEventWriter.kt (Score: 56.00 - 🔴 HIGH)
- **Total Complexity**: 7
- **Real Coverage**: 0.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `persist` | 1 |

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

### RemoteFirstEventPersistence.kt (Score: 56.00 - 🔴 HIGH)
- **Total Complexity**: 7
- **Real Coverage**: 0.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `reset` | 3 |
| `save` | 1 |
| `update` | 1 |
| `delete` | 1 |
| `retryLocalOnly` | 1 |

### StateFlowWrapper.kt (Score: 42.00 - 🔴 HIGH)
- **Total Complexity**: 6
- **Real Coverage**: 0.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `collect` | 1 |
| `setValue` | 1 |
| `collect` | 1 |
| `asStateFlow` | 1 |
| `setValue` | 1 |
| `<T> mutableStateFlowWrapper` | 1 |

### SourceFragmentBatcher.kt (Score: 42.00 - 🔴 HIGH)
- **Total Complexity**: 6
- **Real Coverage**: 0.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `batch` | 1 |

### FragmentRanker.kt (Score: 42.00 - 🔴 HIGH)
- **Total Complexity**: 6
- **Real Coverage**: 0.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `rankFragments` | 1 |

### OAuthExchange.kt (Score: 42.00 - 🔴 HIGH)
- **Total Complexity**: 6
- **Real Coverage**: 0.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `performTokenExchange` | 2 |
| `exchangeCodeForTokens` | 1 |
| `refreshAccessToken` | 1 |

### TaskDecompositionService.kt (Score: 42.00 - 🔴 HIGH)
- **Total Complexity**: 6
- **Real Coverage**: 0.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `decompose` | 1 |
| `applyDecomposition` | 1 |
| `stepId` | 1 |

### CalendarEventGrouper.kt (Score: 42.00 - 🔴 HIGH)
- **Total Complexity**: 6
- **Real Coverage**: 0.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `groupEventsByDate` | 2 |
| `isDecomposable` | 1 |
| `filterEventsByDateRange` | 1 |

### GeminiRateLimitPolicy.kt (Score: 42.00 - 🔴 HIGH)
- **Total Complexity**: 6
- **Real Coverage**: 0.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `decide` | 1 |

### SourceScanner.kt (Score: 42.00 - 🔴 HIGH)
- **Total Complexity**: 6
- **Real Coverage**: 0.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `getWatchedLocalDirectories` | 1 |
| `setWatchedLocalDirectories` | 1 |
| `getWatchedGDriveFolders` | 1 |
| `setWatchedGDriveFolders` | 1 |
| `scanNewLocalFiles` | 1 |
| `scanNewDriveFiles` | 1 |

### SourceContextBuilder.kt (Score: 42.00 - 🔴 HIGH)
- **Total Complexity**: 6
- **Real Coverage**: 0.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `formatFragments` | 2 |
| `buildContextBlocks` | 1 |

### BugReporter.kt (Score: 42.00 - 🔴 HIGH)
- **Total Complexity**: 6
- **Real Coverage**: 0.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `reportError` | 6 |

### LocalFileFetcher.kt (Score: 30.00 - 🟡 MEDIUM)
- **Total Complexity**: 5
- **Real Coverage**: 0.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `fetchFromDirectories` | 5 |

### SourceProcessingPipeline.kt (Score: 30.00 - 🟡 MEDIUM)
- **Total Complexity**: 5
- **Real Coverage**: 0.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `processSource` | 5 |

### CalendarIdResolver.kt (Score: 30.00 - 🟡 MEDIUM)
- **Total Complexity**: 5
- **Real Coverage**: 0.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `getCEFCalendarId` | 3 |
| `resolveCalendarId` | 2 |

### SourceProcessor.kt (Score: 30.00 - 🟡 MEDIUM)
- **Total Complexity**: 5
- **Real Coverage**: 0.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `split` | 3 |
| `process` | 2 |

### GoogleAuthManager.kt (Score: 30.00 - 🟡 MEDIUM)
- **Total Complexity**: 5
- **Real Coverage**: 0.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `loginAndLink` | 2 |
| `unlinkAccount` | 2 |
| `isLinked` | 1 |

### UserPreferenceMemoryRepository.kt (Score: 30.00 - 🟡 MEDIUM)
- **Total Complexity**: 5
- **Real Coverage**: 0.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `logOverride` | 1 |
| `getDerivedConstraints` | 1 |
| `logOverride` | 1 |
| `pruneOldLogs` | 1 |
| `getDerivedConstraints` | 1 |

### StudyBlockOverrideLogger.kt (Score: 30.00 - 🟡 MEDIUM)
- **Total Complexity**: 5
- **Real Coverage**: 0.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `checkMove` | 3 |
| `checkDelete` | 2 |

### DeadlineSummary.kt (Score: 30.00 - 🟡 MEDIUM)
- **Total Complexity**: 5
- **Real Coverage**: 0.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `from` | 5 |

### PreferencesRepository.kt (Score: 30.00 - 🟡 MEDIUM)
- **Total Complexity**: 5
- **Real Coverage**: 0.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `readSync` | 3 |
| `getPreferences` | 1 |
| `savePreferences` | 1 |

### SourceSelector.kt (Score: 30.00 - 🟡 MEDIUM)
- **Total Complexity**: 5
- **Real Coverage**: 0.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `autoSelectFirstFrom` | 2 |
| `clearIfRemovedFrom` | 2 |
| `selectSource` | 1 |

### PollScheduler.kt (Score: 30.00 - 🟡 MEDIUM)
- **Total Complexity**: 5
- **Real Coverage**: 0.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `shouldPoll` | 3 |
| `getLastPollTime` | 1 |
| `setLastPollTime` | 1 |

### SyncGate.kt (Score: 30.00 - 🟡 MEDIUM)
- **Total Complexity**: 5
- **Real Coverage**: 0.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `isLiveSyncEnabled` | 2 |
| `isGoogleLinked` | 2 |
| `isLive` | 1 |

### GeminiRequestBuilder.kt (Score: 30.00 - 🟡 MEDIUM)
- **Total Complexity**: 5
- **Real Coverage**: 0.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `buildPostUrl` | 2 |
| `postToModel` | 2 |
| `hasApiKey` | 1 |

### EventDisplayPipeline.kt (Score: 20.00 - 🟡 MEDIUM)
- **Total Complexity**: 4
- **Real Coverage**: 0.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `getExpandedAndFilteredEvents` | 1 |

### ChatInputPresenter.kt (Score: 20.00 - 🟡 MEDIUM)
- **Total Complexity**: 4
- **Real Coverage**: 0.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `placeholder` | 2 |
| `chipLabel` | 2 |

### LocalDirectoryPreferences.kt (Score: 20.00 - 🟡 MEDIUM)
- **Total Complexity**: 4
- **Real Coverage**: 0.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `getWatchedDirectories` | 2 |
| `setWatchedDirectories` | 2 |

### LocalOnlyRetrier.kt (Score: 20.00 - 🟡 MEDIUM)
- **Total Complexity**: 4
- **Real Coverage**: 0.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `retry` | 4 |

### SyncProposal.kt (Score: 20.00 - 🟡 MEDIUM)
- **Total Complexity**: 4
- **Real Coverage**: 0.0%


### RetryAfterParser.kt (Score: 20.00 - 🟡 MEDIUM)
- **Total Complexity**: 4
- **Real Coverage**: 0.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `extractRetryAfterMs` | 4 |

### WebSourceReader.kt (Score: 20.00 - 🟡 MEDIUM)
- **Total Complexity**: 4
- **Real Coverage**: 0.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `readTextFromUrl` | 2 |
| `cleanHtml` | 2 |

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

### KotlinxSerialization.kt (Score: 20.00 - 🟡 MEDIUM)
- **Total Complexity**: 4
- **Real Coverage**: 0.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `serialize` | 1 |
| `deserialize` | 1 |
| `serialize` | 1 |
| `deserialize` | 1 |

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

### PushButtonState.kt (Score: 20.00 - 🟡 MEDIUM)
- **Total Complexity**: 4
- **Real Coverage**: 0.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `variant` | 2 |
| `label` | 2 |

### DriveFileScanner.kt (Score: 20.00 - 🟡 MEDIUM)
- **Total Complexity**: 4
- **Real Coverage**: 0.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `scanNewFiles` | 4 |

### GoogleCalendarSelector.kt (Score: 20.00 - 🟡 MEDIUM)
- **Total Complexity**: 4
- **Real Coverage**: 0.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `GoogleCalendarSelector` | 1 |

### DeliverableExtractor.kt (Score: 20.00 - 🟡 MEDIUM)
- **Total Complexity**: 4
- **Real Coverage**: 0.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `run` | 4 |

### SqlDelightAnalysisCacheRepository.kt (Score: 20.00 - 🟡 MEDIUM)
- **Total Complexity**: 4
- **Real Coverage**: 0.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `getCached` | 2 |
| `putCache` | 1 |
| `evict` | 1 |

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

### StudioStatusFormatter.kt (Score: 20.00 - 🟡 MEDIUM)
- **Total Complexity**: 4
- **Real Coverage**: 0.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `format` | 1 |

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

### GeminiBodyBuilder.kt (Score: 12.00 - 🟢 LOW)
- **Total Complexity**: 3
- **Real Coverage**: 0.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `buildJsonRequestBody` | 1 |
| `buildTextRequestBody` | 1 |

### HarnessSourceProcessor.kt (Score: 12.00 - 🟢 LOW)
- **Total Complexity**: 3
- **Real Coverage**: 0.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `processSource` | 1 |
| `processLocalFiles` | 1 |
| `processDriveFiles` | 1 |

### SourceFragment.kt (Score: 12.00 - 🟢 LOW)
- **Total Complexity**: 3
- **Real Coverage**: 0.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `toJson` | 1 |

### EventRescheduler.kt (Score: 12.00 - 🟢 LOW)
- **Total Complexity**: 3
- **Real Coverage**: 0.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `run` | 3 |

### EventQueryService.kt (Score: 12.00 - 🟢 LOW)
- **Total Complexity**: 3
- **Real Coverage**: 0.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `getEventsInRange` | 1 |
| `getEventsBySyncStatus` | 1 |
| `getIncompleteEventsBefore` | 1 |

### LocalFileProcessor.kt (Score: 12.00 - 🟢 LOW)
- **Total Complexity**: 3
- **Real Coverage**: 0.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `processLocalFiles` | 3 |

### ConflictResolutionPresenter.kt (Score: 12.00 - 🟢 LOW)
- **Total Complexity**: 3
- **Real Coverage**: 0.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `bodyText` | 1 |
| `instructionsText` | 1 |
| `hasReason` | 1 |

### DecompositionAcceptor.kt (Score: 12.00 - 🟢 LOW)
- **Total Complexity**: 3
- **Real Coverage**: 0.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `run` | 3 |

### EventDeleter.kt (Score: 12.00 - 🟢 LOW)
- **Total Complexity**: 3
- **Real Coverage**: 0.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `delete` | 3 |

### GeminiErrorHandler.kt (Score: 12.00 - 🟢 LOW)
- **Total Complexity**: 3
- **Real Coverage**: 0.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `categorizeError` | 1 |
| `handleStructuralError` | 1 |
| `handleServerError` | 1 |

### ContributionValidator.kt (Score: 12.00 - 🟢 LOW)
- **Total Complexity**: 3
- **Real Coverage**: 0.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `validate` | 3 |

### SourcesPanel.kt (Score: 12.00 - 🟢 LOW)
- **Total Complexity**: 3
- **Real Coverage**: 0.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `SourcesPanel` | 1 |

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

### EventTimeRepairer.kt (Score: 12.00 - 🟢 LOW)
- **Total Complexity**: 3
- **Real Coverage**: 0.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `repair` | 3 |

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

### RoutineRepository.kt (Score: 12.00 - 🟢 LOW)
- **Total Complexity**: 3
- **Real Coverage**: 0.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `getRoutineEvents` | 2 |
| `saveRoutineEvents` | 1 |

### DriveFileProcessor.kt (Score: 12.00 - 🟢 LOW)
- **Total Complexity**: 3
- **Real Coverage**: 0.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `processDriveFiles` | 3 |

### PreferencesPort.kt (Score: 6.00 - 🟢 LOW)
- **Total Complexity**: 2
- **Real Coverage**: 0.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `getPreferences` | 1 |
| `getPreferences` | 1 |

### SourceInterfaces.kt (Score: 6.00 - 🟢 LOW)
- **Total Complexity**: 2
- **Real Coverage**: 0.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `isAuthorized` | 1 |
| `SelectorUI` | 1 |

### AcademicCalendarSyncHelper.kt (Score: 6.00 - 🟢 LOW)
- **Total Complexity**: 2
- **Real Coverage**: 0.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `performCalendarSync` | 1 |

### IcsExport.kt (Score: 6.00 - 🟢 LOW)
- **Total Complexity**: 2
- **Real Coverage**: 0.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `generateIcsString` | 1 |
| `writeIcsFile` | 1 |

### RetryCountdown.kt (Score: 6.00 - 🟢 LOW)
- **Total Complexity**: 2
- **Real Coverage**: 0.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `secondsRemaining` | 2 |

### DriveFileFetcher.kt (Score: 6.00 - 🟢 LOW)
- **Total Complexity**: 2
- **Real Coverage**: 0.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `fetchFromFolders` | 1 |
| `deduplicateFiles` | 1 |

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

### WarningAggregator.kt (Score: 6.00 - 🟢 LOW)
- **Total Complexity**: 2
- **Real Coverage**: 0.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `collect` | 1 |

### AiEventsService.kt (Score: 6.00 - 🟢 LOW)
- **Total Complexity**: 2
- **Real Coverage**: 0.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `addEvents` | 1 |
| `clearEvents` | 1 |

### CalendarDisplayName.kt (Score: 6.00 - 🟢 LOW)
- **Total Complexity**: 2
- **Real Coverage**: 0.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `resolve` | 2 |

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

### IcsCalendarSource.kt (Score: 2.00 - 🟢 LOW)
- **Total Complexity**: 1
- **Real Coverage**: 0.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `readSource` | 1 |

### CalendarErrorFormatter.kt (Score: 2.00 - 🟢 LOW)
- **Total Complexity**: 1
- **Real Coverage**: 0.0%


### ContentHasher.kt (Score: 2.00 - 🟢 LOW)
- **Total Complexity**: 1
- **Real Coverage**: 0.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `hash` | 1 |

### DependencyContainer.kt (Score: 2.00 - 🟢 LOW)
- **Total Complexity**: 1
- **Real Coverage**: 0.0%


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

### DecomposedTask.kt (Score: 2.00 - 🟢 LOW)
- **Total Complexity**: 1
- **Real Coverage**: 0.0%


### AppEnv.kt (Score: 2.00 - 🟢 LOW)
- **Total Complexity**: 1
- **Real Coverage**: 0.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `get` | 1 |

### AppNavigationService.kt (Score: 2.00 - 🟢 LOW)
- **Total Complexity**: 1
- **Real Coverage**: 0.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `navigateTo` | 1 |

### RecursiveDecompositionAIService.kt (Score: 2.00 - 🟢 LOW)
- **Total Complexity**: 1
- **Real Coverage**: 0.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `decomposeTask` | 1 |

### SourceItem.kt (Score: 2.00 - 🟢 LOW)
- **Total Complexity**: 1
- **Real Coverage**: 0.0%


### CachedAnalysis.kt (Score: 2.00 - 🟢 LOW)
- **Total Complexity**: 1
- **Real Coverage**: 0.0%


### RoutineItem.kt (Score: 2.00 - 🟢 LOW)
- **Total Complexity**: 1
- **Real Coverage**: 0.0%


### StudyPreferences.kt (Score: 2.00 - 🟢 LOW)
- **Total Complexity**: 1
- **Real Coverage**: 0.0%


### DocumentFrequencyCalculator.kt (Score: 2.00 - 🟢 LOW)
- **Total Complexity**: 1
- **Real Coverage**: 0.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `calculateDocumentFrequencies` | 1 |

### UserOverrideLog.kt (Score: 2.00 - 🟢 LOW)
- **Total Complexity**: 1
- **Real Coverage**: 0.0%


### UiOnly.kt (Score: 2.00 - 🟢 LOW)
- **Total Complexity**: 1
- **Real Coverage**: 0.0%


### GoogleConnectionState.kt (Score: 2.00 - 🟢 LOW)
- **Total Complexity**: 1
- **Real Coverage**: 0.0%


### PlatformUtils.kt (Score: 2.00 - 🟢 LOW)
- **Total Complexity**: 1
- **Real Coverage**: 0.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `openBrowser` | 1 |

### CollisionResolver.kt (Score: 2.00 - 🟢 LOW)
- **Total Complexity**: 1
- **Real Coverage**: 0.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `resolve` | 1 |

### QuotaExhaustionDetector.kt (Score: 2.00 - 🟢 LOW)
- **Total Complexity**: 1
- **Real Coverage**: 0.0%

#### Methods list:
| Method | Complexity |
| :--- | :---: |
| `isQuotaExhausted` | 1 |

