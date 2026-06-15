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
    private val logger: Logger? = null,
    private val maxDepth: Int = 3
) {
    private val tag = "DecompositionOrchestrator"

    suspend fun decompose(rootTitle: String, rootDueDate: String): List<DecomposedTask> {
        val queue = ArrayDeque<WorkUnit>()
        queue.add(WorkUnit.Task(rootTitle, rootDueDate, depth = 0))

        val finalLeaves = mutableListOf<DecomposedTask>()

        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()

            if (current.depth >= maxDepth) {
                val daysBeforeDue = calculateDaysBeforeDue(current.dueDate, rootDueDate)
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
                logger?.e(tag, "Decomposition failed for $current", e)
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

        return finalLeaves.sortedByDescending { it.daysBeforeDue }
    }

    private fun isComplex(task: DecomposedTask): Boolean {
        val words = task.title.lowercase().split(" ")
        if (words.size < 3) return false
        val complexKeywords = listOf(
            "draft",
            "write",
            "research",
            "outline",
            "design",
            "implement",
            "build",
            "analyze",
            "review"
        )
        return task.daysBeforeDue > 1 || words.any { it in complexKeywords }
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
