# AdgeistKit — Benchmark Tracking Plan

**Purpose:** This document defines the metrics we track to quantify what it costs a publisher's app to integrate the AdgeistKit SDK, and how we track each one. It serves two goals:

1. **Publisher-facing proof** — credible, measured answers to "what does adding AdgeistKit cost my app?" (size, startup time, memory, etc.)
2. **Internal regression tracking** — catching any release-to-release degradation before publishers do.

All measurements follow the same discipline used by Google Play SDK Console and Emerge Tools' SDK Index: we measure the **delta** a host app pays (app *with* SDK minus the same app *without* SDK), on a **minified release build** — exactly what a publisher ships to users.

**How measurements are made:** A dedicated benchmark harness app (`benchmarks/`) is built in identical variants — `withSdk` (AdgeistKit integrated), `noSdk` (no SDK), and `depsOnly` (shared dependencies only, used for Metric 2) — and metrics are reported as the difference between them (plus one absolute number: the isolated `initialize()` cost in Metric 4). All runs are **scripted from the terminal** (no manual measurement), producing timestamped JSON results that can be compared across SDK versions.

---

## List of metrics

| # | Metric |
|---|--------|
| 1 | APK Download-Size Delta |
| 2 | Marginal Size (dependencies already present) |
| 3 | Dex Methods / Permissions / Transitive Dependencies |
| 4 | Startup Impact: Cold/Warm Startup Delta + `initialize()` Cost in Isolation |
| 5 | Memory Footprint (init-only) |

---

## Metric 1 — APK Download-Size Delta

### What it is
The number of kilobytes a publisher's app download grows when AdgeistKit is added. This is the single most-quoted SDK integration cost in the industry — Google Play SDK Console shows it to every developer evaluating an SDK.

**Important:** this is *not* the size of our AAR file. The AAR size is misleading in both directions:
- It **understates** the cost, because our transitive dependencies (OkHttp, Gson, coroutines) also get pulled into the publisher's app.
- It **overstates** the cost, because R8/ProGuard strips unused code and the Play Store compresses the download.

The honest number is: build the same app twice — once with the SDK, once without — as minified release builds, and subtract the estimated Play Store download sizes.

### How we measure it
- **Tool:** `apkanalyzer apk download-size` (reports the estimated Play Store *download* size, not raw file size) + `diffuse` (breaks the delta down by component: dex code, resources, which dependency added what).
- **Build:** Both harness flavors (`withSdk`, `noSdk`) built as release with R8 minification and resource shrinking on — what a publisher actually ships.
- **Formula:** `withSdk download size − noSdk download size = X KB`
- **No device required** — this is static build analysis; it runs on a laptop and is fully deterministic (same inputs → same bytes).

### The process (one terminal command)
```
cd benchmarks && ./scripts/run_size.sh
```

Comparing two runs: `report.py --baseline <previous-run>` diffs the results and flags any regression above **5%**.

### How it helps
- **Sales / adoption:** Publishers routinely reject ad SDKs over size. A measured, reproducible "AdgeistKit adds ~X KB to your download" — measured the same way Play SDK Console does it — is far more credible than quoting an AAR size.
- **Regression tracking:** If a release jumps in size because of a new dependency, we catch it before publishers do.
- **Optimization roadmap:** The `diffuse` breakdown shows exactly where the kilobytes live (e.g. how much our blanket `-keep class com.adgeistkit.**` ProGuard rule costs), so optimization decisions are driven by data.

### Output
`results/<timestamp>/size.json` containing: total delta (KB), per-component breakdown, SDK version, and build metadata. Headline line in the report: **"withSdk − noSdk = X KB download size."**

---

## Metric 2 — Marginal Size (dependencies already present)

### What it is
Metric 1 assumes the worst case: the host app has *none* of our dependencies, so it pays for AdgeistKit **plus** OkHttp, Gson, and coroutines. In reality, most production Android apps already use OkHttp and coroutines (and often Gson) — for those apps the shared libraries are already in their APK, so adding AdgeistKit costs far less.

That smaller number is the **marginal size** (the term Emerge Tools' SDK Index uses). It answers a different publisher question: not "what's the maximum cost?" but "what will it *actually* cost my app, which already has a networking stack?"

### How we measure it
- **Extra flavor:** A third harness flavor, `depsOnly` — an app that declares OkHttp, Gson, and coroutines (and uses them enough that R8 keeps them) but does **not** include AdgeistKit.
- **Formulas:**
  - `withSdk − noSdk` = worst-case delta (Metric 1)
  - `withSdk − depsOnly` = **marginal delta** (Metric 2) — the pure cost of AdgeistKit's own code
- **Tools:** Same as Metric 1 — `apkanalyzer apk download-size` on minified release builds, `diffuse` for the breakdown. No device required; fully deterministic.

### The process (same command as Metric 1)
```
cd benchmarks && ./scripts/run_size.sh
```
The script builds the `depsOnly` flavor alongside the other two and reports both deltas in the same `size.json`.

### When we run it
Same triggers as Metric 1 — every release (mandatory), dependency/ProGuard PRs (recommended), once now for the baseline. It adds no extra runs; it's part of the same size measurement.

### How it helps
- **Sales / adoption:** Gives a two-line pitch instead of one scary number — "worst case X KB; if you already use OkHttp/Gson/coroutines (most apps do), only Y KB." Y is typically a fraction of X, and that's often the difference between a publisher saying yes or no.
- **Engineering insight:** The gap between X and Y shows how much of our footprint is *dependencies* versus *our own code*. If dependencies dominate, the optimization leverage is in trimming or making dependencies optional — not in shrinking our code.

### Output
Included in the same `results/<timestamp>/size.json` as Metric 1: marginal delta (KB) alongside the worst-case delta. Headline line: **"withSdk − depsOnly = Y KB marginal size."**

---

## Metric 3 — Dex Methods / Permissions / Transitive Dependencies

### What it is
The "metadata cost" of the SDK — three things a publisher's engineering team checks before approving any SDK, beyond raw kilobytes:

1. **Dex method count** — how many methods AdgeistKit (plus its dependencies) adds to the app. Android historically had a 65,536-method limit per dex file; method count remains a standard code-footprint measure, and teams near the limit care a lot.
2. **Permissions** — whether integrating the SDK silently adds Android permissions to the publisher's app. Manifests merge at build time, so an SDK *can* introduce permissions the publisher never asked for (e.g. `READ_PHONE_STATE`). Surprise permissions can trigger Play Store review issues and user distrust — publishers want zero surprises here.
3. **Transitive dependency list** — the exact libraries and versions AdgeistKit pulls in. Publishers audit this for version conflicts with their own dependencies, licensing, and security posture.

### How we measure it
All static analysis, in the same run as Metrics 1–2:
- **Method count:** `apkanalyzer dex packages` on both `withSdk` and `noSdk` release APKs; diff the totals. Per-package counts show *which* packages contribute what.
- **Permissions:** `aapt2 dump permissions` on both APKs; diff the lists — the output is literally "these permissions appear only in the withSdk build."
- **Dependencies:** read from the published POM/module metadata — the authoritative list of what Maven pulls in, with versions.

Because it diffs the merged final APK, it catches anything a transitive dependency sneaks in — not just what our own manifest declares. No device required; deterministic.

### The process (same command as Metrics 1–2)
```
cd benchmarks && ./scripts/run_size.sh
```
The script collects method counts, the permission diff, and the dependency list into the same build report.

### When we run it
Same triggers as Metric 1 — every release (mandatory), dependency/ProGuard/manifest PRs (recommended), once now for the baseline. No extra runs; part of the same size measurement.

### How it helps
- **Publisher trust / integration review:** This is the section a publisher's tech lead reads before approving the SDK. Having it pre-answered ("adds N methods, these permissions, these dependencies at these versions") shortens their evaluation.
- **Regression tracking:** A new permission or new transitive dependency appearing between releases is a red-flag event — this diff makes it impossible to miss at release time.
- **Play compliance:** Play SDK Console publishes exactly this metadata to developers; tracking it ourselves keeps us aligned with what Google will show publishers anyway.

### Output
Included in the build report alongside Metrics 1–2: method-count delta, permission diff (expected: only what we document), and the transitive dependency list with versions.

---

## Metric 4 — Startup Impact: Cold/Warm Startup Delta + `initialize()` Cost in Isolation

### What it is
How much AdgeistKit slows the publisher's app launch — measured at two layers, because the answer depends on where the host app calls `AdgeistCore.initialize()`:

1. **Cold/warm startup delta** — how much longer the app takes to launch when the SDK is integrated and initialized at launch (the common pattern: `initialize()` in `Application.onCreate()` or the launcher activity). *Cold start* = process created from scratch (the expensive path, and the number publishers ask about); *warm start* = process alive, activity recreated (the most common real-world launch). Startup time is tracked in Play Vitals and slow-starting apps lose Play Store visibility, so publishers are protective of it.
2. **`initialize()` cost in isolation** — the duration of the `initialize()` call itself, measured via a trace section wrapped around it. This number is **placement-independent**: if a host app initializes on an inner screen instead of at launch, their startup delta is ~0 and this is the cost that moves to that screen. It is therefore the primary publisher-facing figure — valid wherever the host calls it.

The startup delta also captures the **passive cost** of merely shipping the SDK in the APK (larger dex to load, class verification, any auto-running initializers). AdgeistKit has no ContentProvider and no auto-init, so this should be ~0 — the benchmark *proves* that claim, and catches any future release that accidentally adds eager startup work.

### How we measure it
- **Tool:** Jetpack **Macrobenchmark** — Google's official startup benchmarking library, same methodology behind Play Vitals — with two metrics in the same run:
  - `StartupTimingMetric` → Time To Initial Display (process start → first frame drawn)
  - `TraceSectionMetric("bench#init")` → duration of the `initialize()` call, wrapped in a trace section on the harness side
- **Scenario:** launching a deliberately minimal `BareActivity` — bare on purpose, so the only difference between flavors is the SDK. Both flavors (`withSdk`, `noSdk`), both COLD and WARM modes.
- **Formulas:**
  - `withSdk launch time − noSdk launch time` = startup delta (device speed cancels out)
  - `bench#init` section duration = absolute `initialize()` cost
- **Rigor:** physical device required (emulator timings are meaningless). 15 iterations per configuration; report **median** plus P50/P90 — single launches vary by tens of ms. Device preflight (battery ≥80%, temperature check, animations off), cooldowns between runs, Macrobenchmark's built-in thermal-throttle detection, device fingerprint recorded in every report.

### The process
```
cd benchmarks && ./scripts/run_macro.sh
```
Phone connected via USB. The script runs the preflight checks, executes the startup benchmarks for both flavors in COLD and WARM modes, and harvests `benchmarkData.json` into `results/`.

### When we run it
| Trigger | Requirement |
|---------|-------------|
| Every SDK release, before publishing | **Mandatory** — compared against previous release; >5% regression flagged |
| Any PR touching `initialize()` or startup-path code | Recommended |
| Once, now | Establishes the baseline |
| CI (later) | Same script, on a dedicated device |

### How it helps
- **Sales / adoption:** the claim becomes "AdgeistKit adds ~Y ms to cold start; `initialize()` itself costs Z ms wherever you call it" — measured with Google's own methodology. Publishers who init at launch read Y; publishers who init lazily read Z and know their startup is untouched. If Y is near zero (likely — our init does no network and registers no ContentProvider), that is a strong selling point stated explicitly.
- **Regression tracking:** startup is where SDK bloat classically creeps in — someone adds disk I/O or eager work to `initialize()` and nobody notices until publishers complain. Release-over-release comparison catches it immediately, and the passive-cost check catches accidental auto-init.
- **Honesty guardrail:** if the launch-level delta is within measurement noise, the report says so — the isolated `bench#init` number remains precise even when the delta is lost in noise.

### Output
`results/<timestamp>/benchmarkData.json` → report lines: **"Cold start delta: Y ms (median of 15); warm start delta: W ms; `initialize()` cost: Z ms (placement-independent)"**, with P50/P90 and device metadata.

---

## Metric 5 — Memory Footprint (init-only)

### What it is
How much RAM the SDK adds to the host app once `AdgeistCore.initialize()` has run. Publishers care because memory pressure is what gets apps killed in the background (users notice they "restart" instead of resuming) and causes OutOfMemory crashes on low-end devices. Play Vitals tracks app memory via PSS; Emerge measures SDK memory the same delta way we do.

As with every metric, it is a **delta**: `withSdk` memory minus `noSdk` memory at the same checkpoint, so the app-and-OS baseline cancels out and what remains is the SDK's footprint.

**Scope note:** this metric is deliberately limited to the initialization footprint. Ad-rendering memory checkpoints (WebView cost, `AdSessionStore` eviction, leak detection with LeakCanary) are excluded from the current benchmark scope along with the other ad-path scenarios; they can be added later if that scope changes.

### How we measure it
- **Tool:** `adb shell dumpsys meminfo -d <package>` — the standard Android memory dump. We record **PSS** (Proportional Set Size — the Android-standard "fair share" memory number that Play Vitals uses).
- **Checkpoint:** post-`initialize()` — the harness app is driven into a known state via `am broadcast` commands (`BenchControlReceiver`), so the script captures the dump at exactly the right moment with no manual tapping.
- **Formula:** `withSdk PSS − noSdk PSS` at the same checkpoint = X MB
- **Rigor:** physical device; **5 repeats, median reported** — memory measurements are noisy (GC timing, allocator behavior). Release builds, same flavors as the other metrics.

### The process
```
cd benchmarks && ./scripts/run_memory.sh
```
Device connected via USB. The script installs each flavor, launches it, triggers `initialize()` via broadcast checkpoint, captures `dumpsys meminfo`, repeats 5×, and writes medians to `results/`.

### When we run it
| Trigger | Requirement |
|---------|-------------|
| Every SDK release, before publishing | **Mandatory** — compared against previous release; >5% regression flagged |
| Any PR touching `initialize()` or object caching/singletons | Recommended |
| Once, now | Establishes the baseline |
| CI (later) | Same script, on a dedicated device |

### How it helps
- **Sales / adoption:** "AdgeistKit adds ~X MB after initialization" — a measured answer to a standard publisher evaluation question, especially for apps targeting low-end devices.
- **Regression tracking:** if a release starts allocating heavy state at init (bigger caches, eager singletons, preloaded resources), the delta jumps and we catch it before publishers do.

### Output
`results/<timestamp>/` memory JSON → report line: **"Post-init memory delta: X MB PSS (median of 5)"**, with device metadata.

---

## How we will run this (execution plan)

### One harness app
All metrics come from a single purpose-built **benchmark harness app** that lives in the repo under `benchmarks/`. It is built in three variants ("flavors") from the **same codebase** — the only difference between them is the dependency list:

| Flavor | Contains | Used for |
|--------|----------|----------|
| `withSdk` | AdgeistKit integrated (consumed as a published AAR, exactly like a real publisher) | every metric |
| `noSdk` | nothing SDK-related | the baseline every delta is measured against |
| `depsOnly` | OkHttp/Gson/coroutines but no SDK | Metric 2 (marginal size) |

The harness is a **standalone Gradle build** (own toolchain, independent of the SDK's build), and it consumes AdgeistKit from a Maven repository — the same way a publisher does — so consumer ProGuard rules and transitive dependencies are all accounted for. This also makes version-vs-version comparison trivial: point the harness at 1.1.30, then at 1.1.31, re-run, diff.

### Two execution lanes

**Lane 1 — Build metrics (Metrics 1–3): automated in GitHub Actions.**
These need no device, so they run fully automated in CI:
- A `benchmark-size` workflow runs on every pull request that touches the SDK or its dependencies, and on demand.
- It builds all three flavors, measures size / method count / permissions / dependencies, and produces `report.md` + `size.json`.
- The report is uploaded as a **workflow artifact** (downloadable from the workflow run page).
- A **baseline JSON per release** is committed to the repo (`benchmarks/baselines/`). Every CI run diffs against the latest baseline and **fails the workflow if the size delta grows more than 5%** — so a size regression blocks the PR instead of being discovered after release. After each SDK release, the baseline is refreshed.

**Lane 2 — Device metrics (Metrics 4–5): scripted local runs.**
Startup and memory need a **physical phone** (emulator timings are not credible), which standard GitHub runners don't have. For now these we can run from the terminal with a phone connected via USB:
- `run_macro.sh` → startup benchmarks (Metric 4)
- `run_memory.sh` → memory checkpoints (Metric 5)
- `run_all.sh` → everything, merged into one report

These are **mandatory before every SDK release**; results are saved timestamped and diffed against the previous release with the same >5% rule. The scripts are written CI-portable, so device automation (Firebase Test Lab or a self-hosted runner with an attached phone) can be added later without rework — that decision is deliberately deferred until the numbers stabilize.

### Reporting
Every run — CI or local — produces the same machine-readable JSON plus a human-readable `report.md`: a headline table (size delta, marginal size, startup delta, init cost, memory delta), the device/build metadata needed to reproduce it, and a comparison column against the previous release with regressions flagged.
