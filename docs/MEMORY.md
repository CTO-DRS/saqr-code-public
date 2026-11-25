# محرك الذاكرة · Saqr Memory Engine (v0.17.0)

> كل ميزة جديدة خلف مفتاح في الإعدادات ← «محرك الذاكرة والموجّه». الميزات التي تغيّر ما يراه النموذج (الذاكرة ذات الصلة، ملخّص الجلسة، الذاكرة العامة) **مُطفأة افتراضياً**؛ فالترقية تتصرّف كإصدار 0.16 تماماً حتى تفعّلها. «دروس من الإصلاحات» مفعّلة لأنها تضيف ولا تحذف، وتظهر في ورقة الذاكرة.
>
> Every new feature sits behind a switch in Settings → «Memory engine & router». The ones that change what the model sees (relevant memory, session summary, global memory) are **off by default**, so an upgrade behaves exactly like 0.16 until you opt in. «Lessons from fixes» is on: it only adds entries, visible in the Memory sheet.

Code: `agent/MemoryBank.kt` (typed project/global bank), `agent/memory/MemoryRecall.kt` (BM25), `agent/memory/MemoryEngine.kt` (`MemoryEngine`, `MemorySafety`, `Lessons`, `SessionMemory`, `SessionSummary`), wired into `AgentSession`, `ProjectIndex` (invalidation hook), `ChatPersistence` (`SessionData.summary`), `ui/chat/MemorySheet.kt`, `ui/MemoryRouterSettings.kt`. Tests: `V017FeaturesTest`, `V017SettingsTest`.

## 1. ثلاث طبقات · Three layers

| Layer · الطبقة | Where · أين | What · ماذا | Switch |
|---|---|---|---|
| Session · الجلسة | the session file (`sessions/…json`, key `summary`) | the chat itself, `ContextCompactor` inside a turn, and — new — a **rolling summary** of old turns | `sessionSummary` (off) |
| Project · المشروع | `<project>/.saqr/memory.json` (schema 2) | typed entries: decision, architecture, dependency, convention, lesson, preference, note | always (as before) |
| Long-term · طويلة المدى | app-private `files/memory/global.json` | only the user's own preferences, across projects | `globalMemory` (off) |

### Session layer
After a finished turn, when the verbatim part of the session is over ~60k chars and has more than 4 user turns, the older turns (plus the previous summary — that is what makes it *rolling*) are summarised on the cheap compaction model and stored with the session as `{"text","turns","anchor","at"}`. On the next send the model's history is `[summary note] + the last turns verbatim` instead of everything. The summary is anchored on the first kept user prompt; if the chat no longer matches (a checkpoint restore, another build editing it) the summary is ignored and the full history is rebuilt — the pre-0.17 behaviour. Old session files (no `summary`) load unchanged. The summary text goes through the secret scrub before it is stored. The Memory sheet's **Session** tab shows it and can clear it.

### Project layer
`memory.json` moves to schema 2: every entry keeps `key, value, kind, createdAt, updatedAt` (the v1 fields, unchanged) and gains `type`, `source` (`agent`, `user`, `auto:gate`, `legacy`), `files` (project-relative refs, ≤ 8), `hits` (dedupe counter) and `stale`.

- **Migration:** a v1 file loads as is; every old entry becomes type `note` with source `legacy`, its kind and timestamps untouched, and the prompt rendering is identical. The next write stores schema 2.
- **Caps:** 200 entries (was 60), key 120 chars (was 80), value 800 chars (was 500). With relevance on, a larger bank no longer costs prompt space. Over the cap, stale entries are evicted first, then the oldest-updated.
- **`memory_save`** gains optional `type`, `files` and `scope` (`project` default, `global`); `kind` keeps working. **`memory_forget`** keeps working; `scope: "global"` forgets from the global layer when it is on.
- **Lessons (error → solution):** when a run's completion gate goes from FAILED to PASSED, the first meaningful error line of the failing lint/tests becomes the lesson's signature (paths reduced to the file name; line/column numbers, hex addresses and long numbers stripped), and the fix — files changed since the failure plus the coder's own one-line summary — its solution. Same signature (or ≥ 75% token overlap) ⇒ the existing lesson is updated (`hits + 1`, newest solution) instead of a duplicate. A quiet chat pill says «Lesson saved / reinforced». Matching lessons (≥ 2 shared tokens with the current error) are appended to the coder's `[Verifier]` feedback and to the **Debugger**'s context in Chief mode. Switch: `memoryLessons` (on).
- **Stale file refs:** every applied file change invalidates the `ProjectIndex`; its next rebuild (and opening the Memory sheet) re-checks each entry's `files`. Missing files mark the entry `stale` — tagged in the prompt and the sheet, half weight in retrieval, evicted first — never deleted silently.

### Long-term (global) layer
App-private, outside every project, so it never ends up in a repository. It holds only preferences: a global save is forced to type `preference`, file refs are refused, and project entries can never be written there. It is written only (a) by `memory_save` with `scope: "global"` while `globalMemory` is on, or (b) when the user taps «Make global» on a project preference in the Memory sheet. With the switch off a global save is refused with a clear message and nothing is written; the global block is not injected. One bank object per file is shared by all project sessions so one project's save is never overwritten by another's stale copy.

**Isolation:** each project's bank lives in its own `.saqr/`; the only shared layer is the opt-in preferences file. Tested in `projectMemoryNeverLeaksAndGlobalIsOptIn`.

## 2. الاسترجاع بالصلة · Retrieval by relevance

`MemoryRecall` is a few-KB BM25 index (k1 = 1.2, b = 0.75) — no embedding library, no ML runtime. Text is normalized with `SlashCommands.normalize` then `MemoryBank.normalize` (alef forms, yaa, taa marbouta, diacritics, tatweel), camelCase split, stop words dropped, and lightly stemmed (Arabic: `ال/وال/بال/كال/فال/لل` prefixes and `ات/ون/ين/ها/هم/كم/نا/ه/ي` suffixes, never below 3 letters; English: plural/-ing/-ed). The document is key + value + type + file names.

With `memoryRelevance` on, the prompt gets: up to 4 newest **preferences** (standing rules for every answer) + the **top-K** entries for the query (`memoryTopK`, default 8, 1–30). The query is the user's request, recent failing tool output and — for a team role — its subtask. If fewer than K entries match, the rest is filled only with standing project rules (decisions, architecture, conventions), never with unrelated notes or lessons. With the switch off the whole bank is injected exactly as in 0.13–0.16.

Provider embeddings are **not** used in this release (see Remaining).

## 3. حارس الأسرار · Secret guard on every layer

`MemorySafety` = the v0.13 heuristic (`password=`/`token:`/known key shapes/Arabic secret words/long base64) **plus** the user's real configured secrets (every provider API key and the GitHub token, checked through `Redact`). It runs on `memory_save` for both layers, on lesson capture (the failing output and the solution are scrubbed first — configured secrets redacted, secret-looking lines dropped — and the save itself refuses), on file refs, and on the session summary before it is stored. Secrets are never written to memory, logs or the audit file by this feature.

## 4. ورقة الذاكرة · Memory sheet

Tabs **Project · Global · Session**; search (BM25-ranked, then plain contains) when there are more than 4 entries; a type filter; tags for type and «stale»; the file refs; delete per entry (exact entry, not a fuzzy forget); clear all; «Make global» on a preference when the global layer is on; the session summary with «Clear summary».

## 5. الإعدادات · Settings

| Key | Default | Effect |
|---|---|---|
| `memoryRelevance` | off | top-K injection instead of the whole bank |
| `memoryTopK` | 8 | K (1–30) |
| `sessionSummary` | off | rolling session summary |
| `memoryLessons` | on | capture error → solution lessons, surface matches to the Debugger / Verifier |
| `globalMemory` | off | the cross-project preferences layer |

## 6. المتبقي · Remaining

- Optional provider embeddings (only when the configured provider supports them and a setting enables it) — not built; BM25 is the only retrieval.
- The rolling summary and lesson capture are unit-tested with synthetic chats/outputs and wired into the real loop, but were not exercised against a live provider on a device in this release.
- The new settings are not part of the encrypted repository sync whitelist yet.
