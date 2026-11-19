# Native weighted-Z checkpoint — 18 November 2026

The active goal resumed after the [METAL handover](HANDOVER_2026-11-18_METAL.md)
was pushed as `4e2bc8b3b6d1f0d3637d19c39ac3567540ceccb0`. The previous goal turn
made progress by preserving and publishing all current task work. This resumed
turn restored and qualified the independent native mathematical component.
**The full research/toolbox goal remains unfinished.**

## Result

`org.jlinalg.meta.SampleSizeWeightedMetaAnalysis` is now implemented in executable
source, with signed-Z, two-sided P/direction and natural-log-P/direction entry
points. The original handover draft and its hashes remain unchanged. Applied
Java source was normalized to the repository's LF policy; no mathematical
source tokens changed. Read the
[beginner guide](../vignettes/sample-size-weighted-meta-analysis.md),
[qualification](weighted-z-development/README.md) and
[warnings](weighted-z-development/WARNINGS.md).

Actual packaged Windows and Linux runs passed the same seven focused tests.
There are 69 finite R reference models / 1,844 fields, one expected complete-model
refusal / 20 retained raw R fields, and two further tiny-Q R controls / ten
fields. The complete fixture and tiny-Q model payloads are byte-identical across
platforms. A public Java example executes on both platforms. Every one of
23,031 pre-existing JAR members is unchanged; four new component classes appear.

Unmodified original METAL controls agree on 62 ordinary models in both normal
and log-output modes at 844 explicitly rounded numerical comparisons. The
24 single-cohort source conventions remain distinct. A separate tiny-Q run
demonstrates original P=1 versus native P approximately 0.999887. A boundary
run exits 0 but emits nonfinite output and omits invalid rows: it remains
**FAIL2, no complete result**. All 136 privately held original source files
were rehashed unchanged after these controls. No original source, executable,
archive or bundled example data is redistributed.

The new source-bound verifier passes and independently rechecks all reference
and original numerical comparisons from the saved evidence. Separate copied
snapshot controls check intact success, changed-source failure, missing-fixture
SKIP3, changed-archive failure, wrong-JAR failure, reused-output refusal and
restored success. The 527-payload qualification archive was fully read back.
These are synthetic numerical/source controls, not biological validation.

## Scope and remaining work

The catalog stays at **129 new scoped integrations: 24 native and 105 upstream**,
with 1,266 individually reviewed positive uses and 1,429 observations awaiting
review. There are 259 missing and 46 method-overlap definitions. **METAL remains
method-overlap; this SDK component adds no family credit.** The 250-paper frozen
cohort, catalog data, original runtime and all historical verifiers are unchanged.

Next, implement the typed captured **original METAL runtime adapter**, including
strict input/runtime capture and admission beyond exit code. Qualify the actual
paper-relevant scheme, allele orientation, genomic control, overlap,
heterogeneity and pooled-frequency roles. Do not duplicate existing native
inverse-variance `org.jlinalg.meta.MetaAnalysis`. Independently audit all 24
paper roles, editions and settings before any METAL family admission. Original
2011-03-25 editions named by some papers are not reproduced by this 2020 runtime.

Retain the numerical conventions described in the guide: native logs are
natural; original METAL log output is base 10. Original `LOGPVALUE` is output
control, not a log-input option. Native heterogeneity is null for one cohort
and uses the ordinary small-Q chi-square tail. Do not silently change these to
force whole-tool parity. Supply aligned independent cohorts and trustworthy
effective sample sizes explicitly; the API does not infer them.

Continue the broader high-impact priorities and 1,429 outstanding semantic
reviews from the handover afterward. Do not publish a release or either
unfinished literature HTML page. Existing user permissions and Cell Ranger
exclusion persist. Install permitted prerequisites or clearly skip dependent
analysis with the exact recovery action if installation is unavailable.

## Locations and recovery

Authoritative worktree:
`D:/projects/JLinAlg/build/high-impact-stats-1`, branch
`codex/metal-native-qualification-20261118`. Do not reset or broadly stage the
older dirty root checkout or other worktrees.

New qualified local source-build JAR:
`build/metal-1/weighted-z-build-2/jar/omicsmatic-0.4.0.jar`,
194,098,392 bytes, SHA-256
`0d9dad6c28c759c5097f38c200652859d905a0a5579b5aca679f9ea5386a9aa8`.
It is byte-identical to the earlier development JAR, now qualified for the
explicit SDK scope by new evidence. Keep the preceding frozen readers.
The published 0.4.0 release still predates these additions.

Private original runtime and paper intake remain under `build/metal-1/` as
documented in the handover. New controls are in `weighted-z-portable-{1,2}`,
`weighted-z-original-1`, `weighted-z-tiny-q-1`, `qualification-audit-1` and
`auditor-controls-1`. The full private LDAK store and all other worktree
overlays remain retained.

For future audits, use a NEW output directory with
`tools/literature/weighted_z/verify.py`; source changes require a new
qualification, never a historical fingerprint refresh. Missing prerequisites
produce SKIP3 in the auditor. A failed input, numerical or integrity gate
produces FAIL2, with no complete result. The library itself throws exceptions;
retain them and the original input, correct the stated cause and retry the
whole intended model. Exact source-build pins and warning actions are in
`docs/literature/weighted-z-development/VALIDATION.json` and `WARNINGS.md`.
