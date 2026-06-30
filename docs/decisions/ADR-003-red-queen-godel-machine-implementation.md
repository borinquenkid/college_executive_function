# ADR-003: Red Queen Gödel Machine (RQGM) Implementation Architecture

## Status
Accepted

## Date
2026-06-29

## Context

To enhance recursive self-improvement of agents inside the College Executive Function (CEF) application (e.g. for generating study plans, deadline decomposition, and task reviews), we require an execution architecture that supports co-evolving task agents alongside their evaluators/judges.

Standard self-improving agent architectures (like Darwin or Huxley Gödel Machines) evaluate candidate solutions against static benchmarks. This approach has key limitations:
1. **Lack of Labeled Benchmarks:** Many academic tasks (such as writing structured study summaries or organizing custom workflows) do not have direct test suites or ground-truth verifiers.
2. **AI Leniency & Reward Hacking:** Static LLM-based judges are prone to verbosity, position biases, and self-preference, which task agents eventually exploit.
3. **Execution Overhead:** Compiling and dynamically loading Kotlin binaries at runtime inside search loops (like [DecompositionOrchestrator](file:///Users/walterduquedeestrada/AndroidStudioProjects/college_executive_function/composeApp/src/commonMain/kotlin/com/borinquenterrier/cef/DecompositionOrchestrator.kt)) is resource-intensive and slow on the JVM.

---

## Decision

CEF will adopt the **Red Queen Gödel Machine (RQGM)** architecture, co-evolving task agents (generators) and evaluators (judges/critics) in epochs:

1. **Orchestration Layer (Kotlin):**
   - Kotlin will handle all state, interfaces, concurrency (Coroutines), and LLM API calls.
   - We define strict interfaces for tasks, solutions, and clades:
     - `Task` and `Solution` represent the domain boundaries.
     - `Agent` manages solving and prompt self-evolution.
     - `Evaluator` assesses task solutions and evolves its own grading criteria.
   - Evolution is managed by a `RedQueenHarness` running in distinct epochs:
     - **Within-Epoch Phase:** Task agents search and self-improve against a frozen `Evaluator` instance, maintaining epoch-level utility convergence guarantees.
     - **Epoch-Boundary Phase (The Red Queen Shift):** Challenger evaluators are generated and scored against a held-out ground-truth validation split. If a challenger statistically outperforms the current evaluator, it is promoted, and **selective erasure** is applied to discard utility history tied to the previous evaluator.
2. **Dynamic Scripting & Compilation (Groovy):**
   - For runtime script execution and fast solver iteration, task agents will produce **Groovy** scripts rather than raw Kotlin binaries.
   - Groovy executes directly on the JVM, allowing instant execution within the same memory space as the Kotlin orchestrator.
3. **Safe AST Sandboxing:**
   - Groovy execution will be sandboxed using `SecureASTCustomizer` and `CompilerConfiguration`.
   - Security policies will blacklist critical classes (such as `java.lang.System`, `java.lang.Runtime`, and file I/O operations) to prevent AI-generated scripts from escaping their execution boundaries.
   - Script execution errors, timeouts, and stack traces will be structured into the `Evaluation` payload and returned directly to the agent's meta-prompt for self-correction.

---

## Alternatives Considered

### Runtime Kotlin Compilation (`kotlin-compiler-embeddable`)
- **Pros:** Keeps the codebase 100% Kotlin.
- **Cons:** Slow compilation startup times inside quick search loops, heavy class-loading overhead, and lacks straightforward, declarative AST-level sandboxing utilities.
- **Rejected:** Runtime compilation latency degrades local search performance on user devices.

### Process-Isolated Python Execution
- **Pros:** High availability of sandbox tools (like Docker or gVisor) in server environments.
- **Cons:** High latency of launching processes, complex IPC (Inter-Process Communication) translation layers, and incompatible with native mobile platforms (Android/iOS) where running subprocesses is highly restricted.
- **Rejected:** Incompatible with CEF’s multi-platform Kotlin Multiplatform (KMP) targets.

---

## Consequences

### Positive
- **Mitigates Judge Bias:** Evolving evaluators prevents task agents from saturating static benchmarks or exploiting static prompt guides.
- **Execution Speed:** Executing Groovy scripts on `GroovyShell` inside the JVM provides near-instantaneous execution compared to dynamic Kotlin compilation.
- **AST Security:** Safely isolates generated code using JVM-native AST customization, protecting user systems.
- **Robust Self-Correction:** Direct feedback of execution stack traces into agent prompts allows automated recovery from compiler or syntax errors.

### Negative
- **Transitive Dependency:** Introduces Groovy runtime as a dependency in the JVM build configurations.
- **Sandbox Maintenance:** Requires continuous auditing of the `SecureASTCustomizer` whitelist/blacklist definitions to prevent sandboxing escapes or memory leak issues under intensive search runs.
