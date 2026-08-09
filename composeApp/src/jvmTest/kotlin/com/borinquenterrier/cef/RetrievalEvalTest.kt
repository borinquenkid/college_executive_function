package com.borinquenterrier.cef

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
private data class RetrievalQuestion(
    val question: String,
    val expectedSource: String,
    val expectAnyOf: List<String>,
    /** "verbatim" (shares the source's vocabulary) or "paraphrase" (deliberately avoids it). */
    val style: String,
    /** "deadline" (missing it harms the student) or "general" (policy/logistics). */
    val stake: String
)

/**
 * Deterministic, API-free eval of the chat-retrieval pipeline (tasks/plan.md T1/T2).
 *
 * Mirrors production ingestion (SourceNormalizer): PDFs become per-page fragments via
 * [PdfReader], plain text becomes 3000-char chunks via [SourceProcessor.split] — NOT the
 * single-giant-fragment shortcut other tests use, which would make ranking trivially perfect
 * over this 5-course corpus. Two stages are measured:
 *
 *  1. fragment recall@5/@15 + MRR through [FragmentRanker] (did the answer-bearing fragment
 *     survive top-K selection);
 *  2. promptContainsAnswer through the real [SourceContextBuilder] + [ChatBuilder] assembly
 *     (did the answer text survive per-source compression into the final prompt) — the
 *     headline metric, since it is what bounds the LLM's ability to answer without
 *     confabulating.
 *
 * REPORT-ONLY: no metric assertion here (see plan — thresholds get chosen from observed data;
 * the deterministic deadline gate lives in the events-digest tests instead). The only hard
 * assertions are harness-integrity ones: the fixture parses, and every expected answer string
 * still exists somewhere in the corpus (guards against fixture rot silently zeroing metrics).
 */
class RetrievalEvalTest : FunSpec({

    val json = Json { ignoreUnknownKeys = true }

    fun resource(name: String): File = listOf(
        File("src/commonTest/resources/$name"),
        File("composeApp/src/commonTest/resources/$name"),
        File("../composeApp/src/commonTest/resources/$name")
    ).find { it.exists() } ?: error("Fixture not found: $name")

    fun normalize(s: String): String = s.lowercase().replace(Regex("""\s+"""), " ")

    suspend fun loadCorpus(): List<SourceItem> {
        val pdfNames = listOf("syllabus_bdan250.pdf", "syllabus_hist152.pdf")
        val txtNames = listOf("syllabus.txt", "week_based_syllabus.txt", "day_within_week_syllabus.txt")

        val pdfSources = pdfNames.map { name ->
            SourceItem(
                title = name,
                fragments = PdfReader().readSource(resource(name).absolutePath),
                category = SourceCategory.SYLLABUS
            )
        }
        val txtSources = txtNames.map { name ->
            SourceItem(
                title = name,
                fragments = SourceProcessor.split(resource(name).readText()),
                category = SourceCategory.SYLLABUS
            )
        }
        return pdfSources + txtSources
    }

    test("RetrievalEvalMetrics round-trips through JSON") {
        val metrics = RetrievalEvalMetrics(
            questionCount = 2,
            fragmentRecallAt5Percent = 50.0,
            fragmentRecallAt15Percent = 100.0,
            meanReciprocalRank = 0.75,
            promptContainsAnswerPercent = 50.0,
            perBucket = mapOf(
                "paraphrase|deadline" to RetrievalBucketMetric(1, 100.0, 0.0)
            )
        )
        val roundTripped = json.decodeFromString(
            RetrievalEvalMetrics.serializer(),
            json.encodeToString(RetrievalEvalMetrics.serializer(), metrics)
        )
        roundTripped shouldBe metrics
    }

    test("Retrieval eval corpus report (deterministic, report-only)") {
        val questions = json.decodeFromString<List<RetrievalQuestion>>(
            resource("retrieval_eval_questions.json").readText()
        )
        questions.size shouldBe 18

        val sources = loadCorpus()
        val totalFragments = sources.sumOf { it.fragments.size }
        println("\n================== RETRIEVAL EVAL ==================")
        println("Corpus: ${sources.size} sources, $totalFragments fragments")
        sources.forEach { println("  ${it.title}: ${it.fragments.size} fragments") }

        // Harness integrity: every expected answer must exist somewhere in the corpus — a
        // fixture/PDF change that breaks this should fail loudly, not read as recall=0.
        questions.forEach { q ->
            val inCorpus = sources.any { s ->
                s.fragments.any { f -> q.expectAnyOf.any { normalize(f.text).contains(normalize(it)) } }
            }
            withClue("Fixture rot: none of ${q.expectAnyOf} found anywhere in corpus for question '${q.question}'") {
                inCorpus shouldBe true
            }
        }

        data class Result(val q: RetrievalQuestion, val rank: Int?, val promptHit: Boolean)

        val ranker = FragmentRanker()
        val contextBuilder = SourceContextBuilder()

        val results = questions.map { q ->
            val ranked = ranker.rankFragments(sources, q.question, topK = 15)
            val rank = ranked.indexOfFirst { (_, fragment) ->
                q.expectAnyOf.any { normalize(fragment.text).contains(normalize(it)) }
            }.let { if (it == -1) null else it + 1 }

            val blocks = contextBuilder.buildContextBlocks(ranked) { null }
            val prompt = ChatBuilder.getMultiSourceChatPrompt(blocks, emptyList(), q.question)
            val promptHit = q.expectAnyOf.any { normalize(prompt).contains(normalize(it)) }

            Result(q, rank, promptHit)
        }

        println("\n%-4s %-10s %-9s %-6s %-7s Question".format("rank", "style", "stake", "prompt", "source"))
        results.forEach { r ->
            println(
                "%-4s %-10s %-9s %-6s %-7s %s".format(
                    r.rank?.toString() ?: "MISS",
                    r.q.style,
                    r.q.stake,
                    if (r.promptHit) "YES" else "NO",
                    r.q.expectedSource.take(7),
                    r.q.question.take(70)
                )
            )
        }

        fun pct(hits: Int, total: Int) = if (total == 0) 100.0 else hits * 100.0 / total
        fun List<Result>.recallAt(k: Int) = pct(count { it.rank != null && it.rank <= k }, size)
        fun List<Result>.promptPct() = pct(count { it.promptHit }, size)

        val perBucket = results
            .groupBy { "${it.q.style}|${it.q.stake}" }
            .mapValues { (_, rs) ->
                RetrievalBucketMetric(
                    questionCount = rs.size,
                    fragmentRecallAt15Percent = rs.recallAt(15),
                    promptContainsAnswerPercent = rs.promptPct()
                )
            }

        val metrics = RetrievalEvalMetrics(
            questionCount = results.size,
            fragmentRecallAt5Percent = results.recallAt(5),
            fragmentRecallAt15Percent = results.recallAt(15),
            meanReciprocalRank = results.sumOf { r -> r.rank?.let { 1.0 / it } ?: 0.0 } / results.size,
            promptContainsAnswerPercent = results.promptPct(),
            perBucket = perBucket
        )

        println("\nOverall: recall@5=%.1f%% recall@15=%.1f%% MRR=%.3f promptContainsAnswer=%.1f%%".format(
            metrics.fragmentRecallAt5Percent, metrics.fragmentRecallAt15Percent,
            metrics.meanReciprocalRank, metrics.promptContainsAnswerPercent
        ))
        perBucket.toSortedMap().forEach { (bucket, m) ->
            println("  %-20s n=%-3d recall@15=%.1f%% promptContainsAnswer=%.1f%%".format(
                bucket, m.questionCount, m.fragmentRecallAt15Percent, m.promptContainsAnswerPercent
            ))
        }
        println("====================================================\n")

        EvalBaseline.writeCurrent("retrieval", RetrievalEvalMetrics.serializer(), metrics)
    }
})
