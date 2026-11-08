# Gemini thinking policy (2026-11-08)

Origin: T4 gold-set run. `SPMS1185` (2.5K chars) failed all 5 attempts on `gemini-3.8-flash` at the 120 s request
ceiling, three runs in a row. Not input size: the model spent its time on hidden thinking tokens.

## Probe (SPMS1185 text, simplified extraction prompt, one request per cell, `GEMINI_API_KEY`)

| Model | Setting | Time | Thought tokens | Result |
|---|---|---|---|---|
| 3.8-flash | default | 59 s | 7,929 | ok |
| 3.8-flash | budget 0 / budget 1024 / level low | 10 / 8 / 9 s | 0 | ok |
| 3.8-flash | level minimal | - | - | 400 not supported |
| 3.6-flash | default | 23 s | 4,849 | ok |
| 3.6-flash | budget 0 / 1024, level low / minimal | 6-7 s | 0 | ok |
| 2.5-flash | budget 0 / 1024 | 6 / 9 s | 0 / 864 | ok |
| 2.5-flash | level low / minimal | - | - | 400 not supported |

Single run per cell, simplified prompt, event counts varied 32-39 between cells: not a quality measurement.
The gold set is the quality gate.

## Design

`ThinkingPolicy.kt`: `ModelFamily x ThinkingEffort -> thinkingConfig JSON`. Only probed cells are listed; an absent
cell sends nothing. Extraction (`PromptFamily.EVENT_EXTRACTION`) asks for `LOW`; everything else keeps `DEFAULT`.
`gemini-2.5-*-lite` and unknown models are `OTHER` (not probed). Adding a model family or effort level means a new
enum value, a probe, and a cell in `ThinkingMatrixTest`, which fails until the pair is decided.
