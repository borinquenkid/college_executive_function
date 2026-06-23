# ADR-001: Vector database for on-device RAG in CEF

## Status
Deferred — pending ADR-000: "Is dense-embedding RAG warranted for CEF?"
This ADR answers *which* vector DB to use; it should only be accepted after the prerequisite question
(is TF-IDF a measurable problem worth the added complexity?) has been answered affirmatively.

## Date
2026-06-22

## Context

CEF currently ranks source fragments for prompt injection using `TFIDFScorer`, a sparse keyword-overlap scorer
implemented in `composeApp/src/commonMain/kotlin/com/borinquenterrier/cef/TFIDFScorer.kt`. Fragments are
assembled by `SourceContextBuilder` and injected by `ContextAgent.queryAllSources()`.

TF-IDF works for exact-term overlap but degrades on semantic queries — a student asking "what's due next week"
will miss fragments that say "submission deadline" without the word "due". Dense embedding similarity (cosine /
dot-product over float32 vectors) fixes this class of failure.

Moving to dense embeddings requires two additions:
1. A way to generate embeddings (embedding model or API).
2. A way to store and query them efficiently on-device (vector DB or ANN index).

**Constraints that shape the decision:**
- The app targets **Android and iOS** from a single **Kotlin Multiplatform** codebase.
- Embeddings must be queryable **offline** — the app is used in classrooms that may have no network.
- The existing `SourceFragment` persistence layer uses **SQLDelight** (SQLite under the hood) on both platforms.
- We want to avoid a new server-side dependency; the server is already deployed separately.
- Integration cost matters: CEF is a solo project and any new dependency must have KMP-compatible bindings
  or a thin interop layer.

---

## Decision

**Adopt ObjectBox with VectorSearch as the vector store, paired with the Gemini Embedding API for vector
generation (with a local ONNX fallback as a future option).**

ObjectBox is the primary recommendation because it is the only embedded vector DB with first-class Kotlin
Multiplatform support covering Android, iOS, JVM, and desktop — the exact target matrix of CEF.

Gemini Embedding API (`models/text-embedding-004`, 768-dim) is used for generation because:
- CEF already has a Gemini client (`GeminiClient.kt`, `GeminiService.kt`); adding an embed call is additive.
- No new on-device model binary (avoids ~50–200 MB APK/IPA size increase).
- Consistent quality: same provider as the extraction/reasoning pipeline.

The TF-IDF scorer is **not deleted** at adoption time. It remains as the offline fallback when the network
is unavailable and no cached embedding exists for a fragment.

---

## Alternatives Considered

### SQLite-vec (layered on SQLDelight)
- **Pros:** Reuses existing SQLite infra; no new dependency for storage. CEF already has a SQLDelight
  schema in `commonMain`; `sqlite-vec` adds a loadable extension on top.
- **Cons:** `sqlite-vec` is a C extension requiring JNI on Android and `cinterop` on iOS. There is no
  official KMP wrapper as of mid-2026; a custom interop layer would need to be maintained. Extension
  loading APIs differ between Android (`SQLiteDatabase` custom path) and SQLDelight's driver abstraction.
- **Rejected:** Integration complexity outweighs the benefit of reusing SQLite. Worth revisiting if
  an official KMP sqlite-vec binding ships.

### FAISS (C++ via JNI / cinterop)
- **Pros:** Battle-tested ANN library, supports multiple index types (Flat, IVF, HNSW).
- **Cons:** No Kotlin or Swift bindings — requires JNI wrappers on Android and `cinterop` on iOS,
  both maintained manually. No KMP abstraction layer exists. Build tooling for native C++ in a KMP
  project adds significant CI complexity.
- **Rejected:** Engineering cost too high for a solo KMP project with no dedicated platform team.

### On-device embedding model (ONNX Runtime Mobile)
- **Pros:** Fully offline from day one; no API cost; no network requirement for embedding generation.
- **Cons:** Adds ~50–150 MB to app binary (model weight file). ONNX Runtime Mobile has Android support
  but iOS KMP interop requires a wrapper. Quantized models reduce size but may degrade quality vs. Gemini.
- **Decision:** Deferred, not rejected. Treating this as the future fallback once a suitable small
  model (e.g., `all-MiniLM-L6-v2` at 22 MB quantized) has been validated for academic-domain queries.
  See "Future Work" below.

### Pinecone / Weaviate (cloud-hosted vector DB)
- **Rejected immediately:** Requires persistent network; contradicts the offline-first constraint.

---

## Consequences

**Positive:**
- Semantic query recall improves for paraphrased questions (the main TF-IDF failure mode).
- ObjectBox HNSW index gives sub-millisecond ANN queries for the typical CEF corpus size
  (hundreds to low-thousands of fragments per user).
- The existing `SourceContextBuilder → ContextAgent` pipeline shape is preserved; only the
  scorer is swapped.

**Negative / Risks:**
- Gemini Embedding API requires a network call at indexing time (when a new source is added).
  Fragments ingested offline will not have an embedding until next sync — TF-IDF covers this window.
- ObjectBox adds a new transitive dependency (~2 MB AAR + iOS framework). Must be added to
  `gradle/libs.versions.toml` and the iOS podfile.
- Embedding vectors are 768 floats × 4 bytes = ~3 KB per fragment. A user with 500 fragments
  stores ~1.5 MB of vector data — acceptable on modern devices.

**Migration path:**
1. Add ObjectBox + VectorSearch to `gradle/libs.versions.toml`.
2. Extend `SourceFragment` with an `embedding: FloatArray?` field (nullable — fragments ingested
   before this change have no vector).
3. Add `GeminiEmbeddingService` wrapping `models/text-embedding-004`.
4. Update `SourceContextBuilder` to call `GeminiEmbeddingService` when storing new fragments.
5. Add `VectorFragmentRanker` implementing the same `FragmentRanker` interface as `TFIDFScorer`.
6. In `ContextAgent`, use `VectorFragmentRanker` when embeddings are available; fall back to
   `TFIDFScorer` for fragments without a vector or when offline.

---

## Future Work

- **ONNX on-device fallback:** Once a suitable small model is validated, generate embeddings
  locally so the full pipeline works offline without the TF-IDF fallback.
- **Re-evaluate sqlite-vec:** If an official KMP binding ships, the SQLDelight-native path may
  eliminate the ObjectBox dependency.
- **Hybrid retrieval:** BM25 (sparse) + embedding (dense) re-ranking is common in production RAG;
  the `TFIDFScorer` can serve as the BM25 stage if both scorers are kept.
