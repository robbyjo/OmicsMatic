# IMPUTE2 continuation notes — 23 November 2026

The 250-paper goal remains active and incomplete. Canonical counts remain **156 scoped families (29 native, 127 upstream), 1,707 reviewed and 1,016 pending uses**. IMPUTE2's new [development checkpoint](IMPUTE2_CHECKPOINT_2026-11-23.md) adds zero family/review credit until guarded source-role admission is complete. Its implementation was pushed to main in `7616323cf10222a9eade93ef6c9ac0a79c70551e`; publication and cleanup receipts accompany this note.

The working checkout is `D:/projects/JLinAlg/build/fu-1`, branch `codex/impute-20261123`. Retain all other worktrees/indexes and the ten previously restricted local refs; do not merge or export those histories. The project-root checkout contains unrelated work. Publication is explicit selected-file, nonforce to main.

## Qualified component

`impute-tools` supplies PREPHASE, IMPUTE_GENOTYPES, IMPUTE_PREPHASED and MERGE_REFERENCE through unmodified original v2.3.2. Original default MCMC/template/buffer settings remain unchanged. Typed JSON is closed; no arbitrary flags or version substitutions. Native Java validates/captures Oxford inputs and audits source outputs. VERIFY and REPLAY actually refit the parent's captured bytes to a fresh disjoint output; `--inspect` only checks saved closure and exact producer identity. Unlabelled column ordinals do not identify people or establish independence.

The exact complete producer is:

```text
build/impute-543/normal-build-3/project-build/cli/omicsmatic-0.4.0.jar
bytes 199408465
SHA256 b7b15b83449bb2da767f5890ec46c8f1d8a6651fe11be1fa48209d12a1737d82
```

The original private runtime is `build/impute-543/original-runtime-1/impute_v2.3.2_x86_64_static/` within this checkout. User acceptance covers its exact bundled 2007 Oxford terms for an unmodified private academic noncommercial runtime; binaries and raw vendor traces stay private. The current generic website licence is a different document and must not be substituted for that acceptance. Original archive SHA256: `95615900464505ddff4251137523740c7f35ac2865eb1eac4d8584ab15a8c45f`. No emulator is needed or used. Help/no-argument probes crashed; real scientific fits established health. Bundled saved example outputs identify v2.3.1 and were not used as matching goldens.

Validation: 1,856 selected normal tests; four operations with RUN/VERIFY/REPLAY plus the composed Java-merge-output-to-imputation workflow; 15 completed fits and 16 full scientific-file comparisons to independent original controls. Twelve actual refusals passed, plus replay with canonical external input paths absent. Windows reports SKIP3 with the WSL remedy and launches no original executable. Use WSL distro `JLinAlg-Omics-SAIGE-20261026`, `/usr/bin/java` and `/usr/bin/python3`. The public checkpoint reader works with Python 3.10 and newer and can rehash the exact JAR plus 1,269 retained private evidence files without fitting.

Nearest-only GP bounds were empirically falsified. Private 3/6/9-decimal controls support zero-censoring below one output unit followed by nearest rounding for nonzero values. Native `RoundedGenotypeProbabilities` now has explicit quantization contracts. Adapter bounds are conditional on both unit mass and the zero-censor contract; printed GEN tokens establish neither premise. The original unphased example retains 25 incompatible typed vectors with no invented bounds. Raw probabilities, missing all-zero codes, source INFO, negative-one/nonfinite fields and original diagnostics remain unchanged. No wrapper normalization or silent posterior repair is allowed.

Keep the superseded first qualification at `build/impute-543/workflow-qualification-1/`, its `BOUNDS-NOT-ADMITTED.json`, and its exact historical producer at `normal-build-2/project-build/cli/omicsmatic-0.4.0.jar` (SHA256 `8e5e967f9ae023a71417c420c7297c2867f938064b40f4f10e7ec6122efb75d2`). Later captures use `workflow-qualification-2/` and `merged-panel-qualification-1/`. Native tests include 3,000 nearest-rounded and 3,000 independently censored synthetic distributions; 180,000 moment proxies from 60,000 original nine-place genotypes were also contained at the explicitly reported 1e-8 proxy tolerance. Do not upgrade these empirical checks into a formal global source proof or biological validation.

## Next work

1. Complete guarded source-role/catalog admission, preserving 1,707 unrelated reviewed uses. Audit whether partial edition coverage warrants an IMPUTE2-scoped family entry or whether the generic IMPUTE label must remain pending. Do not claim IMPUTE4/5 execution or silently route either to v2.3.2.
2. Use the fresh private all-250 census and six-paper anchors at `build/impute-543/primary-audit-1/`. Raw primary XML and licensed traces must stay outside GitHub. Source findings to retain:
   - P076: IMPUTE2, minor edition unreported; BioMe/1000G Phase3v5, INFO and ancestry-specific QC, allele-count dosage context.
   - P084: IMPUTE2, version unreported; UKBB SHAPEIT/HRC+UK10K. Preserve the source's MAC>100 removal wording and its “Minimac r2” quality label as written; do not silently reverse the comparator or relabel it IMPUTE INFO.
   - P136: explicit v2.3.2 for autosomal reference-panel merging and ToMMo imputation; chromosome-X merging uses BEAGLE separately. This is the strongest exact-edition role for the new adapter.
   - P140: explicit IMPUTE4.1.2_r300.3 in primary context, even though the near-name extraction's version field is null. Preserve raw extraction and separately record the contextual version.
   - P149: the Methods HMM comparison “similar to IMPUTE2” does not establish execution. Code Availability lists v2.3.1; retain uncertainty about the local operation rather than inventing one.
   - P180: IMPUTE5, minor edition unreported; masked Armenian-genome comparison with 1000G versus an Armenian-augmented panel. Source validation uses squared Pearson correlation between dosage and comparison genotypes. IMPUTE2 is not equivalent.
3. Prioritize other high-impact pending tools as appropriate; recorded frequency is triage, not licensing or qualification. The last screening ranked GraphPad Prism/FlowJo/FACSDiva/Space Ranger/bcl2fastq highly, followed by IMPUTE, MaxQuant and nf-core. Existing native Tukey/Dunn components do not implement the whole Prism suite; several nf-core pipeline components already exist. Check current coverage before duplicating work.

The source-pinned preceding catalog reader cannot simply be rerun against the changed dispatcher. Its exact base-13b source context and verified archive are retained at `build/impute-543/predecessor-context-1/source/` and `source.zip`. The archived reader passed; 1,698 predecessor members stayed unchanged, with an exact three-line main extension. Retain this chain and historical JARs rather than refreshing old source hashes. The public evidence archive contains component sources, metadata, comparisons and declared failures, excluding original binaries, bundled licence contents, raw vendor traces, primary XML and author resources.

Finish every subsequent feature with validation, requested main publication and guarded cleanup. Compiler duplicates are reproducible; original inputs, runtimes, all scientific/failed/superseded captures, exact JARs, source contexts and test reports are retained. See `impute2-checkpoint/CLEANUP.json` for the actual measured cleanup rather than guessing from directory age. This is a local validated development checkpoint and a main-branch source publication, not a new release, whole-paper reproduction or completed 250-paper goal.
