# TwoSampleMR command

Use the source-built JAR's `twosamplemr-tools` command for the qualified original
prepared-data and local-clumping workflows. Start with the
[examples](../../examples/twosamplemr-global/README.md). Read the
[qualification and remaining work](TWOSAMPLEMR_GLOBAL_CHECKPOINT_2026-11-21.md)
and [contingent catalog plan](TWOSAMPLEMR_CATALOG_PLAN.md).

```powershell
java -jar "C:/path/to/omicsmatic.jar" twosamplemr-tools --help
java -Xmx1g -jar "C:/path/to/omicsmatic.jar" twosamplemr-tools --request "D:/path/to/recipe.json"
java -Xmx1g -jar "C:/path/to/omicsmatic.jar" twosamplemr-tools --verify "D:/path/to/completed-analysis"
```

Build current source or restore the exact producer named in the checkpoint;
the older published0.4.0 release predates this command. RUN and REPLAY require
the original R package and declared runtime. VERIFY needs the matching JAR
and saved capture, without executing R.

Choose the exact profile in the request JSON. The command routes that profile
to its existing source-specific schema; it does not guess a paper's edition.

| Profile | Available scope and guide |
| --- | --- |
| `TwoSampleMR0.7.14-c08ab8a6-R4.6.1` | [Prepared MR, harmonization, sensitivity and MV preparation](TWOSAMPLEMR_WORKFLOWS.md) |
| `TwoSampleMR0.7.14-ieugwasr1.2.1-PLINK1.9-20260927-R4.6.1-Windows64` | [Original local clumping](TWOSAMPLEMR_LOCAL_CLUMPING.md) |
| `TwoSampleMR0.4.9-db55b97a-R4.6.1-Windows64` | [Historical formatting/prepared workflows](TWOSAMPLEMR_HISTORICAL_WORKFLOWS.md) |
| `TwoSampleMR0.5.6-8db843c0-R4.6.1-Windows64` | Same historical guide |
| `TwoSampleMR0.5.7-592ebe05-R4.6.1-Windows64` | Same historical guide |
| `TwoSampleMR0.6.8-806703f0-R4.6.1-Windows64` | Same historical guide |
| `TwoSampleMR0.6.8-ieugwasr1.2.1-PLINK1.9-20260927-R4.6.1-Windows64` | [Original0.6.8 local clumping](TWOSAMPLEMR_HISTORICAL_LOCAL_CLUMPING.md) |

The request fields, explicit source defaults, seed, capacities and preparation
notes remain those of the selected workflow. Runtime paths and library order
must be explicit. Historical0.4.9 uses the documented documentation-only build;
historical dependencies and actual paper execution commits are not established.
Do not substitute adjacent editions, provider functions, LD panels or units.
The native Java statistical helpers retain their separately documented Java API
scope; these commands execute the selected original package.

VERIFY and REPLAY read the capture's engine/profile, reject conflicts, then
delegate to its matching verifier/workflow. A malformed, incomplete or foreign
capture fails. Replay uses a NEW output outside the original capture, preserving
parent files. Schema-specific runtime overrides must still provide the exact
profile. The examples include a replay request and the underlying guides define
input columns and interpretation.

**SKIP3: DEPENDENT ANALYSIS SKIPPED.** Supply the named exact permitted runtime,
package/profile, PLINK or input. Download/private installation is authorized
when allowed; absence is not an installation prohibition. If installation or
execution is actually forbidden, retain the skip and explain what must be
supplied. Cell Ranger remains excluded by user instruction.

**FAIL2: NO COMPLETE RESULT.** Retain raw/partial/failed evidence, original
objects/errors and logs. Fix the named data/design, numerical, integrity,
capacity or process cause, then retry NEW output. Never silently repair IDs,
populations, allele/trait order, thresholds, versions, nonfinite values or
estimators/providers. Read WARNINGS.md,analysis-status.json,worker/report.json
and source logs before interpreting output. Preparation or empty clumping
selections are not causal estimates. Linux saved verification does not establish
Linux original-R execution; qualified R execution is Windows4.6.1.

The source-frozen components retain their earlier warning/scope text, including
historical references to global-registration work. This guide describes the
current command; catalog status is separate and remains contingent until its
guarded admission. Other API/provider scopes, whole-tool parity and whole-paper
reproduction are not established by this entrypoint. Both literature reports
remain withheld from Pages; exact runtimes, JARs, full primary text and complete
private evidence require a separate checksummed machine transfer.
