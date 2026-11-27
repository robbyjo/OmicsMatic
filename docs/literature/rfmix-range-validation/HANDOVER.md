# Continue the full 250-paper implementation goal

The RFMix restricted-range formatting bug is fixed, qualified and published. Feature commit: `68868454607d1d3afd8d5375e446b3c01db53755`. Current totals remain **250 papers, 453 labels, 492 providers, 2,733 observations, 1,825 reviewed/908 pending uses, and 172 scoped families (34 native + 138 upstream)**. There are 251 missing labels. This fix adds no tool-family or paper-review credit. Keep the original frozen 2023-10-23..2026-10-23 cohort and continue screening beyond registry labels.

## Completed interval fix

`RfmixRange` emits plain round-trip decimal Mbp endpoints. The source's first-hyphen parser can no longer consume a negative exponent as the interval delimiter. The adapter also compares the original printed effective bp range with independent native multiplication/truncation, including when the SNP population alone would not expose a mismatch. No original scientific source, genotype, threshold or output was repaired.

[Qualification](QUALIFICATION.json) seals 46 native tests per OS, 13 current original captures/fresh replays, five independent restricted-range original comparisons, reproduction of the previous JAR's failure, and refusal controls. Restricted cases include subnormal and negative-zero starts, fractional bp truncation and a synthetic short-coordinate interval retaining 151 loci. Five complete core model output sets still match the prior originals, including all 536 retained truth-state mismatches among 96,000 haplotype states. These are synthetic software checks; no author-recipe, biological-accuracy or convergence claim is made.

The exact predecessor source archive and all original seals remain unchanged. `verify_rfmix_workflows.py` recognizes this explicit successor, validates its current qualification and exact source derivations, then preserves the predecessor's historical interpretation. Old private evidence still validates with the old producer/captures; the current verifier uses the new producer and all 13 cases. Do not refresh old hashes.

Keep the active checkout `D:/projects/JLinAlg/build/fu-1`, branch `codex/rfmix-range-20261127`. Current evidence: `build/rfmix-895`; producer: `build/rfmix-895/project-build-Windows-2/project-build/cli/omicsmatic-0.4.1.jar`, SHA256 `2ec14ffcf7a17f84d2cc0722a5428017088a6aca0f2f1ced4377c067fda477ec`. Keep both current producing JARs, including the failed first build, plus all four predecessor JARs under `build/rfmix-894`. Original model/runtime/source still use the same private academic RFMix v2.03-r0 profile and 38-file source tree. A newly built JAR requires NEW bindings and captures.

```console
python -B tools/literature/verify_rfmix_range_workflows.py --private build/rfmix-895 --xml-cache D:/projects/JLinAlg/build/literature-review/xml --upstream-source build/rfmix-892/original-v2-source-intake-1/source
python -B tools/literature/verify_rfmix_workflows.py --private build/rfmix-894
python -B tools/literature/rfmix_catalog.py
```

These are retained-evidence/source/metadata checks, not new inference. Add the current verifier's `--execute-replays` with NEW Linux scratch and the correct Linux Java to run actual original replay. If later code changes, use the archived matching context for historical checks. Canonical metadata remains unchanged by this fix; its effective registry is `docs/literature/rfmix-catalog-admission/REGISTRY.json` (492 providers), not the 425-definition base registry.

## Next high-impact work: WASP and Hornet

Private preliminary source evidence is already retained at `build/wasp-896/initial-source-census-1/{CENSUS,TRIAGE}.json`. All 250 original XML identities were checked. There are four main-text contexts and one bibliographic context. No WASP runtime/source archive was installed/downloaded, no new review was admitted and no implementation credit was awarded. Existing bulk STAR code explicitly excludes WASP/STARsolo support. Hornet is already a registered, missing label with one paper; do not add it again.

- **P007 / PMC13668659:** a modified WASP pipeline explicitly linked to [Hornet](https://github.com/TheFraserLab/Hornet), run independently against human/chimpanzee and human/gorilla references. Exact source revision and full study variant preparation remain unknown. Do not substitute standalone WASP for this fork.
- **P073 / PMC11631754:** STARsolo 2.7.10a's built-in `--waspOutputMode`, using each sample's post-QC VCF, with barcode/UMI context and retained unique proper pairs passing WASP. This differs from both standalone WASP and the current bulk STAR 2.7.11b adapter.
- **P157 / PMC11525184:** **WASP was explicitly omitted** from the sQTL filters. Its pending generic use summary must become an explicit omission, not an executed-method claim or a required runtime. Retain its original observation/source locator.
- **P187 / PMC12805840:** standalone WASP v0.3.4 with updates as of 2023-02-09, removing allelic mapping bias from pseudobulk ATAC BAMs. It approximated alignment with BWA-mem 0.7.17 and reported `-M -I 250,150`. The date/version statement does not authenticate an exact author commit.

The WASP observations for P007/P073 are already reviewed; P157/P187 remain pending. Check the separate Hornet observation before counting additional reviews. Read all original contexts before canonical changes. Official [WASP](https://github.com/bmvdgeijn/WASP) has independently usable mapping and CHT components; these mapping roles do not establish CHT use. Its current [license](https://raw.githubusercontent.com/bmvdgeijn/WASP/master/LICENSE) is Apache 2.0. Inspect the exact selected revision/license before runtime preparation. The [mapping documentation](https://raw.githubusercontent.com/bmvdgeijn/WASP/master/mapping/README.md) distinguishes text SNP inputs from phased HDF5 and source-defined read rejection rules. Hornet's phased two-sequence behavior must not be equated with every standalone mode. Native Java is preferred when practical; retain tested original adapters for substantial external components.

## Persistent warnings and cleanup

SIS-dependent analysis remains SKIPPED because the original writer does not certify final-iteration probabilities. P041's v1.5.4 remains skipped/unimplemented; v2 is not a substitute. Exact author reference panels, supplementary recipes, masking and dating remain open. P142's version stays unreported; P229's reported 0.2 cM windows differ from inspected current 50/5 SNP defaults. Cell Ranger stays excluded. MaxQuant/Andromeda/MaxDIA remain pending with runtime analyses skipped. Latest iDEP still needs its own current source/license assessment, preserving the literal legacy iDEP.96 review. ConstructASCATFiles/PREASCAT code-use permission remains pending. Previously accepted academic terms remain authorized; do not repeat approval prompts.

Missing/forbidden prerequisites produce prominent SKIP3 and skip dependent analysis, with precise recovery steps. Invalid/changed input, source, runtime, JAR, capture, model/QC, cancellation or resources produce FAIL2 and no complete result. Preserve every attempt and correct NEW paths. Keep original runtime/source/papers/raw process traces private. Literature HTMLs remain withheld from Pages. No release/tag was created.

Verified compiler/resource/cache copies were cleaned after publication. The recovery ZIP/plan is `build/rfmix-895/cleanup-plan-Windows-1`; the measured receipt is `build/rfmix-895/cleanup-Windows-1/RECEIPT.json`. [COMPLETION.json](COMPLETION.json) records exact totals and post-cleanup checks. Keep all 1,548 current protected evidence files, all 2,939 predecessor files, original inputs/outputs/failures, focused test classes/fixtures, original archives, required runtimes and all 19 worktrees. The 18 foreign worktrees' 51 tracked edits, HEADs and statuses remain unchanged. No worktree or unrelated process was removed/stopped. Apply mandatory verified task-local cleanup to every subsequent feature.
