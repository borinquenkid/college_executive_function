package com.borinquenterrier.cef

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.minus

sealed interface WorkUnit {
    val title: String
    val dueDate: String
    val depth: Int

    data class Task(
        override val title: String,
        override val dueDate: String,
        override val depth: Int = 0
    ) : WorkUnit

    data class SubTask(
        override val title: String,
        override val dueDate: String,
        override val depth: Int,
        val parentTitle: String
    ) : WorkUnit
}

class DecompositionOrchestrator(
    private val delegate: AIService,
    private val maxDepth: Int = 3
) {

    suspend fun decompose(rootTitle: String, rootDueDate: String): List<DecomposedTask> =
        AppTracer.current.span("decomposition.orchestrate", mapOf(
            "task.title" to rootTitle,
            "task.due_date" to rootDueDate,
            "max_depth" to maxDepth.toString()
        )) {
        val queue = ArrayDeque<WorkUnit>()
        queue.add(WorkUnit.Task(rootTitle, rootDueDate, depth = 0))

        val finalLeaves = mutableListOf<DecomposedTask>()

        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()

            if (current.depth >= maxDepth) {
                val daysBeforeDue = calculateDaysBeforeDue(current.dueDate, rootDueDate)
                AppTracer.current.event("decomposition.max_depth_leaf", mapOf(
                    "node.title" to current.title, "node.depth" to current.depth.toString()
                ))
                finalLeaves.add(
                    DecomposedTask(
                        title = current.title,
                        daysBeforeDue = daysBeforeDue,
                        description = "Decomposition leaf"
                    )
                )
                continue
            }

            try {
                val subTasks = delegate.decomposeTask(current.title, current.dueDate)
                AppTracer.current.event("decomposition.node_expanded", mapOf(
                    "node.title" to current.title,
                    "node.depth" to current.depth.toString(),
                    "subtask.count" to subTasks.size.toString()
                ))
                if (subTasks.isEmpty()) {
                    val daysBeforeDue = calculateDaysBeforeDue(current.dueDate, rootDueDate)
                    finalLeaves.add(
                        DecomposedTask(
                            title = current.title,
                            daysBeforeDue = daysBeforeDue,
                            description = "Decomposed leaf"
                        )
                    )
                    continue
                }

                for (sub in subTasks) {
                    val subDueDate = calculateSubDueDate(current.dueDate, sub.daysBeforeDue)

                    if (current.depth + 1 < maxDepth && isComplex(sub)) {
                        queue.add(
                            WorkUnit.SubTask(
                                title = sub.title,
                                dueDate = subDueDate,
                                depth = current.depth + 1,
                                parentTitle = current.title
                            )
                        )
                    } else {
                        val daysBefore = calculateDaysBeforeDue(subDueDate, rootDueDate)
                        finalLeaves.add(
                            DecomposedTask(
                                title = sub.title,
                                daysBeforeDue = daysBefore,
                                description = sub.description
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                // Quota and auth errors mean no further calls will succeed — propagate so
                // EventAgent can surface a proper message instead of returning silent garbage.
                val msg = e.message ?: ""
                if (msg.contains("QuotaExhausted", ignoreCase = true) ||
                    msg.contains("RateLimited", ignoreCase = true) ||
                    msg.contains("Unauthorized", ignoreCase = true)
                ) throw e

                val daysBeforeDue = calculateDaysBeforeDue(current.dueDate, rootDueDate)
                finalLeaves.add(
                    DecomposedTask(
                        title = current.title,
                        daysBeforeDue = daysBeforeDue,
                        description = "Decomposition fallback: ${e.message}"
                    )
                )
            }
        }

        val result = finalLeaves.sortedByDescending { it.daysBeforeDue }
        setAttribute("leaves.count", result.size.toLong())
        result
    }

    private fun isComplex(task: DecomposedTask): Boolean {
        // Only recurse on genuinely multi-day, coarse-grained tasks.
        // Single-action steps (≤ 3 days before sub-due) are always leaves.
        if (task.daysBeforeDue <= 3) return false
        val title = task.title.lowercase()
        // Multi-word phrases that signal a broad phase, not an action step.
        // Keep these specific enough that action steps like "Revise first draft" don't match.
        val phaseSignals = listOf(
            "literature review", "research paper", "complete analysis",
            "full draft", "entire section", "essay section",
            "research phase", "writing phase"
        )
        return phaseSignals.any { title.contains(it) }
    }

    private fun calculateSubDueDate(parentDueDate: String, daysBeforeDue: Int): String {
        val parentDate = LocalDate.parse(parentDueDate)
        return parentDate.minus(daysBeforeDue, DateTimeUnit.DAY).toString()
    }

    private fun calculateDaysBeforeDue(subDueDate: String, rootDueDate: String): Int {
        val rootDate = LocalDate.parse(rootDueDate)
        val subDate = LocalDate.parse(subDueDate)
        return subDate.daysUntil(rootDate)
    }
}
