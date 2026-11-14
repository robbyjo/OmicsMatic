# Current rstatix catalog route: supervisor revision 2

**For new rstatix analyses, use `tools/literature/rstatix072_workflow_v2/run.py`.** The catalog now points to the qualified recovery revision. It executes the unchanged original rstatix 0.7.2 namespace using the same permitted private Linux/WSL R 4.5.3 provider and exact trusted caller script. This is a Python source entry point, not a standalone JAR; the canonical executable and native Java Levene component are unchanged.

The catalog still has **250 papers, 432 named families, 2,671 observations and 1,156 individually reviewed uses**; 1,515 uses await review. There are **109 scoped new integrations: 20 native subsets and 89 upstream workflows**, or 112 including three existing adapters; 264 named families remain missing. This routing change adds **zero** family, native-method or review credit. All paper observations, individual reviews and summary bytes remain unchanged. Historical component/catalog guides retain their original source and evidence hashes. The full review and implementation goal remains open.

## Install and run

Use the [permitted private-provider instructions](RSTATIX072_DEVELOPMENT.md). Windows original execution skips: run native Linux Python/R, including through permitted WSL. Install/download only where the operating environment permits. Analysis itself installs nothing. If prerequisites are missing or forbidden, skip the dependent original analysis and resolve the named requirement; never substitute native Levene for another original test.

```sh
python3 tools/literature/rstatix072_workflow_v2/make_example.py \
  --provider build/my-ready-rstatix-provider \
  --out build/my-new-rstatix-example
python3 tools/literature/rstatix072_workflow_v2/run.py \
  --request build/my-new-rstatix-example/request.json
```

Use NEW output directories. Existing exact `version: 0.7.2` / `mode: SCRIPT` requests remain usable with the new entry point and a NEW output. The request must declare complete inputs, every expected result, provenance/population, literal exports/dependencies, execution/trusted-code policies and resource capacities. The example retains all 24 synthetic subjects and all seven original operations. It does not reconstruct any author's data or settings.

## Warnings, errors and required actions

**SKIP3 skips dependent analysis** when matching source, runtime, provider, input, literal export/package, permitted installation/download or execution is unavailable/forbidden. Supply only permitted exact prerequisites and retry NEW paths. A READY provider is not a result. Trusted scripts run with account permissions; they are not in an OS sandbox or total CPU/RSS enclosure. Owned-group supervision does not cover child-created separate sessions.

**FAIL2 admits no complete result.** Retain all inputs, full/partial original objects, warnings/errors, conditions, raw worker reports, controller tracebacks, full console/process logs, inventories and status. Correct the named cause and retry NEW paths. Do not drop failed cases or relabel a partial population complete. Original `warn=2` semantics remain unchanged.

**Incomplete condition totals are unavailable, never zero.** Read `analysis-status.json`, `WARNINGS.md`, `MANIFEST.json`, `WORKER_DIAGNOSTIC_ERROR.txt`, `SUPERVISOR_ERROR.txt`, full `process.stdout`/`process.stderr`, `diagnostics/conditions.json` and complete original RDS objects. After an incomplete worker report, the three original condition totals are JSON `null`, `condition_counts_complete` is false and `warning_review_required` is true. The raw malformed report is retained without repair, including after an original process exits zero.

The historical supervisor correctly refused the truncated-report control but could omit its warning and manifest files. Revision2 preserves those records when the filesystem permits. It also tolerates only a temporary child directory disappearing during monitoring; missing owned roots, persistent capacity violations and forbidden links remain failures. If a blocked path or OS restriction prevents writing a warning/status/inventory file, the controller attempts other records independently and prints the named failure and recovery action to the complete console. **Keep that console log.** Resolve the filesystem prerequisite and retry a NEW directory. Unwritable files cannot be guaranteed; an incomplete inventory is not complete evidence.

## Six unchanged primary-source roles

All 12 named paragraphs across the same six papers were checked against the exact 250 cached primary XML identities. No new source reading or paper review is claimed.

| Paper | Established role and retained uncertainty |
| --- | --- |
| P032 | Adjusted p-values for MED12 CAR/GSVA scores; the exact test, correction method and family remain unknown. |
| P040 | Two-sided t-tests of IMC cell abundance; grouping, variance assumptions and pairing remain unresolved. ggpubr, GeomxTools and GraphPad roles remain separate. |
| P054 | Normality/variance checks, correlation, group/pairwise/paired and one-sided comparisons; all six named analysis paragraphs remain preserved. The printed `wilcoxon_test` is not exported by selected 0.7.2 (`wilcox_test` is): the literal missing export skips analysis, without spelling repair. Author pairing, direction and families remain inputs. |
| P087 | Original `wilcox_test`/BH for ARLD/PBC OLR1 CPM comparisons; exact groups, families and tie/correction options remain author inputs. |
| P098 | Pairwise `t_test`/Holm on supplied VST-normalized expression; variance assumptions and comparison families remain inputs. DESeq2 preprocessing and separate signature tests remain separate. |
| P241 | Generic statistical use and a separate Web resources link; edition, test, design and family remain unreported. The link is not another execution. |

Five papers report 0.7.2; selected 0.7.2 for P241 is a caller choice. R 4.5.3/car 3.1-5 does not establish equivalence to historical author runtimes. Original p-value rounding, complete returned objects, formulas, grouping, conditions and RNG behavior remain original R behavior. Native one-way mean/median-centered Levene stays separate. No paper reproduction is established by this routing.

## Qualification and verification

Read the [revision qualification/recovery guide](RSTATIX072_WORKFLOW_REVISION2.md). All 248 original calls, 250 declared result files, 444 rows and 5,664 cells matched direct original R and the predecessor at tolerance zero. All 31 earlier controls plus eight malformed-report and two blocked-recording cases passed, alongside the Windows refusal, seven Windows/Linux monitor controls, the complete seven-operation example and four frozen-verifier refusals. The 5,185-member main archive and auxiliary archive retain failed attempts, both link identities and blocked empty-directory identities. Full RDS objects remain retained; opaque byte parity and formula-environment pointer identity are not claimed.

```sh
python3 tools/literature/rstatix_revision2_catalog/verify.py \
  --rescan-sources \
  --out build/literature-review/monocle-runtime/my-new-rstatix-revision2-catalog-check
```

This gate executes no new R analysis or installation. It checks exact current routing, all unchanged paper/review records, the full 250-source census, unchanged original statistical/worker sources, complete scientific/control evidence, lossless negative-index inputs and all earlier cumulative gates. Earlier gates receive only their exact eight historical catalog metadata inputs; scientific code, verifier definitions, input/result bytes and source profiles are never overlaid. SHA-256 establishes integrity, not authenticity or biological validity.

Other current tool restrictions remain visible: ggpubr failed statistical layers and silent empty-layer eligibility; QuPath's edition/image/model limits; the unresolved reported AllelicSeries 1.1.2 edition; unknown palette/figure assignments; and separate ImageJ distributions/plugins. Cell Ranger and Cell Ranger ARC remain excluded by user request. Installation restrictions skip the dependent analysis with recovery instructions.
